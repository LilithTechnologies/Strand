package re.lilith.strand.backend

import re.lilith.strand.backend.mojang.MojangClient
import re.lilith.strand.backend.security.KeyManager
import re.lilith.strand.backend.security.TokenService
import re.lilith.strand.backend.service.AuthCodeService
import re.lilith.strand.backend.service.ChallengeService
import re.lilith.strand.backend.service.InviteService
import re.lilith.strand.backend.service.SessionService
import re.lilith.strand.backend.service.UserService
import re.lilith.strand.backend.service.VoiceService

class AppServices(
    val config: Config,
    val keys: KeyManager,
    val tokens: TokenService,
    val users: UserService,
    val challenges: ChallengeService,
    val authCodes: AuthCodeService,
    val sessions: SessionService,
    val invites: InviteService,
    val mojang: MojangClient,
    val voice: VoiceService?,
)
