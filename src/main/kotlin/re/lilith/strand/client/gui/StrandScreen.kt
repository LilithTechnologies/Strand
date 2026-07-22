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

import re.lilith.strand.StrandState
import re.lilith.strand.session.SessionController
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.StringWidget
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import kotlin.math.min

abstract class StrandScreen(title: Component, protected val parent: Screen?) : Screen(title) {

    protected val controller: SessionController? get() = StrandState.controller

    private var listening = false
    private val refresh: () -> Unit = {
        val mc = Minecraft.getInstance()
        mc.execute { if (mc.gui.screen() === this) rebuildWidgets() }
    }

    override fun init() {
        if (!listening) {
            controller?.addListener(refresh)
            listening = true
        }
        build()
    }

    protected abstract fun build()

    override fun removed() {
        if (listening) {
            controller?.removeListener(refresh)
            listening = false
        }
        super.removed()
    }

    override fun onClose() {
        if (parent != null) {
            minecraft.setScreenAndShow(parent)
        } else {
            minecraft.gui.setScreen(null)
        }
    }

    protected fun label(centerX: Int, y: Int, text: Component): StringWidget {
        val widget = StringWidget(centerX - font.width(text) / 2, y, font.width(text), 9, text, font)
        return addRenderableWidget(widget)
    }

    protected fun button(centerX: Int, y: Int, width: Int, text: Component, onPress: () -> Unit): Button {
        val widget = Button.builder(text) { _ -> onPress() }.bounds(centerX - width / 2, y, width, 20).build()
        return addRenderableWidget(widget)
    }
}
