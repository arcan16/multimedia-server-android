package com.multimediaserver.android.ui.admin;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.LifecycleOwner;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;
import com.multimediaserver.android.api.Api;
import com.multimediaserver.android.api.ApiError;
import com.multimediaserver.android.databinding.DialogUserFormBinding;
import com.multimediaserver.android.model.User;
import com.multimediaserver.android.ui.common.Dropdown;
import com.multimediaserver.android.ui.common.Ui;
import com.multimediaserver.android.util.Async;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Create (no user) or edit a user (UserFormModal.jsx). Edits send only the changed fields;
 * {@code onSaved} receives what was sent (or the created user) so the caller can react.
 */
public final class UserFormDialog {

    public interface OnSaved {
        void onSaved(Map<String, Object> changes, @Nullable User created);
    }

    private UserFormDialog() {
    }

    public static void show(Context context, LifecycleOwner owner, @Nullable User user, boolean isSelf, OnSaved onSaved) {
        boolean isEdit = user != null;
        DialogUserFormBinding f = DialogUserFormBinding.inflate(LayoutInflater.from(context));

        Dropdown.setOptions(f.rol, Arrays.asList("USER", "ADMIN"));
        f.rol.setText(isEdit && user.rol != null ? user.rol : "USER", false);
        f.passwordLayout.setHint(isEdit ? "Nueva contraseña (opcional)" : "Contraseña");
        if (isEdit) {
            Ui.setText(f.nameLayout, user.name);
            Ui.setText(f.lastnameLayout, user.lastname);
            Ui.setText(f.emailLayout, user.email);
            Ui.setText(f.usernameLayout, user.username);
            if (isSelf) f.usernameLayout.setHelperText("Si lo cambias, tendrás que volver a iniciar sesión.");
        }
        for (TextInputLayout layout : new TextInputLayout[]{f.nameLayout, f.lastnameLayout, f.emailLayout,
                f.usernameLayout, f.passwordLayout}) {
            Ui.clearErrorOnEdit(layout, null);
        }

        String submitLabel = isEdit ? "Guardar cambios" : "Crear usuario";
        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setTitle(isEdit ? "Editar " + user.username : "Nuevo usuario")
                .setView(f.getRoot())
                .setNegativeButton("Cancelar", null)
                .setPositiveButton(submitLabel, null)
                .create();

        dialog.setOnShowListener(d -> {
            View positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            positive.setOnClickListener(v -> {
                Ui.hide(f.formError);
                if (!validate(f, isEdit)) return;

                Map<String, Object> changes = new LinkedHashMap<>();
                if (isEdit) {
                    changes.put("id", user.id);
                    putIfChanged(changes, "name", Ui.text(f.nameLayout).trim(), user.name);
                    putIfChanged(changes, "lastname", Ui.text(f.lastnameLayout).trim(), user.lastname);
                    putIfChanged(changes, "email", Ui.text(f.emailLayout).trim(), user.email);
                    putIfChanged(changes, "username", Ui.text(f.usernameLayout).trim(), user.username);
                    putIfChanged(changes, "rol", Ui.text(f.rol).trim(), user.rol);
                    if (!Ui.text(f.passwordLayout).isEmpty()) changes.put("password", Ui.text(f.passwordLayout));
                } else {
                    changes.put("username", Ui.text(f.usernameLayout).trim());
                    changes.put("password", Ui.text(f.passwordLayout));
                    changes.put("rol", Ui.text(f.rol).trim());
                    changes.put("name", Ui.text(f.nameLayout).trim());
                    changes.put("lastname", Ui.text(f.lastnameLayout).trim());
                    changes.put("email", Ui.text(f.emailLayout).trim());
                }

                setBusy(dialog, true, submitLabel);
                Async.run(owner, () -> {
                    if (!isEdit) return Api.Users.create(changes);
                    if (changes.size() > 1) Api.Users.update(changes);
                    return null;
                }, created -> {
                    dialog.dismiss();
                    onSaved.onSaved(changes, created);
                }, error -> {
                    setBusy(dialog, false, submitLabel);
                    showErrors(f, error);
                });
            });
        });
        dialog.show();
    }

    private static boolean validate(DialogUserFormBinding f, boolean isEdit) {
        boolean ok = true;
        if (Ui.text(f.usernameLayout).trim().isEmpty()) {
            Ui.setError(f.usernameLayout, "El nombre de usuario es obligatorio.");
            ok = false;
        }
        if (!isEdit) {
            if (Ui.text(f.nameLayout).trim().isEmpty()) {
                Ui.setError(f.nameLayout, "El nombre es obligatorio.");
                ok = false;
            }
            if (Ui.text(f.lastnameLayout).trim().isEmpty()) {
                Ui.setError(f.lastnameLayout, "El apellido es obligatorio.");
                ok = false;
            }
            if (Ui.text(f.emailLayout).trim().isEmpty()) {
                Ui.setError(f.emailLayout, "El email es obligatorio.");
                ok = false;
            }
        }
        String password = Ui.text(f.passwordLayout);
        if ((!isEdit || !password.isEmpty()) && (password.length() < 8 || password.length() > 72)) {
            Ui.setError(f.passwordLayout, "La contraseña debe tener entre 8 y 72 caracteres.");
            ok = false;
        }
        return ok;
    }

    private static void showErrors(DialogUserFormBinding f, ApiError error) {
        Ui.setError(f.nameLayout, error.field("name"));
        Ui.setError(f.lastnameLayout, error.field("lastname"));
        Ui.setError(f.emailLayout, error.field("email"));
        Ui.setError(f.usernameLayout, error.field("username"));
        Ui.setError(f.passwordLayout, error.field("password"));
        Ui.setError(f.rolLayout, error.field("rol"));
        // Field-level messages are shown next to their field; others go on top.
        if (error.fieldErrors.isEmpty()) Ui.showAlert(f.formError, Ui.Alert.ERROR, error.getMessage());
    }

    private static void putIfChanged(Map<String, Object> changes, String key, String value, @Nullable String original) {
        if (!value.equals(original != null ? original : "")) changes.put(key, value);
    }

    private static void setBusy(AlertDialog dialog, boolean busy, String label) {
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(!busy);
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(!busy);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setText(busy ? "Guardando…" : label);
        dialog.setCancelable(!busy);
    }
}
