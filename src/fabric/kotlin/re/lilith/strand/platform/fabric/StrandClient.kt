package re.lilith.strand.platform.fabric

import re.lilith.strand.StrandConfig
import re.lilith.strand.StrandState
import re.lilith.strand.backend.BackendClient
import re.lilith.strand.client.StrandClientHooks
import re.lilith.strand.client.voice.VoiceClient
import re.lilith.strand.client.voice.VoiceKeys
import re.lilith.strand.eos.EosManager
import re.lilith.strand.session.SessionController
import gg.sona.eos.Eos
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.loader.api.FabricLoader
import org.slf4j.LoggerFactory

class StrandClient : ClientModInitializer {

    private val logger = LoggerFactory.getLogger("strand")

    override fun onInitializeClient() {
        logger.info("EOS SDK version: ${Eos.version}")

        val configDir = FabricLoader.getInstance().configDir
        val config = StrandConfig.load(configDir)
        StrandState.config = config
        StrandState.configDir = configDir

        val backend = BackendClient("https://strand.lilith.re", config.oidcClientId, config.oidcRedirectUri)
        val hooks = StrandClientHooks()
        val controller = SessionController(config, backend, hooks)
        StrandState.controller = controller

        EosManager.init()

        StrandCommands.register(controller)
        StrandScreenButtons.register()

        VoiceKeys.all().forEach { KeyMappingHelper.registerKeyMapping(it) }
        ClientTickEvents.END_CLIENT_TICK.register(ClientTickEvents.EndTick { client -> VoiceClient.clientTick(client) })
        ClientPlayConnectionEvents.DISCONNECT.register(ClientPlayConnectionEvents.Disconnect { _, _ -> controller.onLeftWorld() })

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
