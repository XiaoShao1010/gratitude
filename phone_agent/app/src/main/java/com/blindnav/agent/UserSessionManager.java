package com.blindnav.agent;

import android.content.Context;
import android.content.SharedPreferences;

public class UserSessionManager {
    private static final String PREF_NAME = "blindnav_user_session";
    private static final String KEY_LOGGED_IN = "logged_in";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_DISPLAY_NAME = "display_name";
    private static final String KEY_TOKEN = "token";

    private final SharedPreferences preferences;

    public UserSessionManager(Context context) {
        preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public void saveLogin(String username, String displayName, String token) {
        preferences.edit()
                .putBoolean(KEY_LOGGED_IN, true)
                .putString(KEY_USERNAME, username)
                .putString(KEY_DISPLAY_NAME, displayName)
                .putString(KEY_TOKEN, token)
                .apply();
    }

    public boolean isLoggedIn() {
        return preferences.getBoolean(KEY_LOGGED_IN, false);
    }

    public String getUsername() {
        return preferences.getString(KEY_USERNAME, "");
    }

    public String getDisplayName() {
        String displayName = preferences.getString(KEY_DISPLAY_NAME, "");
        if (displayName == null || displayName.isEmpty()) {
            return getUsername();
        }
        return displayName;
    }

    public String getToken() {
        return preferences.getString(KEY_TOKEN, "");
    }

    public void logout() {
        preferences.edit().clear().apply();
    }
}