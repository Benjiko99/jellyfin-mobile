package org.jellyfin.mobile.network

import android.os.Build

// TODO: the device id should be a persisted random value, not derived from the build.
//  Devices of the same model currently share an id, so the server groups them into one device
//  entry. Fix when settings storage lands (PLAN.md Phase 0/2).
actual fun platformDeviceInfo(): DeviceInfo = DeviceInfo(
    name = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
    id = "android-${Build.MANUFACTURER}-${Build.MODEL}-${Build.DEVICE}".lowercase().replace(' ', '-'),
)
