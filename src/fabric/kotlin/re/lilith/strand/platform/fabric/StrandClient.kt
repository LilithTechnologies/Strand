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

package re.lilith.strand.platform.fabric

import re.lilith.strand.StrandConfig
import re.lilith.strand.StrandState
import re.lilith.strand.backend.BackendClient
import re.lilith.strand.client.StrandClientHooks
import re.lilith.strand.eos.EosManager
import re.lilith.strand.session.SessionController
import gg.sona.eos.Eos
import gg.sona.eos.EosNatives
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.loader.api.FabricLoader
import org.slf4j.LoggerFactory

class StrandClient : ClientModInitializer {

    private val logger = LoggerFactory.getLogger("strand")

    override fun onInitializeClient() {
        EosNatives.baseUrl = "https://eos-cdn.lilith.re"
        logger.info("Setting up EOS SDK...")
        logger.info("SDK version: ${Eos.version}")

        val config = StrandConfig.load(FabricLoader.getInstance().configDir)
        StrandState.config = config

        val backend = BackendClient("https://strand.lilith.re", config.oidcClientId, config.oidcRedirectUri)
        val hooks = StrandClientHooks()
        val controller = SessionController(config, backend, hooks)
        StrandState.controller = controller

        EosManager.init()

        StrandCommands.register(controller)
        StrandScreenButtons.register()

        ClientLifecycleEvents.CLIENT_STARTED.register {
            controller.ensureLogin()
        }
        ClientLifecycleEvents.CLIENT_STOPPING.register {
            controller.shutdown()
            EosManager.shutdown()
        }

        logger.info("Strand client initialized")
    }
}
