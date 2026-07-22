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

package re.lilith.strand.backend.util

import java.security.SecureRandom

object Ids {
    private val random = SecureRandom()
    private const val SOCKET_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
    private const val CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    fun socketToken(length: Int = 32): String = build(length, SOCKET_ALPHABET)

    fun inviteCode(length: Int = 8): String = build(length, CODE_ALPHABET)

    private fun build(length: Int, alphabet: String): String {
        val sb = StringBuilder(length)
        repeat(length) { sb.append(alphabet[random.nextInt(alphabet.length)]) }
        return sb.toString()
    }
}
