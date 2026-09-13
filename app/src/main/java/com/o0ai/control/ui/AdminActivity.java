package com.o0ai.control.ui;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.o0ai.control.core.ControlPolicyController;

public class AdminActivity extends Activity {
    private ControlPolicyController controller;
    private EditText password;
    private TextView status;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        controller = new ControlPolicyController(this);
        buildUi();
        leaveKioskWhenDebugging();
        handlePendingDebugAction();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32, 32, 32, 32);

        TextView title = new TextView(this);
        title.setText("设备管控");
        title.setTextSize(24);
        root.addView(title);

        status = new TextView(this);
        status.setPadding(0, 24, 0, 24);
        root.addView(status);

        password = new EditText(this);
        password.setHint("请输入管理员或工程密码");
        password.setInputType(0x00000081);
        root.addView(password);

        if (!controller.hasPassword()) {
            Button setup = button("设置初始密码");
            setup.setOnClickListener(v -> setupPassword());
            root.addView(setup);
        } else {
            addButton(root, "应用设备管控策略", v -> requirePassword(true));
            addButton(root, "恢复系统管控", v -> restore());
            addButton(root, controller.isPermanentMode() ? "关闭完全授权模式" : "开启完全授权模式",
                    v -> togglePermanent());
            addButton(root, "权限管理", v -> openPermissions());
            addButton(root, "打开 Wi-Fi 设置", v -> openSettings("wifi"));
            addButton(root, "打开蓝牙设置", v -> openSettings("bluetooth"));
            addButton(root, "悬浮窗权限", v -> openSpecialSettings("overlay"));
            addButton(root, "修改系统设置权限", v -> openSpecialSettings("write"));
            addButton(root, "通知使用权", v -> openSpecialSettings("notification"));
            addButton(root, "使用情况访问权限", v -> openSpecialSettings("usage"));
            addButton(root, "无障碍服务", v -> openSpecialSettings("accessibility"));
            addButton(root, "设备策略开关", v -> openPolicyOptions());
        }
        setContentView(root);
        refreshStatus();
    }

    private void setupPassword() {
        if (controller.setPassword(password.getText().toString())) {
            toast("初始密码已设置，请重新打开管控页面");
            recreate();
        } else toast("密码至少需要 6 位");
    }

    private void requirePassword(boolean apply) {
        if (!controller.verifyPassword(password.getText().toString())) {
            toast("密码错误");
            return;
        }
        if (apply) toast(controller.applyPolicy() ? "设备管控策略已应用" : "应用失败：尚未成为 Device Owner");
        refreshStatus();
    }

    private void restore() {
        if (!controller.verifyPassword(password.getText().toString())) {
            toast("密码错误");
            return;
        }
        boolean restored = controller.lock(password.getText().toString());
        toast(restored ? "已恢复系统管控" : "恢复失败");
        if (restored) {
            startActivity(new Intent(this, KioskActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
            finish();
            return;
        }
        refreshStatus();
    }

    private void togglePermanent() {
        String value = password.getText().toString();
        if (!controller.verifyPassword(value)) {
            toast("密码错误");
            return;
        }
        boolean enabled = !controller.isPermanentMode();
        toast(controller.setPermanentMode(value, enabled)
                ? (enabled ? "完全授权模式已开启" : "完全授权模式已关闭")
                : "操作失败");
        recreate();
    }

    private void openPermissions() {
        startActivity(new Intent(this, PermissionActivity.class));
    }

    private void openSettings(String page) {
        if (!controller.verifyPassword(password.getText().toString())) {
            toast("密码错误");
            return;
        }
        if (controller.unlock(password.getText().toString(), false)) {
            startActivity(controller.settingsIntent(page));
        }
        else toast("打开失败：尚未成为 Device Owner");
    }

    private void openSpecialSettings(String page) {
        if (!controller.verifyPassword(password.getText().toString())) {
            toast("密码错误");
            return;
        }
        Intent intent = new Intent();
        if ("overlay".equals(page)) {
            intent.setAction("android.settings.action.MANAGE_OVERLAY_PERMISSION");
            intent.setData(android.net.Uri.parse("package:" + getPackageName()));
        } else if ("write".equals(page)) {
            intent.setAction("android.settings.action.MANAGE_WRITE_SETTINGS");
            intent.setData(android.net.Uri.parse("package:" + getPackageName()));
        } else if ("notification".equals(page)) {
            intent.setAction("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS");
        } else if ("usage".equals(page)) {
            intent.setAction("android.settings.USAGE_ACCESS_SETTINGS");
        } else {
            intent.setAction("android.settings.ACCESSIBILITY_SETTINGS");
        }
        try {
            startActivity(intent);
        } catch (RuntimeException e) {
            toast("此固件不支持该设置页面");
        }
    }

    private void openPolicyOptions() {
        if (!controller.verifyPassword(password.getText().toString())) {
            toast("密码错误");
            return;
        }
        LinearLayout options = new LinearLayout(this);
        options.setOrientation(LinearLayout.VERTICAL);
        addPolicyOption(options, "禁用相机", ControlPolicyController.KEY_CAMERA_DISABLED);
        addPolicyOption(options, "禁止截屏", ControlPolicyController.KEY_SCREEN_CAPTURE_DISABLED);
        addPolicyOption(options, "禁用状态栏", ControlPolicyController.KEY_STATUS_BAR_DISABLED);
        setContentView(options);
    }

    private void addPolicyOption(LinearLayout root, String label, String key) {
        Button button = button(label + "：" + (controller.getOptionalRestriction(key) ? "已开启" : "已关闭"));
        button.setOnClickListener(v -> {
            boolean enabled = !controller.getOptionalRestriction(key);
            if (controller.setOptionalRestriction(key, password.getText().toString(), enabled)) {
                button.setText(label + "：" + (enabled ? "已开启" : "已关闭"));
                toast("策略已更新");
            } else {
                toast("更新失败");
            }
        });
        root.addView(button);
    }

    private void handlePendingDebugAction() {
        if (!getIntent().getBooleanExtra("open_settings", false)) return;
        if (controller.isDebugMode()) {
            startActivity(controller.settingsIntent("all"));
        }
    }

    private void leaveKioskWhenDebugging() {
        if (!controller.isDebugMode()) return;
        try {
            stopLockTask();
        } catch (IllegalStateException ignored) {
            // The activity may have been launched before the kiosk task started.
        }
    }

    private void refreshStatus() {
        if (status != null) status.setText(controller.statusText());
    }

    private void addButton(LinearLayout root, String text, View.OnClickListener listener) {
        Button button = button(text);
        button.setOnClickListener(listener);
        root.addView(button);
    }

    private Button button(String text) {
        Button button = new Button(this);
        button.setText(text);
        return button;
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }
}
