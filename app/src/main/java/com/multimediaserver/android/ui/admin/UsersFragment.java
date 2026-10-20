package com.multimediaserver.android.ui.admin;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.multimediaserver.android.MultimediaApp;
import com.multimediaserver.android.R;
import com.multimediaserver.android.api.Api;
import com.multimediaserver.android.api.Session;
import com.multimediaserver.android.databinding.FragmentUsersBinding;
import com.multimediaserver.android.databinding.ItemUserBinding;
import com.multimediaserver.android.model.User;
import com.multimediaserver.android.ui.MainActivity;
import com.multimediaserver.android.ui.common.Ui;
import com.multimediaserver.android.util.Async;
import com.multimediaserver.android.util.Format;

import java.util.ArrayList;
import java.util.List;

/** Users (UsersPage.jsx). Another admin's row is locked: an ADMIN never edits or deletes it. */
public class UsersFragment extends Fragment {
    private FragmentUsersBinding b;
    private List<User> users = new ArrayList<>();
    private boolean loadedOnce;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        b = FragmentUsersBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        Ui.showAlert(b.info, Ui.Alert.INFO, "Las cuentas de otros administradores no se pueden editar ni eliminar. "
                + "Siempre debe quedar al menos un administrador activo.");
        b.newUser.setOnClickListener(v -> openForm(null));
        b.refresh.setColorSchemeResources(R.color.accent);
        b.refresh.setProgressBackgroundColorSchemeResource(R.color.surface_2);
        b.refresh.setOnRefreshListener(this::load);
        load();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }

    private static boolean isSelf(User u) {
        User me = Session.getUser();
        if (me == null) return false;
        return (me.id != null && me.id.equals(u.id)) || (me.username != null && me.username.equals(u.username));
    }

    private static boolean isLocked(User u) {
        return u.isAdmin() && !isSelf(u);
    }

    private void load() {
        if (!loadedOnce) Ui.loading(b.state, null);
        Async.run(getViewLifecycleOwner(), Api.Users::list, list -> {
            loadedOnce = true;
            b.refresh.setRefreshing(false);
            users = list;
            render();
        }, error -> {
            b.refresh.setRefreshing(false);
            b.list.setVisibility(View.GONE);
            Ui.error(b.state, error, this::load);
        });
    }

    private void render() {
        b.list.removeAllViews();
        if (users.isEmpty()) {
            b.list.setVisibility(View.GONE);
            Ui.empty(b.state, "No hay usuarios", null, null, null);
            return;
        }
        Ui.hide(b.state);
        b.list.setVisibility(View.VISIBLE);
        for (int i = 0; i < users.size(); i++) addRow(users.get(i), i == users.size() - 1);
    }

    private void addRow(User u, boolean last) {
        ItemUserBinding row = ItemUserBinding.inflate(getLayoutInflater(), b.list, false);
        boolean locked = isLocked(u);
        row.username.setText(u.username + (isSelf(u) ? " (tú)" : ""));
        row.email.setText(Format.orDash(u.email));
        String name = Format.fullName(u);
        row.fullName.setText(name.isEmpty() ? "Sin datos personales" : name);
        row.role.setText(u.rol);
        if (u.isAdmin()) {
            row.role.setTextColor(ContextCompat.getColor(requireContext(), R.color.accent));
            row.role.setBackgroundResource(R.drawable.bg_badge_accent);
        }
        row.status.setText(Format.statusLabel(u.status));
        row.lockIcon.setVisibility(locked ? View.VISIBLE : View.GONE);
        row.lockLabel.setVisibility(locked ? View.VISIBLE : View.GONE);
        row.divider.setVisibility(last ? View.GONE : View.VISIBLE);

        row.edit.setEnabled(!locked);
        row.delete.setEnabled(!locked);
        row.edit.setAlpha(locked ? 0.35f : 1f);
        row.delete.setAlpha(locked ? 0.35f : 1f);
        row.edit.setContentDescription("Editar " + u.username);
        row.delete.setContentDescription("Eliminar " + u.username);
        row.edit.setOnClickListener(v -> openForm(u));
        row.delete.setOnClickListener(v -> confirmDelete(u));
        b.list.addView(row.getRoot());
    }

    private void openForm(@Nullable User user) {
        boolean self = user != null && isSelf(user);
        UserFormDialog.show(requireContext(), getViewLifecycleOwner(), user, self, (changes, created) -> {
            if (user != null && self) {
                if (changes.containsKey("username")) {
                    MultimediaApp.endSession("Has cambiado tu nombre de usuario. Inicia sesión de nuevo.");
                    return;
                }
                refreshSelf();
            }
            Ui.showAlert(b.notice, Ui.Alert.SUCCESS, user != null ? "Cambios guardados." : "Usuario creado.");
            load();
        });
    }

    /** After editing your own account (e.g. demoting yourself), reload it and the drawer. */
    private void refreshSelf() {
        Async.run(getViewLifecycleOwner(), Api.Auth::me, me -> {
            Session.setUser(me);
            if (getActivity() instanceof MainActivity) {
                MainActivity activity = (MainActivity) getActivity();
                activity.bindUser();
                if (!me.isAdmin()) activity.navigate(MainActivity.CATALOG, null);
            }
        }, error -> {
        });
    }

    private void confirmDelete(User u) {
        boolean self = isSelf(u);
        Ui.confirm(requireContext(), "¿Eliminar a " + u.username + "?",
                self ? "Vas a eliminar tu propia cuenta y se cerrará la sesión." : "La cuenta dejará de poder iniciar sesión.",
                "Eliminar", dialog -> {
                    Ui.setBusy(dialog, true, "Eliminar");
                    Async.run(getViewLifecycleOwner(), () -> {
                        Api.Users.remove(u.id);
                        return true;
                    }, ok -> {
                        dialog.dismiss();
                        if (self) {
                            MultimediaApp.endSession("Tu cuenta ha sido eliminada.");
                            return;
                        }
                        Ui.showAlert(b.notice, Ui.Alert.SUCCESS, "Usuario eliminado.");
                        load();
                    }, error -> {
                        Ui.setBusy(dialog, false, "Eliminar");
                        dialog.setMessage(error.getMessage());
                    });
                });
    }
}
