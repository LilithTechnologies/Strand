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

import re.lilith.strand.StrandConfig
import re.lilith.strand.StrandState
import re.lilith.strand.backend.BackendClient
import re.lilith.strand.client.StrandClientHooks
import re.lilith.strand.eos.EosManager
import re.lilith.strand.session.SessionController
import gg.sona.eos.Eos
import gg.sona.eos.EosNatives
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.ModContainer
import net.neoforged.fml.common.Mod
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent
import net.neoforged.fml.loading.FMLPaths
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.common.NeoForge
import org.slf4j.LoggerFactory

@Mod("strand")
class StrandNeoForgeMod(modBus: IEventBus, container: ModContainer) {

    private val logger = LoggerFactory.getLogger("strand")

    init {
        modBus.addListener(::onClientSetup)
    }

    private fun onClientSetup(event: FMLClientSetupEvent) {
        EosNatives.baseUrl = "https://eos-cdn.lilith.re"
        logger.info("Setting up EOS SDK...")
        logger.info("SDK version: ${Eos.version}")

        val config = StrandConfig.load(FMLPaths.CONFIGDIR.get())
        StrandState.config = config

        val backend = BackendClient("https://strand.lilith.re", config.oidcClientId, config.oidcRedirectUri)
        val hooks = StrandClientHooks()
        val controller = SessionController(config, backend, hooks)
        StrandState.controller = controller

        EosManager.init()

        StrandNeoForgeCommands.register(controller)
        StrandNeoForgeScreenButtons.register()

        var loggedIn = false
        NeoForge.EVENT_BUS.addListener { _: ClientTickEvent.Post ->
            if (!loggedIn) {
                loggedIn = true
                controller.ensureLogin()
            }
        }

        Runtime.getRuntime().addShutdownHook(Thread {
            controller.shutdown()
            EosManager.shutdown()
        })

        logger.info("Strand client initialized")
    }
}
