package re.lilith.strand.client.gui

import re.lilith.strand.StrandState
import re.lilith.strand.voice.MicMode
import re.lilith.strand.voice.VoiceDevice
import re.lilith.strand.voice.VoiceManager
import net.minecraft.ChatFormatting
import net.minecraft.client.gui.components.CycleButton
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

class StrandVoiceSettingsScreen(parent: Screen?) : StrandScreen(Component.literal("Voice settings"), parent) {

    private val systemDefault = VoiceDevice("", "System default", true)
    private var hostNear = VoiceManager.proxNear
    private var hostMax = VoiceManager.proxMax

    override fun build() {
        VoiceManager.refreshDevices()
        val cx = width / 2
        val w = 260
        val left = cx - w / 2
        var y = 30

        label(cx, y - 14, Component.literal("Voice settings").withStyle(ChatFormatting.BOLD))

        addRenderableWidget(
            CycleButton.builder<MicMode>(
                { Component.literal(if (it == MicMode.VOICE_ACTIVITY) "Voice activity" else "Push-to-talk") },
                VoiceManager.micMode,
            )
                .withValues(MicMode.PUSH_TO_TALK, MicMode.VOICE_ACTIVITY)
                .create(left, y, w, 20, Component.literal("Input mode")) { _, value ->
                    controller?.setMicMode(value)
                    rebuildWidgets()
                },
        )
        y += 24

        addRenderableWidget(StrandSlider(left, y, w, 20, "Output volume", 0.0, 1.0, VoiceManager.masterVolume.toDouble(),
            { "${(it * 100).toInt()}%" }) { controller?.setMasterVolume(it.toFloat()) })
        y += 24
        addRenderableWidget(StrandSlider(left, y, w, 20, "Mic gain", 0.0, 2.0, VoiceManager.inputGain.toDouble(),
            { "${(it * 100).toInt()}%" }) { controller?.setInputGain(it.toFloat()) })
        y += 24

        if (VoiceManager.micMode == MicMode.VOICE_ACTIVITY) {
            addRenderableWidget(StrandSlider(left, y, w, 20, "Mic threshold", 0.005, 0.3, VoiceManager.inputSensitivity.toDouble(),
                { "${(it * 100).toInt() / 3}%" }) { controller?.setInputSensitivity(it.toFloat()) })
            y += 24
        }

        val effMax = minOf(VoiceManager.proxMax, StrandState.config.voice.proximityMax)
        addRenderableWidget(StrandSlider(left, y, w, 20, "Hearing range", 4.0, 128.0, effMax.toDouble(),
            { "${it.toInt()} blocks" }) { controller?.setProximityMaxPreference(it.toInt()) })
        y += 24

        // Devices
        addRenderableWidget(deviceButton(left, y, w, "Microphone", VoiceManager.inputDevices, StrandState.config.voice.inputDeviceId) {
            controller?.setInputDevice(it)
        })
        y += 24
        addRenderableWidget(deviceButton(left, y, w, "Speaker", VoiceManager.outputDevices, StrandState.config.voice.outputDeviceId) {
            controller?.setOutputDevice(it)
        })
        y += 24

        val hud = StrandState.config.voice.hudEnabled
        button(cx, y, w, Component.literal("HUD overlay: ${if (hud) "ON" else "OFF"}")) {
            StrandState.updateVoice { it.copy(hudEnabled = !hud) }
            rebuildWidgets()
        }
        y += 26

        if (VoiceManager.isHost) {
            label(cx, y, Component.literal("Host: hearing range for everyone").withStyle(ChatFormatting.GOLD))
            y += 12
            addRenderableWidget(StrandSlider(left, y, w, 20, "Full-volume radius", 1.0, 64.0, hostNear.toDouble(),
                { "${it.toInt()} blocks" }) { hostNear = it.toInt(); controller?.setHostRange(hostNear, hostMax) })
            y += 24
            addRenderableWidget(StrandSlider(left, y, w, 20, "Max hearing radius", 8.0, 256.0, hostMax.toDouble(),
                { "${it.toInt()} blocks" }) { hostMax = it.toInt(); controller?.setHostRange(hostNear, hostMax) })
            y += 24
        }

        button(cx, y + 4, w, Component.literal("Back")) { onClose() }
    }

    private fun deviceButton(
        x: Int, y: Int, w: Int, label: String, devices: List<VoiceDevice>, selectedId: String, onSelect: (String) -> Unit,
    ): CycleButton<VoiceDevice> {
        val values = listOf(systemDefault) + devices
        val initial = values.firstOrNull { it.id == selectedId } ?: systemDefault
        return CycleButton.builder<VoiceDevice>({ Component.literal(shorten(it.name)) }, initial)
            .withValues(values)
            .create(x, y, w, 20, Component.literal(label)) { _, value -> onSelect(value.id) }
    }

    private fun shorten(name: String): String = if (name.length > 24) name.take(23) + "…" else name
}
