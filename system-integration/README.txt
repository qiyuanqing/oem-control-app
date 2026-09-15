O0AI system integration payload

Merge the contents of the system directory into the Android 8.1 system image:

  system/priv-app/O0AIControl/O0AIControl.apk
  system/etc/init/o0ai-control.rc
  system/bin/o0ai-control-provision

The APK must be signed with the OEM platform certificate for production.
The init service runs as Android shell UID from `post-fs-data`, waits for the
package manager to expose the APK, and retries dpm for up to five minutes.
It records every dpm result. The script does not clear or modify
`device_provisioned` or `user_setup_complete`; Android itself decides whether
the current provisioning window still permits Device Owner setup.

The provisioning log is written to /data/local/tmp/o0ai-control/provision.log.

This ZIP is a system-image integration payload, not a recovery-flashable ZIP.
It does not require Magisk or ADB on the production device.

Important: if SetupWizard completes before one of the retries succeeds, Android
will reject `dpm set-device-owner`. For guaranteed production provisioning,
call the same component from ManagedProvisioning/SetupWizard before that state
transition.
