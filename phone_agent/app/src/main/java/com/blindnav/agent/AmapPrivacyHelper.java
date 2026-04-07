package com.blindnav.agent;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AlertDialog;

import com.amap.api.maps.MapsInitializer;

public final class AmapPrivacyHelper {
    private static final String PREFS_NAME = "amap_privacy_prefs";
    private static final String KEY_PRIVACY_ACCEPTED = "privacy_accepted";

    private AmapPrivacyHelper() {
    }

    public static void ensureConsent(Activity activity, Runnable onAgreed) {
        MapsInitializer.updatePrivacyShow(activity, true, true);

        SharedPreferences prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (prefs.getBoolean(KEY_PRIVACY_ACCEPTED, false)) {
            MapsInitializer.updatePrivacyAgree(activity, true);
            onAgreed.run();
            return;
        }

        new AlertDialog.Builder(activity)
                .setTitle("高德地图隐私提示")
                .setMessage("使用高德地图/导航/定位功能前，需要确认隐私政策。点击同意后，应用才会初始化高德 SDK。")
                .setCancelable(false)
                .setPositiveButton("同意", (dialog, which) -> {
                    prefs.edit().putBoolean(KEY_PRIVACY_ACCEPTED, true).apply();
                    MapsInitializer.updatePrivacyAgree(activity, true);
                    onAgreed.run();
                })
                .setNegativeButton("不同意", (dialog, which) -> {
                    MapsInitializer.updatePrivacyAgree(activity, false);
                    activity.finish();
                })
                .show();
    }
}