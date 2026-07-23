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

package re.lilith.strand.net

import gg.sona.eos.common.ProductUserId
import org.slf4j.LoggerFactory
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicInteger

class JoinBridge(private val hostPuid: ProductUserId, private val socketName: String) {

    private val logger = LoggerFactory.getLogger("strand/join")
    private val channelSeq = AtomicInteger(0)
    private lateinit var serverSocket: ServerSocket

    val port: Int get() = serverSocket.localPort

    fun start() {
        serverSocket = ServerSocket(0, 16, InetAddress.getLoopbackAddress())
        Thread(::acceptLoop, "strand-join-accept").apply { isDaemon = true; start() }
    }

    private fun acceptLoop() {
        while (!serverSocket.isClosed) {
            val tcp = try {
                serverSocket.accept()
            } catch (e: Exception) {
                break
            }
            val channel = channelSeq.getAndIncrement() and 0xff
            val stream = TcpP2PStream(socketName, hostPuid, channel, tcp)
            P2PHub.openStream(hostPuid, socketName, channel, stream)
            stream.start()
            logger.info("Bridged local connection on channel {} to host {}", channel, hostPuid)
        }
    }

    fun stop() {
        runCatching { serverSocket.close() }
    }
}
