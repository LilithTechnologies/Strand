package re.lilith.strand

import re.lilith.strand.session.SessionController
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

object StrandState {

    @Volatile
    var controller: SessionController? = null

    @Volatile
    var config: StrandConfig = StrandConfig()

    @Volatile
    var configDir: Path? = null

    private val saveExecutor = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "strand-config-save").apply { isDaemon = true }
    }
    @Volatile private var pendingSave: ScheduledFuture<*>? = null

    fun updateConfig(block: (StrandConfig) -> StrandConfig) {
        config = block(config)
        val dir = configDir ?: return
        val snapshot = config
        pendingSave?.cancel(false)
        pendingSave = saveExecutor.schedule({ StrandConfig.save(dir, snapshot) }, 400, TimeUnit.MILLISECONDS)
    }

    fun updateVoice(block: (VoicePrefs) -> VoicePrefs) = updateConfig { it.copy(voice = block(it.voice)) }

    @JvmStatic
    fun onLanOpened(port: Int) {
        if (port <= 0) return
        if (!config.autoHostOnLanOpen) return
        controller?.host()
    }

    @JvmStatic
    fun onLanClosed() {
        if (controller?.isHosting == true) controller?.unhost()
    }
}
