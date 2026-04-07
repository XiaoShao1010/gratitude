package com.blindnav.agent;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.blindnav.agent.navi.GaodeNaviManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 导航功能独立测试 Activity
 * 直接调用 GaodeNaviManager 测试：地理编码 → 步行算路 → 导航事件回调
 * 无需蓝牙 / 树莓派
 */
public class NaviTestActivity extends AppCompatActivity {
    private static final int PERM_REQUEST_CODE = 2001;

    private GaodeNaviManager naviManager;
    private TextInputEditText editDestination;
    private TextInputEditText editCity;
    private TextView textStatus;
    private TextView textLog;
    private StringBuilder logBuilder = new StringBuilder();
    private SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
    private boolean uiInitialized;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_navi_test);

        AmapPrivacyHelper.ensureConsent(this, this::initUi);
    }

    private void initUi() {
        if (uiInitialized) {
            return;
        }
        uiInitialized = true;

        // 1. 绑定控件
        editDestination = findViewById(R.id.edit_destination);
        editCity = findViewById(R.id.edit_city);
        textStatus = findViewById(R.id.text_status);
        textLog = findViewById(R.id.text_log);
        MaterialButton btnStartNav = findViewById(R.id.btn_start_nav);
        MaterialButton btnQueryLocation = findViewById(R.id.btn_query_location);
        MaterialButton btnClearLog = findViewById(R.id.btn_clear_log);

        // 2. 初始化导航管理器
        naviManager = new GaodeNaviManager(this);

        // 3. 设置导航事件回调 — 直接显示在日志中
        naviManager.setNaviEventCallback(new GaodeNaviManager.NaviEventCallback() {
            @Override
            public void onNaviEvent(String status, String event, int distance) {
                runOnUiThread(() -> {
                    setStatus("导航中: " + event + " " + distance + "m");
                    appendLog("📡 NAV_EVENT | status=" + status
                            + " event=" + event + " distance=" + distance + "m");
                });
            }

            @Override
            public void onArrived() {
                runOnUiThread(() -> {
                    setStatus("✅ 已到达目的地");
                    appendLog("🏁 ARRIVED | 导航结束");
                });
            }
        });

        // 4. 开始导航按钮
        btnStartNav.setOnClickListener(v -> {
            String dest = editDestination.getText() != null
                    ? editDestination.getText().toString().trim() : "";
            String city = editCity.getText() != null
                    ? editCity.getText().toString().trim() : "";

            if (dest.isEmpty()) {
                setStatus("⚠️ 请输入目的地");
                return;
            }

            setStatus("🔍 正在解析地址: " + dest);
            appendLog("→ START_NAV | target=" + dest + " city=" + city);
            naviManager.startNavigationTo(dest, city);
        });

        // 5. 查询当前位置按钮
        btnQueryLocation.setOnClickListener(v -> {
            setStatus("📡 正在定位...");
            appendLog("→ GET_LOCATION | 查询中...");
            naviManager.queryCurrentLocation(new GaodeNaviManager.LocationCallback() {
                @Override
                public void onLocationSuccess(GaodeNaviManager.LocationSnapshot snap) {
                    runOnUiThread(() -> {
                        setStatus("📍 定位成功");
                        appendLog("✅ LOCATION | lat=" + snap.getLatitude()
                                + " lon=" + snap.getLongitude()
                                + "\n   address=" + snap.getAddress()
                                + "\n   city=" + snap.getCity()
                                + "\n   provider=" + snap.getProvider());
                    });
                }

                @Override
                public void onLocationFailure(String reason) {
                    runOnUiThread(() -> {
                        setStatus("❌ 定位失败");
                        appendLog("❌ LOCATION_ERROR | " + reason);
                    });
                }
            });
        });

        // 6. 清空日志
        btnClearLog.setOnClickListener(v -> {
            logBuilder.setLength(0);
            textLog.setText("暂无日志\n");
            setStatus("等待操作...");
        });

        // 7. 请求定位权限
        requestPermissions();
    }

    private void requestPermissions() {
        List<String> needed = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.POST_NOTIFICATIONS);
            }
        }
        if (!needed.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                    needed.toArray(new String[0]), PERM_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERM_REQUEST_CODE) {
            appendLog("📋 权限请求结果已返回");
        }
    }

    private void setStatus(String text) {
        textStatus.setText(text);
    }

    private void appendLog(String message) {
        String time = timeFmt.format(new Date());
        logBuilder.insert(0, "[" + time + "] " + message + "\n");
        textLog.setText(logBuilder.toString());
    }

    @Override
    protected void onDestroy() {
        if (naviManager != null) {
            naviManager.destroy();
        }
        super.onDestroy();
    }
}
