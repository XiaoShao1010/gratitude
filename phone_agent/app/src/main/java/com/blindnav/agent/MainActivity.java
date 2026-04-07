package com.blindnav.agent;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.blindnav.agent.navi.SocketServerService;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final String TAG_HOME = "home";
    private static final String TAG_MAP = "map";
    private static final String TAG_MY = "my";
    private static final int PERM_REQUEST_BT_CODE = 1001;
    private static final int PERM_REQUEST_OTHER_CODE = 1002;

    private Fragment homeFragment;
    private Fragment mapFragment;
    private Fragment myFragment;
    private TextView textAppTitle;
    private TextView textAppSubtitle;
    private TextView textShellState;
    private TextView textShellLocation;
    private TextView textShellLogin;
    private final BroadcastReceiver bluetoothStatusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, android.content.Intent intent) {
            if (intent == null || !SocketServerService.ACTION_BT_STATUS_CHANGED.equals(intent.getAction())) {
                return;
            }
            String state = intent.getStringExtra(SocketServerService.EXTRA_BT_STATE);
            String detail = intent.getStringExtra(SocketServerService.EXTRA_BT_DETAIL);
            applyBluetoothStatus(state, detail);
        }
    };
    private boolean bluetoothReceiverRegistered;
    private boolean startedService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        textAppTitle = findViewById(R.id.text_app_title);
        textAppSubtitle = findViewById(R.id.text_app_subtitle);
        textShellState = findViewById(R.id.text_shell_state);
        textShellLocation = findViewById(R.id.text_shell_location);
        textShellLogin = findViewById(R.id.text_shell_login);

        textAppTitle.setText(R.string.app_header_title);
        textAppSubtitle.setText(R.string.app_header_subtitle);
        applyBluetoothStatus(
            SocketServerService.getStoredBluetoothState(this),
            SocketServerService.getStoredBluetoothDetail(this));

        FragmentManager fragmentManager = getSupportFragmentManager();
        if (savedInstanceState == null) {
            homeFragment = new HomeFragment();
            mapFragment = new MapFragment();
            myFragment = new MyFragment();

            fragmentManager.beginTransaction()
                    .add(R.id.fragment_container, homeFragment, TAG_HOME)
                    .add(R.id.fragment_container, mapFragment, TAG_MAP)
                    .hide(mapFragment)
                    .add(R.id.fragment_container, myFragment, TAG_MY)
                    .hide(myFragment)
                    .commitNow();
        } else {
            homeFragment = fragmentManager.findFragmentByTag(TAG_HOME);
            mapFragment = fragmentManager.findFragmentByTag(TAG_MAP);
            myFragment = fragmentManager.findFragmentByTag(TAG_MY);
        }

        BottomNavigationView bottomNavigationView = findViewById(R.id.bottom_navigation);
        bottomNavigationView.setOnItemSelectedListener(item -> {
            if (item.getItemId() == R.id.nav_home) {
                updateShellHeader(R.string.shell_subtitle_home, R.string.shell_state_location_ready, R.string.shell_state_profile);
                switchFragment(homeFragment);
                return true;
            }
            if (item.getItemId() == R.id.nav_map) {
                updateShellHeader(R.string.shell_subtitle_map, R.string.shell_state_location_ready, R.string.shell_state_profile);
                switchFragment(mapFragment);
                return true;
            }
            if (item.getItemId() == R.id.nav_my) {
                updateShellHeader(R.string.shell_subtitle_my, R.string.shell_state_location_ready, R.string.shell_state_profile);
                switchFragment(myFragment);
                return true;
            }
            return false;
        });

        if (savedInstanceState == null) {
            bottomNavigationView.setSelectedItemId(R.id.nav_home);
        }

        // 11. 先申请蓝牙权限，再走高德隐私与其余运行时权限
        requestBluetoothPermissionsThenContinue();
    }

    @Override
    protected void onStart() {
        super.onStart();
        registerBluetoothStatusReceiver();
        applyBluetoothStatus(
                SocketServerService.getStoredBluetoothState(this),
                SocketServerService.getStoredBluetoothDetail(this));
    }

    @Override
    protected void onStop() {
        unregisterBluetoothStatusReceiver();
        super.onStop();
    }

    // 12. 先请求蓝牙权限 (Android 12+ 需要 BLUETOOTH_CONNECT/SCAN)
    private void requestBluetoothPermissionsThenContinue() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            AmapPrivacyHelper.ensureConsent(this, this::requestPermissionsAndStartService);
            return;
        }

        List<String> needed = collectMissingBluetoothPermissions();

        if (!needed.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                    needed.toArray(new String[0]), PERM_REQUEST_BT_CODE);
        } else {
            AmapPrivacyHelper.ensureConsent(this, this::requestPermissionsAndStartService);
        }
    }

    private List<String> collectMissingBluetoothPermissions() {
        List<String> needed = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.BLUETOOTH_CONNECT);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.BLUETOOTH_SCAN);
        }
        return needed;
    }

    // 13. 请求其余运行时权限 (定位 / 通知)，并在完成后启动服务
    private void requestPermissionsAndStartService() {
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
                    needed.toArray(new String[0]), PERM_REQUEST_OTHER_CODE);
        } else {
            startBluetoothService();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERM_REQUEST_BT_CODE) {
            if (collectMissingBluetoothPermissions().isEmpty()) {
                AmapPrivacyHelper.ensureConsent(this, this::requestPermissionsAndStartService);
            }
        } else if (requestCode == PERM_REQUEST_OTHER_CODE) {
            // 14. 无论用户是否全部授权，都尝试启动服务（Service 内部会做容错）
            startBluetoothService();
        }
    }

    // 15. 启动蓝牙前台服务
    private void startBluetoothService() {
        if (startedService) {
            return;
        }
        startedService = true;
        Intent btService = new Intent(this, SocketServerService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(btService);
        } else {
            startService(btService);
        }
    }

    private void registerBluetoothStatusReceiver() {
        if (bluetoothReceiverRegistered) {
            return;
        }
        IntentFilter filter = new IntentFilter(SocketServerService.ACTION_BT_STATUS_CHANGED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(bluetoothStatusReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(bluetoothStatusReceiver, filter);
        }
        bluetoothReceiverRegistered = true;
    }

    private void unregisterBluetoothStatusReceiver() {
        if (!bluetoothReceiverRegistered) {
            return;
        }
        unregisterReceiver(bluetoothStatusReceiver);
        bluetoothReceiverRegistered = false;
    }

    private void applyBluetoothStatus(String state, String detail) {
        int resId;
        if (SocketServerService.BT_STATE_CONNECTED.equals(state)) {
            resId = R.string.bt_status_connected;
        } else if (SocketServerService.BT_STATE_CONNECTING.equals(state)) {
            resId = R.string.bt_status_connecting;
        } else {
            resId = R.string.bt_status_disconnected;
        }
        textShellState.setText(resId);
    }

    private void switchFragment(Fragment targetFragment) {
        if (homeFragment == null || mapFragment == null || myFragment == null || targetFragment == null) {
            return;
        }

        FragmentManager fragmentManager = getSupportFragmentManager();
        fragmentManager.beginTransaction()
                .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                .hide(homeFragment)
                .hide(mapFragment)
                .hide(myFragment)
                .show(targetFragment)
                .commit();
    }

    private void updateShellHeader(int subtitleResId, int locationResId, int loginResId) {
        textAppTitle.setText(R.string.app_header_title);
        textAppSubtitle.setText(subtitleResId);
        textShellLocation.setText(locationResId);
        textShellLogin.setText(loginResId);
    }
}
