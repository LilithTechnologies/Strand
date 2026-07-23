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

package re.lilith.strand.eos

import gg.sona.eos.Eos
import gg.sona.eos.EosClientCredentials
import gg.sona.eos.EosInitializeOptions
import gg.sona.eos.EosPlatform
import gg.sona.eos.EosPlatformFlags
import gg.sona.eos.EosPlatformOptions
import gg.sona.eos.EosResult
import gg.sona.eos.common.ProductUserId
import gg.sona.eos.logging.EosLogCategory
import gg.sona.eos.logging.EosLogLevel
import gg.sona.eos.logging.EosLogging
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

object EosManager {

    private val logger = LoggerFactory.getLogger("strand/eos")

    @Volatile
    private var current: EosPlatform? = null

    val platform: EosPlatform
        get() = current ?: error("EOS SDK has not been initialized yet")

    val isInitialized: Boolean get() = current != null

    @Volatile
    var localUser: ProductUserId = ProductUserId.Invalid

    val isLoggedIn: Boolean get() = localUser.raw != 0L

    private val pendingCalls = ConcurrentLinkedQueue<() -> Unit>()
    private val frameHooks = CopyOnWriteArrayList<() -> Unit>()

    @Volatile
    private var tickThread: Thread? = null

    val isOnTickThread: Boolean get() = Thread.currentThread() === tickThread

    @Volatile
    private var running = false

    private val starting = AtomicBoolean(false)

    fun init() {
        if (isInitialized || !starting.compareAndSet(false, true)) return

        val ready = CountDownLatch(1)
        val worker = Thread({
            runCatching { start() }.onFailure { logger.error("Could not start EOS, its features will be unavailable", it) }
            ready.countDown()
            if (isInitialized) runTickLoop()
        }, "strand-eos").apply {
            isDaemon = true
            priority = Thread.NORM_PRIORITY + 1
        }
        tickThread = worker
        running = true
        worker.start()

        if (!ready.await(STARTUP_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            logger.warn("EOS is still starting after {}s, continuing without it", STARTUP_TIMEOUT_SECONDS)
        }
    }

    private fun start() {
        val init = Eos.initialize(EosInitializeOptions.create(EosConstants.PRODUCT_NAME, EosConstants.PRODUCT_VERSION))
        if (init != EosResult.Success && init != EosResult.AlreadyConfigured) {
            logger.error("EOS_Initialize failed: {}", init)
            running = false
            return
        }
        EosLogging.setCallback { msg -> onLog(msg.category, msg.level, msg.message) }
        EosLogging.setLogLevel(EosLogCategory.AllCategories, EosLogLevel.Warning)

        current = createPlatform() ?: run {
            running = false
            return
        }
    }

    private fun createPlatform(): EosPlatform? {
        val options = EosPlatformOptions.create(
            productId = EosConstants.PRODUCT_ID,
            sandboxId = EosConstants.SANDBOX_ID,
            deploymentId = EosConstants.DEPLOYMENT_ID,
            clientCredentials = EosClientCredentials.of(EosConstants.CLIENT_ID, EosConstants.CLIENT_SECRET),
        ).apply {
            flags = EosPlatformFlags.DisableOverlay or EosPlatformFlags.DisableSocialOverlay
        }
        return runCatching { Eos.createPlatform(options) }
            .onFailure { logger.error("Could not create the EOS platform", it) }
            .getOrNull()
    }

    private fun runTickLoop() {
        var deadline = System.nanoTime()
        while (running) {
            runCatching { pump() }.onFailure { logger.error("An EOS tick failed", it) }
            deadline += TICK_INTERVAL_NANOS
            val remaining = deadline - System.nanoTime()
            if (remaining > 0) {
                try {
                    Thread.sleep(remaining / 1_000_000, (remaining % 1_000_000).toInt())
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                }
            } else {
                deadline = System.nanoTime()
            }
        }
        teardown()
    }

    private fun pump() {
        var call = pendingCalls.poll()
        while (call != null) {
            runCatching(call).onFailure { logger.warn("A queued EOS call failed", it) }
            call = pendingCalls.poll()
        }
        platform.tick()
        for (hook in frameHooks) {
            runCatching(hook).onFailure { logger.warn("An EOS frame hook failed", it) }
        }
    }

    private fun teardown() {
        val platform = current ?: return
        current = null
        localUser = ProductUserId.Invalid
        runCatching { platform.close() }.onFailure { logger.warn("Closing the EOS platform failed", it) }
        runCatching { Eos.shutdown() }.onFailure { logger.warn("Shutting the EOS SDK down failed", it) }
    }

    fun <T> call(block: () -> T): CompletableFuture<T> {
        val future = CompletableFuture<T>()
        if (isOnTickThread) {
            try {
                future.complete(block())
            } catch (e: Throwable) {
                future.completeExceptionally(e)
            }
            return future
        }
        pendingCalls.add {
            try {
                future.complete(block())
            } catch (e: Throwable) {
                future.completeExceptionally(e)
            }
        }
        return future
    }

    fun post(block: () -> Unit) {
        if (isOnTickThread) {
            runCatching(block).onFailure { logger.warn("A posted EOS call failed", it) }
        } else {
            pendingCalls.add(block)
        }
    }

    fun addFrameHook(hook: () -> Unit) {
        frameHooks.add(hook)
    }

    fun shutdown() {
        if (!isInitialized && !running) return
        running = false
        tickThread?.let { worker ->
            worker.join(SHUTDOWN_JOIN_MILLIS)
            if (worker.isAlive) logger.warn("The EOS thread did not stop in time")
        }
        tickThread = null
        starting.set(false)
    }

    private fun onLog(category: String, level: EosLogLevel, message: String) {
        val id = category
            .removePrefix("LogEOS")
            .replace(Regex("([A-Z]+)([A-Z][a-z])"), "$1-$2")
            .replace(Regex("([a-z\\d])([A-Z])"), "$1-$2")
            .lowercase()

        val log = LoggerFactory.getLogger(if(id.isBlank()) "strand/eos" else "strand/eos/$id")
        when (level) {
            EosLogLevel.Fatal, EosLogLevel.Error -> log.error(message)
            EosLogLevel.Warning -> log.warn(message)
            else -> log.info(message)
        }
    }

    private const val TICK_INTERVAL_NANOS = 10_000_000L
    private const val SHUTDOWN_JOIN_MILLIS = 2_000L
    private const val STARTUP_TIMEOUT_SECONDS = 10L
}
