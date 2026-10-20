package com.multimediaserver.android.ui.common;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.multimediaserver.android.MultimediaApp;
import com.multimediaserver.android.api.Session;
import com.multimediaserver.android.ui.auth.LoginActivity;

/**
 * Base for every screen behind the login (RequireAuth). Without a loaded user it goes back to
 * the login screen, which restores a remembered session; while visible it logs out exactly when
 * the token expires.
 */
public abstract class SessionActivity extends AppCompatActivity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable expire = () -> MultimediaApp.endSession("Tu sesión ha caducado");

    /** True when the screen can run; otherwise it is already finishing. */
    protected boolean hasSession;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        hasSession = Session.getUser() != null && Session.hasToken() && !Session.hasExpiredToken();
        if (!hasSession) {
            startActivity(new Intent(this, LoginActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            finish();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        long millis = Session.millisToExpiry();
        if (millis >= 0) handler.postDelayed(expire, millis);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(expire);
    }
}
