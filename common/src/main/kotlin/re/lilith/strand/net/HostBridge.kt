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

import java.net.InetAddress
import java.net.Socket

class HostBridge(private val socketName: String, private val lanPort: Int) {

    fun start() {
        P2PHub.host(socketName) { remote, channel ->
            val tcp = Socket(InetAddress.getLoopbackAddress(), lanPort)
            val stream = TcpP2PStream(socketName, remote, channel, tcp)
            stream.start()
            stream
        }
    }

    fun stop() {
        P2PHub.unhost(socketName)
    }
}
