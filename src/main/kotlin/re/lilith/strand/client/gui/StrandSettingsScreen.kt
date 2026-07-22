/*
 * Strand - Open your Minecraft world to anyone, anywhere.
 * Copyright (C) 2026 Lilith Technologies LLC <hello@lilith.re>
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

package re.lilith.strand.client.gui

import net.minecraft.ChatFormatting
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

class StrandSettingsScreen(parent: Screen?) : StrandScreen(Component.literal("Settings"), parent) {

    override fun build() {
        val c = controller
        val cx = width / 2
        val top = height / 3

        val blocked = c?.invitesBlocked() == true
        label(cx, top - 28, Component.literal("Invite settings").withStyle(ChatFormatting.BOLD))
        label(
            cx, top - 14,
            Component.literal(if (blocked) "Others can only join with a code you share" else "Anyone can send you invites")
                .withStyle(ChatFormatting.GRAY),
        )

        val toggleText = if (blocked) "Invites: Blocked" else "Invites: Allowed"
        button(cx, top, 204, Component.literal(toggleText)) { c?.setInvitesBlocked(!blocked) }

        if (c != null && c.isHosting) {
            button(cx, top + 24, 204, Component.literal("Stop hosting")) { c.unhost() }
        }

        button(cx, top + 54, 204, Component.literal("Back")) { onClose() }
    }
}
