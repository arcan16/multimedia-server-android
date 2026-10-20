package com.multimediaserver.android.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;

import androidx.appcompat.app.AppCompatActivity;

import com.multimediaserver.android.ui.common.SystemBars;
import com.multimediaserver.android.api.Api;
import com.multimediaserver.android.api.ServerConfig;
import com.multimediaserver.android.api.Session;
import com.multimediaserver.android.databinding.ActivityLoginBinding;
import com.multimediaserver.android.model.LoginResponse;
import com.multimediaserver.android.model.User;
import com.multimediaserver.android.ui.MainActivity;
import com.multimediaserver.android.ui.common.Ui;
import com.multimediaserver.android.util.Async;

/**
 * Login screen (LoginPage.jsx). When a remembered session exists it restores the user with
 * GET /users/me and goes straight to the catalog.
 */
public class LoginActivity extends AppCompatActivity {
    private ActivityLoginBinding b;
    private boolean submitting;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        SystemBars.fit(this);

        b.server.setText(ServerConfig.getBaseUrl());
        updateServerToggle(false);
        b.serverToggle.setOnClickListener(v -> updateServerToggle(b.serverLayout.getVisibility() != View.VISIBLE));

        b.password.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submit();
                return true;
            }
            return false;
        });
        b.submit.setOnClickListener(v -> submit());

        String notice = Session.takeNotice();
        if (notice != null) Ui.showAlert(b.alert, Ui.Alert.INFO, notice);

        if (savedInstanceState == null && Session.hasToken()) restoreSession();
    }

    private void updateServerToggle(boolean expanded) {
        b.serverLayout.setVisibility(expanded ? View.VISIBLE : View.GONE);
        b.serverToggle.setText(expanded ? "Ocultar servidor" : "Servidor: " + ServerConfig.getBaseUrl());
    }

    /** A remembered token: restore id and role from /users/me. */
    private void restoreSession() {
        setBusy(true, "Recuperando sesión…");
        Async.run(this, Api.Auth::me, this::openApp, error -> {
            setBusy(false, null);
            if (error.status == 401) return; // the 401 handler already reopened this screen
            Session.clear();
            Ui.showAlert(b.alert, Ui.Alert.ERROR, error.getMessage());
        });
    }

    private void submit() {
        if (submitting) return;
        String username = Ui.text(b.username).trim();
        String password = Ui.text(b.password);
        if (username.isEmpty() || password.isEmpty()) {
            Ui.showAlert(b.alert, Ui.Alert.ERROR, "Introduce tu usuario y tu contraseña.");
            return;
        }
        ServerConfig.setBaseUrl(Ui.text(b.server));
        b.server.setText(ServerConfig.getBaseUrl());
        boolean remember = b.remember.isChecked();
        Ui.hideKeyboard(b.submit);
        Ui.hide(b.alert);
        setBusy(true, "Entrando…");

        Async.run(this, () -> {
            LoginResponse response = Api.Auth.login(username, password);
            Session.start(response.token, response.username != null ? response.username : username, remember);
            try {
                return Api.Auth.me();
            } catch (Exception e) {
                // The login response already has what the app needs.
                User user = new User();
                user.id = response.id;
                user.username = response.username;
                user.rol = response.rol;
                return user;
            }
        }, this::openApp, error -> {
            setBusy(false, null);
            String message = error.status == 401 && error.getMessage() == null
                    ? "Usuario o contraseña incorrectos." : error.getMessage();
            Ui.showAlert(b.alert, Ui.Alert.ERROR, message);
            // Never keep a rejected password around.
            b.password.setText("");
        });
    }

    private void openApp(User user) {
        Session.setUser(user);
        b.password.setText("");
        startActivity(new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        finish();
    }

    private void setBusy(boolean busy, String label) {
        submitting = busy;
        b.submit.setEnabled(!busy);
        b.submit.setText(busy ? label : "Entrar");
        b.username.setEnabled(!busy);
        b.password.setEnabled(!busy);
    }
}
