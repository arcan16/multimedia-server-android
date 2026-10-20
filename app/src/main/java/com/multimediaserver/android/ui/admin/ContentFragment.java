package com.multimediaserver.android.ui.admin;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.multimediaserver.android.R;
import com.multimediaserver.android.api.Api;
import com.multimediaserver.android.databinding.FragmentContentBinding;
import com.multimediaserver.android.databinding.ItemContentRowBinding;
import com.multimediaserver.android.model.MovieSeries;
import com.multimediaserver.android.ui.MainActivity;
import com.multimediaserver.android.ui.common.Dropdown;
import com.multimediaserver.android.ui.common.Ui;
import com.multimediaserver.android.util.Async;
import com.multimediaserver.android.util.Format;
import com.multimediaserver.android.util.GenreCache;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Admin content list (ContentPage.jsx) with the cascade-delete confirmation. */
public class ContentFragment extends Fragment {
    private static final int PAGE_SIZE = 20;
    private static final String[] TYPE_LABELS = {"Todos", "Películas", "Series"};
    private static final String[] TYPE_VALUES = {null, MovieSeries.MOVIE, MovieSeries.SERIE};

    private FragmentContentBinding b;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable debouncedSearch = () -> {
        page = 0;
        load();
    };
    private String name = "";
    private String type;
    private int page;
    private int requestId;
    private boolean loadedOnce;
    private Map<Long, String> genresById = new HashMap<>();
    private List<MovieSeries> items = new ArrayList<>();

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        b = FragmentContentBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        b.newTitle.setOnClickListener(v -> startActivity(MovieSeriesFormActivity.intent(requireContext(), null)));

        b.search.addTextChangedListener(new Ui.SimpleWatcher(text -> {
            String trimmed = text.trim();
            if (trimmed.equals(name)) return;
            name = trimmed;
            handler.removeCallbacks(debouncedSearch);
            handler.postDelayed(debouncedSearch, 300);
        }));

        Dropdown.setOptions(b.type, Arrays.asList(TYPE_LABELS));
        b.type.setText(TYPE_LABELS[0], false);
        b.type.setOnItemClickListener((parent, v, position, id) -> {
            type = TYPE_VALUES[position];
            page = 0;
            load();
        });

        b.refresh.setColorSchemeResources(R.color.accent);
        b.refresh.setProgressBackgroundColorSchemeResource(R.color.surface_2);
        b.refresh.setOnRefreshListener(this::load);

        GenreCache.load(getViewLifecycleOwner(), (genres, byId) -> {
            genresById = byId;
            if (b != null && loadedOnce) render();
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        load();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        handler.removeCallbacks(debouncedSearch);
        b = null;
    }

    private void load() {
        if (b == null) return;
        final int current = ++requestId;
        if (!loadedOnce) {
            Ui.loading(b.state, null);
            b.rows.setVisibility(View.GONE);
        }
        final String qName = name.isEmpty() ? null : name;
        final String qType = type;
        final int qPage = page;
        Async.run(getViewLifecycleOwner(), () -> Api.MoviesSeries.list(qName, qType, null, qPage, PAGE_SIZE), list -> {
            if (current != requestId) return;
            loadedOnce = true;
            b.refresh.setRefreshing(false);
            items = list;
            render();
        }, error -> {
            if (current != requestId) return;
            b.refresh.setRefreshing(false);
            b.rows.setVisibility(View.GONE);
            Ui.error(b.state, error, this::load);
        });
    }

    private void render() {
        b.rows.removeAllViews();
        if (items.isEmpty()) {
            b.rows.setVisibility(View.GONE);
            boolean filtering = !name.isEmpty() || type != null || page > 0;
            Ui.empty(b.state, filtering ? "Sin resultados" : "Todavía no hay contenido", null,
                    filtering ? null : "Añadir la primera",
                    () -> startActivity(MovieSeriesFormActivity.intent(requireContext(), null)));
        } else {
            Ui.hide(b.state);
            b.rows.setVisibility(View.VISIBLE);
            for (int i = 0; i < items.size(); i++) addRow(items.get(i), i == items.size() - 1);
        }
        Ui.pagination(b.pagination, page, items.size() == PAGE_SIZE, target -> {
            page = Math.max(0, target);
            load();
        });
    }

    private void addRow(MovieSeries title, boolean last) {
        ItemContentRowBinding row = ItemContentRowBinding.inflate(getLayoutInflater(), b.rows, false);
        row.poster.setLabelVisible(false);
        row.poster.bind(title);
        row.name.setText(title.name);
        row.typeBadge.setText(Format.typeLabel(title.type));
        row.year.setText(title.releaseYear != null ? String.valueOf(title.releaseYear) : "");
        row.genres.setText(Format.orDash(Format.genreNames(title.genreIds, genresById)));
        row.divider.setVisibility(last ? View.GONE : View.VISIBLE);

        // One request per row: the list has no counts.
        row.files.setText("…");
        Async.run(getViewLifecycleOwner(), () -> Api.Media.listByTitle(title.id), files -> {
            if (files.isEmpty()) row.files.setText("Sin vídeo");
            else row.files.setText(title.isSeries() ? Format.pluralize(files.size(), "episodio")
                    : Format.pluralize(files.size(), "vídeo"));
        }, error -> row.files.setText("—"));

        row.edit.setContentDescription("Editar " + title.name);
        row.filesButton.setContentDescription("Archivos de " + title.name);
        row.delete.setContentDescription("Eliminar " + title.name);
        row.edit.setOnClickListener(v -> startActivity(MovieSeriesFormActivity.intent(requireContext(), title.id)));
        row.filesButton.setOnClickListener(v -> {
            Bundle args = new Bundle();
            args.putLong(MainActivity.EXTRA_TITLE_ID, title.id);
            ((MainActivity) requireActivity()).navigate(MainActivity.MULTIMEDIA, args);
        });
        row.delete.setOnClickListener(v -> DeleteTitleDialog.show(requireContext(), getViewLifecycleOwner(), title, this::load));
        b.rows.addView(row.getRoot());
    }
}
