package app.warden.ui

/** Start/stop-flow state shared between the toolbar control and the Home screen. */
class StartUi(
    val working: Boolean,
    val needCode: Boolean,
    val msg: String?,
    val onStart: () -> Unit,
    val onStop: () -> Unit,
)
