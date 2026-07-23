package re.lilith.strand.client.voice

import re.lilith.strand.StrandState
import re.lilith.strand.client.gui.StrandVoiceMicModeScreen
import re.lilith.strand.client.gui.StrandVoiceScreen
import re.lilith.strand.voice.VoiceManager
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component

object VoiceClient {
    fun clientTick(mc: Minecraft) {
        VoiceManager.setPttHeld(VoiceKeys.pushToTalk.isDown)
        VoiceManager.setGlobalHeld(VoiceKeys.global.isDown)

        while (VoiceKeys.toggleMute.consumeClick()) {
            VoiceManager.toggleSelfMuted()
            feedback(mc, if (VoiceManager.selfMuted) "Microphone muted" else "Microphone on", VoiceManager.selfMuted)
        }
        while (VoiceKeys.toggleDeafen.consumeClick()) {
            VoiceManager.toggleDeafened()
            feedback(mc, if (VoiceManager.deafened) "Voice deafened" else "Voice undeafened", VoiceManager.deafened)
        }
        while (VoiceKeys.openVoice.consumeClick()) {
            if (mc.gui.screen() == null) mc.setScreenAndShow(StrandVoiceScreen(null))
        }

        ProximityVoiceController.tick()

        promptMicModeIfNeeded(mc)
    }

    private fun feedback(mc: Minecraft, message: String, warn: Boolean) {
        val color = if (warn) ChatFormatting.YELLOW else ChatFormatting.GREEN
        mc.player?.sendSystemMessage(Component.literal("[Voice] $message").withStyle(color))
    }

    private fun promptMicModeIfNeeded(mc: Minecraft) {
        val controller = StrandState.controller ?: return
        if (!controller.needsMicModePrompt) return
        if (mc.player == null || mc.gui.screen() != null) return
        mc.setScreenAndShow(StrandVoiceMicModeScreen(null))
    }
}
