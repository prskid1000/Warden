package app.warden.ui

/** Start-flow state shared between the toolbar chip and the Home screen. */
class StartUi(
    val working: Boolean,
    val needCode: Boolean,
    val msg: String?,
    val onStart: () -> Unit,
)
