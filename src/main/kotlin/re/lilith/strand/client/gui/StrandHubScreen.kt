package re.lilith.strand.client.gui

import re.lilith.strand.session.ConnState
import re.lilith.strand.voice.VoiceManager
import net.minecraft.ChatFormatting
import net.minecraft.client.gui.components.toasts.SystemToast
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

class StrandHubScreen(parent: Screen?) : StrandScreen(Component.literal("Strand"), parent) {

    override fun build() {
        val c = controller
        val cx = width / 2
        var y = height / 5

        label(cx, y, Component.literal("Strand").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD))
        y += 14

        label(cx, y, connectionLine())
        y += 11
        label(cx, y, hostingLine())
        y += 11
        label(cx, y, Component.literal("Invites: ${if (c?.invitesBlocked() == true) "blocked" else "allowed"}").withStyle(ChatFormatting.GRAY))
        y += 11
        label(cx, y, voiceLine())
        y += 18

        val bw = 204
        val gap = 24

        if (c != null && c.isHosting) {
            button(cx, y, bw, Component.literal("Stop hosting")) { c.unhost() }
            y += gap
        } else {
            if (minecraft.hasSingleplayerServer()) {
                button(cx, y, bw, Component.literal("Host this world")) { c?.host() }
                y += gap
            }
        }

        y += 5

        // we use these to join, and it shouldn't be shown if the player is already hosting
        if (c != null && !c.isHosting) {
            button(cx, y, bw, Component.literal("Join by code")) { minecraft.setScreenAndShow(StrandJoinScreen(this)) }
            y += gap

            val pending = c.pendingInvites().size
            val invitesLabel = if (pending > 0) "Invites ($pending)" else "Invites"
            button(cx, y, bw, Component.literal(invitesLabel)) { minecraft.setScreenAndShow(StrandInvitesScreen(this)) }
            y += gap + 5
        }

        if (c != null && c.isHosting) {
            button(cx, y, bw, Component.literal("Invite a player")) { minecraft.setScreenAndShow(StrandInviteScreen(this)) }
            y += gap
            button(cx, y, bw, Component.literal("Copy invite code")) { copyCode() }
            y += gap + 5
        }

        if (c != null && c.isInVoice) {
            button(cx, y, bw, Component.literal("Voice chat (${VoiceManager.participants().size + 1})")) {
                minecraft.setScreenAndShow(StrandVoiceScreen(this))
            }
            y += gap
        }

        button(cx, y, bw, Component.literal("Settings")) { minecraft.setScreenAndShow(StrandSettingsScreen(this)) }
        y += gap

        button(cx, y, bw, Component.literal("Done")) { onClose() }
    }

    private fun connectionLine(): Component {
        val c = controller ?: return Component.literal("Unavailable").withStyle(ChatFormatting.RED)
        return when (c.connectionState()) {
            ConnState.LoggedIn -> Component.literal("Connected as ${c.username() ?: "?"}").withStyle(ChatFormatting.GREEN)
            ConnState.Connecting -> Component.literal("Connecting...").withStyle(ChatFormatting.YELLOW)
            ConnState.Failed -> Component.literal("Connection failed: ${c.lastError() ?: "unknown"}").withStyle(ChatFormatting.RED)
            ConnState.LoggedOut -> Component.literal("Not connected").withStyle(ChatFormatting.GRAY)
        }
    }

    private fun hostingLine(): Component {
        val c = controller
        return if (c != null && c.isHosting) {
            Component.literal("Hosting  |  code ${c.currentCode()}").withStyle(ChatFormatting.GREEN)
        } else {
            Component.literal("Not hosting").withStyle(ChatFormatting.GRAY)
        }
    }

    private fun voiceLine(): Component {
        val c = controller
        return if (c != null && c.isInVoice) {
            Component.literal("Voice: connected").withStyle(ChatFormatting.GREEN)
        } else {
            Component.literal("Voice: off").withStyle(ChatFormatting.GRAY)
        }
    }

    private fun copyCode() {
        val code = controller?.currentCode() ?: return
        minecraft.keyboardHandler.clipboard = code
        runCatching {
            SystemToast.add(
                minecraft.gui.toastManager(),
                SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                Component.literal("Strand"),
                Component.literal("Invite code copied"),
            )
        }
    }
}
