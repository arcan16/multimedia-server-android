package com.multimediaserver.android.ui.admin;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.multimediaserver.android.R;
import com.multimediaserver.android.api.Api;
import com.multimediaserver.android.databinding.FragmentGenresBinding;
import com.multimediaserver.android.databinding.ItemGenreBinding;
import com.multimediaserver.android.model.Genre;
import com.multimediaserver.android.ui.common.Ui;
import com.multimediaserver.android.util.Async;
import com.multimediaserver.android.util.GenreCache;

import java.util.ArrayList;
import java.util.List;

/** Genres (GenresPage.jsx): inline add and edit; delete shows the backend error when in use. */
public class GenresFragment extends Fragment {
    private FragmentGenresBinding b;
    private List<Genre> genres = new ArrayList<>();
    private Long editingId;
    private boolean adding;
    private boolean loadedOnce;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        b = FragmentGenresBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        b.add.setOnClickListener(v -> add());
        b.newName.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId != EditorInfo.IME_ACTION_DONE) return false;
            add();
            return true;
        });
        Ui.clearErrorOnEdit(b.newLayout, null);
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

    private void load() {
        if (!loadedOnce) Ui.loading(b.state, null);
        Async.run(getViewLifecycleOwner(), Api.Genres::list, list -> {
            loadedOnce = true;
            b.refresh.setRefreshing(false);
            genres = list;
            GenreCache.invalidate();
            render();
        }, error -> {
            b.refresh.setRefreshing(false);
            b.list.setVisibility(View.GONE);
            Ui.error(b.state, error, this::load);
        });
    }

    private void render() {
        b.list.removeAllViews();
        if (genres.isEmpty()) {
            b.list.setVisibility(View.GONE);
            Ui.empty(b.state, "Todavía no hay géneros", "Añade el primero con el formulario de arriba.", null, null);
            return;
        }
        Ui.hide(b.state);
        b.list.setVisibility(View.VISIBLE);
        for (int i = 0; i < genres.size(); i++) addRow(genres.get(i), i == genres.size() - 1);
    }

    private void addRow(Genre genre, boolean last) {
        ItemGenreBinding row = ItemGenreBinding.inflate(getLayoutInflater(), b.list, false);
        boolean editing = editingId != null && editingId == genre.id;
        row.view.setVisibility(editing ? View.GONE : View.VISIBLE);
        row.editBox.setVisibility(editing ? View.VISIBLE : View.GONE);
        row.divider.setVisibility(last ? View.GONE : View.VISIBLE);
        row.name.setText(genre.genre);
        row.edit.setContentDescription("Editar " + genre.genre);
        row.delete.setContentDescription("Eliminar " + genre.genre);

        row.edit.setOnClickListener(v -> {
            editingId = genre.id;
            render();
        });
        row.delete.setOnClickListener(v -> confirmDelete(genre));

        if (editing) {
            row.editInput.setText(genre.genre);
            row.editInput.requestFocus();
            row.editInput.setSelection(row.editInput.length());
            Ui.clearErrorOnEdit(row.editLayout, null);
            Runnable save = () -> saveEdit(genre, row);
            row.saveEdit.setOnClickListener(v -> save.run());
            row.editInput.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId != EditorInfo.IME_ACTION_DONE) return false;
                save.run();
                return true;
            });
            row.cancelEdit.setOnClickListener(v -> {
                editingId = null;
                Ui.hideKeyboard(v);
                render();
            });
        }
        b.list.addView(row.getRoot());
    }

    private void add() {
        if (adding) return;
        String name = Ui.text(b.newName).trim();
        if (name.isEmpty()) {
            Ui.setError(b.newLayout, "Escribe el nombre del género.");
            return;
        }
        adding = true;
        b.add.setEnabled(false);
        b.add.setText("Añadiendo…");
        Async.run(getViewLifecycleOwner(), () -> {
            Api.Genres.create(name);
            return true;
        }, ok -> {
            adding = false;
            b.add.setEnabled(true);
            b.add.setText("Añadir");
            b.newName.setText("");
            Ui.setError(b.newLayout, null);
            load();
        }, error -> {
            adding = false;
            b.add.setEnabled(true);
            b.add.setText("Añadir");
            Ui.setError(b.newLayout, error.field("genre") != null ? error.field("genre") : error.getMessage());
        });
    }

    private void saveEdit(Genre genre, ItemGenreBinding row) {
        String value = Ui.text(row.editInput).trim();
        if (value.isEmpty()) {
            Ui.setError(row.editLayout, "El nombre no puede estar vacío.");
            return;
        }
        row.saveEdit.setEnabled(false);
        row.cancelEdit.setEnabled(false);
        row.saveEdit.setText("Guardando…");
        Async.run(getViewLifecycleOwner(), () -> {
            Api.Genres.update(genre.id, value);
            return true;
        }, ok -> {
            editingId = null;
            Ui.hideKeyboard(row.getRoot());
            load();
        }, error -> {
            row.saveEdit.setEnabled(true);
            row.cancelEdit.setEnabled(true);
            row.saveEdit.setText("Guardar");
            Ui.setError(row.editLayout, error.field("genre") != null ? error.field("genre") : error.getMessage());
        });
    }

    private void confirmDelete(Genre genre) {
        Ui.confirm(requireContext(), "¿Eliminar «" + genre.genre + "»?",
                "Solo se puede eliminar si ninguna película o serie lo usa.", "Eliminar", dialog -> {
                    Ui.setBusy(dialog, true, "Eliminar");
                    Ui.hide(b.alert);
                    Async.run(getViewLifecycleOwner(), () -> {
                        Api.Genres.remove(genre.id);
                        return true;
                    }, ok -> {
                        dialog.dismiss();
                        load();
                    }, error -> {
                        // e.g. 400 when the genre is used by a title: shown as returned by the backend.
                        dialog.dismiss();
                        Ui.showAlert(b.alert, Ui.Alert.ERROR, error.getMessage());
                    });
                });
    }
}
