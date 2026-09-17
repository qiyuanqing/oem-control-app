package com.o0ai.control.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.net.TrafficStats;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class WifiActivity extends Activity {
    private static final int BG = 0xff000000;
    private static final int BAR = 0xff242424;
    private static final int CARD = 0xff1f1f1f;
    private static final int WHITE = 0xfff2f2f2;
    private static final int MUTED = 0xffa9a9a9;
    private static final int BLUE = 0xff1597f5;
    private static final String PREFS = "network_page";
    private WifiManager wifi;
    private TextView wlanState;
    private TextView trafficState;
    private TextView mobileState;
    private Switch mobileSwitch;
    private Switch wifiSwitch;
    private BroadcastReceiver screenReceiver;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable disableWifi = () -> {
        if (getPreferences(0).getBoolean("battery_mode", false) && wifi != null) {
            wifi.setWifiEnabled(false);
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        wifi = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        buildUi();
        registerScreenReceiver();
    }

    @Override protected void onResume() {
        super.onResume();
        refreshNetworkState();
        refreshTraffic();
    }

    @Override protected void onDestroy() {
        if (screenReceiver != null) unregisterReceiver(screenReceiver);
        handler.removeCallbacks(disableWifi);
        super.onDestroy();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(28), 0, dp(12), 0);
        bar.setBackgroundColor(BAR);
        TextView title = label("网络", 28, BLUE);
        title.setGravity(Gravity.CENTER_VERTICAL);
        bar.addView(title, new LinearLayout.LayoutParams(0, dp(72), 1));
        TextView menu = label("⋮", 34, BLUE);
        menu.setGravity(Gravity.CENTER);
        menu.setOnClickListener(this::showMenu);
        bar.addView(menu, new LinearLayout.LayoutParams(dp(54), dp(72)));
        root.addView(bar);

        ScrollView scroll = new ScrollView(this);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(6), dp(10), dp(6), dp(20));
        addWlanCard(body);
        addMobileCard(body);
        addTrafficCard(body);
        addHotspotCard(body);
        addBatteryCard(body);
        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
    }

    private void addWlanCard(LinearLayout body) {
        LinearLayout card = card();
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(label("◢", 30, WHITE), new LinearLayout.LayoutParams(dp(72), -1));
        LinearLayout words = words("WLAN");
        wlanState = label(currentNetwork(), 22, MUTED);
        words.addView(wlanState);
        row.addView(words, new LinearLayout.LayoutParams(0, -1, 1));
        wifiSwitch = new Switch(this);
        wifiSwitch.setChecked(wifi != null && wifi.isWifiEnabled());
        wifiSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (wifi != null) wifi.setWifiEnabled(checked);
            refreshNetworkState();
        });
        row.addView(wifiSwitch, new LinearLayout.LayoutParams(dp(74), -1));
        card.addView(row);
        card.setOnClickListener(v -> showNetworks());
        body.addView(card, cardParams(194));
    }

    private void addMobileCard(LinearLayout body) {
        LinearLayout card = card();
        LinearLayout row = row("▰");
        LinearLayout words = words("移动网络");
        mobileState = label(mobileSummary(), 21, MUTED);
        words.addView(mobileState);
        row.addView(words, new LinearLayout.LayoutParams(0, -1, 1));
        mobileSwitch = new Switch(this);
        mobileSwitch.setChecked(isMobileDataEnabled());
        mobileSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (!setMobileDataEnabled(checked)) {
                button.setChecked(isMobileDataEnabled());
                toast("系统未授予移动数据控制权限");
            } else {
                mobileState.setText(mobileSummary());
            }
        });
        row.addView(mobileSwitch, new LinearLayout.LayoutParams(dp(74), -1));
        card.addView(row);
        card.setOnClickListener(v -> showMobileInfo());
        body.addView(card, cardParams(142));
    }

    private void addTrafficCard(LinearLayout body) {
        LinearLayout card = card();
        LinearLayout row = row("◔");
        LinearLayout words = words("流量使用情况");
        trafficState = label("已使用 0 B", 21, MUTED);
        words.addView(trafficState);
        row.addView(words, new LinearLayout.LayoutParams(0, -1, 1));
        card.addView(row);
        card.setOnClickListener(v -> showTrafficInfo());
        body.addView(card, cardParams(142));
    }

    private void addHotspotCard(LinearLayout body) {
        LinearLayout card = card();
        LinearLayout row = row("◉");
        LinearLayout words = words("热点和网络共享");
        TextView state = label("关闭", 21, MUTED);
        words.addView(state);
        row.addView(words, new LinearLayout.LayoutParams(0, -1, 1));
        Switch toggle = new Switch(this);
        toggle.setOnCheckedChangeListener((button, checked) -> {
            if (!setHotspotEnabled(checked)) {
                button.setChecked(false);
                toast("当前系统不允许应用控制热点");
            } else state.setText(checked ? "开启" : "关闭");
        });
        row.addView(toggle, new LinearLayout.LayoutParams(dp(74), -1));
        card.addView(row);
        body.addView(card, cardParams(142));
    }

    private void addBatteryCard(LinearLayout body) {
        LinearLayout card = card();
        LinearLayout row = row("◷");
        LinearLayout words = words("长续航模式");
        words.addView(label("开启后，灭屏 10 分钟后断开 Wi-Fi 连接，亮屏后自动连接；关闭后，Wi-Fi 会保持长连接，但会增加功耗", 21, MUTED));
        row.addView(words, new LinearLayout.LayoutParams(0, -1, 1));
        Switch toggle = new Switch(this);
        toggle.setChecked(getPreferences(0).getBoolean("battery_mode", false));
        toggle.setOnCheckedChangeListener((button, checked) -> getPreferences(0).edit().putBoolean("battery_mode", checked).apply());
        row.addView(toggle, new LinearLayout.LayoutParams(dp(74), -1));
        card.addView(row);
        body.addView(card, cardParams(310));
    }

    private void showNetworks() {
        if (wifi == null || !wifi.isWifiEnabled()) { toast("请先开启 WLAN"); return; }
        wifi.startScan();
        List<ScanResult> results = wifi.getScanResults();
        if (results == null) results = Collections.emptyList();
        Collections.sort(results, Comparator.comparingInt((ScanResult r) -> r.level).reversed());
        final List<ScanResult> networks = new ArrayList<>();
        for (ScanResult result : results) {
            if (result.SSID == null || result.SSID.isEmpty()) continue;
            boolean duplicate = false;
            for (ScanResult old : networks) if (old.SSID.equals(result.SSID)) duplicate = true;
            if (!duplicate) networks.add(result);
        }
        if (networks.isEmpty()) { toast("未发现可用网络"); return; }
        String[] names = new String[networks.size()];
        for (int i = 0; i < networks.size(); i++) names[i] = networks.get(i).SSID;
        new AlertDialog.Builder(this).setTitle("WLAN").setItems(names, (dialog, which) -> showPassword(networks.get(which))).setNegativeButton("取消", null).show();
    }

    private void showPassword(ScanResult network) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("密码");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        new AlertDialog.Builder(this).setTitle(network.SSID).setView(input)
                .setNegativeButton("取消", null)
                .setPositiveButton("连接", (dialog, which) -> connect(network, input.getText().toString())).show();
    }

    private void connect(ScanResult network, String password) {
        WifiConfiguration config = new WifiConfiguration();
        config.SSID = quote(network.SSID);
        String capabilities = network.capabilities == null ? "" : network.capabilities;
        if (capabilities.contains("WEP")) {
            config.wepKeys[0] = quote(password); config.wepTxKeyIndex = 0;
            config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
            config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP40);
        } else if (capabilities.contains("WPA")) config.preSharedKey = quote(password);
        else config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
        int id = wifi.addNetwork(config);
        if (id < 0) { toast("无法添加网络"); return; }
        boolean ok = wifi.disconnect() && wifi.enableNetwork(id, true) && wifi.reconnect();
        toast(ok ? "正在连接" : "连接失败");
        refreshNetworkState();
    }

    private void showMobileInfo() { new AlertDialog.Builder(this).setTitle("移动网络").setMessage("SIM 卡和移动数据由系统电话服务管理。请在系统已授予的运营商配置下使用。\n\n当前页面提供状态查看，暂不强制修改移动数据开关。").setPositiveButton("确定", null).show(); }
    private void showTrafficInfo() { refreshTraffic(); new AlertDialog.Builder(this).setTitle("流量使用情况").setMessage(trafficState == null ? "暂无数据" : trafficState.getText()).setPositiveButton("确定", null).show(); }

    private void showMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add("扫描网络");
        menu.getMenu().add("重置 WLAN、移动数据网络...");
        menu.setOnMenuItemClickListener(item -> { if (item.getTitle().toString().startsWith("扫描")) showNetworks(); else toast("网络重置需要系统确认"); return true; });
        menu.show();
    }

    private boolean setHotspotEnabled(boolean enabled) {
        try {
            java.lang.reflect.Method method = wifi.getClass().getMethod("setWifiApEnabled", WifiConfiguration.class, boolean.class);
            method.invoke(wifi, null, enabled);
            return true;
        } catch (Throwable ignored) { return false; }
    }

    private void refreshNetworkState() { if (wlanState != null) wlanState.setText(currentNetwork()); if (wifiSwitch != null && wifi != null) wifiSwitch.setChecked(wifi.isWifiEnabled()); if (mobileState != null) mobileState.setText(mobileSummary()); if (mobileSwitch != null) mobileSwitch.setChecked(isMobileDataEnabled()); }
    private void refreshTraffic() { if (trafficState != null) trafficState.setText("已使用 " + formatBytes(TrafficStats.getTotalRxBytes() + TrafficStats.getTotalTxBytes())); }
    private String currentNetwork() { if (wifi == null || !wifi.isWifiEnabled()) return "已关闭"; WifiInfo info = wifi.getConnectionInfo(); String ssid = info == null ? null : info.getSSID(); return ssid == null || "<unknown ssid>".equals(ssid) ? "未连接" : ssid.replace("\"", ""); }
    private String mobileSummary() { return hasSim() ? (isMobileDataEnabled() ? "移动数据已开启" : "移动数据已关闭") : "未检测到 SIM 卡"; }
    private boolean hasSim() { try { TelephonyManager t = (TelephonyManager) getSystemService(TELEPHONY_SERVICE); return t != null && t.getSimState() != TelephonyManager.SIM_STATE_ABSENT; } catch (RuntimeException e) { return false; } }
    private boolean isMobileDataEnabled() {
        try {
            TelephonyManager t = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
            java.lang.reflect.Method m = TelephonyManager.class.getDeclaredMethod("getDataEnabled");
            m.setAccessible(true);
            Object value = m.invoke(t);
            return value instanceof Boolean && (Boolean) value;
        } catch (Throwable e) { return false; }
    }
    private boolean setMobileDataEnabled(boolean enabled) {
        try {
            TelephonyManager t = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
            try {
                java.lang.reflect.Method m = TelephonyManager.class.getDeclaredMethod("setDataEnabled", boolean.class);
                m.setAccessible(true); m.invoke(t, enabled); return true;
            } catch (NoSuchMethodException ignored) {
                java.lang.reflect.Method m = TelephonyManager.class.getDeclaredMethod("setDataEnabled", int.class, boolean.class);
                m.setAccessible(true); m.invoke(t, SubscriptionManager.getDefaultDataSubscriptionId(), enabled); return true;
            }
        } catch (Throwable e) { return false; }
    }
    private String formatBytes(long bytes) { if (bytes < 1024) return bytes + " B"; if (bytes < 1048576) return (bytes / 1024) + " KB"; if (bytes < 1073741824) return (bytes / 1048576) + " MB"; return (bytes / 1073741824) + " GB"; }
    private void registerScreenReceiver() {
        screenReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                if (!getPreferences(0).getBoolean("battery_mode", false) || wifi == null) return;
                if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                    handler.removeCallbacks(disableWifi);
                    handler.postDelayed(disableWifi, 10L * 60L * 1000L);
                } else if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
                    handler.removeCallbacks(disableWifi);
                    wifi.setWifiEnabled(true);
                }
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(screenReceiver, filter);
    }
    private LinearLayout row(String icon) { LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL); row.addView(label(icon, 29, WHITE), new LinearLayout.LayoutParams(dp(72), -1)); return row; }
    private LinearLayout words(String title) { LinearLayout words = new LinearLayout(this); words.setOrientation(LinearLayout.VERTICAL); words.setGravity(Gravity.CENTER_VERTICAL); words.addView(label(title, 27, WHITE)); return words; }
    private LinearLayout card() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); v.setPadding(dp(20), dp(10), dp(10), dp(10)); v.setBackground(round(CARD, 18)); return v; }
    private LinearLayout.LayoutParams cardParams(int height) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(height)); p.setMargins(0, dp(5), 0, dp(5)); return p; }
    private TextView label(String text, int size, int color) { TextView v = new TextView(this); v.setText(text); v.setTextSize(size); v.setTextColor(color); v.setGravity(Gravity.CENTER_VERTICAL); return v; }
    private android.graphics.drawable.GradientDrawable round(int color, int radius) { android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d; }
    private static String quote(String value) { return "\"" + value.replace("\"", "\\\"") + "\""; }
    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + .5f); }
    private void toast(String value) { Toast.makeText(this, value, Toast.LENGTH_SHORT).show(); }
}
