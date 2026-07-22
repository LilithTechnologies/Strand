/*
 * Strand - Open your Minecraft world to anyone, anywhere.
 * Copyright (C) 2026  Lunasa
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package dev.lunasa.strand.client.gui

import dev.lunasa.strand.session.ConnState
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
        y += 18

        val bw = 204
        val gap = 24

        if (c != null && c.connectionState() != ConnState.LoggedIn) {
            val connecting = c.connectionState() == ConnState.Connecting
            button(cx, y, bw, Component.literal(if (connecting) "Connecting..." else "Connect")) { c.connect() }
            y += gap
        }

        if (c != null && c.isHosting) {
            button(cx, y, bw, Component.literal("Stop hosting")) { c.unhost() }
        } else {
            button(cx, y, bw, Component.literal("Host this world")) { c?.host() }
        }
        y += gap

        button(cx, y, bw, Component.literal("Join by code")) { minecraft?.setScreenAndShow(StrandJoinScreen(this)) }
        y += gap

        val pending = c?.pendingInvites()?.size ?: 0
        val invitesLabel = if (pending > 0) "Invites ($pending)" else "Invites"
        button(cx, y, bw, Component.literal(invitesLabel)) { minecraft?.setScreenAndShow(StrandInvitesScreen(this)) }
        y += gap

        if (c != null && c.isHosting) {
            button(cx, y, bw, Component.literal("Invite a player")) { minecraft?.setScreenAndShow(StrandInviteScreen(this)) }
            y += gap
            button(cx, y, bw, Component.literal("Copy invite code")) { copyCode() }
            y += gap
        }

        button(cx, y, bw, Component.literal("Settings")) { minecraft?.setScreenAndShow(StrandSettingsScreen(this)) }
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
