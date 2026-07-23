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

package re.lilith.strand.client

import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService
import re.lilith.strand.client.StrandChat.PREFIX
import re.lilith.strand.client.gui.StrandMultiplayerOptionsScreen
import re.lilith.strand.session.ClientHooks
import re.lilith.strand.session.Profile
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.toasts.SystemToast
import net.minecraft.client.gui.screens.ConnectScreen
import net.minecraft.client.gui.screens.MultiplayerOptionsScreen
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.client.multiplayer.ServerData
import net.minecraft.client.multiplayer.resolver.ServerAddress
import net.minecraft.network.chat.Component
import org.slf4j.LoggerFactory

class StrandClientHooks : ClientHooks {

    private val logger = LoggerFactory.getLogger("strand/client")
    private val minecraft: Minecraft get() = Minecraft.getInstance()

    override fun profile(): Profile {
        val user = minecraft.user
        val uuid = user.profileId
        return Profile(uuid.toString(), user.name)
    }

    override fun joinServer(serverId: String) {
        val user = minecraft.user
        val profileId = user.profileId
        try {
            val sessionService = YggdrasilAuthenticationService(minecraft.proxy).createMinecraftSessionService()
            sessionService.joinServer(profileId, user.accessToken, serverId)
        } catch (e: Exception) {
            throw IllegalStateException("Mojang session authentication failed", e)
        }
    }

    override fun connectToLocal(port: Int) {
        minecraft.execute {
            val address = ServerAddress.parseString("127.0.0.1:$port")
            val data = ServerData("Strand", "127.0.0.1:$port", ServerData.Type.OTHER)
            ConnectScreen.startConnecting(TitleScreen(), minecraft, address, data, false, null)
        }
    }

    override fun lanPort(): Int? {
        val server = minecraft.singleplayerServer ?: return null
        if (!server.isPublished) return null
        return server.port.takeIf { it > 0 }
    }

    override fun notify(message: String) {
        minecraft.execute {
            val component = Component.literal("").append(PREFIX).append(" $message")
            val player = minecraft.player
            if (player != null) player.sendSystemMessage(component) else logger.info(message)
        }
    }

    override fun toast(title: String, body: String) {
        minecraft.execute {
            runCatching {
                SystemToast.add(
                    minecraft.gui.toastManager(),
                    SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                    Component.literal(title),
                    Component.literal(body),
                )
            }.onFailure { notify("$title: $body") }
        }
    }

    override fun openHostToLanScreen(onHosted: () -> Unit) {
        val mc = Minecraft.getInstance()
        mc.setScreenAndShow(StrandMultiplayerOptionsScreen(mc.gui.screen()) { onHosted() })
    }
}
