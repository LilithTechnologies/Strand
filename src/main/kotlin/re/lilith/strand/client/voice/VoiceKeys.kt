package re.lilith.strand.client.voice

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.KeyMapping
import org.lwjgl.glfw.GLFW

object VoiceKeys {
    val pushToTalk = mapping("key.strand.ptt", GLFW.GLFW_KEY_V)
    val global = mapping("key.strand.global", GLFW.GLFW_KEY_G)
    val toggleMute = mapping("key.strand.mute", GLFW.GLFW_KEY_N)
    val toggleDeafen = mapping("key.strand.deafen", GLFW.GLFW_KEY_J)
    val openVoice = mapping("key.strand.voice_screen", GLFW.GLFW_KEY_UNKNOWN)

    fun all(): Array<KeyMapping> = arrayOf(pushToTalk, global, toggleMute, toggleDeafen, openVoice)

    private fun mapping(name: String, key: Int) =
        KeyMapping(name, InputConstants.Type.KEYSYM, key, KeyMapping.Category.MISC)
}
