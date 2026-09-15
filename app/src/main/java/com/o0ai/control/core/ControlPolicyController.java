package com.o0ai.control.core;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.UserManager;
import android.provider.Settings;

import com.o0ai.control.BuildConfig;
import com.o0ai.control.admin.ControlAdminReceiver;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

public final class ControlPolicyController {
    public static final String PACKAGE_NAME = "com.o0ai.control";
    public static final String KEY_PERMANENT_MODE = "permanent_mode";
    public static final String KEY_DEBUG_MODE = "debug_mode";
    public static final String KEY_DEBUG_UNTIL = "debug_until";
    public static final String KEY_PENDING_ACTION = "pending_action";
    public static final String KEY_CAMERA_DISABLED = "camera_disabled";
    public static final String KEY_SCREEN_CAPTURE_DISABLED = "screen_capture_disabled";
    public static final String KEY_STATUS_BAR_DISABLED = "status_bar_disabled";
    public static final String KEY_USB_FILE_TRANSFER_DISABLED = "usb_file_transfer_disabled";
    public static final String KEY_UNKNOWN_SOURCES_DISABLED = "unknown_sources_disabled";
    public static final String KEY_DATE_TIME_DISABLED = "date_time_disabled";
    public static final String KEY_LOCALE_DISABLED = "locale_disabled";
    public static final String KEY_SCREEN_TIMEOUT_DISABLED = "screen_timeout_disabled";
    public static final String KEY_ACCOUNTS_DISABLED = "accounts_disabled";
    public static final String KEY_SHARE_LOCATION_DISABLED = "share_location_disabled";
    public static final String KEY_ADJUST_VOLUME_DISABLED = "adjust_volume_disabled";
    public static final String KEY_UNMUTE_MICROPHONE_DISABLED = "unmute_microphone_disabled";
    public static final String KEY_DEBUGGING_DISABLED = "debugging_disabled";
    public static final String KEY_INSTALL_APPS_DISABLED = "install_apps_disabled";
    public static final String KEY_UNINSTALL_APPS_DISABLED = "uninstall_apps_disabled";
    public static final String KEY_SAFE_BOOT_DISABLED = "safe_boot_disabled";
    public static final String KEY_FACTORY_RESET_DISABLED = "factory_reset_disabled";
    public static final String KEY_ADD_USER_DISABLED = "add_user_disabled";
    public static final String KEY_APPS_CONTROL_DISABLED = "apps_control_disabled";
    public static final String KEY_CREDENTIALS_DISABLED = "credentials_disabled";
    public static final String KEY_VPN_DISABLED = "vpn_disabled";
    public static final String KEY_TETHERING_DISABLED = "tethering_disabled";
    public static final String KEY_WIFI_DISABLED = "wifi_disabled";
    public static final String KEY_BLUETOOTH_DISABLED = "bluetooth_disabled";
    public static final String KEY_MOBILE_NETWORKS_DISABLED = "mobile_networks_disabled";
    public static final String KEY_LOCATION_DISABLED = "location_disabled";
    public static final String KEY_OUTGOING_CALLS_DISABLED = "outgoing_calls_disabled";
    public static final String KEY_SMS_DISABLED = "sms_disabled";
    public static final String KEY_MOUNT_MEDIA_DISABLED = "mount_media_disabled";
    public static final String KEY_DEFAULT_APPS_DISABLED = "default_apps_disabled";
    public static final String KEY_DATA_ROAMING_DISABLED = "data_roaming_disabled";
    public static final String KEY_NETWORK_RESET_DISABLED = "network_reset_disabled";

    public static final String ACTION_UNLOCK = "unlock";
    public static final String ACTION_OPEN_SETTINGS = "open_settings";

    private static final String PREFS = "control_policy";
    private static final String PASSWORD_HASH = "password_hash";
    private static final String PASSWORD_SALT = "password_salt";
    private static final String SETTINGS_PACKAGE = "com.android.settings";

    private final Context context;
    private final DevicePolicyManager dpm;
    private final ComponentName admin;

    public ControlPolicyController(Context context) {
        this.context = context.getApplicationContext();
        this.dpm = (DevicePolicyManager) this.context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        this.admin = new ComponentName(this.context, ControlAdminReceiver.class);
    }

    public synchronized void ensureDebugPassword() {
        if (BuildConfig.DEBUG && !hasPassword()) {
            setPassword("260914");
        }
    }

    public boolean isDeviceOwner() {
        return dpm != null && dpm.isDeviceOwnerApp(PACKAGE_NAME);
    }

    public boolean hasPassword() {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).contains(PASSWORD_HASH);
    }

    public boolean setPassword(String password) {
        if (password == null || password.length() < 6) return false;
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        byte[] hash = digest(password, salt);
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(PASSWORD_SALT, hex(salt))
                .putString(PASSWORD_HASH, hex(hash))
                .apply();
        return true;
    }

    public boolean verifyPassword(String password) {
        if (password == null) return false;
        android.content.SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String saltText = prefs.getString(PASSWORD_SALT, null);
        String hashText = prefs.getString(PASSWORD_HASH, null);
        if (saltText == null || hashText == null) return false;
        byte[] expected = fromHex(hashText);
        byte[] actual = digest(password, fromHex(saltText));
        return MessageDigest.isEqual(expected, actual);
    }

    public boolean isPermanentMode() {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_PERMANENT_MODE, false);
    }

    public boolean setPermanentMode(String password, boolean enabled) {
        if (!verifyPassword(password)) return false;
        if (!isDeviceOwner()) return false;
        if (enabled) {
            if (!applyPolicy()) return false;
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putBoolean(KEY_PERMANENT_MODE, true)
                    .putBoolean(KEY_DEBUG_MODE, false)
                    .remove(KEY_DEBUG_UNTIL)
                    .remove(KEY_PENDING_ACTION)
                    .apply();
            return true;
        }
        if (!restorePolicy()) return false;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_PERMANENT_MODE, false)
                .putBoolean(KEY_DEBUG_MODE, false)
                .remove(KEY_DEBUG_UNTIL)
                .remove(KEY_PENDING_ACTION)
                .apply();
        return true;
    }

    public boolean isDebugMode() {
        android.content.SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long until = prefs.getLong(KEY_DEBUG_UNTIL, 0L);
        return prefs.getBoolean(KEY_DEBUG_MODE, false)
                && (until == Long.MAX_VALUE || until > System.currentTimeMillis());
    }

    public boolean unlock(String password, boolean permanentRequest) {
        if (!verifyPassword(password)) return false;
        if (permanentRequest && !isPermanentMode()) return false;
        if (!applyDebugPolicy()) return false;
        long until = permanentRequest ? Long.MAX_VALUE : System.currentTimeMillis() + 10 * 60 * 1000L;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_DEBUG_MODE, true)
                .putLong(KEY_DEBUG_UNTIL, until)
                .putString(KEY_PENDING_ACTION, ACTION_UNLOCK)
                .apply();
        return true;
    }

    public boolean lock(String password) {
        if (!verifyPassword(password)) return false;
        if (!restorePolicy()) return false;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_DEBUG_MODE, false)
                .putBoolean(KEY_PERMANENT_MODE, false)
                .remove(KEY_DEBUG_UNTIL)
                .remove(KEY_PENDING_ACTION)
                .apply();
        return true;
    }

    public boolean restoreExpiredTemporarySession() {
        android.content.SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!prefs.getBoolean(KEY_DEBUG_MODE, false)) return false;
        long until = prefs.getLong(KEY_DEBUG_UNTIL, 0L);
        if (until <= 0L || until == Long.MAX_VALUE || until > System.currentTimeMillis()) return false;
        if (!restorePolicy()) return false;
        prefs.edit()
                .putBoolean(KEY_DEBUG_MODE, false)
                .remove(KEY_DEBUG_UNTIL)
                .remove(KEY_PENDING_ACTION)
                .apply();
        return true;
    }

    public boolean applyPolicy() {
        if (!isDeviceOwner()) return false;
        try {
            dpm.setLockTaskPackages(admin, new String[]{PACKAGE_NAME});
            addRestrictions();
            applyOptionalRestrictions(true);
            return suspendSettings(true);
        } catch (SecurityException e) {
            return false;
        }
    }

    public boolean applyDebugPolicy() {
        if (!isDeviceOwner()) return false;
        try {
            dpm.setLockTaskPackages(admin, new String[]{PACKAGE_NAME, SETTINGS_PACKAGE});
            removeRestrictions();
            applyOptionalRestrictions(false);
            return suspendSettings(false);
        } catch (SecurityException e) {
            return false;
        }
    }

    public boolean restorePolicy() {
        if (!isDeviceOwner()) return false;
        try {
            dpm.setLockTaskPackages(admin, new String[]{PACKAGE_NAME});
            addRestrictions();
            applyOptionalRestrictions(true);
            return suspendSettings(true);
        } catch (SecurityException e) {
            return false;
        }
    }

    /** Suspends Settings while locked so its activities cannot be launched. */
    private boolean suspendSettings(boolean suspended) {
        if (dpm == null || !isDeviceOwner()) return false;
        if (!suspended) {
            try {
                dpm.setApplicationHidden(admin, SETTINGS_PACKAGE, false);
            } catch (RuntimeException ignored) {
                // Continue; package suspension may still be supported.
            }
        }
        try {
            String[] failed = dpm.setPackagesSuspended(
                    admin, new String[]{SETTINGS_PACKAGE}, suspended);
            // On several Android 8.1 vendor builds PackageManager state is updated
            // asynchronously.  A successful DPM call is the reliable result here;
            // checking immediately can incorrectly roll back a valid policy.
            if (failed == null || failed.length == 0) return true;
        } catch (RuntimeException e) {
            // Some vendor builds reject package suspension for system packages.
        }
        try {
            boolean changed = dpm.setApplicationHidden(admin, SETTINGS_PACKAGE, suspended);
            return changed && isSettingsRestricted() == suspended;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private boolean isSettingsRestricted() {
        if (dpm == null || !isDeviceOwner()) return false;
        try {
            if (dpm.isApplicationHidden(admin, SETTINGS_PACKAGE)) return true;
        } catch (SecurityException | UnsupportedOperationException ignored) {
            // Fall through to package manager suspension state.
        }
        if (Build.VERSION.SDK_INT >= 24) {
            try {
                return context.getPackageManager().isPackageSuspended(SETTINGS_PACKAGE);
            } catch (PackageManager.NameNotFoundException | RuntimeException ignored) {
                return false;
            }
        }
        return false;
    }

    private void addRestrictions() {
        addIfEnabled(UserManager.DISALLOW_INSTALL_APPS, KEY_INSTALL_APPS_DISABLED);
        addIfEnabled(UserManager.DISALLOW_UNINSTALL_APPS, KEY_UNINSTALL_APPS_DISABLED);
        addIfEnabled(UserManager.DISALLOW_APPS_CONTROL, KEY_APPS_CONTROL_DISABLED);
        addIfEnabled(UserManager.DISALLOW_SAFE_BOOT, KEY_SAFE_BOOT_DISABLED);
        addIfEnabled(UserManager.DISALLOW_FACTORY_RESET, KEY_FACTORY_RESET_DISABLED);
        addIfEnabled(UserManager.DISALLOW_ADD_USER, KEY_ADD_USER_DISABLED);
        addIfEnabled(UserManager.DISALLOW_DEBUGGING_FEATURES, KEY_DEBUGGING_DISABLED);
        addIfEnabled(UserManager.DISALLOW_CONFIG_CREDENTIALS, KEY_CREDENTIALS_DISABLED);
        addIfEnabled(UserManager.DISALLOW_CONFIG_VPN, KEY_VPN_DISABLED);
        addIfEnabled(UserManager.DISALLOW_CONFIG_TETHERING, KEY_TETHERING_DISABLED);
        addIfEnabled(UserManager.DISALLOW_CONFIG_WIFI, KEY_WIFI_DISABLED);
        addIfEnabled(UserManager.DISALLOW_CONFIG_BLUETOOTH, KEY_BLUETOOTH_DISABLED);
        addIfEnabled(UserManager.DISALLOW_USB_FILE_TRANSFER, KEY_USB_FILE_TRANSFER_DISABLED);
        addIfEnabled(UserManager.DISALLOW_CONFIG_DATE_TIME, KEY_DATE_TIME_DISABLED);
        addIfEnabled(UserManager.DISALLOW_CONFIG_LOCALE, KEY_LOCALE_DISABLED);
        if (Build.VERSION.SDK_INT >= 28) {
            addIfEnabled(UserManager.DISALLOW_CONFIG_SCREEN_TIMEOUT, KEY_SCREEN_TIMEOUT_DISABLED);
        }
        addIfEnabled(UserManager.DISALLOW_MODIFY_ACCOUNTS, KEY_ACCOUNTS_DISABLED);
        addIfEnabled(UserManager.DISALLOW_SHARE_LOCATION, KEY_SHARE_LOCATION_DISABLED);
        addIfEnabled(UserManager.DISALLOW_ADJUST_VOLUME, KEY_ADJUST_VOLUME_DISABLED);
        if (Build.VERSION.SDK_INT >= 26) {
            addIfEnabled(UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES, KEY_UNKNOWN_SOURCES_DISABLED);
        }
        if (Build.VERSION.SDK_INT >= 28) {
            addIfEnabled(UserManager.DISALLOW_UNMUTE_MICROPHONE, KEY_UNMUTE_MICROPHONE_DISABLED);
        }
        if (Build.VERSION.SDK_INT >= 28) {
            addIfEnabled(UserManager.DISALLOW_CONFIG_MOBILE_NETWORKS, KEY_MOBILE_NETWORKS_DISABLED);
        }
        addIfEnabled(UserManager.DISALLOW_CONFIG_LOCATION, KEY_LOCATION_DISABLED);
        addIfEnabled(UserManager.DISALLOW_OUTGOING_CALLS, KEY_OUTGOING_CALLS_DISABLED);
        addIfEnabled(UserManager.DISALLOW_SMS, KEY_SMS_DISABLED);
        addIfEnabled(UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA, KEY_MOUNT_MEDIA_DISABLED);
        if (Build.VERSION.SDK_INT >= 24) {
            addIfEnabled(UserManager.DISALLOW_DATA_ROAMING, KEY_DATA_ROAMING_DISABLED);
            addIfEnabled(UserManager.DISALLOW_NETWORK_RESET, KEY_NETWORK_RESET_DISABLED);
        }
        if (Build.VERSION.SDK_INT >= 26) {
            addIfEnabled(UserManager.DISALLOW_CONFIG_DEFAULT_APPS, KEY_DEFAULT_APPS_DISABLED);
        }
        try {
            dpm.setUninstallBlocked(admin, PACKAGE_NAME, true);
        } catch (SecurityException ignored) {
            // Some vendor Android 8.1 builds do not expose this policy to DPCs.
        }
    }

    private void removeRestrictions() {
        remove(UserManager.DISALLOW_INSTALL_APPS);
        remove(UserManager.DISALLOW_UNINSTALL_APPS);
        remove(UserManager.DISALLOW_APPS_CONTROL);
        remove(UserManager.DISALLOW_SAFE_BOOT);
        remove(UserManager.DISALLOW_FACTORY_RESET);
        remove(UserManager.DISALLOW_ADD_USER);
        remove(UserManager.DISALLOW_DEBUGGING_FEATURES);
        remove(UserManager.DISALLOW_CONFIG_CREDENTIALS);
        remove(UserManager.DISALLOW_CONFIG_VPN);
        remove(UserManager.DISALLOW_CONFIG_TETHERING);
        remove(UserManager.DISALLOW_CONFIG_WIFI);
        remove(UserManager.DISALLOW_CONFIG_BLUETOOTH);
        remove(UserManager.DISALLOW_USB_FILE_TRANSFER);
        remove(UserManager.DISALLOW_CONFIG_DATE_TIME);
        remove(UserManager.DISALLOW_CONFIG_LOCALE);
        if (Build.VERSION.SDK_INT >= 28) {
            remove(UserManager.DISALLOW_CONFIG_SCREEN_TIMEOUT);
        }
        remove(UserManager.DISALLOW_MODIFY_ACCOUNTS);
        remove(UserManager.DISALLOW_SHARE_LOCATION);
        remove(UserManager.DISALLOW_ADJUST_VOLUME);
        if (Build.VERSION.SDK_INT >= 26) {
            remove(UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES);
        }
        if (Build.VERSION.SDK_INT >= 28) {
            remove(UserManager.DISALLOW_UNMUTE_MICROPHONE);
        }
        if (Build.VERSION.SDK_INT >= 28) {
            remove(UserManager.DISALLOW_CONFIG_MOBILE_NETWORKS);
        }
        remove(UserManager.DISALLOW_CONFIG_LOCATION);
        remove(UserManager.DISALLOW_OUTGOING_CALLS);
        remove(UserManager.DISALLOW_SMS);
        remove(UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA);
        if (Build.VERSION.SDK_INT >= 24) {
            remove(UserManager.DISALLOW_DATA_ROAMING);
            remove(UserManager.DISALLOW_NETWORK_RESET);
        }
        if (Build.VERSION.SDK_INT >= 26) {
            remove(UserManager.DISALLOW_CONFIG_DEFAULT_APPS);
        }
        try {
            dpm.setUninstallBlocked(admin, PACKAGE_NAME, false);
        } catch (SecurityException ignored) {
            // Keep the state readable on vendor builds with partial DPC support.
        }
    }

    private void add(String restriction) {
        try {
            dpm.addUserRestriction(admin, restriction);
        } catch (RuntimeException ignored) {
            // Vendor builds may reject individual restrictions.
        }
    }

    private void addIfEnabled(String restriction, String key) {
        if (getOptionalRestriction(key)) add(restriction);
    }

    private void remove(String restriction) {
        try {
            dpm.clearUserRestriction(admin, restriction);
        } catch (RuntimeException ignored) {
            // Vendor builds may omit individual restrictions.
        }
    }

    private void applyOptionalRestrictions(boolean locked) {
        android.content.SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        boolean cameraDisabled = prefs.getBoolean(KEY_CAMERA_DISABLED, false);
        boolean captureDisabled = prefs.getBoolean(KEY_SCREEN_CAPTURE_DISABLED, false);
        boolean statusBarDisabled = prefs.getBoolean(KEY_STATUS_BAR_DISABLED, false);
        try {
            dpm.setCameraDisabled(admin, locked && cameraDisabled);
        } catch (RuntimeException ignored) { }
        try {
            dpm.setScreenCaptureDisabled(admin, locked && captureDisabled);
        } catch (RuntimeException ignored) { }
        try {
            dpm.setStatusBarDisabled(admin, locked && statusBarDisabled);
        } catch (RuntimeException ignored) { }
    }

    public boolean setOptionalRestriction(String key, String password, boolean enabled) {
        if (!verifyPassword(password) || !isDeviceOwner()) return false;
        if (!isSupportedOptionalKey(key)) return false;
        boolean previous = getOptionalRestriction(key);
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean(key, enabled).apply();
        boolean wasDebug = isDebugMode();
        boolean applied = wasDebug ? applyDebugPolicy() : applyPolicy();
        if (applied) return true;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean(key, previous).apply();
        if (wasDebug) applyDebugPolicy();
        else applyPolicy();
        return false;
    }

    private boolean isSupportedOptionalKey(String key) {
        return KEY_CAMERA_DISABLED.equals(key)
                || KEY_SCREEN_CAPTURE_DISABLED.equals(key)
                || KEY_STATUS_BAR_DISABLED.equals(key)
                || KEY_USB_FILE_TRANSFER_DISABLED.equals(key)
                || KEY_UNKNOWN_SOURCES_DISABLED.equals(key)
                || KEY_DATE_TIME_DISABLED.equals(key)
                || KEY_LOCALE_DISABLED.equals(key)
                || KEY_SCREEN_TIMEOUT_DISABLED.equals(key)
                || KEY_ACCOUNTS_DISABLED.equals(key)
                || KEY_SHARE_LOCATION_DISABLED.equals(key)
                || KEY_ADJUST_VOLUME_DISABLED.equals(key)
                || KEY_UNMUTE_MICROPHONE_DISABLED.equals(key)
                || KEY_DEBUGGING_DISABLED.equals(key)
                || KEY_INSTALL_APPS_DISABLED.equals(key)
                || KEY_UNINSTALL_APPS_DISABLED.equals(key)
                || KEY_SAFE_BOOT_DISABLED.equals(key)
                || KEY_FACTORY_RESET_DISABLED.equals(key)
                || KEY_ADD_USER_DISABLED.equals(key)
                || KEY_APPS_CONTROL_DISABLED.equals(key)
                || KEY_CREDENTIALS_DISABLED.equals(key)
                || KEY_VPN_DISABLED.equals(key)
                || KEY_TETHERING_DISABLED.equals(key)
                || KEY_WIFI_DISABLED.equals(key)
                || KEY_BLUETOOTH_DISABLED.equals(key)
                || KEY_MOBILE_NETWORKS_DISABLED.equals(key)
                || KEY_LOCATION_DISABLED.equals(key)
                || KEY_OUTGOING_CALLS_DISABLED.equals(key)
                || KEY_SMS_DISABLED.equals(key)
                || KEY_MOUNT_MEDIA_DISABLED.equals(key)
                || KEY_DEFAULT_APPS_DISABLED.equals(key)
                || KEY_DATA_ROAMING_DISABLED.equals(key)
                || KEY_NETWORK_RESET_DISABLED.equals(key);
    }

    public boolean getOptionalRestriction(String key) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(key, defaultRestrictionValue(key));
    }

    private boolean defaultRestrictionValue(String key) {
        return KEY_INSTALL_APPS_DISABLED.equals(key)
                || KEY_UNINSTALL_APPS_DISABLED.equals(key)
                || KEY_SAFE_BOOT_DISABLED.equals(key)
                || KEY_FACTORY_RESET_DISABLED.equals(key)
                || KEY_ADD_USER_DISABLED.equals(key)
                || KEY_APPS_CONTROL_DISABLED.equals(key)
                || KEY_CREDENTIALS_DISABLED.equals(key)
                || KEY_VPN_DISABLED.equals(key)
                || KEY_TETHERING_DISABLED.equals(key)
                || KEY_WIFI_DISABLED.equals(key)
                || KEY_BLUETOOTH_DISABLED.equals(key)
                || KEY_MOBILE_NETWORKS_DISABLED.equals(key);
    }

    public boolean setRuntimePermission(String packageName, String permission, boolean allowed) {
        if (!isDeviceOwner() || packageName == null || permission == null || Build.VERSION.SDK_INT < 23) return false;
        try {
            int state = allowed
                    ? DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED
                    : DevicePolicyManager.PERMISSION_GRANT_STATE_DENIED;
            return dpm.setPermissionGrantState(admin, packageName, permission, state);
        } catch (SecurityException | IllegalArgumentException e) {
            return false;
        }
    }

    public boolean isRuntimePermissionAllowed(String packageName, String permission) {
        if (packageName == null || permission == null) return false;
        return context.getPackageManager().checkPermission(permission, packageName)
                == PackageManager.PERMISSION_GRANTED;
    }

    public void setPendingAction(String action) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_PENDING_ACTION, action).apply();
    }

    public String consumePendingAction() {
        android.content.SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String action = prefs.getString(KEY_PENDING_ACTION, "");
        prefs.edit().remove(KEY_PENDING_ACTION).apply();
        return action;
    }

    public Intent settingsIntent(String page) {
        if ("wifi".equals(page)) return new Intent(Settings.ACTION_WIFI_SETTINGS);
        if ("bluetooth".equals(page)) return new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
        if ("apps".equals(page)) return new Intent(Settings.ACTION_APPLICATION_SETTINGS);
        return new Intent(Settings.ACTION_SETTINGS);
    }

    public String statusText() {
        boolean settingsSuspended = isSettingsRestricted();
        StringBuilder out = new StringBuilder();
        out.append("Device Owner=").append(isDeviceOwner())
                .append("\n完全授权模式=").append(isPermanentMode())
                .append("\n当前调试模式=").append(isDebugMode())
                .append("\n系统设置已限制=").append(settingsSuspended)
                .append("\n相机禁用=").append(getOptionalRestriction(KEY_CAMERA_DISABLED))
                .append("\n禁止截屏=").append(getOptionalRestriction(KEY_SCREEN_CAPTURE_DISABLED))
                .append("\n状态栏禁用=").append(getOptionalRestriction(KEY_STATUS_BAR_DISABLED))
                .append("\n调试功能禁用=").append(getOptionalRestriction(KEY_DEBUGGING_DISABLED))
                .append("\n安装应用禁用=").append(getOptionalRestriction(KEY_INSTALL_APPS_DISABLED))
                .append("\nWi-Fi 配置禁用=").append(getOptionalRestriction(KEY_WIFI_DISABLED))
                .append("\n蓝牙配置禁用=").append(getOptionalRestriction(KEY_BLUETOOTH_DISABLED))
                .append("\n表冠映射由甲方软件处理");
        return out.toString();
    }

    private static byte[] digest(String password, byte[] salt) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] value = (password + ":o0ai-control").getBytes(StandardCharsets.UTF_8);
            for (int i = 0; i < 120000; i++) {
                md.reset();
                md.update(salt);
                value = md.digest(value);
            }
            return value;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) out.append(String.format("%02x", value & 0xff));
        return out.toString();
    }

    private static byte[] fromHex(String text) {
        byte[] out = new byte[text.length() / 2];
        for (int i = 0; i < out.length; i++) out[i] = (byte) Integer.parseInt(text.substring(i * 2, i * 2 + 2), 16);
        return out;
    }
}
