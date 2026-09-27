package app.warden.api

import android.os.IBinder
import android.os.Parcel
import android.os.Parcelable

/**
 * Carries a live IBinder through an Intent extra.
 *
 * A raw binder cannot be reliably placed in broadcast extras (AMS may strip it);
 * wrapping it in a Parcelable that uses writeStrongBinder/readStrongBinder makes
 * it survive the trip. This is how the shell-started server hands its binder to
 * the manager's BroadcastReceiver on the non-root (ADB) path.
 */
class BinderContainer : Parcelable {
    @JvmField val binder: IBinder?

    constructor(binder: IBinder?) { this.binder = binder }
    private constructor(p: Parcel) { binder = p.readStrongBinder() }

    override fun writeToParcel(dest: Parcel, flags: Int) = dest.writeStrongBinder(binder)
    override fun describeContents() = 0

    companion object CREATOR : Parcelable.Creator<BinderContainer> {
        override fun createFromParcel(p: Parcel) = BinderContainer(p)
        override fun newArray(size: Int) = arrayOfNulls<BinderContainer>(size)
    }
}
