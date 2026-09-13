package com.o0ai.control.ui;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.TextView;

import com.o0ai.control.core.ControlPolicyController;

public class KioskActivity extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        TextView view = new TextView(this);
        view.setText("设备已受管控");
        view.setTextSize(22);
        view.setGravity(Gravity.CENTER);
        setContentView(view);
        enterKiosk();
    }

    @Override
    protected void onResume() {
        super.onResume();
        enterKiosk();
    }

    private void enterKiosk() {
        ControlPolicyController controller = new ControlPolicyController(this);
        if (controller.isDeviceOwner() && !controller.isDebugMode()) {
            startLockTask();
        }
    }
}
