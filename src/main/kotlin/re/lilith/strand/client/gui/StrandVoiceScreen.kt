package re.lilith.strand.client.gui

import re.lilith.strand.StrandState
import re.lilith.strand.voice.VoiceManager
import re.lilith.strand.voice.VoiceParticipant
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.StringWidget
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

class StrandVoiceScreen(parent: Screen?) : StrandScreen(Component.literal("Voice"), parent) {

    private companion object {
        const val PER_PAGE = 5
    }

    private var page = 0
    private var voiceListening = false
    private var builtKey = ""
    private val nameWidgets = mutableListOf<Pair<StringWidget, VoiceParticipant>>()

    private val voiceRefresh: () -> Unit = {
        val mc = Minecraft.getInstance()
        mc.execute { if (mc.gui.screen() === this && structuralKey() != builtKey) rebuildWidgets() }
    }

    override fun build() {
        if (!voiceListening) {
            VoiceManager.addListener(voiceRefresh)
            VoiceManager.refreshDevices()
            voiceListening = true
        }
        nameWidgets.clear()
        builtKey = structuralKey()

        val cx = width / 2
        var y = 22

        label(cx, y, Component.literal("Proximity Voice").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD))
        y += 20

        button(cx - 106, y, 100, muteLabel()) { VoiceManager.toggleSelfMuted() }
        button(cx + 6, y, 100, deafenLabel()) { VoiceManager.toggleDeafened() }
        y += 24
        button(cx, y, 206, Component.literal("Voice settings")) {
            minecraft.setScreenAndShow(StrandVoiceSettingsScreen(this))
        }
        y += 26

        if (VoiceManager.isHost) {
            val enabled = VoiceManager.voiceEnabled
            button(cx, y, 206, Component.literal("Global voice: ${if (enabled) "ON" else "OFF"}")
                .withStyle(if (enabled) ChatFormatting.GREEN else ChatFormatting.RED)) {
                controller?.setVoiceEnabled(!enabled)
            }
            y += 24
        }

        buildParticipants(cx, y)

        button(cx, height - 28, 206, Component.literal("Done")) { onClose() }
    }

    private fun buildParticipants(cx: Int, top: Int) {
        var y = top
        val all = VoiceManager.participants().sortedBy { (it.username ?: it.puid).lowercase() }
        label(cx, y, Component.literal("In voice: ${all.size + 1}").withStyle(ChatFormatting.GRAY))
        y += 14

        val pages = maxOf(1, (all.size + PER_PAGE - 1) / PER_PAGE)
        page = page.coerceIn(0, pages - 1)
        val slice = all.drop(page * PER_PAGE).take(PER_PAGE)

        val host = VoiceManager.isHost
        val rowW = if (host) 300 else 240
        val left = cx - rowW / 2
        val nameW = 92

        for (p in slice) {
            val nameWidget = StringWidget(left + 2, y + 6, nameW - 6, 9, Component.literal(displayName(p)), font)
            addRenderableWidget(nameWidget)
            nameWidgets += nameWidget to p

            val uuid = p.mcUuid
            val initial = uuid?.let { StrandState.config.voice.perPlayerVolume[it] } ?: 1.0f
            addRenderableWidget(
                StrandSlider(left + nameW, y, 96, 20, "Vol", 0.0, 2.0, initial.toDouble(),
                    { "${(it * 100).toInt()}%" }) { v -> uuid?.let { controller?.setPlayerVolume(it, v.toFloat()) } },
            )
            val muted = p.locallyMuted || (uuid != null && uuid in StrandState.config.voice.perPlayerMuted)
            rowButton(left + nameW + 100, y, 20, if (muted) "M" else "m") { controller?.setPlayerLocalMuted(p, !muted) }
            if (host) {
                rowButton(left + nameW + 124, y, 44, if (p.serverMuted) "unmute" else "mute") {
                    controller?.voiceMute(p.puid, !p.serverMuted)
                }
                rowButton(left + nameW + 172, y, 40, "kick") { controller?.voiceKick(p.puid) }
            }
            y += 22
        }

        if (pages > 1) {
            button(cx - 106, y, 100, Component.literal("< Prev")) { page = (page - 1 + pages) % pages; rebuildWidgets() }
            button(cx + 6, y, 100, Component.literal("Next >")) { page = (page + 1) % pages; rebuildWidgets() }
        }
    }

    override fun tick() {
        if (structuralKey() != builtKey) {
            rebuildWidgets()
            return
        }
        for ((widget, p) in nameWidgets) {
            val speaking = p.speaking && !p.locallyMuted && !p.serverMuted
            val fmt = when {
                p.serverMuted -> ChatFormatting.RED
                speaking -> ChatFormatting.GREEN
                else -> ChatFormatting.WHITE
            }
            val text = if (speaking) "● ${displayName(p)}" else displayName(p)
            widget.setMessage(Component.literal(text).withStyle(fmt))
        }
    }

    private fun rowButton(x: Int, y: Int, w: Int, text: String, onPress: () -> Unit) {
        addRenderableWidget(Button.builder(Component.literal(text)) { _ -> onPress() }.bounds(x, y, w, 20).build())
    }

    private fun displayName(p: VoiceParticipant): String {
        val name = p.username ?: "player"
        return if (name.length > 12) name.take(11) + "…" else name
    }

    private fun muteLabel(): Component = if (VoiceManager.selfMuted)
        Component.literal("Unmute").withStyle(ChatFormatting.RED) else Component.literal("Mute")

    private fun deafenLabel(): Component = if (VoiceManager.deafened)
        Component.literal("Undeafen").withStyle(ChatFormatting.RED) else Component.literal("Deafen")

    private fun structuralKey(): String {
        val ids = VoiceManager.participants().map { it.puid }.sorted().joinToString(",")
        return "$ids|host=${VoiceManager.isHost}|en=${VoiceManager.voiceEnabled}|page=$page"
    }

    override fun removed() {
        if (voiceListening) {
            VoiceManager.removeListener(voiceRefresh)
            voiceListening = false
        }
        super.removed()
    }
}
