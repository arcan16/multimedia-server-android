package com.multimediaserver.android;

import android.app.Application;
import android.content.Intent;

import com.multimediaserver.android.api.ApiClient;
import com.multimediaserver.android.api.ServerConfig;
import com.multimediaserver.android.api.Session;
import com.multimediaserver.android.ui.auth.LoginActivity;
import com.multimediaserver.android.util.Async;
import com.multimediaserver.android.util.GenreCache;

public class MultimediaApp extends Application {
    private static MultimediaApp instance;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        ServerConfig.init(this);
        Session.init(this);
        // A 401 (or an expired token) ends the session and goes back to the login screen.
        ApiClient.setUnauthorizedHandler(() -> Async.main(() -> endSession("Tu sesión ha caducado. Vuelve a iniciar sesión.")));
    }

    /**
     * Clears the session and opens the login screen on a fresh task, showing {@code notice}
     * (null for a plain logout). Repeated calls after the first one do nothing.
     */
    public static void endSession(String notice) {
        boolean hadSession = Session.hasToken();
        Session.clear();
        GenreCache.invalidate();
        if (!hadSession) return;
        Session.setNotice(notice);
        Intent intent = new Intent(instance, LoginActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        instance.startActivity(intent);
    }
}
