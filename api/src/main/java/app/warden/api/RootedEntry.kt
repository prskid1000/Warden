package app.warden.api

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * One package's place on the rooted list.
 *
 * [giveBroker] / [giveSu] work on any device for cooperating apps (layers A/B).
 * [spoofDetection] / [propsProfile] require the Zygisk module + a rooted device
 * (layer C); on an unrooted device the manager shows them as unavailable.
 */
@Parcelize
data class RootedEntry(
    val pkg: String,
    /** Broker/API access (layer A). */
    val giveBroker: Boolean = false,
    /** Working `su` when the app shells out (layer B). */
    val giveSu: Boolean = false,
    /** Make the app's own root-detection report "rooted" (layer C, root only). */
    val spoofDetection: Boolean = false,
    /** getprop profile the module presents to the app: "", "rooted", "debug". */
    val propsProfile: String = "",
) : Parcelable
