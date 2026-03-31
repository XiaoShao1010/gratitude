package com.blindnav.agent.navi;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.blindnav.agent.MainActivity;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.UUID;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.app.Notification;

public class SocketServerService extends Service {
    private static final String TAG = "BtServer";
    private static final String SERVICE_NAME = "BlindNavPi";
    private static final UUID BT_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final String CHANNEL_ID = "BlindNavChannel";

    private BluetoothServerSocket serverSocket;
    private BluetoothSocket clientSocket;
    private PrintWriter out;
    private boolean running = false;
    private GaodeNaviManager naviManager;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(1, buildNotification("导航服务启动中..."));
        naviManager = new GaodeNaviManager(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        running = true;
        new Thread(this::startServer).start();
        return START_STICKY;
    }

    private void startServer() {
        try {
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            if (adapter == null) {
                Log.e(TAG, "Bluetooth not available");
                return;
            }
            serverSocket = adapter.listenUsingRfcommWithServiceRecord(SERVICE_NAME, BT_UUID);
            updateNotification("等待树莓派蓝牙连接...");
            while (running) {
                clientSocket = serverSocket.accept();
                out = new PrintWriter(
                        new OutputStreamWriter(clientSocket.getOutputStream()), true);
                BufferedReader in = new BufferedReader(
                        new InputStreamReader(clientSocket.getInputStream()));
                String line;
                while (running && (line = in.readLine()) != null) {
                    handleRequest(line);
                }
                in.close();
                out.close();
                clientSocket.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "Server error", e);
        }
    }

    private void handleRequest(String jsonStr) {
        try {
            JSONObject req = new JSONObject(jsonStr);
            String action = req.getString("action");
            if ("START_NAV".equals(action)) {
                String target = req.getString("target");
                updateNotification("正在导航至: " + target);
                naviManager.startNavigationTo(target, "杭州");
            }
        } catch (Exception e) {
            Log.e(TAG, "Handle request error", e);
        }
    }

    private void sendResponse(String status, String event, int distance) {
        JSONObject resp = new JSONObject();
        try {
            resp.put("status", status);
            resp.put("event", event);
            resp.put("distance", distance);
            // 如果下面还有 send(resp.toString()) 相关的代码，也一起包在这个 try 里面
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendArrived() {
        JSONObject resp = new JSONObject();
        try {
            resp.put("status", "ARRIVED");
            // 同理，发送数据的代码也包进来
        } catch (Exception e) {
            e.printStackTrace();
        }
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

    private void updateNotification(String text) {
        NotificationManager mgr = getSystemService(NotificationManager.class);
        mgr.notify(1, buildNotification(text));
    }

    @Override
    public void onDestroy() {
        running = false;
        try {
            if (serverSocket != null) serverSocket.close();
            if (clientSocket != null) clientSocket.close();
        } catch (Exception e) {
            Log.e(TAG, "Close error", e);
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
