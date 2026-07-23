package re.lilith.strand.client.gui

import re.lilith.strand.StrandState
import re.lilith.strand.voice.MicMode
import net.minecraft.ChatFormatting
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

class StrandVoiceMicModeScreen(parent: Screen?) : StrandScreen(Component.literal("Voice input"), parent) {

    private var chosen = false

    override fun build() {
        val cx = width / 2
        var y = height / 4

        label(cx, y, Component.literal("How do you want to talk?").withStyle(ChatFormatting.BOLD))
        y += 16
        label(cx, y, Component.literal("You can change this later in Voice settings.").withStyle(ChatFormatting.GRAY))
        y += 24

        button(cx, y, 240, Component.literal("Push-to-talk (hold a key)")) { choose(MicMode.PUSH_TO_TALK) }
        y += 24
        label(cx, y, Component.literal("Only transmit while holding the key").withStyle(ChatFormatting.DARK_GRAY))
        y += 22

        button(cx, y, 240, Component.literal("Voice activity (open mic)")) { choose(MicMode.VOICE_ACTIVITY) }
        y += 24
        label(cx, y, Component.literal("Transmit automatically when you speak").withStyle(ChatFormatting.DARK_GRAY))
    }

    private fun choose(mode: MicMode) {
        chosen = true
        StrandState.controller?.setMicMode(mode)
        onClose()
    }

    override fun onClose() {
        if (!chosen) {
            chosen = true
            StrandState.controller?.setMicMode(MicMode.PUSH_TO_TALK)
        }
        super.onClose()
    }
}
