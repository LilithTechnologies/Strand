package re.lilith.strand.client.gui

import net.minecraft.client.gui.components.AbstractSliderButton
import net.minecraft.network.chat.Component

class StrandSlider(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    private val label: String,
    private val min: Double,
    private val max: Double,
    initial: Double,
    private val display: (Double) -> String,
    private val onApply: (Double) -> Unit,
) : AbstractSliderButton(x, y, width, height, Component.literal(""), ((initial - min) / (max - min)).coerceIn(0.0, 1.0)) {

    init { updateMessage() }

    private fun mapped(): Double = min + value * (max - min)

    override fun updateMessage() {
        message = Component.literal("$label: ${display(mapped())}")
    }

    override fun applyValue() {
        onApply(mapped())
    }
}
