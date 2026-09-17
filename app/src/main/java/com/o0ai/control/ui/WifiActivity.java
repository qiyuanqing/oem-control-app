package com.o0ai.control.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.text.InputType;
import android.graphics.Color;
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
    private static final int BG = 0xff000000, BAR = 0xff242424, CARD = 0xff1f1f1f;
    private static final int WHITE = 0xfff2f2f2, MUTED = 0xffa9a9a9, BLUE = 0xff1597f5;
    private WifiManager wifi;
    private TextView wlanState;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        wifi = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        buildUi();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(BG);
        LinearLayout bar = new LinearLayout(this); bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(28), 0, dp(12), 0); bar.setBackgroundColor(BAR);
        TextView title = label("网络", 28, BLUE); title.setGravity(Gravity.CENTER_VERTICAL);
        bar.addView(title, new LinearLayout.LayoutParams(0, dp(72), 1));
        TextView menu = label("⋮", 34, BLUE); menu.setGravity(Gravity.CENTER);
        menu.setOnClickListener(this::showMenu); bar.addView(menu, new LinearLayout.LayoutParams(dp(54), dp(72)));
        root.addView(bar);
        ScrollView scroll = new ScrollView(this);
        LinearLayout body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(6), dp(10), dp(6), dp(20));
        addWlanCard(body);
        addCard(body, "移动网络", "SIM 卡和移动数据", "▰", null, false);
        addCard(body, "流量使用情况", "已使用 0 B", "◔", null, false);
        addCard(body, "热点和网络共享", "关闭", "◉", null, false);
        addCard(body, "飞行模式", "", "✈", checked -> toast(checked ? "飞行模式已开启" : "飞行模式已关闭"), false);
        addCard(body, "长续航模式", "开启后，灭屏 10 分钟后断开 Wi-Fi 连接，亮屏后自动连接；关闭后，Wi-Fi 会保持长连接，但会增加功耗", "◷", checked -> toast(checked ? "长续航模式已开启" : "长续航模式已关闭"), true);
        scroll.addView(body); root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1)); setContentView(root);
    }

    private void addWlanCard(LinearLayout body) {
        LinearLayout card = card(), row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(label("◢", 30, WHITE), new LinearLayout.LayoutParams(dp(72), -1));
        LinearLayout words = new LinearLayout(this); words.setOrientation(LinearLayout.VERTICAL); words.setGravity(Gravity.CENTER_VERTICAL);
        words.addView(label("WLAN", 28, WHITE)); wlanState = label(currentNetwork(), 22, MUTED); words.addView(wlanState);
        row.addView(words, new LinearLayout.LayoutParams(0, -1, 1));
        Switch toggle = new Switch(this); toggle.setChecked(wifi != null && wifi.isWifiEnabled());
        toggle.setOnCheckedChangeListener((button, checked) -> { if (wifi != null) wifi.setWifiEnabled(checked); refreshNetwork(); });
        row.addView(toggle, new LinearLayout.LayoutParams(dp(74), -1)); card.addView(row);
        card.setOnClickListener(v -> showNetworks()); body.addView(card, cardParams(194));
    }

    private void addCard(LinearLayout body, String title, String subtitle, String icon, ToggleAction action, boolean tall) {
        LinearLayout card = card(), row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(label(icon, 29, WHITE), new LinearLayout.LayoutParams(dp(72), -1));
        LinearLayout words = new LinearLayout(this); words.setOrientation(LinearLayout.VERTICAL); words.setGravity(Gravity.CENTER_VERTICAL);
        words.addView(label(title, 27, WHITE)); if (!subtitle.isEmpty()) words.addView(label(subtitle, 21, MUTED));
        row.addView(words, new LinearLayout.LayoutParams(0, -1, 1));
        if (action != null) { Switch toggle = new Switch(this); toggle.setOnCheckedChangeListener((button, checked) -> action.changed(checked)); row.addView(toggle, new LinearLayout.LayoutParams(dp(74), -1)); }
        card.addView(row); body.addView(card, cardParams(tall ? 310 : 142));
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
        input.setSingleLine(true); input.setHint("密码");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        new AlertDialog.Builder(this).setTitle(network.SSID).setView(input)
                .setNegativeButton("取消", null)
                .setPositiveButton("连接", (dialog, which) -> connect(network, input.getText().toString())).show();
    }

    private void connect(ScanResult network, String password) {
        WifiConfiguration config = new WifiConfiguration(); config.SSID = quote(network.SSID);
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
        toast(ok ? "正在连接" : "连接失败"); refreshNetwork();
    }

    private void showMenu(View anchor) { PopupMenu p = new PopupMenu(this, anchor); p.getMenu().add("扫描网络"); p.getMenu().add("重置 WLAN、移动数据网络..."); p.setOnMenuItemClickListener(item -> { if (item.getTitle().toString().startsWith("扫描")) showNetworks(); else toast("网络设置已重置"); return true; }); p.show(); }
    private String currentNetwork() { if (wifi == null || !wifi.isWifiEnabled()) return "已关闭"; WifiInfo i = wifi.getConnectionInfo(); String s = i == null ? null : i.getSSID(); return s == null || "<unknown ssid>".equals(s) ? "未连接" : s.replace("\"", ""); }
    private void refreshNetwork() { if (wlanState != null) wlanState.setText(currentNetwork()); }
    private static String quote(String value) { return "\"" + value.replace("\"", "\\\"") + "\""; }
    private LinearLayout card() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); v.setPadding(dp(20), dp(10), dp(10), dp(10)); v.setBackground(round(CARD, 18)); return v; }
    private LinearLayout.LayoutParams cardParams(int h) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(h)); p.setMargins(0, dp(5), 0, dp(5)); return p; }
    private TextView label(String s, int size, int color) { TextView v = new TextView(this); v.setText(s); v.setTextSize(size); v.setTextColor(color); v.setGravity(Gravity.CENTER_VERTICAL); return v; }
    private android.graphics.drawable.GradientDrawable round(int c, int r) { android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable(); d.setColor(c); d.setCornerRadius(dp(r)); return d; }
    private int dp(int n) { return (int) (n * getResources().getDisplayMetrics().density + .5f); }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
    private interface ToggleAction { void changed(boolean checked); }
}
