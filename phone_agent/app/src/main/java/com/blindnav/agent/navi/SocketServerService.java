package com.blindnav.agent.navi;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.blindnav.agent.MainActivity;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class SocketServerService extends Service {
    public static final String ACTION_BT_STATUS_CHANGED = "com.blindnav.agent.action.BT_STATUS_CHANGED";
    public static final String EXTRA_BT_STATE = "extra_bt_state";
    public static final String EXTRA_BT_DETAIL = "extra_bt_detail";
    public static final String BT_STATE_CONNECTING = "CONNECTING";
    public static final String BT_STATE_CONNECTED = "CONNECTED";
    public static final String BT_STATE_DISCONNECTED = "DISCONNECTED";

    private static final String PREFS_BT_STATUS = "bt_status_prefs";
    private static final String PREF_KEY_STATE = "state";
    private static final String PREF_KEY_DETAIL = "detail";

    private static final String TAG = "BtClient";
    private static final String SERVICE_NAME = "BlindNavPi";
    private static final UUID BT_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final String CHANNEL_ID = "BlindNavChannel";
    private static final long RETRY_DELAY_MS = 3000L;

    private BluetoothSocket clientSocket;
    private BufferedReader inputReader;
    private PrintWriter out;
    private volatile boolean running = false;
    private Thread connectThread;
    private GaodeNaviManager naviManager;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForegroundCompat(buildNotification("蓝牙连接启动中..."));
        publishBluetoothState(BT_STATE_CONNECTING, "正在等待手机连接");
        naviManager = new GaodeNaviManager(this);

        // 10. 将导航事件通过蓝牙回传给树莓派
        naviManager.setNaviEventCallback(new GaodeNaviManager.NaviEventCallback() {
            @Override
            public void onNaviEvent(String status, String event, int distance) {
                JSONObject resp = new JSONObject();
                try {
                    resp.put("status", status);
                    resp.put("event", event);
                    resp.put("distance", distance);
                    sendResponse(resp);
                    updateNotification(event + " " + distance + "m");
                } catch (Exception e) {
                    Log.e(TAG, "Build navi event error", e);
                }
            }

            @Override
            public void onArrived() {
                sendArrived();
                updateNotification("已到达目的地");
            }
        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        running = true;
        if (connectThread == null || !connectThread.isAlive()) {
            connectThread = new Thread(this::connectLoop, "blindnav-bt-client");
            connectThread.start();
        }
        return START_STICKY;
    }

    @SuppressLint("MissingPermission")
    private void connectLoop() {
        while (running) {
            BluetoothSocket activeSocket = null;
            try {
                publishBluetoothState(BT_STATE_CONNECTING, "正在检测蓝牙环境");
                BluetoothAdapter adapter = getBluetoothAdapter();
                if (adapter == null) {
                    publishBluetoothState(BT_STATE_DISCONNECTED, "蓝牙不可用");
                    updateNotification("蓝牙不可用");
                    sleepRetry();
                    continue;
                }
                if (!adapter.isEnabled()) {
                    publishBluetoothState(BT_STATE_DISCONNECTED, "请先开启手机蓝牙");
                    updateNotification("请先开启手机蓝牙");
                    sleepRetry();
                    continue;
                }

                List<BluetoothDevice> candidates = findCandidateDevices(adapter);
                if (candidates.isEmpty()) {
                    publishBluetoothState(BT_STATE_DISCONNECTED, "请先与树莓派完成蓝牙配对");
                    updateNotification("请先与树莓派完成蓝牙配对");
                    sleepRetry();
                    continue;
                }

                boolean connected = false;
                for (BluetoothDevice device : candidates) {
                    String deviceName = safeDeviceName(device);
                    try {
                        activeSocket = connectToDevice(adapter, device);
                        publishBluetoothState(BT_STATE_CONNECTED, deviceName);
                        updateNotification("已连接树莓派: " + deviceName);
                        connected = true;
                        break;
                    } catch (IOException e) {
                        Log.w(TAG, "Connect failed: " + deviceName, e);
                        closeSocket(activeSocket);
                        activeSocket = null;
                    }
                }

                if (!connected || activeSocket == null) {
                    publishBluetoothState(BT_STATE_DISCONNECTED, "连接树莓派失败，3秒后重试");
                    updateNotification("连接树莓派失败，3秒后重试");
                    sleepRetry();
                    continue;
                }

                synchronized (this) {
                    closeClientLocked();
                    clientSocket = activeSocket;
                    inputReader = new BufferedReader(
                            new InputStreamReader(activeSocket.getInputStream()));
                    out = new PrintWriter(
                            new OutputStreamWriter(activeSocket.getOutputStream()), true);
                }

                readRequests();
            } catch (Exception e) {
                if (running) {
                    Log.e(TAG, "Connection loop error", e);
                    publishBluetoothState(BT_STATE_DISCONNECTED, "蓝牙连接异常");
                    updateNotification("蓝牙连接异常，3秒后重试");
                }
            } finally {
                synchronized (this) {
                    closeClientLocked();
                }
                closeSocket(activeSocket);
                if (running) {
                    sleepRetry();
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private BluetoothAdapter getBluetoothAdapter() {
        BluetoothManager bluetoothManager = getSystemService(BluetoothManager.class);
        return bluetoothManager == null ? null : bluetoothManager.getAdapter();
    }

    @SuppressLint("MissingPermission")
    private List<BluetoothDevice> findCandidateDevices(BluetoothAdapter adapter) {
        List<BluetoothDevice> prioritized = new ArrayList<>();
        List<BluetoothDevice> fallback = new ArrayList<>();
        Set<BluetoothDevice> bondedDevices = adapter.getBondedDevices();
        for (BluetoothDevice device : bondedDevices) {
            if (device == null) {
                continue;
            }
            String deviceName = safeDeviceName(device);
            if (SERVICE_NAME.equals(deviceName) || deviceName.contains("BlindNav")) {
                prioritized.add(device);
            } else {
                fallback.add(device);
            }
        }
        prioritized.addAll(fallback);
        return prioritized;
    }

    @SuppressLint("MissingPermission")
    private BluetoothSocket connectToDevice(BluetoothAdapter adapter, BluetoothDevice device)
            throws IOException {
        BluetoothSocket socket = device.createRfcommSocketToServiceRecord(BT_UUID);
        adapter.cancelDiscovery();
        socket.connect();
        return socket;
    }

    private void readRequests() {
        while (running) {
            BufferedReader reader;
            synchronized (this) {
                reader = inputReader;
            }

            if (reader == null) {
                break;
            }

            try {
                String line = reader.readLine();
                if (line == null) {
                    break;
                }
                handleRequest(line);
            } catch (IOException e) {
                if (running) {
                    Log.e(TAG, "Read request error", e);
                }
                break;
            }
        }

        publishBluetoothState(BT_STATE_DISCONNECTED, "树莓派连接已断开");
        updateNotification("树莓派连接已断开");
    }

    private void handleRequest(String jsonStr) {
        try {
            JSONObject req = new JSONObject(jsonStr);
            String action = req.getString("action");
            if ("START_NAV".equals(action)) {
                String target = req.getString("target");
                updateNotification("正在导航至: " + target);
                naviManager.startNavigationTo(target, "杭州");
            } else if ("STOP_NAV".equals(action)) {
                updateNotification("导航已取消");
                naviManager.stopNavigation();
            } else if ("GET_LOCATION".equals(action)) {
                updateNotification("正在查询当前位置...");
                naviManager.queryCurrentLocation(new GaodeNaviManager.LocationCallback() {
                    @Override
                    public void onLocationSuccess(GaodeNaviManager.LocationSnapshot locationSnapshot) {
                        JSONObject resp = new JSONObject();
                        try {
                            resp.put("status", "OK");
                            resp.put("event", "CURRENT_LOCATION");
                            resp.put("latitude", locationSnapshot.getLatitude());
                            resp.put("longitude", locationSnapshot.getLongitude());
                            resp.put("address", locationSnapshot.getAddress());
                            resp.put("city", locationSnapshot.getCity());
                            resp.put("provider", locationSnapshot.getProvider());
                            resp.put("detail", locationSnapshot.getDetail());
                            sendResponse(resp);
                            updateNotification("当前位置已返回");
                        } catch (Exception e) {
                            Log.e(TAG, "Build location response error", e);
                        }
                    }

                    @Override
                    public void onLocationFailure(String reason) {
                        JSONObject resp = new JSONObject();
                        try {
                            resp.put("status", "ERROR");
                            resp.put("event", "CURRENT_LOCATION");
                            resp.put("reason", reason);
                            sendResponse(resp);
                            updateNotification("当前位置查询失败");
                        } catch (Exception e) {
                            Log.e(TAG, "Build location error response error", e);
                        }
                    }
                });
            }
        } catch (Exception e) {
            Log.e(TAG, "Handle request error", e);
        }
    }

    private void sendResponse(JSONObject response) {
        try {
            if (out != null) {
                out.println(response.toString());
            }
        } catch (Exception e) {
            Log.e(TAG, "Send response error", e);
        }
    }

    private void sendArrived() {
        JSONObject resp = new JSONObject();
        try {
            resp.put("status", "ARRIVED");
            sendResponse(resp);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @SuppressLint("MissingPermission")
    private String safeDeviceName(BluetoothDevice device) {
        if (device == null) {
            return "unknown";
        }
        try {
            String name = device.getName();
            if (name != null && !name.isEmpty()) {
                return name;
            }
            return device.getAddress();
        } catch (Exception e) {
            return "unknown";
        }
    }

    private void closeSocket(BluetoothSocket socket) {
        if (socket != null) {
            try {
                socket.close();
            } catch (Exception e) {
                Log.e(TAG, "Close socket error", e);
            }
        }
    }

    private void closeClientLocked() {
        if (inputReader != null) {
            try {
                inputReader.close();
            } catch (Exception e) {
                Log.e(TAG, "Close reader error", e);
            }
            inputReader = null;
        }

        if (out != null) {
            try {
                out.close();
            } catch (Exception e) {
                Log.e(TAG, "Close writer error", e);
            }
            out = null;
        }

        if (clientSocket != null) {
            try {
                clientSocket.close();
            } catch (Exception e) {
                Log.e(TAG, "Close client socket error", e);
            }
            clientSocket = null;
        }
    }

    private void sleepRetry() {
        try {
            Thread.sleep(RETRY_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void publishBluetoothState(String state, String detail) {
        try {
            getSharedPreferences(PREFS_BT_STATUS, MODE_PRIVATE)
                    .edit()
                    .putString(PREF_KEY_STATE, state)
                    .putString(PREF_KEY_DETAIL, detail == null ? "" : detail)
                    .apply();

            Intent intent = new Intent(ACTION_BT_STATUS_CHANGED);
            intent.setPackage(getPackageName());
            intent.putExtra(EXTRA_BT_STATE, state);
            intent.putExtra(EXTRA_BT_DETAIL, detail == null ? "" : detail);
            sendBroadcast(intent);
        } catch (Exception e) {
            Log.e(TAG, "Publish bluetooth state error", e);
        }
    }

    public static String getStoredBluetoothState(android.content.Context context) {
        return context.getSharedPreferences(PREFS_BT_STATUS, MODE_PRIVATE)
                .getString(PREF_KEY_STATE, BT_STATE_DISCONNECTED);
    }

    public static String getStoredBluetoothDetail(android.content.Context context) {
        return context.getSharedPreferences(PREFS_BT_STATUS, MODE_PRIVATE)
                .getString(PREF_KEY_DETAIL, "");
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "导航服务", NotificationManager.IMPORTANCE_LOW);
            NotificationManager mgr = getSystemService(NotificationManager.class);
            mgr.createNotificationChannel(ch);
        }
    }

    private Notification buildNotification(String text) {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("盲-nav")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentIntent(pi)
                .build();
    }

    private void startForegroundCompat(Notification notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
        } else {
            startForegroundReflectively(notification);
        }
    }

    private void startForegroundReflectively(Notification notification) {
        try {
            Method method = Service.class.getMethod("startForeground", int.class, Notification.class);
            method.invoke(this, 1, notification);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to start foreground service", e);
        }
    }

    private void updateNotification(String text) {
        NotificationManager mgr = getSystemService(NotificationManager.class);
        mgr.notify(1, buildNotification(text));
    }

    @Override
    public void onDestroy() {
        running = false;
        if (connectThread != null) {
            connectThread.interrupt();
        }
        if (naviManager != null) {
            naviManager.destroy();
        }
        synchronized (this) {
            closeClientLocked();
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
