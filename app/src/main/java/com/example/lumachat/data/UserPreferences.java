package com.example.lumachat.data;

import android.content.Context;
import android.content.SharedPreferences;

public class UserPreferences {
    private static final String PREF_NAME = "luma_user_prefs";
    private static final String KEY_USER_NAME = "key_user_name";
    private static final String KEY_USER_BIO = "key_user_bio";
    private static final String KEY_USER_AVATAR = "key_user_avatar";
    private static final String KEY_DARK_MODE = "key_dark_mode";
    private static final String KEY_NOTIFICATIONS = "key_notifications";
    private static final String KEY_THEME_COLOR = "key_theme_color";

    private static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static String getUserName(Context context) {
        return getPrefs(context).getString(KEY_USER_NAME, "Harshitha");
    }

    public static void setUserName(Context context, String name) {
        getPrefs(context).edit().putString(KEY_USER_NAME, name).apply();
    }

    public static String getUserBio(Context context) {
        return getPrefs(context).getString(KEY_USER_BIO, "Making memories, one message at a time ✨");
    }

    public static void setUserBio(Context context, String bio) {
        getPrefs(context).edit().putString(KEY_USER_BIO, bio).apply();
    }

    public static String getUserAvatar(Context context) {
        return getPrefs(context).getString(KEY_USER_AVATAR, "H");
    }

    public static void setUserAvatar(Context context, String avatar) {
        getPrefs(context).edit().putString(KEY_USER_AVATAR, avatar).apply();
    }

    public static boolean isDarkMode(Context context) {
        return getPrefs(context).getBoolean(KEY_DARK_MODE, false);
    }

    public static void setDarkMode(Context context, boolean darkMode) {
        getPrefs(context).edit().putBoolean(KEY_DARK_MODE, darkMode).apply();
    }

    public static boolean isNotificationsEnabled(Context context) {
        return getPrefs(context).getBoolean(KEY_NOTIFICATIONS, true);
    }

    public static void setNotificationsEnabled(Context context, boolean enabled) {
        getPrefs(context).edit().putBoolean(KEY_NOTIFICATIONS, enabled).apply();
    }

    public static String getThemeColor(Context context) {
        return getPrefs(context).getString(KEY_THEME_COLOR, "lavender");
    }

    public static void setThemeColor(Context context, String themeColor) {
        getPrefs(context).edit().putString(KEY_THEME_COLOR, themeColor).apply();
    }
}
