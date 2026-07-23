package re.lilith.strand.platform.neoforge

import re.lilith.strand.StrandConfig
import re.lilith.strand.StrandState
import re.lilith.strand.backend.BackendClient
import re.lilith.strand.client.StrandClientHooks
import re.lilith.strand.client.voice.VoiceClient
import re.lilith.strand.client.voice.VoiceKeys
import re.lilith.strand.eos.EosManager
import re.lilith.strand.session.SessionController
import gg.sona.eos.Eos
import net.minecraft.client.Minecraft
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.ModContainer
import net.neoforged.fml.common.Mod
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent
import net.neoforged.fml.loading.FMLPaths
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent
import net.neoforged.neoforge.common.NeoForge
import org.slf4j.LoggerFactory

@Mod("strand")
class StrandNeoForgeMod(modBus: IEventBus, container: ModContainer) {

    private val logger = LoggerFactory.getLogger("strand")

    init {
        modBus.addListener(::onClientSetup)
        modBus.addListener(::onRegisterKeyMappings)
    }

    private fun onRegisterKeyMappings(event: RegisterKeyMappingsEvent) {
        VoiceKeys.all().forEach { event.register(it) }
    }

    private fun onClientSetup(event: FMLClientSetupEvent) {
        logger.info("EOS SDK version: ${Eos.version}")

        val configDir = FMLPaths.CONFIGDIR.get()
        val config = StrandConfig.load(configDir)
        StrandState.config = config
        StrandState.configDir = configDir

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
            VoiceClient.clientTick(Minecraft.getInstance())
        }
        NeoForge.EVENT_BUS.addListener { _: ClientPlayerNetworkEvent.LoggingOut ->
            controller.onLeftWorld()
        }

        Runtime.getRuntime().addShutdownHook(Thread {
            controller.shutdown()
            EosManager.shutdown()
        })

        logger.info("Strand client initialized")
    }
}
