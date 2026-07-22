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
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.StringWidget
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

class StrandInvitesScreen(parent: Screen?) : StrandScreen(Component.literal("Invites"), parent) {

    override fun build() {
        val cx = width / 2
        var y = height / 6

        label(cx, y, Component.literal("Pending invites").withStyle(ChatFormatting.BOLD))
        y += 20

        val invites = controller?.pendingInvites().orEmpty()
        if (invites.isEmpty()) {
            label(cx, y, Component.literal("No pending invites").withStyle(ChatFormatting.GRAY))
            y += 24
        } else {
            for (invite in invites) {
                val name = invite.hostName.ifBlank { "A player" }
                addRenderableWidget(StringWidget(cx - 150, y + 5, 150, 9, Component.literal(name), font))
                addRenderableWidget(
                    Button.builder(Component.literal("Accept")) { _ ->
                        controller?.acceptInvite(invite.inviteId)
                        onClose()
                    }.bounds(cx + 4, y, 70, 20).build()
                )
                addRenderableWidget(
                    Button.builder(Component.literal("Decline")) { _ ->
                        controller?.declineInvite(invite.inviteId)
                    }.bounds(cx + 78, y, 72, 20).build()
                )
                y += 24
            }
        }

        button(cx, minOf(y + 6, height - 28), 204, Component.literal("Back")) { onClose() }
    }
}
