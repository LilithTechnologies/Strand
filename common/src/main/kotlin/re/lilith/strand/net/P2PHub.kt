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

import re.lilith.strand.eos.EosManager
import gg.sona.eos.EosResult
import gg.sona.eos.common.ProductUserId
import gg.sona.eos.p2p.EosNetworkConnectionType
import gg.sona.eos.p2p.EosP2PSocketId
import gg.sona.eos.p2p.EosPacketReliability
import gg.sona.eos.p2p.EosRelayControl
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap


private data class StreamKey(val remoteRaw: Long, val socket: String, val channel: Int)

object P2PHub {
    private val logger = LoggerFactory.getLogger("strand/p2p")

    private const val OP_OPEN: Byte = 1
    private const val OP_DATA: Byte = 2
    private const val OP_CLOSE: Byte = 3

    const val MAX_PAYLOAD = 1170 - 1

    private const val PACKET_QUEUE_BYTES = 16L * 1024 * 1024

    private const val MAX_PACKETS_PER_TICK = 4096

    private val handlers = ConcurrentHashMap<StreamKey, StreamHandler>()
    private val acceptedSockets = ConcurrentHashMap.newKeySet<String>()
    private val inboundFactories = ConcurrentHashMap<String, (ProductUserId, Int) -> StreamHandler>()
    private val socketIds = ConcurrentHashMap<String, EosP2PSocketId>()

    private fun socketId(name: String): EosP2PSocketId = socketIds.getOrPut(name) { EosP2PSocketId(name) }

    @Volatile private var installed = false

    fun install() {
        if (installed) return
        installed = true
        EosManager.call {
            val p2p = EosManager.platform.p2p
            p2p.queryNATType().thenAccept {
                logger.info("P2P NAT type -> {} ({})", it.natType, it.result)
            }

            p2p.setRelayControl(EosRelayControl.AllowRelays) // can be used as a fallback

            val effectiveRelay = p2p.getRelayControl()
            logger.info("P2P Relay Control -> $effectiveRelay")

            val queueResult = p2p.setPacketQueueSize(PACKET_QUEUE_BYTES, PACKET_QUEUE_BYTES)
            logger.info("P2P packet queue -> {} bytes each way ({})", PACKET_QUEUE_BYTES, queueResult)

            p2p.addNotifyPeerConnectionRequest(EosManager.localUser, null) { info ->
                val name = info.socketId.name
                if (acceptedSockets.contains(name)) {
                    val result = EosManager.platform.p2p.acceptConnection(info.localUserId, info.remoteUserId, info.socketId)
                    logger.info("Accepted P2P from {} on '{}' (len {}): {}", info.remoteUserId, name, name.length, result)
                } else {
                    logger.warn("P2P request from {} on UNKNOWN socket '{}' (len {}); hosting {}", info.remoteUserId, name, name.length, acceptedSockets)
                }
            }
            p2p.addNotifyPeerConnectionEstablished(EosManager.localUser, null) { info ->
                logger.info("P2P established with {} on '{}' ({} / {})", info.remoteUserId, info.socketId.name, info.type, info.networkType)
                if (info.networkType == EosNetworkConnectionType.DirectConnection) {
                    logger.warn("P2P with {} negotiated a DirectConnection despite ForceRelays", info.remoteUserId)
                }
            }
            p2p.addNotifyPeerConnectionInterrupted(EosManager.localUser, null) { info ->
                logger.warn("P2P interrupted with {} on '{}' (awaiting reconnect)", info.remoteUserId, info.socketId.name)
            }
            p2p.addNotifyPeerConnectionClosed(EosManager.localUser, null) { info ->
                logger.info("P2P closed with {} on '{}': {}", info.remoteUserId, info.socketId.name, info.reason)
                dropStreamsFor(info.remoteUserId.raw, info.socketId.name)
            }
            p2p.addNotifyIncomingPacketQueueFull { info ->
                logger.warn(
                    "P2P incoming queue full on channel {}: dropping {}B ({}/{}B used)",
                    info.channel, info.packetSizeBytes, info.currentSizeBytes, info.maxSizeBytes,
                )
            }
        }
        EosManager.addFrameHook(::pollReceive)
    }

    fun host(socket: String, factory: (ProductUserId, Int) -> StreamHandler) {
        acceptedSockets.add(socket)
        inboundFactories[socket] = factory
        logger.info("Hosting P2P socket '{}' (len {})", socket, socket.length)
    }

    fun unhost(socket: String) {
        inboundFactories.remove(socket)
        acceptedSockets.remove(socket)
        handlers.keys.filter { it.socket == socket }.forEach { handlers.remove(it) }
        val id = socketIds.remove(socket) ?: EosP2PSocketId(socket)
        EosManager.call { EosManager.platform.p2p.closeConnections(EosManager.localUser, id) }
    }

    fun openStream(remote: ProductUserId, socket: String, channel: Int, handler: StreamHandler) {
        acceptedSockets.add(socket)
        handlers[StreamKey(remote.raw, socket, channel)] = handler
        logger.info("Opening P2P stream to {} on '{}' (len {}) channel {}", remote, socket, socket.length, channel)
        postFrame(remote, socket, channel, byteArrayOf(OP_OPEN))
    }

    fun send(remote: ProductUserId, socket: String, channel: Int, bytes: ByteArray, length: Int) {
        var offset = 0
        while (offset < length) {
            val len = minOf(MAX_PAYLOAD, length - offset)
            val frame = ByteArray(len + 1)
            frame[0] = OP_DATA
            System.arraycopy(bytes, offset, frame, 1, len)
            postFrame(remote, socket, channel, frame)
            offset += len
        }
    }

    fun closeStream(remote: ProductUserId, socket: String, channel: Int) {
        if (handlers.remove(StreamKey(remote.raw, socket, channel)) != null) {
            postFrame(remote, socket, channel, byteArrayOf(OP_CLOSE))
        }
    }

    private fun postFrame(remote: ProductUserId, socket: String, channel: Int, frame: ByteArray) {
        EosManager.post {
            try {
                val result = EosManager.platform.p2p.sendPacket(
                    localUserId = EosManager.localUser,
                    remoteUserId = remote,
                    socketId = socketId(socket),
                    channel = channel,
                    data = frame,
                    reliability = EosPacketReliability.ReliableOrdered,
                )
                if (result != EosResult.Success) {
                    logger.warn("sendPacket to {} on '{}' ch {} op {} -> {}", remote, socket, channel, frame[0], result)
                }
            } catch (e: Throwable) {
                logger.error("sendPacket threw for op {} to {} on '{}' ch {}", frame[0], remote, socket, channel, e)
            }
        }
    }

    private fun pollReceive() {
        if (!EosManager.isLoggedIn) return
        val p2p = EosManager.platform.p2p
        var budget = MAX_PACKETS_PER_TICK
        while (budget-- > 0) {
            val packet = p2p.receivePacket(EosManager.localUser) ?: break
            val data = packet.data
            if (data.isEmpty()) continue
            val op = data[0]
            val socket = packet.socketId.name
            val key = StreamKey(packet.remoteUserId.raw, socket, packet.channel)

            if (op == OP_CLOSE) {
                handlers.remove(key)?.onClose()
                continue
            }

            var handler = handlers[key]
            if (handler == null) {
                val factory = inboundFactories[socket] ?: continue
                handler = runCatching { factory(packet.remoteUserId, packet.channel) }
                    .onFailure { logger.error("Failed to open inbound stream on '{}'", socket, it) }
                    .getOrNull() ?: continue
                handlers[key] = handler
                logger.info("Bridged inbound P2P stream from {} channel {} on '{}'", packet.remoteUserId, packet.channel, socket)
            }
            if (op == OP_DATA && data.size > 1) {
                handler.onData(data, 1, data.size - 1)
            }
        }
    }

    private fun dropStreamsFor(remoteRaw: Long, socket: String) {
        handlers.keys.filter { it.remoteRaw == remoteRaw && it.socket == socket }.forEach { key ->
            handlers.remove(key)?.onClose()
        }
    }
}
