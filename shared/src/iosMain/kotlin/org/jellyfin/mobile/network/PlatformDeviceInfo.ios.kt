package org.jellyfin.mobile.network

import platform.UIKit.UIDevice

/**
 * `identifierForVendor` is stable across launches for all apps from the same vendor on the device,
 * and resets on uninstall — exactly the semantics we want for a device id.
 */
actual fun platformDeviceInfo(): DeviceInfo {
    val device = UIDevice.currentDevice
    return DeviceInfo(
        name = device.name,
        id = device.identifierForVendor?.UUIDString ?: "ios-unknown",
    )
}
