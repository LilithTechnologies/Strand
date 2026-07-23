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

package re.lilith.strand

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists

@Serializable
data class StrandConfig(
    val oidcClientId: String = "strand",
    val oidcRedirectUri: String = "http://127.0.0.1:4924/callback",
    val autoHostOnLanOpen: Boolean = true,
    val hideCodeInChat: Boolean = false,
) {
    companion object {
        private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }

        fun load(configDir: Path): StrandConfig {
            val path = configDir.resolve("strand.json")
            if (!path.exists()) {
                val default = StrandConfig()
                runCatching { Files.writeString(path, json.encodeToString(default)) }
                return default
            }
            val parsed = runCatching { json.decodeFromString<StrandConfig>(Files.readString(path)) }
                .getOrElse { StrandConfig() }
            return parsed
        }

    }
}
