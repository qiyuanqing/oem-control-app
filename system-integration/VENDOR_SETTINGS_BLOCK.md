# Vendor-level Settings blocking

The current Android 8.1 device accepts the Device Owner restrictions, but its
vendor `PackageManager` does not implement package suspension for the system
Settings package. `DevicePolicyManager.setPackagesSuspended()` and
`setApplicationHidden()` therefore cannot guarantee that
`com.android.settings` is unlaunchable.

This cannot be fixed by changing the APK or by appending a property to
`vendor/build.prop`. It requires an Android framework/vendor source change.

## Required behavior

When the device is locked, reject every activity start whose resolved package
is `com.android.settings`. The control application must remain allowed so that
an authenticated administrator can enter its UI.

When an authenticated debug/unlock session is active, allow Settings for the
session lifetime. On timeout, reboot, or explicit lock, reject it again.

## Recommended integration point

Add the check in the Android 8.1 framework activity-start path used by the
device build, normally `ActivityStarter`/`ActivityStackSupervisor` in
`frameworks/base/services/core`, after the target package has been resolved
and before the activity is placed on a task. Do not rely only on the Settings
application's launcher activity: Settings contains many exported activities.

Pseudo-code:

```java
private boolean isO0aiSettingsBlocked(String packageName, int userId) {
    if (!"com.android.settings".equals(packageName)) return false;
    if (!isO0aiDeviceOwnerPresent(userId)) return false;
    return !isO0aiDebugSessionActive(userId);
}
```

If blocked, return the normal activity-start denial result and log a short
reason. The state should be read from a framework-owned, permission-protected
provider or binder service; do not let an ordinary application write a global
property to bypass the check.

## Required framework permissions

The production O0AI controller must be platform-signed or otherwise granted a
private framework permission to expose the lock/debug state. The permission
must be signature-level and the binder endpoint must verify the caller UID.

## Validation

Test all of these while locked:

```text
am start -a android.settings.SETTINGS
am start -a android.settings.WIFI_SETTINGS
am start -n com.android.settings/.Settings$NetworkDashboardActivity
am start -a android.settings.APPLICATION_SETTINGS
```

Each must be rejected while the lock is active, and allowed only during a
password-authenticated debug session. Validate after reboot and after a
factory-reset/provisioning cycle as well.

## Important

`vendor.img` and `system.img` are binary images, not framework source trees.
Do not use `dd` to inject this change into a production image without a
matching partition backup, verified block-device mapping, and a bootable test
image. The APK-only fallback can restrict individual Settings functions but
cannot reliably block every Settings activity on this ROM.
