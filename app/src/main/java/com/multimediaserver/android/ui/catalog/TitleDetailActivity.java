package com.multimediaserver.android.ui.catalog;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.Nullable;

import com.google.android.material.chip.Chip;
import com.google.android.material.tabs.TabLayout;
import com.multimediaserver.android.ui.common.SystemBars;
import com.multimediaserver.android.R;
import com.multimediaserver.android.api.Api;
import com.multimediaserver.android.api.ApiError;
import com.multimediaserver.android.api.Session;
import com.multimediaserver.android.databinding.ActivityTitleDetailBinding;
import com.multimediaserver.android.databinding.ItemEpisodeBinding;
import com.multimediaserver.android.model.MovieSeries;
import com.multimediaserver.android.model.Multimedia;
import com.multimediaserver.android.ui.MainActivity;
import com.multimediaserver.android.ui.admin.DeleteTitleDialog;
import com.multimediaserver.android.ui.admin.MovieSeriesFormActivity;
import com.multimediaserver.android.ui.common.SessionActivity;
import com.multimediaserver.android.ui.common.Ui;
import com.multimediaserver.android.ui.player.PlayerActivity;
import com.multimediaserver.android.util.Async;
import com.multimediaserver.android.util.Format;
import com.multimediaserver.android.util.GenreCache;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Title detail (TitleDetailPage.jsx): synopsis, genres, episodes by season and the Play button. */
public class TitleDetailActivity extends SessionActivity {
    private static final String EXTRA_ID = "id";

    private ActivityTitleDetailBinding b;
    private long titleId;
    private MovieSeries title;
    private List<Multimedia> files = new ArrayList<>();
    private TreeMap<Integer, List<Multimedia>> seasons = new TreeMap<>();
    private Integer selectedSeason;

    public static Intent intent(Context context, long id) {
        return new Intent(context, TitleDetailActivity.class).putExtra(EXTRA_ID, id);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!hasSession) return;
        b = ActivityTitleDetailBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        SystemBars.fit(this);
        titleId = getIntent().getLongExtra(EXTRA_ID, 0);
        if (savedInstanceState != null && savedInstanceState.containsKey("season")) {
            selectedSeason = savedInstanceState.getInt("season");
        }

        b.toolbar.setNavigationOnClickListener(v -> finish());
        b.refresh.setColorSchemeResources(R.color.accent);
        b.refresh.setProgressBackgroundColorSchemeResource(R.color.surface_2);
        b.refresh.setOnRefreshListener(this::load);

        b.seasons.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                selectedSeason = (Integer) tab.getTag();
                renderEpisodes();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });

        Ui.loading(b.state, null);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Coming back from the form or the player: refresh (the title or its files may have changed).
        load();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        if (selectedSeason != null) out.putInt("season", selectedSeason);
    }

    private void load() {
        Async.run(this, () -> Api.MoviesSeries.get(titleId), loaded -> {
            title = loaded;
            b.refresh.setRefreshing(false);
            renderTitle();
            loadFiles();
        }, error -> {
            b.refresh.setRefreshing(false);
            b.content.setVisibility(View.GONE);
            Ui.error(b.state, error, this::load);
        });
    }

    private void loadFiles() {
        if (files.isEmpty()) Ui.loading(b.episodesState, "Cargando episodios…");
        Async.run(this, () -> Api.Media.listByTitle(titleId), list -> {
            files = list;
            seasons = Format.groupBySeason(list);
            renderTitle();
            renderSeasons();
        }, this::filesError);
    }

    private void filesError(ApiError error) {
        b.episodes.setVisibility(View.GONE);
        Ui.error(b.episodesState, error, this::loadFiles);
    }

    private void renderTitle() {
        Ui.hide(b.state);
        b.content.setVisibility(View.VISIBLE);
        boolean isSeries = title.isSeries();
        boolean isAdmin = Session.isAdmin();
        Multimedia first = files.isEmpty() ? null : files.get(0);

        b.poster.bind(title);
        b.typeBadge.setText(Format.typeLabel(title.type));
        List<String> meta = new ArrayList<>();
        if (title.releaseYear != null) meta.add(String.valueOf(title.releaseYear));
        if (title.classification != null && !title.classification.isEmpty()) meta.add(title.classification);
        if (isSeries && !seasons.isEmpty()) meta.add(Format.pluralize(seasons.size(), "temporada"));
        if (!isSeries && first != null && first.durationSeconds != null) meta.add(Format.duration(first.durationSeconds));
        b.meta.setText(String.join(" · ", meta));
        b.name.setText(title.name);
        b.synopsis.setText(title.synopsis);
        b.synopsis.setVisibility(title.synopsis == null || title.synopsis.isEmpty() ? View.GONE : View.VISIBLE);

        GenreCache.load(this, (genres, byId) -> renderTags(byId));

        if (first == null) {
            b.play.setText("Sin vídeo disponible");
            b.play.setEnabled(false);
        } else {
            b.play.setEnabled(true);
            b.play.setText(isSeries ? "Reproducir T" + first.season + " · E" + first.episodeNumber : "Reproducir");
            b.play.setOnClickListener(v -> startActivity(PlayerActivity.intent(this, first.id)));
        }

        b.adminActions.setVisibility(isAdmin ? View.VISIBLE : View.GONE);
        if (isAdmin) {
            b.edit.setOnClickListener(v -> startActivity(MovieSeriesFormActivity.intent(this, title.id)));
            b.upload.setText(isSeries ? "Añadir episodio" : first != null ? "Reemplazar vídeo" : "Subir vídeo");
            b.upload.setOnClickListener(v -> openMultimedia(null));
            b.delete.setOnClickListener(v -> DeleteTitleDialog.show(this, this, title, () -> {
                startActivity(MainActivity.intent(this, MainActivity.CATALOG, null, null));
                finish();
            }));
        }
        b.episodesSection.setVisibility(isSeries ? View.VISIBLE : View.GONE);
    }

    private void renderTags(Map<Long, String> byId) {
        b.tags.removeAllViews();
        for (Long id : title.genres()) {
            String name = byId.get(id);
            if (name == null) continue;
            Chip chip = new Chip(this);
            chip.setText(name);
            chip.setClickable(false);
            chip.setCheckable(false);
            chip.setChipMinHeight(Ui.dp(this, 32));
            b.tags.addView(chip);
        }
        b.tags.setVisibility(b.tags.getChildCount() > 0 ? View.VISIBLE : View.GONE);
    }

    private void renderSeasons() {
        if (!title.isSeries()) return;
        Integer active = selectedSeason != null && seasons.containsKey(selectedSeason)
                ? selectedSeason : (seasons.isEmpty() ? null : seasons.firstKey());
        selectedSeason = active;

        b.seasons.removeAllTabs();
        b.seasons.setVisibility(seasons.size() > 1 ? View.VISIBLE : View.GONE);
        for (Integer season : seasons.keySet()) {
            TabLayout.Tab tab = b.seasons.newTab().setText(Format.seasonLabel(season)).setTag(season);
            b.seasons.addTab(tab, season.equals(active));
        }
        renderEpisodes();
    }

    private void renderEpisodes() {
        List<Multimedia> episodes = selectedSeason != null ? seasons.get(selectedSeason) : null;
        b.episodes.removeAllViews();
        if (episodes == null || episodes.isEmpty()) {
            b.episodes.setVisibility(View.GONE);
            Ui.empty(b.episodesState, "Sin episodios", "Esta serie todavía no tiene episodios.", null, null);
            return;
        }
        Ui.hide(b.episodesState);
        b.episodes.setVisibility(View.VISIBLE);
        boolean isAdmin = Session.isAdmin();
        LayoutInflater inflater = getLayoutInflater();
        for (Multimedia ep : episodes) {
            ItemEpisodeBinding row = ItemEpisodeBinding.inflate(inflater, b.episodes, false);
            String label = Format.episodeTitle(ep);
            row.code.setText("E" + ep.episodeNumber);
            row.title.setText(label);
            row.duration.setText(Format.duration(ep.durationSeconds));
            row.getRoot().setContentDescription("Reproducir episodio " + ep.episodeNumber + ": " + label);
            row.getRoot().setOnClickListener(v -> startActivity(PlayerActivity.intent(this, ep.id)));
            if (isAdmin) {
                row.editEpisode.setVisibility(View.VISIBLE);
                row.deleteEpisode.setVisibility(View.VISIBLE);
                row.editEpisode.setContentDescription("Editar episodio");
                row.deleteEpisode.setContentDescription("Eliminar episodio");
                row.editEpisode.setOnClickListener(v -> openMultimedia(ep.id));
                row.deleteEpisode.setOnClickListener(v -> confirmDeleteEpisode(ep));
            }
            b.episodes.addView(row.getRoot());
        }
    }

    private void openMultimedia(@Nullable Long editId) {
        startActivity(MainActivity.intent(this, MainActivity.MULTIMEDIA, title.id, editId));
    }

    private void confirmDeleteEpisode(Multimedia ep) {
        Ui.confirm(this, "¿Eliminar «" + Format.episodeTitle(ep) + "»?",
                "Se quitará de la temporada " + ep.season + ". El archivo se conserva en el servidor.",
                "Eliminar", dialog -> {
                    Ui.setBusy(dialog, true, "Eliminar");
                    Async.run(this, () -> {
                        Api.Media.remove(ep.id);
                        return true;
                    }, ok -> {
                        dialog.dismiss();
                        loadFiles();
                    }, error -> {
                        Ui.setBusy(dialog, false, "Eliminar");
                        dialog.setMessage(error.getMessage());
                    });
                });
    }
}
