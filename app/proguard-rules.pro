# Keep the device-admin receiver and debug entry points discoverable on vendor builds.
-keep public class com.o0ai.control.admin.ControlAdminReceiver { *; }
-keep public class com.o0ai.control.debug.DebugCommandReceiver { *; }
-keep public class com.o0ai.control.boot.BootReceiver { *; }
