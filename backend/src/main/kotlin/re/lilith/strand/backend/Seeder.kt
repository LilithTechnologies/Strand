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

import org.slf4j.LoggerFactory
import java.util.UUID

object Seeder {

    private val logger = LoggerFactory.getLogger("strand/seed")
    private val DEMO_UUID = UUID.fromString("00000000-0000-0000-0000-0000000000de")
    private const val DEMO_NAME = "StrandDemo"
    private const val DEMO_PUID = "0002000000000000000000000000demo"

    fun run(config: Config, services: AppServices) {
        if (!config.seed) return
        if (services.users.me(DEMO_UUID) != null) {
            logger.info("Seed data already present, skipping")
            return
        }
        services.users.upsert(DEMO_UUID, DEMO_NAME)
        services.users.linkPuid(DEMO_UUID, DEMO_PUID)
        val session = services.sessions.createOrReplace(DEMO_UUID, DEMO_PUID, null)
        logger.info("Seeded demo user {} with invite code {}", DEMO_NAME, session.inviteCode)
    }
}
