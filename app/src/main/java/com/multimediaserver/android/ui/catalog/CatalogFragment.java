package com.multimediaserver.android.ui.catalog;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;

import com.google.android.material.chip.Chip;
import com.multimediaserver.android.R;
import com.multimediaserver.android.api.Api;
import com.multimediaserver.android.api.Session;
import com.multimediaserver.android.databinding.FragmentCatalogBinding;
import com.multimediaserver.android.model.Genre;
import com.multimediaserver.android.model.MovieSeries;
import com.multimediaserver.android.ui.admin.MovieSeriesFormActivity;
import com.multimediaserver.android.ui.common.Ui;
import com.multimediaserver.android.util.Async;
import com.multimediaserver.android.util.GenreCache;

import java.util.List;

/** Catalog (CatalogPage.jsx): search by name, type and genre filters, poster grid, pagination. */
public class CatalogFragment extends Fragment {
    private static final int PAGE_SIZE = 20;
    private static final long DEBOUNCE_MS = 300;

    private FragmentCatalogBinding b;
    private TitleCardAdapter adapter;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable debouncedSearch = () -> {
        page = 0;
        load();
    };

    private String name = "";
    private String type = null;
    private Long genreId = null;
    private int page = 0;
    private int requestId = 0;
    private boolean loadedOnce;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        b = FragmentCatalogBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        if (state != null) {
            name = state.getString("name", "");
            type = state.getString("type");
            genreId = state.containsKey("genreId") ? state.getLong("genreId") : null;
            page = state.getInt("page", 0);
        }

        boolean isAdmin = Session.isAdmin();
        b.newTitle.setVisibility(isAdmin ? View.VISIBLE : View.GONE);
        b.newTitle.setOnClickListener(v -> startActivity(MovieSeriesFormActivity.intent(requireContext(), null)));

        adapter = new TitleCardAdapter(title -> startActivity(TitleDetailActivity.intent(requireContext(), title.id)));
        int widthDp = getResources().getConfiguration().screenWidthDp;
        b.grid.setLayoutManager(new GridLayoutManager(requireContext(), Math.max(2, widthDp / 170)));
        b.grid.setAdapter(adapter);

        b.search.setText(name);
        b.search.addTextChangedListener(new Ui.SimpleWatcher(text -> {
            String trimmed = text.trim();
            if (trimmed.equals(name)) return;
            name = trimmed;
            handler.removeCallbacks(debouncedSearch);
            handler.postDelayed(debouncedSearch, DEBOUNCE_MS);
        }));

        b.typeGroup.check(MovieSeries.MOVIE.equals(type) ? R.id.typeMovie
                : MovieSeries.SERIE.equals(type) ? R.id.typeSerie : R.id.typeAll);
        b.typeGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            type = checkedId == R.id.typeMovie ? MovieSeries.MOVIE : checkedId == R.id.typeSerie ? MovieSeries.SERIE : null;
            page = 0;
            load();
        });

        b.refresh.setColorSchemeResources(R.color.accent);
        b.refresh.setProgressBackgroundColorSchemeResource(R.color.surface_2);
        b.refresh.setOnRefreshListener(() -> {
            GenreCache.invalidate();
            loadGenres();
            load();
        });

        loadGenres();
    }

    @Override
    public void onResume() {
        super.onResume();
        // Reload when coming back from the detail or the form (a title may have changed).
        load();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        out.putString("name", name);
        out.putString("type", type);
        if (genreId != null) out.putLong("genreId", genreId);
        out.putInt("page", page);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        handler.removeCallbacks(debouncedSearch);
        b = null;
    }

    private void loadGenres() {
        GenreCache.load(getViewLifecycleOwner(), (genres, byId) -> {
            if (b == null) return;
            adapter.setGenres(byId);
            renderChips(genres);
        });
    }

    private void renderChips(List<Genre> genres) {
        b.chips.removeAllViews();
        b.chipsScroll.setVisibility(genres.isEmpty() ? View.GONE : View.VISIBLE);
        if (genres.isEmpty()) return;
        addChip("Todos", null);
        for (Genre g : genres) addChip(g.genre, g.id);
    }

    private void addChip(String label, @Nullable Long id) {
        Chip chip = new Chip(requireContext());
        chip.setText(label);
        chip.setCheckable(true);
        chip.setChecked(id == null ? genreId == null : id.equals(genreId));
        chip.setOnCheckedChangeListener((button, checked) -> {
            if (!checked) return;
            if (id == null ? genreId == null : id.equals(genreId)) return;
            genreId = id;
            page = 0;
            load();
        });
        b.chips.addView(chip);
    }

    private void load() {
        if (b == null) return;
        final int current = ++requestId;
        if (!loadedOnce) {
            Ui.loading(b.state, null);
            b.grid.setVisibility(View.GONE);
        }
        final String qName = name.isEmpty() ? null : name;
        final String qType = type;
        final Long qGenre = genreId;
        final int qPage = page;
        Async.run(getViewLifecycleOwner(), () -> Api.MoviesSeries.list(qName, qType, qGenre, qPage, PAGE_SIZE), items -> {
            if (current != requestId) return;
            loadedOnce = true;
            b.refresh.setRefreshing(false);
            render(items);
        }, error -> {
            if (current != requestId) return;
            b.refresh.setRefreshing(false);
            b.grid.setVisibility(View.GONE);
            Ui.pagination(b.pagination, page, false, this::goToPage);
            Ui.error(b.state, error, this::load);
        });
    }

    private void render(List<MovieSeries> items) {
        adapter.setItems(items);
        boolean filtering = qFiltering();
        if (items.isEmpty()) {
            b.grid.setVisibility(View.GONE);
            String message = filtering ? "Prueba con otro nombre, tipo o género."
                    : Session.isAdmin() ? "Añade la primera película o serie para empezar."
                    : "Todavía no hay películas ni series disponibles.";
            Ui.empty(b.state, filtering ? "Sin resultados" : "El catálogo está vacío", message, null, null);
        } else {
            Ui.hide(b.state);
            b.grid.setVisibility(View.VISIBLE);
        }
        Ui.pagination(b.pagination, page, items.size() == PAGE_SIZE, this::goToPage);
    }

    private boolean qFiltering() {
        return !name.isEmpty() || type != null || genreId != null || page > 0;
    }

    private void goToPage(int target) {
        page = Math.max(0, target);
        load();
    }
}
