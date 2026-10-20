package com.multimediaserver.android.api;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.multimediaserver.android.model.User;

import java.nio.charset.StandardCharsets;

/**
 * Single owner of the session token (the Android twin of Frontend/src/api/tokenStore.js).
 *
 * - Only the JWT and the username are kept. The password never leaves the login form.
 * - The token lives in memory. When the user ticks "Mantener la sesión iniciada" it is also
 *   persisted in private SharedPreferences so the session survives the app being closed;
 *   otherwise it ends with the app process.
 * - The JWT {@code exp} claim is read (not verified: the server is the authority) to drop an
 *   expired token before sending it and to log out on time.
 * - The current user (id, role, person fields) is held in memory only and restored from
 *   GET /users/me when a persisted session is resumed.
 */
public final class Session {
    private static final String PREFS = "ms.session";
    private static final String KEY_TOKEN = "token";
    private static final String KEY_USERNAME = "username";

    private static SharedPreferences prefs;
    private static String token;
    private static String username;
    private static long expiresAt;
    private static User user;
    private static String notice;

    private Session() {
    }

    public static void init(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String stored = prefs.getString(KEY_TOKEN, null);
        if (stored != null) {
            long exp = readExpiry(stored);
            if (exp > 0 && exp <= System.currentTimeMillis()) {
                prefs.edit().clear().apply();
            } else {
                token = stored;
                expiresAt = exp;
                username = prefs.getString(KEY_USERNAME, null);
            }
        }
    }

    public static synchronized void start(String newToken, String newUsername, boolean remember) {
        token = newToken;
        username = newUsername;
        expiresAt = readExpiry(newToken);
        notice = null;
        SharedPreferences.Editor editor = prefs.edit().clear();
        if (remember) editor.putString(KEY_TOKEN, newToken).putString(KEY_USERNAME, newUsername);
        editor.apply();
    }

    public static synchronized void clear() {
        token = null;
        username = null;
        expiresAt = 0;
        user = null;
        prefs.edit().clear().apply();
    }

    public static synchronized String getToken() {
        return token;
    }

    public static synchronized String getUsername() {
        return username;
    }

    public static synchronized boolean hasToken() {
        return token != null;
    }

    public static synchronized boolean hasExpiredToken() {
        return token != null && expiresAt > 0 && expiresAt <= System.currentTimeMillis();
    }

    /** Milliseconds until the token expires, or -1 when it has no {@code exp}. */
    public static synchronized long millisToExpiry() {
        if (token == null || expiresAt <= 0) return -1;
        return Math.max(0, expiresAt - System.currentTimeMillis());
    }

    public static synchronized User getUser() {
        return user;
    }

    public static synchronized void setUser(User value) {
        user = value;
        if (value != null && value.username != null) username = value.username;
    }

    public static synchronized boolean isAdmin() {
        return user != null && user.isAdmin();
    }

    /** One-shot message for the login screen ("Tu sesión ha caducado…"). */
    public static synchronized void setNotice(String message) {
        notice = message;
    }

    public static synchronized String takeNotice() {
        String value = notice;
        notice = null;
        return value;
    }

    /** Returns the {@code exp} claim in milliseconds, or 0 when the token has none. */
    static long readExpiry(String jwt) {
        try {
            String[] parts = jwt.split("\\.");
            byte[] bytes = Base64.decode(parts[1], Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
            JsonObject payload = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
            return payload.has("exp") ? payload.get("exp").getAsLong() * 1000L : 0;
        } catch (RuntimeException e) {
            return 0;
        }
    }
}
