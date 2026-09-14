package com.o0ai.control.ui;

import android.Manifest;
import android.app.Activity;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.o0ai.control.core.ControlPolicyController;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PermissionActivity extends Activity {
    private static final List<String> MANAGEABLE = Arrays.asList(
            Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS,
            Manifest.permission.READ_PHONE_STATE, Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_SMS, Manifest.permission.SEND_SMS,
            Manifest.permission.BODY_SENSORS);

    private final List<PackageInfo> packages = new ArrayList<>();
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private ControlPolicyController controller;
    private Spinner applications;
    private EditText password;
    private LinearLayout permissions;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        controller = new ControlPolicyController(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(24, 24, 24, 24);

        TextView title = new TextView(this);
        title.setText("应用权限管理");
        title.setTextSize(22);
        content.addView(title);

        applications = new Spinner(this);
        content.addView(applications);
        password = new EditText(this);
        password.setHint("管理员密码");
        password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        content.addView(password);

        ScrollView scroll = new ScrollView(this);
        permissions = new LinearLayout(this);
        permissions.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(permissions);
        content.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(content);
        addMessage("正在加载应用列表...");

        applications.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < packages.size()) rebuildPermissions(packages.get(position));
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { permissions.removeAllViews(); }
        });

        worker.execute(() -> {
            List<PackageInfo> loaded = loadApplications();
            main.post(() -> {
                packages.clear();
                packages.addAll(loaded);
                List<String> labels = new ArrayList<>();
                for (PackageInfo info : packages) labels.add(appLabel(info) + "\n" + info.packageName);
                applications.setAdapter(new ArrayAdapter<>(this,
                        android.R.layout.simple_spinner_dropdown_item, labels));
                if (packages.isEmpty()) {
                    permissions.removeAllViews();
                    addMessage("没有找到声明运行时权限的应用");
                }
            });
        });
    }

    @Override
    protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }

    private List<PackageInfo> loadApplications() {
        List<PackageInfo> result = new ArrayList<>();
        for (PackageInfo info : getPackageManager().getInstalledPackages(PackageManager.GET_PERMISSIONS)) {
            if (info.applicationInfo != null && info.requestedPermissions != null
                    && info.requestedPermissions.length > 0) result.add(info);
        }
        return result;
    }

    private CharSequence appLabel(PackageInfo info) {
        ApplicationInfo app = info.applicationInfo;
        return app == null ? info.packageName : getPackageManager().getApplicationLabel(app);
    }

    private void rebuildPermissions(PackageInfo selected) {
        permissions.removeAllViews();
        Set<String> requested = new HashSet<>(Arrays.asList(selected.requestedPermissions));
        for (String permission : MANAGEABLE) {
            if (requested.contains(permission)) addPermissionRow(selected.packageName, permission);
        }
        if (permissions.getChildCount() == 0) addMessage("该应用没有可管理的运行时权限");
    }

    private void addPermissionRow(String packageName, String permission) {
        CheckBox allowed = new CheckBox(this);
        allowed.setText(permissionLabel(permission));
        allowed.setTextSize(17);
        allowed.setEnabled(false);
        worker.execute(() -> {
            boolean checked = controller.isRuntimePermissionAllowed(packageName, permission);
            main.post(() -> {
                if (isFinishing()) return;
                allowed.setChecked(checked);
                allowed.setEnabled(true);
            });
        });
        allowed.setOnClickListener(v -> change(packageName, permission, allowed.isChecked(), allowed));
        permissions.addView(allowed, new LinearLayout.LayoutParams(-1, -2));
    }

    private void change(String packageName, String permission, boolean allowed, CheckBox control) {
        final String value = password.getText().toString();
        control.setEnabled(false);
        worker.execute(() -> {
            boolean updated = controller.verifyPassword(value)
                    && controller.setRuntimePermission(packageName, permission, allowed);
            main.post(() -> {
                control.setEnabled(true);
                if (!updated) control.setChecked(!allowed);
                toast(updated ? (allowed ? "权限已允许" : "权限已禁止") : "密码错误或更新失败");
            });
        });
    }

    private void addMessage(String text) {
        TextView message = new TextView(this);
        message.setText(text);
        message.setTextColor(Color.DKGRAY);
        permissions.addView(message);
    }

    private String permissionLabel(String permission) {
        if (Manifest.permission.CAMERA.equals(permission)) return "相机";
        if (Manifest.permission.RECORD_AUDIO.equals(permission)) return "麦克风";
        if (permission.contains("LOCATION")) return "定位";
        if (permission.contains("STORAGE")) return "存储";
        if (permission.contains("CONTACTS")) return "通讯录";
        if (permission.contains("PHONE")) return "电话";
        if (permission.contains("SMS")) return "短信";
        if (permission.contains("SENSORS")) return "身体传感器";
        return permission;
    }

    private void toast(String text) { Toast.makeText(this, text, Toast.LENGTH_SHORT).show(); }
}
