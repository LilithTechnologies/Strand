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

import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

class StrandJoinScreen(parent: Screen?) : StrandScreen(Component.literal("Join by code"), parent) {

    private var codeText: String = ""
    private lateinit var codeBox: EditBox

    override fun build() {
        val cx = width / 2
        val top = height / 3

        label(cx, top - 24, Component.literal("Enter an invite code"))

        codeBox = EditBox(font, cx - 102, top, 204, 20, Component.literal("Invite code")).apply {
            setMaxLength(16)
            setHint(Component.literal("e.g. ABCD2345"))
            value = codeText
            setResponder { codeText = it }
        }
        addRenderableWidget(codeBox)

        button(cx, top + 30, 204, Component.literal("Join")) { submit() }
        button(cx, top + 54, 204, Component.literal("Back")) { onClose() }

        setInitialFocus(codeBox)
    }

    private fun submit() {
        val code = codeText.trim()
        if (code.isEmpty()) return
        controller?.joinByCode(code)
        onClose()
    }
}
