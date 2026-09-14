package com.o0ai.control.core;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Process;
import android.os.UserManager;
import android.provider.Settings;

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
        if (!enabled) {
            if (!restorePolicy()) return false;
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_PERMANENT_MODE, enabled).apply();
        if (!enabled) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putBoolean(KEY_DEBUG_MODE, false)
                    .remove(KEY_DEBUG_UNTIL)
                    .remove(KEY_PENDING_ACTION)
                    .apply();
        }
        return true;
    }

    public boolean isDebugMode() {
        android.content.SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (prefs.getBoolean(KEY_PERMANENT_MODE, false)) return prefs.getBoolean(KEY_DEBUG_MODE, false);
        long until = prefs.getLong(KEY_DEBUG_UNTIL, 0L);
        return prefs.getBoolean(KEY_DEBUG_MODE, false) && until > System.currentTimeMillis();
    }

    public boolean unlock(String password, boolean permanentRequest) {
        if (!verifyPassword(password)) return false;
        if (permanentRequest && !isPermanentMode()) return false;
        long until = permanentRequest ? Long.MAX_VALUE : System.currentTimeMillis() + 10 * 60 * 1000L;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_DEBUG_MODE, true)
                .putLong(KEY_DEBUG_UNTIL, until)
                .putString(KEY_PENDING_ACTION, ACTION_UNLOCK)
                .apply();
        return applyDebugPolicy();
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
        if (prefs.getBoolean(KEY_PERMANENT_MODE, false)) return false;
        if (!prefs.getBoolean(KEY_DEBUG_MODE, false)) return false;
        long until = prefs.getLong(KEY_DEBUG_UNTIL, 0L);
        if (until <= 0L || until > System.currentTimeMillis()) return false;
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
            return true;
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
            return true;
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
            return true;
        } catch (SecurityException e) {
            return false;
        }
    }

    private void addRestrictions() {
        add(UserManager.DISALLOW_INSTALL_APPS);
        add(UserManager.DISALLOW_UNINSTALL_APPS);
        add(UserManager.DISALLOW_APPS_CONTROL);
        add(UserManager.DISALLOW_SAFE_BOOT);
        add(UserManager.DISALLOW_FACTORY_RESET);
        add(UserManager.DISALLOW_ADD_USER);
        add(UserManager.DISALLOW_DEBUGGING_FEATURES);
        add(UserManager.DISALLOW_CONFIG_CREDENTIALS);
        add(UserManager.DISALLOW_CONFIG_VPN);
        add(UserManager.DISALLOW_CONFIG_TETHERING);
        add(UserManager.DISALLOW_CONFIG_WIFI);
        add(UserManager.DISALLOW_CONFIG_BLUETOOTH);
        if (Build.VERSION.SDK_INT >= 28) {
            add(UserManager.DISALLOW_CONFIG_MOBILE_NETWORKS);
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
        if (Build.VERSION.SDK_INT >= 28) {
            remove(UserManager.DISALLOW_CONFIG_MOBILE_NETWORKS);
        }
        try {
            dpm.setUninstallBlocked(admin, PACKAGE_NAME, false);
        } catch (SecurityException ignored) {
            // Keep the state readable on vendor builds with partial DPC support.
        }
    }

    private void add(String restriction) {
        dpm.addUserRestriction(admin, restriction);
    }

    private void remove(String restriction) {
        dpm.clearUserRestriction(admin, restriction);
    }

    private void applyOptionalRestrictions(boolean locked) {
        android.content.SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        boolean cameraDisabled = prefs.getBoolean(KEY_CAMERA_DISABLED, false);
        boolean captureDisabled = prefs.getBoolean(KEY_SCREEN_CAPTURE_DISABLED, false);
        boolean statusBarDisabled = prefs.getBoolean(KEY_STATUS_BAR_DISABLED, false);
        dpm.setCameraDisabled(admin, locked && cameraDisabled);
        dpm.setScreenCaptureDisabled(admin, locked && captureDisabled);
        dpm.setStatusBarDisabled(admin, locked && statusBarDisabled);
    }

    public boolean setOptionalRestriction(String key, String password, boolean enabled) {
        if (!verifyPassword(password) || !isDeviceOwner()) return false;
        if (!KEY_CAMERA_DISABLED.equals(key)
                && !KEY_SCREEN_CAPTURE_DISABLED.equals(key)
                && !KEY_STATUS_BAR_DISABLED.equals(key)) return false;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean(key, enabled).apply();
        return applyPolicy();
    }

    public boolean getOptionalRestriction(String key) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(key, false);
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
        try {
            int uid = context.getPackageManager().getPackageUid(packageName, 0);
            return context.checkPermission(permission, Process.myPid(), uid)
                    == PackageManager.PERMISSION_GRANTED;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
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
        return "Device Owner=" + isDeviceOwner()
                + "\n完全授权模式=" + isPermanentMode()
                + "\n当前调试模式=" + isDebugMode()
                + "\n相机禁用=" + getOptionalRestriction(KEY_CAMERA_DISABLED)
                + "\n禁止截屏=" + getOptionalRestriction(KEY_SCREEN_CAPTURE_DISABLED)
                + "\n状态栏禁用=" + getOptionalRestriction(KEY_STATUS_BAR_DISABLED)
                + "\n表冠映射=BUTTON_1 / 188";
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
