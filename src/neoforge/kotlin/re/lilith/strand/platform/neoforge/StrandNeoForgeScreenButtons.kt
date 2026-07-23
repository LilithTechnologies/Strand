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

package re.lilith.strand.platform.neoforge

import re.lilith.strand.client.gui.StrandHubScreen
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.PauseScreen
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen
import net.minecraft.network.chat.Component
import net.neoforged.neoforge.client.event.ScreenEvent
import net.neoforged.neoforge.common.NeoForge

object StrandNeoForgeScreenButtons {
    fun register() {
        NeoForge.EVENT_BUS.addListener { event: ScreenEvent.Init.Post ->
            val screen = event.screen
            if (screen is TitleScreen || screen is JoinMultiplayerScreen || screen is PauseScreen) {
                val client = Minecraft.getInstance()
                val button = Button.builder(Component.literal("Strand")) { _ ->
                    client.setScreenAndShow(StrandHubScreen(screen))
                }.bounds(screen.width - 104, 4, 100, 20).build()
                event.addListener(button)
            }
        }
    }
}
