package com.o0ai.control.ui;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
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
        getWindow().setStatusBarColor(Color.rgb(10, 14, 20));
        getWindow().setNavigationBarColor(Color.rgb(10, 14, 20));
        controller = new ControlPolicyController(this);
        buildUi();
        leaveKioskWhenDebugging();
        handlePendingDebugAction();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(10, 14, 20));
        root.setPadding(dp(18), dp(14), dp(18), dp(40));

        root.addView(toolbar("设备管控", "O0AI CONTROL  ·  DEBUG"));

        addSectionLabel(root, "设备状态");
        LinearLayout statusPanel = panel();
        status = new TextView(this);
        status.setTextColor(Color.rgb(216, 224, 234));
        status.setTextSize(14);
        status.setTypeface(Typeface.MONOSPACE);
        status.setLineSpacing(2, 1.0f);
        statusPanel.addView(status, fullWidth());
        root.addView(statusPanel);

        addSectionLabel(root, "管理员验证");
        LinearLayout authPanel = panel();

        password = new EditText(this);
        password.setHint("请输入管理员或工程密码");
        password.setInputType(0x00000081);
        password.setSingleLine(true);
        password.setImeOptions(EditorInfo.IME_ACTION_DONE);
        password.setTextColor(Color.WHITE);
        password.setHintTextColor(Color.rgb(119, 133, 151));
        password.setTextSize(16);
        password.setPadding(dp(14), 0, dp(14), 0);
        password.setBackground(inputBackground());
        password.setOnEditorActionListener((view, actionId, event) -> {
            boolean enterPressed = event != null
                    && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN;
            if (actionId == EditorInfo.IME_ACTION_DONE || enterPressed) {
                submitPassword();
                return true;
            }
            return false;
        });
        authPanel.addView(password, new LinearLayout.LayoutParams(-1, dp(48)));

        Button verify = primaryButton("确认密码");
        verify.setOnClickListener(v -> submitPassword());
        authPanel.addView(verify, marginTop(10));
        root.addView(authPanel);

        if (!controller.hasPassword()) {
            addSectionLabel(root, "首次使用");
            addMessage(root, "Debug 版本首次打开时可设置密码，密码至少 6 位。");
            Button setup = primaryButton("设置初始密码");
            setup.setOnClickListener(v -> setupPassword());
            root.addView(setup);
        } else {
            addSectionLabel(root, "设备管控");
            addButton(root, "应用设备管控策略", v -> requirePassword(true));
            addButton(root, "恢复系统管控", v -> restore());
            addButton(root, controller.isPermanentMode() ? "关闭完全授权模式" : "开启完全授权模式",
                    v -> togglePermanent());

            addSectionLabel(root, "权限与设置");
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
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(true);
        scroll.setScrollbarFadingEnabled(false);
        scroll.setBackgroundColor(Color.rgb(10, 14, 20));
        scroll.addView(root);
        setContentView(scroll);
        refreshStatus();
    }

    private void submitPassword() {
        if (!controller.hasPassword()) {
            setupPassword();
        } else if (controller.verifyPassword(password.getText().toString())) {
            toast("密码正确");
        } else {
            toast("密码错误");
        }
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
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(10, 14, 20));
        root.setPadding(dp(18), dp(14), dp(18), dp(40));
        root.addView(toolbar("设备策略", "可选限制"));
        addSectionLabel(root, "策略开关");
        LinearLayout options = panel();
        addPolicyOption(options, "禁用相机", ControlPolicyController.KEY_CAMERA_DISABLED);
        addPolicyOption(options, "禁止截屏", ControlPolicyController.KEY_SCREEN_CAPTURE_DISABLED);
        addPolicyOption(options, "禁用状态栏", ControlPolicyController.KEY_STATUS_BAR_DISABLED);
        root.addView(options);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(10, 14, 20));
        scroll.addView(root);
        setContentView(scroll);
    }

    private void addPolicyOption(LinearLayout root, String label, String key) {
        Button button = button(label + "    " + (controller.getOptionalRestriction(key) ? "● 已开启" : "○ 已关闭"));
        button.setOnClickListener(v -> {
            boolean enabled = !controller.getOptionalRestriction(key);
            if (controller.setOptionalRestriction(key, password.getText().toString(), enabled)) {
                button.setText(label + "    " + (enabled ? "● 已开启" : "○ 已关闭"));
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
        root.addView(button, marginTop(8));
    }

    private Button button(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextColor(Color.rgb(222, 230, 240));
        button.setTextSize(15);
        button.setGravity(android.view.Gravity.CENTER_VERTICAL | android.view.Gravity.LEFT);
        button.setPadding(dp(16), 0, dp(16), 0);
        button.setMinHeight(dp(46));
        button.setStateListAnimator(null);
        button.setBackground(secondaryBackground());
        return button;
    }

    private Button primaryButton(String text) {
        Button button = button(text);
        button.setTextColor(Color.rgb(6, 19, 22));
        button.setGravity(android.view.Gravity.CENTER);
        button.setBackground(primaryBackground());
        return button;
    }

    private LinearLayout toolbar(String titleText, String subtitleText) {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.VERTICAL);
        bar.setPadding(0, 0, 0, dp(16));

        TextView title = new TextView(this);
        title.setText(titleText);
        title.setTextColor(Color.WHITE);
        title.setTextSize(25);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        bar.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText(subtitleText);
        subtitle.setTextColor(Color.rgb(92, 207, 192));
        subtitle.setTextSize(11);
        subtitle.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        bar.addView(subtitle);
        return bar;
    }

    private void addSectionLabel(LinearLayout root, String text) {
        TextView label = new TextView(this);
        label.setText(text.toUpperCase());
        label.setTextColor(Color.rgb(92, 207, 192));
        label.setTextSize(11);
        label.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        label.setPadding(dp(2), dp(16), 0, dp(7));
        root.addView(label);
    }

    private void addMessage(LinearLayout root, String text) {
        TextView message = new TextView(this);
        message.setText(text);
        message.setTextColor(Color.rgb(158, 171, 188));
        message.setTextSize(13);
        message.setPadding(dp(2), 0, dp(2), dp(10));
        root.addView(message);
    }

    private LinearLayout panel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(14), dp(12), dp(14), dp(12));
        panel.setBackground(panelBackground());
        return panel;
    }

    private GradientDrawable panelBackground() {
        return rounded(Color.rgb(22, 29, 38), 8, Color.rgb(42, 54, 68), 1);
    }

    private GradientDrawable inputBackground() {
        return rounded(Color.rgb(12, 17, 24), 6, Color.rgb(57, 73, 91), 1);
    }

    private GradientDrawable primaryBackground() {
        return rounded(Color.rgb(92, 207, 192), 6, Color.rgb(92, 207, 192), 1);
    }

    private GradientDrawable secondaryBackground() {
        return rounded(Color.rgb(29, 38, 50), 6, Color.rgb(49, 63, 80), 1);
    }

    private GradientDrawable rounded(int fill, int radiusDp, int stroke, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radiusDp));
        drawable.setStroke(dp(strokeWidth), stroke);
        return drawable;
    }

    private LinearLayout.LayoutParams fullWidth() {
        return new LinearLayout.LayoutParams(-1, -2);
    }

    private LinearLayout.LayoutParams marginTop(int valueDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(46));
        params.topMargin = dp(valueDp);
        return params;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }
}
