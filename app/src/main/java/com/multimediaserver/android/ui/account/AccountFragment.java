package com.multimediaserver.android.ui.account;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.textfield.TextInputLayout;
import com.multimediaserver.android.MultimediaApp;
import com.multimediaserver.android.api.Api;
import com.multimediaserver.android.api.ApiError;
import com.multimediaserver.android.api.Session;
import com.multimediaserver.android.databinding.FragmentAccountBinding;
import com.multimediaserver.android.model.User;
import com.multimediaserver.android.ui.MainActivity;
import com.multimediaserver.android.ui.common.Ui;
import com.multimediaserver.android.util.Async;

import java.util.LinkedHashMap;
import java.util.Map;

/** My account (AccountPage.jsx): personal data, username and password; the role is shown locked. */
public class AccountFragment extends Fragment {
    private FragmentAccountBinding b;
    private User account;
    private boolean saving;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        b = FragmentAccountBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        for (TextInputLayout layout : new TextInputLayout[]{b.nameLayout, b.lastnameLayout, b.emailLayout,
                b.usernameLayout, b.passwordLayout, b.password2Layout}) {
            Ui.clearErrorOnEdit(layout, null);
        }
        b.discard.setOnClickListener(v -> {
            fill(account);
            Ui.hide(b.alert);
        });
        b.save.setOnClickListener(v -> save());
        load();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }

    private void load() {
        User me = Session.getUser();
        if (me == null || me.id == null) {
            Ui.showAlert(b.alert, Ui.Alert.INFO, "No se pudieron cargar los datos de tu cuenta.");
            return;
        }
        Ui.loading(b.state, null);
        Async.run(getViewLifecycleOwner(), () -> Api.Users.get(me.id), user -> {
            account = user;
            Ui.hide(b.state);
            b.form.setVisibility(View.VISIBLE);
            fill(user);
        }, error -> Ui.error(b.state, error, this::load));
    }

    private void fill(User user) {
        if (user == null) return;
        Ui.setText(b.nameLayout, user.name);
        Ui.setText(b.lastnameLayout, user.lastname);
        Ui.setText(b.emailLayout, user.email);
        Ui.setText(b.usernameLayout, user.username);
        Ui.setText(b.passwordLayout, "");
        Ui.setText(b.password2Layout, "");
        b.role.setText("Rol: " + user.rol);
        b.roleHint.setText(user.isAdmin()
                ? "Tu rol se gestiona desde Administración › Usuarios."
                : "Solo un administrador puede cambiar tu rol o eliminar tu cuenta.");
    }

    private boolean validate() {
        boolean ok = true;
        String password = Ui.text(b.passwordLayout);
        if (Ui.text(b.usernameLayout).trim().isEmpty()) {
            Ui.setError(b.usernameLayout, "El nombre de usuario es obligatorio.");
            ok = false;
        }
        if (!password.isEmpty() && (password.length() < 8 || password.length() > 72)) {
            Ui.setError(b.passwordLayout, "La contraseña debe tener entre 8 y 72 caracteres.");
            ok = false;
        }
        if (!password.equals(Ui.text(b.password2Layout))) {
            Ui.setError(b.password2Layout, "Las contraseñas no coinciden.");
            ok = false;
        }
        return ok;
    }

    private void save() {
        if (saving || account == null) return;
        Ui.hide(b.alert);
        Ui.hideKeyboard(b.save);
        if (!validate()) return;

        // Send only what changed; omitted fields are left unchanged by the API.
        Map<String, Object> changes = new LinkedHashMap<>();
        changes.put("id", account.id);
        putIfChanged(changes, "name", Ui.text(b.nameLayout).trim(), account.name);
        putIfChanged(changes, "lastname", Ui.text(b.lastnameLayout).trim(), account.lastname);
        putIfChanged(changes, "email", Ui.text(b.emailLayout).trim(), account.email);
        putIfChanged(changes, "username", Ui.text(b.usernameLayout).trim(), account.username);
        String password = Ui.text(b.passwordLayout);
        if (!password.isEmpty()) changes.put("password", password);
        if (changes.size() == 1) {
            Ui.showAlert(b.alert, Ui.Alert.SUCCESS, "Datos guardados correctamente.");
            return;
        }

        setSaving(true);
        Async.run(getViewLifecycleOwner(), () -> {
            Api.Users.update(changes);
            // The token is tied to the old username: no refetch is possible after a rename.
            return changes.containsKey("username") ? null : Api.Users.get(account.id);
        }, fresh -> {
            setSaving(false);
            if (fresh == null) {
                MultimediaApp.endSession("Has cambiado tu nombre de usuario. Inicia sesión de nuevo.");
                return;
            }
            account = fresh;
            Session.setUser(fresh);
            if (getActivity() instanceof MainActivity) ((MainActivity) getActivity()).bindUser();
            fill(fresh);
            Ui.showAlert(b.alert, Ui.Alert.SUCCESS, "Datos guardados correctamente.");
        }, error -> {
            setSaving(false);
            showFieldErrors(error);
            Ui.showAlert(b.alert, Ui.Alert.ERROR, error.getMessage());
        });
    }

    private static void putIfChanged(Map<String, Object> changes, String key, String value, @Nullable String original) {
        if (!value.equals(original != null ? original : "")) changes.put(key, value);
    }

    private void showFieldErrors(ApiError error) {
        Ui.setError(b.nameLayout, error.field("name"));
        Ui.setError(b.lastnameLayout, error.field("lastname"));
        Ui.setError(b.emailLayout, error.field("email"));
        Ui.setError(b.usernameLayout, error.field("username"));
        Ui.setError(b.passwordLayout, error.field("password"));
    }

    private void setSaving(boolean value) {
        saving = value;
        b.save.setEnabled(!value);
        b.discard.setEnabled(!value);
        b.save.setText(value ? "Guardando…" : "Guardar cambios");
    }
}
