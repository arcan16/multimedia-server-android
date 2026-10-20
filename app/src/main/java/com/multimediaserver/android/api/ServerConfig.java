package com.multimediaserver.android.api;

import android.content.Context;
import android.content.SharedPreferences;

import com.multimediaserver.android.BuildConfig;

/**
 * Base URL of the backend. The default comes from BuildConfig.API_URL and the user can change
 * it on the login screen (the server is self-hosted, so its address varies per install).
 */
public final class ServerConfig {
    private static final String PREFS = "ms.server";
    private static final String KEY_URL = "url";

    private static SharedPreferences prefs;
    private static volatile String baseUrl;

    private ServerConfig() {
    }

    public static void init(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        baseUrl = normalize(prefs.getString(KEY_URL, BuildConfig.API_URL));
    }

    public static String getBaseUrl() {
        return baseUrl;
    }

    public static void setBaseUrl(String url) {
        baseUrl = normalize(url);
        prefs.edit().putString(KEY_URL, baseUrl).apply();
    }

    /** Trims, adds http:// when no scheme is given and drops trailing slashes. */
    public static String normalize(String url) {
        String value = url == null ? "" : url.trim();
        if (value.isEmpty()) value = BuildConfig.API_URL;
        if (!value.startsWith("http://") && !value.startsWith("https://")) value = "http://" + value;
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        return value;
    }
}
