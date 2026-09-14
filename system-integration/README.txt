O0AI system integration payload

Merge the contents of the system directory into the Android 8.1 system image:

  system/priv-app/O0AIControl/O0AIControl.apk
  system/etc/init/o0ai-control.rc
  system/bin/o0ai-control-provision

The APK must be signed with the OEM platform certificate for production.
The init service runs as Android shell UID and invokes dpm during the first
boot window. Device Owner provisioning is accepted only before primary-user
setup is complete and when no other Device Owner exists.

The provisioning log is written to /data/local/tmp/o0ai-control/provision.log.

This ZIP is a system-image integration payload, not a recovery-flashable ZIP.
It does not require Magisk or ADB on the production device.
