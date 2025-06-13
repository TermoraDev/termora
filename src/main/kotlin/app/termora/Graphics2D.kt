package app.termora

import java.awt.*
import java.awt.geom.AffineTransform


private val states = ArrayDeque<State>()

private data class State(
    val stroke: Stroke,
    val composite: Composite,
    val color: Color,
    val transform: AffineTransform,
    val clip: Shape,
    val font: Font,
    val renderingHints: RenderingHints
)

fun Graphics2D.save() {
    states.addFirst(
        State(
            stroke = this.stroke,
            composite = this.composite,
            color = this.color,
            transform = this.transform,
            clip = this.clip,
            font = this.font,
            renderingHints = this.renderingHints
        )
    )
}

fun Graphics2D.restore() {
    val state = states.removeFirst()
    this.stroke = state.stroke
    this.composite = state.composite
    this.color = state.color
    this.transform = state.transform
    this.clip = state.clip
    this.font = state.font
    this.setRenderingHints(state.renderingHints)
}

fun setupAntialiasing(graphics: Graphics2D) {
    graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
}

