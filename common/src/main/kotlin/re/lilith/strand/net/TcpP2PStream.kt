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
import java.net.Socket
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean

class TcpP2PStream(
    private val socketName: String,
    private val remote: ProductUserId,
    private val channel: Int,
    private val tcp: Socket,
) : StreamHandler {

    private val inbound = LinkedBlockingQueue<Chunk>()
    private val closed = AtomicBoolean(false)
    private val poison = Chunk(ByteArray(0), 0, 0)

    private class Chunk(val data: ByteArray, val off: Int, val len: Int)

    fun start() {
        tcp.tcpNoDelay = true
        Thread(::readLoop, "strand-p2p-read-$channel").apply { isDaemon = true; start() }
        Thread(::writeLoop, "strand-p2p-write-$channel").apply { isDaemon = true; start() }
    }

    private fun readLoop() {
        val buffer = ByteArray(P2PHub.MAX_PAYLOAD)
        try {
            val input = tcp.getInputStream()
            while (!closed.get()) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read > 0) P2PHub.send(remote, socketName, channel, buffer, read)
            }
        } catch (_: Exception) {
        } finally {
            closeLocal(notifyPeer = true)
        }
    }

    private fun writeLoop() {
        try {
            val output = tcp.getOutputStream()
            while (true) {
                val chunk = inbound.take()
                if (chunk === poison) break
                output.write(chunk.data, chunk.off, chunk.len)
                output.flush()
            }
        } catch (_: Exception) {
        } finally {
            closeLocal(notifyPeer = true)
        }
    }

    override fun onData(data: ByteArray, off: Int, len: Int) {
        if (!closed.get()) inbound.add(Chunk(data, off, len))
    }

    override fun onClose() {
        closeLocal(notifyPeer = false)
    }

    private fun closeLocal(notifyPeer: Boolean) {
        if (!closed.compareAndSet(false, true)) return
        inbound.add(poison)
        runCatching { tcp.close() }
        if (notifyPeer) P2PHub.closeStream(remote, socketName, channel)
    }
}
