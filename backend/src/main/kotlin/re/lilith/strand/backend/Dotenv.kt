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

package re.lilith.strand.backend

import java.io.File

object Dotenv {

    fun load(system: Map<String, String> = System.getenv()): Map<String, String> {
        val file = findEnvFile() ?: return system
        return parse(file) + system
    }

    private fun findEnvFile(): File? {
        var dir: File? = File("").absoluteFile
        repeat(5) {
            val candidate = File(dir, ".env")
            if (candidate.isFile) return candidate
            dir = dir?.parentFile
        }
        return null
    }

    private fun parse(file: File): Map<String, String> {
        val values = LinkedHashMap<String, String>()
        file.readLines().forEach { raw ->
            val line = raw.trim().removePrefix("export ").trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEach
            val index = line.indexOf('=')
            if (index <= 0) return@forEach
            val key = line.substring(0, index).trim()
            var value = line.substring(index + 1).trim()
            if (value.length >= 2 &&
                ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'")))
            ) {
                value = value.substring(1, value.length - 1)
            }
            values[key] = value
        }
        return values
    }
}
