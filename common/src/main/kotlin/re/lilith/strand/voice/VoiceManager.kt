package re.lilith.strand.voice

import gg.sona.eos.EosResult
import gg.sona.eos.common.ProductUserId
import gg.sona.eos.rtc.EosRtcAudioStatus
import gg.sona.eos.rtc.EosRtcParticipantStatus
import re.lilith.strand.eos.EosManager
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

enum class MicMode { PUSH_TO_TALK, VOICE_ACTIVITY }

data class VoiceDevice(val id: String, val name: String, val isDefault: Boolean)

class VoiceParticipant(val id: ProductUserId) {
    val puid: String = id.toStringValue()
    @Volatile
    var mcUuid: String? = null
    @Volatile
    var username: String? = null
    @Volatile
    var speaking: Boolean = false
    @Volatile
    var audioStatus: EosRtcAudioStatus = EosRtcAudioStatus.Enabled
    @Volatile
    var global: Boolean = false
    @Volatile
    var locallyMuted: Boolean = false
    @Volatile
    var lastVolume: Float = -1f
    val serverMuted: Boolean get() = audioStatus == EosRtcAudioStatus.AdminDisabled
}

object VoiceManager {

    private val logger = LoggerFactory.getLogger("strand/voice")
    private val json = Json { ignoreUnknownKeys = true }

    private const val DATA_TYPE = "t"
    private const val ID_MCUUID = "u"
    private const val ID_NAME = "n"
    private const val CFG_NEAR = "near"
    private const val CFG_MAX = "max"
    private const val CFG_ENABLED = "en"
    private const val GLOBAL_VALUE = "v"
    private const val T_ID = "id"
    private const val T_GLOBAL = "g"
    private const val T_CONFIG = "cfg"

    private const val IDENTITY_INTERVAL_NANOS = 4_000_000_000L
    private const val VOLUME_EPSILON = 2f

    private val participants = ConcurrentHashMap<Long, VoiceParticipant>()
    private val listeners = CopyOnWriteArrayList<() -> Unit>()
    private val notifications = mutableListOf<() -> Unit>()

    @Volatile
    private var roomId: String? = null
    @Volatile
    private var hostPuid: ProductUserId = ProductUserId.Invalid
    @Volatile
    private var localMcUuid: String = ""
    @Volatile
    private var localName: String = ""
    @Volatile
    private var connected = false

    @Volatile
    var micMode: MicMode = MicMode.PUSH_TO_TALK
        private set
    @Volatile
    var selfMuted = false
        private set
    @Volatile
    var deafened = false
        private set
    @Volatile
    var masterVolume = 1f
        private set
    @Volatile
    var inputGain = 1f
        private set
    @Volatile
    var inputSensitivity = 0.08f
        private set

    @Volatile
    private var pttHeld = false
    @Volatile
    private var globalHeld = false
    @Volatile
    private var vadOpen = false
    @Volatile
    private var vadHangUntilNanos = 0L
    @Volatile
    private var lastSendingEnabled: Boolean? = null
    @Volatile
    private var lastSendVolume = -1f
    @Volatile
    private var lastGlobalBroadcast = false
    @Volatile
    private var lastIdentityNanos = 0L

    @Volatile
    var proxNear = 8
        private set
    @Volatile
    var proxMax = 48
        private set
    @Volatile
    var voiceEnabled = true
        private set
    @Volatile
    var isHost = false
        private set

    val isConnected: Boolean get() = connected
    val roomName: String? get() = roomId
    fun participants(): List<VoiceParticipant> = participants.values.toList()
    fun participant(puidRaw: Long): VoiceParticipant? = participants[puidRaw]

    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    private fun changed() {
        listeners.forEach { runCatching { it() } }
    }

    fun enterRoom(
        roomId: String,
        clientBaseUrl: String,
        token: String,
        hostProductUserId: String,
        localMcUuid: String,
        localName: String,
        near: Int,
        max: Int,
        voiceEnabled: Boolean,
        isHost: Boolean,
    ) {
        leaveRoom()
        this.roomId = roomId
        this.hostPuid = ProductUserId.fromString(hostProductUserId)
        this.localMcUuid = localMcUuid
        this.localName = localName
        this.proxNear = near
        this.proxMax = max
        this.voiceEnabled = voiceEnabled
        this.isHost = isHost

        val local = EosManager.localUser
        EosManager.call {
            EosManager.rtc.joinRoom(local, roomId, clientBaseUrl, token)
        }.thenCompose { it }.whenComplete { result, error ->
            if (error != null || result == null || result.result != EosResult.Success) {
                logger.warn("Voice joinRoom failed: {}", error?.message ?: result?.result)
                connected = false
                changed()
                return@whenComplete
            }
            logger.info("Joined voice room {}", roomId)
            connected = true
            EosManager.post { registerNotifications(local, roomId) }
            applyAudioState()
            broadcastIdentity(force = true)
            if (isHost) broadcastConfig()
            changed()
        }
    }

    fun leaveRoom() {
        val room = roomId ?: return
        val local = EosManager.localUser
        connected = false
        EosManager.post {
            notifications.forEach { runCatching { it() } }
            notifications.clear()
            runCatching { EosManager.rtc.leaveRoom(local, room) }
        }
        roomId = null
        hostPuid = ProductUserId.Invalid
        participants.clear()
        lastSendingEnabled = null
        lastSendVolume = -1f
        changed()
    }

    private fun registerNotifications(local: ProductUserId, room: String) {
        val rtc = EosManager.rtc
        val audio = rtc.audio
        val data = rtc.data

        val statusHandle = rtc.addNotifyParticipantStatusChanged(local, room) { info ->
            if (info.participantId.raw == local.raw) return@addNotifyParticipantStatusChanged
            when (info.status) {
                EosRtcParticipantStatus.Joined -> {
                    participants.getOrPut(info.participantId.raw) { VoiceParticipant(info.participantId) }
                    broadcastIdentity(force = true)
                }

                EosRtcParticipantStatus.Left -> participants.remove(info.participantId.raw)
            }
            changed()
        }
        notifications += { rtc.removeNotifyParticipantStatusChanged(statusHandle) }

        val updatedHandle = audio.addNotifyParticipantUpdated(local, room) { info ->
            if (info.participantId.raw == local.raw) return@addNotifyParticipantUpdated
            val p = participants.getOrPut(info.participantId.raw) { VoiceParticipant(info.participantId) }
            p.speaking = info.speaking
            p.audioStatus = info.audioStatus
            changed()
        }
        notifications += { audio.removeNotifyParticipantUpdated(updatedHandle) }

        val inputHandle = audio.addNotifyAudioInputState(local, room) { info ->
            val failed = info.status.name.contains("Disconnected") || info.status.name == "Failed"
            EosManager.voiceDeviceFailed = failed
            if (failed) {
                logger.warn("Voice input device state: {}", info.status); changed()
            }
        }
        notifications += { audio.removeNotifyAudioInputState(inputHandle) }

        val dataHandle = data.addNotifyDataReceived(local, room) { info ->
            runCatching { onData(info.sender, info.data) }
                .onFailure { logger.debug("Bad voice data packet", it) }
        }
        notifications += { data.removeNotifyDataReceived(dataHandle) }

        val beforeSendHandle = audio.addNotifyAudioBeforeSend(local, room) { info ->
            if (micMode == MicMode.VOICE_ACTIVITY) updateVad(info.buffer.rms())
        }
        notifications += { audio.removeNotifyAudioBeforeSend(beforeSendHandle) }
    }

    private fun onData(sender: ProductUserId, bytes: ByteArray) {
        val obj = json.parseToJsonElement(bytes.decodeToString()) as? JsonObject ?: return
        when ((obj[DATA_TYPE] as? JsonPrimitive)?.contentOrNull) {
            T_ID -> {
                val p = participants.getOrPut(sender.raw) { VoiceParticipant(sender) }
                p.mcUuid = (obj[ID_MCUUID] as? JsonPrimitive)?.contentOrNull
                p.username = (obj[ID_NAME] as? JsonPrimitive)?.contentOrNull
                changed()
            }

            T_GLOBAL -> {
                val p = participants.getOrPut(sender.raw) { VoiceParticipant(sender) }
                p.global = (obj[GLOBAL_VALUE] as? JsonPrimitive)?.boolean ?: false
                changed()
            }

            T_CONFIG -> if (sender.raw == hostPuid.raw && !isHost) {
                (obj[CFG_NEAR] as? JsonPrimitive)?.let { proxNear = it.int }
                (obj[CFG_MAX] as? JsonPrimitive)?.let { proxMax = it.int }
                (obj[CFG_ENABLED] as? JsonPrimitive)?.let { voiceEnabled = it.boolean }
                changed()
            }
        }
    }

    private fun send(obj: JsonObject) {
        val room = roomId ?: return
        val bytes = obj.toString().encodeToByteArray()
        EosManager.post { runCatching { EosManager.rtc.data.sendData(EosManager.localUser, room, bytes) } }
    }

    private fun broadcastIdentity(force: Boolean = false) {
        if (!connected) return
        val nowNanos = System.nanoTime()
        if (!force && nowNanos - lastIdentityNanos < IDENTITY_INTERVAL_NANOS) return
        lastIdentityNanos = nowNanos
        send(buildJsonObject {
            put(DATA_TYPE, JsonPrimitive(T_ID))
            put(ID_MCUUID, JsonPrimitive(localMcUuid))
            put(ID_NAME, JsonPrimitive(localName))
        })
    }

    fun broadcastConfig() {
        if (!isHost) return
        send(buildJsonObject {
            put(DATA_TYPE, JsonPrimitive(T_CONFIG))
            put(CFG_NEAR, JsonPrimitive(proxNear))
            put(CFG_MAX, JsonPrimitive(proxMax))
            put(CFG_ENABLED, JsonPrimitive(voiceEnabled))
        })
    }

    fun setMicMode(mode: MicMode) {
        if (micMode == mode) return
        micMode = mode
        vadOpen = false
        applyTransmit()
        changed()
    }

    fun setInputSensitivity(v: Float) {
        inputSensitivity = v.coerceIn(0.005f, 0.5f)
    }

    fun setSelfMuted(muted: Boolean) {
        selfMuted = muted; applyAudioState(); changed()
    }

    fun toggleSelfMuted() = setSelfMuted(!selfMuted)
    fun setDeafened(value: Boolean) {
        deafened = value; applyReceivingVolume(); changed()
    }

    fun toggleDeafened() = setDeafened(!deafened)
    fun setMasterVolume(v: Float) {
        masterVolume = v.coerceIn(0f, 1f); applyReceivingVolume()
    }

    fun setInputGain(v: Float) {
        inputGain = v.coerceIn(0f, 2f); applyTransmit()
    }

    fun setPttHeld(held: Boolean) {
        if (pttHeld != held) {
            pttHeld = held; applyTransmit()
        }
    }

    fun setGlobalHeld(held: Boolean) {
        if (globalHeld == held) return
        globalHeld = held
        applyTransmit()
        if (lastGlobalBroadcast != held) {
            lastGlobalBroadcast = held
            send(buildJsonObject {
                put(DATA_TYPE, JsonPrimitive(T_GLOBAL))
                put(GLOBAL_VALUE, JsonPrimitive(held))
            })
        }
    }

    val isTransmitting: Boolean
        get() = !selfMuted && (globalHeld || if (micMode == MicMode.VOICE_ACTIVITY) vadOpen else pttHeld)

    val isGlobalTransmitting: Boolean get() = globalHeld && !selfMuted

    private fun updateVad(rms: Float) {
        val now = System.nanoTime()
        if (rms >= inputSensitivity) {
            vadOpen = true
            vadHangUntilNanos = now + 350_000_000L
        } else if (now > vadHangUntilNanos) {
            vadOpen = false
        }
        applyTransmit()
    }

    private fun applyAudioState() {
        val room = roomId ?: return
        val local = EosManager.localUser
        val enabled = !selfMuted
        if (lastSendingEnabled != enabled) {
            lastSendingEnabled = enabled
            val status = if (enabled) EosRtcAudioStatus.Enabled else EosRtcAudioStatus.Disabled
            EosManager.post { runCatching { EosManager.rtc.audio.updateSending(local, room, status) } }
        }
        applyTransmit()
        applyReceivingVolume()
    }

    private fun applyTransmit() {
        val room = roomId ?: return
        val local = EosManager.localUser
        val volume = if (isTransmitting) (inputGain * 100f).coerceIn(0f, 100f) else 0f
        if (kotlin.math.abs(volume - lastSendVolume) < 0.5f) return
        lastSendVolume = volume
        EosManager.post { runCatching { EosManager.rtc.audio.updateSendingVolume(local, room, volume) } }
    }

    private fun applyReceivingVolume() {
        val room = roomId ?: return
        val local = EosManager.localUser
        val volume = if (deafened) 0f else masterVolume * 100f
        EosManager.post { runCatching { EosManager.rtc.audio.updateReceivingVolume(local, room, volume) } }
    }

    fun setParticipantVolume(p: VoiceParticipant, volume01: Float) {
        val room = roomId ?: return
        val target = volume01.coerceIn(0f, 1f) * 100f
        if (p.lastVolume >= 0 && kotlin.math.abs(target - p.lastVolume) < VOLUME_EPSILON) return
        p.lastVolume = target
        EosManager.post {
            runCatching { EosManager.rtc.audio.updateParticipantVolume(EosManager.localUser, room, p.id, target) }
        }
    }

    fun applyIdentity(puid: String, mcUuid: String, username: String) {
        val id = ProductUserId.fromString(puid)
        if (id.raw == EosManager.localUser.raw || id.raw == 0L) return
        val p = participants.getOrPut(id.raw) { VoiceParticipant(id) }
        if (p.mcUuid != mcUuid || p.username != username) {
            p.mcUuid = mcUuid
            p.username = username
            changed()
        }
    }

    fun setParticipantLocallyMuted(p: VoiceParticipant, muted: Boolean) {
        p.locallyMuted = muted
        changed()
    }

    @Volatile
    var inputDevices: List<VoiceDevice> = emptyList()
        private set
    @Volatile
    var outputDevices: List<VoiceDevice> = emptyList()
        private set

    fun setInputDevice(id: String) {
        if (!EosManager.isInitialized) return
        EosManager.post {
            runCatching {
                EosManager.rtc.audio.setInputDeviceSettings(
                    EosManager.localUser,
                    id.ifBlank { null })
            }
        }
    }

    fun setOutputDevice(id: String) {
        if (!EosManager.isInitialized) return
        EosManager.post {
            runCatching {
                EosManager.rtc.audio.setOutputDeviceSettings(
                    EosManager.localUser,
                    id.ifBlank { null })
            }
        }
    }

    fun refreshDevices() {
        if (!EosManager.isInitialized) return
        EosManager.call { EosManager.rtc.audio.queryInputDevicesInformation() }.thenCompose { it }
            .whenComplete { _, _ ->
                EosManager.post {
                    val a = EosManager.rtc.audio
                    inputDevices =
                        (0 until a.getInputDevicesCount()).mapNotNull { a.copyInputDeviceInformationByIndex(it) }
                            .map { VoiceDevice(it.deviceId, it.deviceName, it.isDefault) }
                    changed()
                }
            }
        EosManager.call { EosManager.rtc.audio.queryOutputDevicesInformation() }.thenCompose { it }
            .whenComplete { _, _ ->
                EosManager.post {
                    val a = EosManager.rtc.audio
                    outputDevices =
                        (0 until a.getOutputDevicesCount()).mapNotNull { a.copyOutputDeviceInformationByIndex(it) }
                            .map { VoiceDevice(it.deviceId, it.deviceName, it.isDefault) }
                    changed()
                }
            }
    }

    fun setHostRange(near: Int, max: Int) {
        proxNear = near.coerceIn(1, 512)
        proxMax = max.coerceIn(proxNear, 512)
        if (isHost) broadcastConfig()
        changed()
    }

    fun setVoiceEnabledLocal(enabled: Boolean) {
        voiceEnabled = enabled
        if (isHost) broadcastConfig()
        changed()
    }

    fun onFrame() {
        if (connected) broadcastIdentity()
    }
}