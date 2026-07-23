package re.lilith.strand.client.voice

import re.lilith.strand.StrandState
import re.lilith.strand.voice.VoiceManager
import net.minecraft.client.Minecraft
import java.util.UUID
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sqrt

object ProximityVoiceController {
    fun tick() {
        if (!VoiceManager.isConnected) return
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return
        val level = mc.level ?: return
        val prefs = StrandState.config.voice

        val near = VoiceManager.proxNear.toDouble()
        val max = minOf(VoiceManager.proxMax, prefs.proximityMax).toDouble().coerceAtLeast(near + 1.0)

        for (p in VoiceManager.participants()) {
            val uuid = p.mcUuid
            val muted = p.locallyMuted || (uuid != null && uuid in prefs.perPlayerMuted)
            val volume: Float = when {
                muted -> 0f
                p.global -> 1f
                uuid == null -> 0f
                else -> {
                    val other = runCatching { level.getPlayerByUUID(UUID.fromString(uuid)) }.getOrNull()
                    if (other == null) 0f
                    else falloff(sqrt(other.distanceToSqr(player)), near, max) * (prefs.perPlayerVolume[uuid] ?: 1f)
                }
            }
            VoiceManager.setParticipantVolume(p, volume)
        }
    }

    private fun falloff(distance: Double, near: Double, max: Double): Float {
        if (distance <= near) return 1f
        if (distance >= max) return 0f
        val t = (distance - near) / (max - near)
        return (0.5 * (1.0 + cos(PI * t))).toFloat()
    }
}
