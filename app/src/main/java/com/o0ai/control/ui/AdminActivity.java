package com.o0ai.control.ui;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AdminActivity extends Activity {
    private interface Task<T> { T run(); }
    private interface Result<T> { void accept(T value); }

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private ControlPolicyController controller;
    private EditText password;
    private TextView status;
    private boolean busy;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(10, 14, 20));
        getWindow().setNavigationBarColor(Color.rgb(10, 14, 20));
        controller = new ControlPolicyController(this);
        showLoading();
        worker.execute(() -> {
            controller.ensureDebugPassword();
            main.post(() -> {
                if (isFinishing()) return;
                buildUi();
                leaveKioskWhenDebugging();
                handlePendingDebugAction();
            });
        });
    }

    @Override
    protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }

    private void showLoading() {
        TextView view = new TextView(this);
        view.setText("正在加载设备管控...");
        view.setTextColor(Color.WHITE);
        view.setTextSize(16);
        view.setGravity(Gravity.CENTER);
        view.setBackgroundColor(Color.rgb(10, 14, 20));
        setContentView(view);
    }

    private void buildUi() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setBackgroundColor(Color.rgb(10, 14, 20));
        content.setPadding(dp(18), dp(14), dp(18), dp(40));
        content.addView(toolbar("设备管控", "O0AI CONTROL  /  DEVICE OWNER"));

        addSectionLabel(content, "设备状态");
        LinearLayout panel = panel();
        status = new TextView(this);
        status.setTextColor(Color.rgb(216, 224, 234));
        status.setTextSize(14);
        status.setTypeface(Typeface.MONOSPACE);
        panel.addView(status, fullWidth());
        content.addView(panel);

        addSectionLabel(content, "管理员验证");
        LinearLayout auth = panel();
        password = new EditText(this);
        password.setHint("请输入管理员密码");
        password.setInputType(0x00000081);
        password.setSingleLine(true);
        password.setImeOptions(EditorInfo.IME_ACTION_DONE);
        password.setTextColor(Color.WHITE);
        password.setHintTextColor(Color.rgb(119, 133, 151));
        password.setTextSize(16);
        password.setPadding(dp(14), 0, dp(14), 0);
        password.setBackground(inputBackground());
        password.setOnEditorActionListener((view, actionId, event) -> {
            boolean enter = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN;
            if (actionId == EditorInfo.IME_ACTION_DONE || enter) {
                submitPassword();
                return true;
            }
            return false;
        });
        auth.addView(password, new LinearLayout.LayoutParams(-1, dp(48)));
        Button verify = primaryButton("确认密码");
        verify.setOnClickListener(v -> submitPassword());
        auth.addView(verify, marginTop(10));
        content.addView(auth);

        if (!controller.hasPassword()) {
            addSectionLabel(content, "首次使用");
            addMessage(content, "请输入至少 6 位密码完成初始化。");
            Button setup = primaryButton("设置初始密码");
            setup.setOnClickListener(v -> setupPassword());
            content.addView(setup);
        } else {
            addSectionLabel(content, "设备管控");
            addButton(content, "应用设备管控策略", v -> requirePassword());
            addButton(content, "恢复系统管控", v -> restore());
            addButton(content, controller.isPermanentMode() ? "关闭完全授权模式" : "开启完全授权模式",
                    v -> togglePermanent());

            addSectionLabel(content, "权限与设置");
            addButton(content, "权限管理", v -> openPermissions());
            addButton(content, "打开 Wi-Fi 设置", v -> openSettings("wifi"));
            addButton(content, "打开蓝牙设置", v -> openSettings("bluetooth"));
            addButton(content, "修改系统设置权限", v -> openSpecialSettings("write"));
            addButton(content, "悬浮窗权限", v -> openSpecialSettings("overlay"));
            addButton(content, "使用情况访问权限", v -> openSpecialSettings("usage"));
            addButton(content, "设备策略开关", v -> openPolicyOptions());
        }

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(true);
        scroll.setScrollbarFadingEnabled(false);
        scroll.setBackgroundColor(Color.rgb(10, 14, 20));
        scroll.addView(content);
        setContentView(scroll);
        refreshStatus();
    }

    private void submitPassword() {
        final String value = password.getText().toString();
        final boolean hadPassword = controller.hasPassword();
        runTask(() -> hadPassword
                ? controller.verifyPassword(value)
                : controller.setPassword(value), ok -> {
            toast(ok ? "密码正确" : "密码错误或长度不足");
            if (ok && !hadPassword) recreate();
        });
    }

    private void setupPassword() {
        final String value = password.getText().toString();
        runTask(() -> controller.setPassword(value), ok -> {
            toast(ok ? "初始密码已设置" : "密码至少需要 6 位");
            if (ok) recreate();
        });
    }

    private void requirePassword() {
        final String value = password.getText().toString();
        runTask(() -> controller.verifyPassword(value) && controller.applyPolicy(), ok -> {
            toast(ok ? "设备管控策略已应用" : "密码错误或尚未成为 Device Owner");
            refreshStatus();
        });
    }

    private void restore() {
        final String value = password.getText().toString();
        runTask(() -> controller.lock(value), ok -> {
            toast(ok ? "系统管控已恢复" : "恢复失败");
            if (ok) finish();
            else refreshStatus();
        });
    }

    private void togglePermanent() {
        final String value = password.getText().toString();
        final boolean enabled = !controller.isPermanentMode();
        runTask(() -> controller.setPermanentMode(value, enabled), ok -> {
            toast(ok ? "完全授权模式已更新" : "密码错误或尚未成为 Device Owner");
            if (ok) recreate();
        });
    }

    private void openPermissions() {
        startActivity(new Intent(this, PermissionActivity.class));
    }

    private void openSettings(String page) {
        if ("wifi".equals(page)) {
            startActivity(new Intent(this, WifiActivity.class));
            return;
        }
        final String value = password.getText().toString();
        runTask(() -> controller.unlock(value, false), ok -> {
            if (ok) startActivity(controller.settingsIntent(page));
            else toast("密码错误或尚未成为 Device Owner");
        });
    }

    private void openSpecialSettings(String page) {
        final String value = password.getText().toString();
        runTask(() -> controller.verifyPassword(value), ok -> {
            if (!ok) {
                toast("密码错误");
                return;
            }
            Intent intent = new Intent();
            if ("write".equals(page)) {
                intent.setAction(Settings.ACTION_MANAGE_WRITE_SETTINGS);
                intent.setData(android.net.Uri.parse("package:" + getPackageName()));
            } else if ("overlay".equals(page)) {
                intent.setAction(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
                intent.setData(android.net.Uri.parse("package:" + getPackageName()));
            } else {
                intent.setAction(Settings.ACTION_USAGE_ACCESS_SETTINGS);
            }
            try {
                startActivity(intent);
            } catch (RuntimeException e) {
                toast("此系统不支持该设置页面");
            }
        });
    }

    private void openPolicyOptions() {
        final String value = password.getText().toString();
        runTask(() -> controller.verifyPassword(value), ok -> {
            if (ok) showPolicyOptions();
            else toast("密码错误");
        });
    }

    private void showPolicyOptions() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setBackgroundColor(Color.rgb(10, 14, 20));
        content.setPadding(dp(18), dp(14), dp(18), dp(40));
        content.addView(toolbar("设备策略", "可选限制"));
        addSectionLabel(content, "策略开关");
        LinearLayout options = panel();
        addPolicyOption(options, "禁用相机", ControlPolicyController.KEY_CAMERA_DISABLED);
        addPolicyOption(options, "禁止截屏", ControlPolicyController.KEY_SCREEN_CAPTURE_DISABLED);
        addPolicyOption(options, "禁用状态栏", ControlPolicyController.KEY_STATUS_BAR_DISABLED);
        addPolicyOption(options, "禁止 USB 文件传输", ControlPolicyController.KEY_USB_FILE_TRANSFER_DISABLED);
        addPolicyOption(options, "禁止安装未知来源应用", ControlPolicyController.KEY_UNKNOWN_SOURCES_DISABLED);
        addPolicyOption(options, "禁止修改日期时间", ControlPolicyController.KEY_DATE_TIME_DISABLED);
        addPolicyOption(options, "禁止修改语言地区", ControlPolicyController.KEY_LOCALE_DISABLED);
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            addPolicyOption(options, "禁止修改屏幕超时", ControlPolicyController.KEY_SCREEN_TIMEOUT_DISABLED);
        }
        addPolicyOption(options, "禁止修改账户", ControlPolicyController.KEY_ACCOUNTS_DISABLED);
        addPolicyOption(options, "禁止分享位置", ControlPolicyController.KEY_SHARE_LOCATION_DISABLED);
        addPolicyOption(options, "禁止调节音量", ControlPolicyController.KEY_ADJUST_VOLUME_DISABLED);
        addPolicyOption(options, "禁止 USB 调试和开发者调试功能", ControlPolicyController.KEY_DEBUGGING_DISABLED);
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            addPolicyOption(options, "禁止解除麦克风静音", ControlPolicyController.KEY_UNMUTE_MICROPHONE_DISABLED);
        }
        addSectionLabel(content, "系统限制");
        LinearLayout systemOptions = panel();
        addPolicyOption(systemOptions, "禁止安装应用", ControlPolicyController.KEY_INSTALL_APPS_DISABLED);
        addPolicyOption(systemOptions, "禁止卸载应用", ControlPolicyController.KEY_UNINSTALL_APPS_DISABLED);
        addPolicyOption(systemOptions, "禁止应用管理", ControlPolicyController.KEY_APPS_CONTROL_DISABLED);
        addPolicyOption(systemOptions, "禁止安全模式启动", ControlPolicyController.KEY_SAFE_BOOT_DISABLED);
        addPolicyOption(systemOptions, "禁止恢复出厂设置", ControlPolicyController.KEY_FACTORY_RESET_DISABLED);
        addPolicyOption(systemOptions, "禁止添加用户", ControlPolicyController.KEY_ADD_USER_DISABLED);
        addPolicyOption(systemOptions, "禁止修改凭据", ControlPolicyController.KEY_CREDENTIALS_DISABLED);
        addPolicyOption(systemOptions, "禁止配置 VPN", ControlPolicyController.KEY_VPN_DISABLED);
        addPolicyOption(systemOptions, "禁止配置网络共享", ControlPolicyController.KEY_TETHERING_DISABLED);
        addPolicyOption(systemOptions, "禁止修改蓝牙", ControlPolicyController.KEY_BLUETOOTH_DISABLED);
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            addPolicyOption(systemOptions, "禁止修改移动网络", ControlPolicyController.KEY_MOBILE_NETWORKS_DISABLED);
        }
        addPolicyOption(systemOptions, "禁止修改定位设置", ControlPolicyController.KEY_LOCATION_DISABLED);
        addPolicyOption(systemOptions, "禁止拨打电话", ControlPolicyController.KEY_OUTGOING_CALLS_DISABLED);
        addPolicyOption(systemOptions, "禁止短信", ControlPolicyController.KEY_SMS_DISABLED);
        addPolicyOption(systemOptions, "禁止挂载外部存储", ControlPolicyController.KEY_MOUNT_MEDIA_DISABLED);
        if (android.os.Build.VERSION.SDK_INT >= 24) {
            addPolicyOption(systemOptions, "禁止数据漫游", ControlPolicyController.KEY_DATA_ROAMING_DISABLED);
            addPolicyOption(systemOptions, "禁止网络重置", ControlPolicyController.KEY_NETWORK_RESET_DISABLED);
        }
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            addPolicyOption(systemOptions, "禁止修改默认应用", ControlPolicyController.KEY_DEFAULT_APPS_DISABLED);
        }
        content.addView(systemOptions);
        content.addView(options);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(content);
        setContentView(scroll);
    }

    private void addPolicyOption(LinearLayout root, String label, String key) {
        Button button = button(label + "    " + (controller.getOptionalRestriction(key) ? "已开启" : "已关闭"));
        button.setOnClickListener(v -> {
            final boolean enabled = !controller.getOptionalRestriction(key);
            final String value = password.getText().toString();
            runTask(() -> controller.setOptionalRestriction(key, value, enabled), ok -> {
                if (ok) button.setText(label + "    " + (enabled ? "已开启" : "已关闭"));
                toast(ok ? "策略已更新" : "更新失败");
            });
        });
        root.addView(button);
    }

    private void handlePendingDebugAction() {
        if (getIntent().getBooleanExtra("open_settings", false) && controller.isDebugMode()) {
            startActivity(controller.settingsIntent("all"));
        }
    }

    private void leaveKioskWhenDebugging() {
        if (!controller.isDebugMode()) return;
        try { stopLockTask(); } catch (IllegalStateException ignored) { }
    }

    private void refreshStatus() {
        if (status != null) status.setText(controller.statusText());
    }

    private <T> void runTask(Task<T> task, Result<T> result) {
        if (busy) return;
        busy = true;
        worker.execute(() -> {
            T value;
            try { value = task.run(); }
            catch (RuntimeException e) { value = null; }
            final T finalValue = value;
            main.post(() -> { busy = false; result.accept(finalValue); });
        });
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
        button.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
        button.setPadding(dp(16), 0, dp(16), 0);
        button.setMinHeight(dp(46));
        button.setStateListAnimator(null);
        button.setBackground(rounded(Color.rgb(29, 38, 50), 6, Color.rgb(49, 63, 80), 1));
        return button;
    }

    private Button primaryButton(String text) {
        Button button = button(text);
        button.setTextColor(Color.rgb(6, 19, 22));
        button.setGravity(Gravity.CENTER);
        button.setBackground(rounded(Color.rgb(92, 207, 192), 6, Color.rgb(92, 207, 192), 1));
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
        root.addView(message);
    }

    private LinearLayout panel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(14), dp(12), dp(14), dp(12));
        panel.setBackground(rounded(Color.rgb(22, 29, 38), 8, Color.rgb(42, 54, 68), 1));
        return panel;
    }

    private GradientDrawable inputBackground() {
        return rounded(Color.rgb(12, 17, 24), 6, Color.rgb(57, 73, 91), 1);
    }

    private GradientDrawable rounded(int fill, int radiusDp, int stroke, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radiusDp));
        drawable.setStroke(dp(strokeWidth), stroke);
        return drawable;
    }

    private LinearLayout.LayoutParams fullWidth() { return new LinearLayout.LayoutParams(-1, -2); }

    private LinearLayout.LayoutParams marginTop(int valueDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(46));
        params.topMargin = dp(valueDp);
        return params;
    }

    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }

    private void toast(String text) { Toast.makeText(this, text, Toast.LENGTH_SHORT).show(); }
}
