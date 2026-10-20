package com.multimediaserver.android.ui.admin;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;

import com.google.android.material.chip.Chip;
import com.google.android.material.textfield.TextInputLayout;
import com.multimediaserver.android.ui.common.SystemBars;
import com.multimediaserver.android.R;
import com.multimediaserver.android.api.Api;
import com.multimediaserver.android.api.ApiError;
import com.multimediaserver.android.api.MultipartForm;
import com.multimediaserver.android.api.UriRequestBody;
import com.multimediaserver.android.databinding.ActivityMovieSeriesFormBinding;
import com.multimediaserver.android.model.Genre;
import com.multimediaserver.android.model.MovieSeries;
import com.multimediaserver.android.ui.MainActivity;
import com.multimediaserver.android.ui.catalog.TitleDetailActivity;
import com.multimediaserver.android.ui.common.SessionActivity;
import com.multimediaserver.android.ui.common.Ui;
import com.multimediaserver.android.util.Async;
import com.multimediaserver.android.util.PickedFile;
import com.multimediaserver.android.util.PosterLoader;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Create or edit a movie/series with its poster (MovieSeriesFormPage.jsx). */
public class MovieSeriesFormActivity extends SessionActivity {
    private static final String EXTRA_ID = "id";
    private static final List<String> POSTER_TYPES = Arrays.asList("image/jpeg", "image/png", "image/webp");
    private static final long POSTER_MAX_BYTES = 5L * 1024 * 1024;

    private ActivityMovieSeriesFormBinding b;
    private Long id;
    private MovieSeries original;
    private boolean typeLocked;
    private final Set<Long> selectedGenres = new LinkedHashSet<>();
    private PickedFile poster;
    private boolean saving;

    private final ActivityResultLauncher<String[]> pickPoster =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::onPosterPicked);

    public static Intent intent(Context context, @Nullable Long id) {
        Intent intent = new Intent(context, MovieSeriesFormActivity.class);
        if (id != null) intent.putExtra(EXTRA_ID, (long) id);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!hasSession) return;
        if (!com.multimediaserver.android.api.Session.isAdmin()) {
            finish();
            return;
        }
        b = ActivityMovieSeriesFormBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        SystemBars.fit(this);
        id = getIntent().hasExtra(EXTRA_ID) ? getIntent().getLongExtra(EXTRA_ID, 0) : null;

        b.toolbar.setTitle(id != null ? "Editar" : "Nueva película o serie");
        b.toolbar.setNavigationOnClickListener(v -> finish());
        b.cancel.setOnClickListener(v -> finish());
        b.save.setText(id != null ? "Guardar cambios" : "Crear");
        b.save.setOnClickListener(v -> save());
        b.posterPick.setOnClickListener(v -> pickPoster.launch(POSTER_TYPES.toArray(new String[0])));
        b.typeMovie.setChecked(true);
        b.typeGroup.setOnCheckedChangeListener((group, checkedId) -> b.typeError.setVisibility(View.GONE));
        for (TextInputLayout layout : new TextInputLayout[]{b.nameLayout, b.synopsisLayout, b.yearLayout, b.classificationLayout}) {
            Ui.clearErrorOnEdit(layout, null);
        }
        b.poster.bind(0, "Sin título", false);
        b.posterTitle.setText("Subir póster");

        if (id != null) loadTitle();
        else loadGenres();
    }

    private void loadTitle() {
        b.form.setVisibility(View.GONE);
        Ui.loading(b.state, null);
        Async.run(this, () -> {
            MovieSeries title = Api.MoviesSeries.get(id);
            int fileCount = Api.Media.listByTitle(id).size();
            return new Object[]{title, fileCount};
        }, result -> {
            original = (MovieSeries) result[0];
            typeLocked = (Integer) result[1] > 0;
            Ui.hide(b.state);
            b.form.setVisibility(View.VISIBLE);
            fill();
            loadGenres();
        }, error -> Ui.error(b.state, error, this::loadTitle));
    }

    private void fill() {
        b.toolbar.setTitle("Editar «" + original.name + "»");
        Ui.setText(b.nameLayout, original.name);
        Ui.setText(b.synopsisLayout, original.synopsis);
        Ui.setText(b.yearLayout, original.releaseYear != null ? String.valueOf(original.releaseYear) : "");
        Ui.setText(b.classificationLayout, original.classification);
        boolean series = original.isSeries();
        b.typeMovie.setChecked(!series);
        b.typeSerie.setChecked(series);
        if (typeLocked) {
            b.typeMovie.setEnabled(!series);
            b.typeSerie.setEnabled(series);
            b.typeHint.setText("No se puede cambiar el tipo mientras tenga " + (series ? "episodios" : "vídeo") + ".");
            b.typeHint.setVisibility(View.VISIBLE);
        }
        selectedGenres.clear();
        selectedGenres.addAll(original.genres());
        b.poster.bind(original);
        b.posterTitle.setText(original.posterPath != null ? "Reemplazar póster" : "Subir póster");
    }

    private void loadGenres() {
        // Always fresh: the admin may have just created genres.
        Async.run(this, Api.Genres::list, this::renderGenres, error -> {
            b.genresHint.setText(error.getMessage());
            b.genresHint.setVisibility(View.VISIBLE);
        });
    }

    private void renderGenres(List<Genre> genres) {
        b.genres.removeAllViews();
        if (genres.isEmpty()) {
            b.genresHint.setText("No hay géneros. Crea alguno en Administración › Géneros antes de continuar.");
            b.genresHint.setOnClickListener(v -> {
                startActivity(MainActivity.intent(this, MainActivity.GENRES, null, null));
                finish();
            });
            b.genresHint.setVisibility(View.VISIBLE);
            return;
        }
        b.genresHint.setVisibility(View.GONE);
        for (Genre g : genres) {
            Chip chip = new Chip(this);
            chip.setText(g.genre);
            chip.setCheckable(true);
            chip.setCheckedIconVisible(true);
            chip.setChecked(selectedGenres.contains(g.id));
            chip.setOnCheckedChangeListener((button, checked) -> {
                if (checked) selectedGenres.add(g.id);
                else selectedGenres.remove(g.id);
                b.genresError.setVisibility(View.GONE);
            });
            b.genres.addView(chip);
        }
    }

    private void onPosterPicked(@Nullable Uri uri) {
        if (uri == null) return;
        PickedFile picked = PickedFile.from(this, uri);
        poster = picked;
        b.posterError.setVisibility(View.GONE);
        b.posterTitle.setText(picked.name);
        // Local preview of the picked poster.
        Async.run(this, () -> PosterLoader.decode(picked.readBytes(this)), bitmap -> {
            if (bitmap != null) b.poster.showPreview(Ui.text(b.nameLayout), bitmap);
        }, error -> {
        });
    }

    private boolean validate() {
        boolean ok = true;
        String name = Ui.text(b.nameLayout).trim();
        if (name.isEmpty()) {
            Ui.setError(b.nameLayout, "El nombre es obligatorio.");
            ok = false;
        } else if (name.length() > 100) {
            Ui.setError(b.nameLayout, "El nombre admite como máximo 100 caracteres.");
            ok = false;
        }
        if (Ui.text(b.synopsisLayout).trim().isEmpty()) {
            Ui.setError(b.synopsisLayout, "La sinopsis es obligatoria.");
            ok = false;
        }
        Integer year = parseYear();
        if (year == null || year < 1901 || year > 2155) {
            Ui.setError(b.yearLayout, "El año de estreno debe estar entre 1901 y 2155");
            ok = false;
        }
        if (Ui.text(b.classificationLayout).trim().length() > 10) {
            Ui.setError(b.classificationLayout, "La clasificación admite como máximo 10 caracteres.");
            ok = false;
        }
        if (selectedGenres.isEmpty()) {
            showError(b.genresError, "Elige al menos un género.");
            ok = false;
        }
        if (poster != null && (poster.mimeType == null || !POSTER_TYPES.contains(poster.mimeType))) {
            showError(b.posterError, "El póster debe ser JPG, PNG o WEBP.");
            ok = false;
        } else if (poster != null && poster.size > POSTER_MAX_BYTES) {
            showError(b.posterError, "El póster no puede superar los 5MB.");
            ok = false;
        }
        return ok;
    }

    @Nullable
    private Integer parseYear() {
        try {
            return Integer.parseInt(Ui.text(b.yearLayout).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static void showError(android.widget.TextView view, @Nullable String message) {
        view.setText(message);
        view.setVisibility(message != null ? View.VISIBLE : View.GONE);
    }

    private void save() {
        if (saving) return;
        Ui.hide(b.alert);
        Ui.hideKeyboard(b.save);
        if (!validate()) return;

        MultipartForm form = new MultipartForm()
                .add("name", Ui.text(b.nameLayout).trim())
                .add("synopsis", Ui.text(b.synopsisLayout).trim())
                .add("releaseYear", parseYear())
                .add("classification", Ui.text(b.classificationLayout).trim())
                .add("genreIds", new ArrayList<>(selectedGenres));
        if (!typeLocked) form.add("type", b.typeSerie.isChecked() ? MovieSeries.SERIE : MovieSeries.MOVIE);
        if (poster != null) {
            form.file("poster", poster.name,
                    new UriRequestBody(getContentResolver(), poster.uri, poster.mimeType, poster.size, null));
        }
        if (id != null) form.add("id", id);

        setSaving(true);
        Async.run(this, () -> {
            if (id != null) {
                Api.MoviesSeries.update(form.build());
                return id;
            }
            MovieSeries created = Api.MoviesSeries.create(form.build());
            return created != null ? created.id : null;
        }, targetId -> {
            if (poster != null && targetId != null) PosterLoader.invalidate(targetId);
            if (targetId != null) startActivity(TitleDetailActivity.intent(this, targetId));
            finish();
        }, error -> {
            setSaving(false);
            showFieldErrors(error);
            Ui.showAlert(b.alert, Ui.Alert.ERROR, error.getMessage());
        });
    }

    private void showFieldErrors(ApiError error) {
        if (error.field("name") != null) Ui.setError(b.nameLayout, error.field("name"));
        if (error.field("synopsis") != null) Ui.setError(b.synopsisLayout, error.field("synopsis"));
        if (error.field("releaseYear") != null) Ui.setError(b.yearLayout, error.field("releaseYear"));
        if (error.field("classification") != null) Ui.setError(b.classificationLayout, error.field("classification"));
        if (error.field("type") != null) showError(b.typeError, error.field("type"));
        String genres = error.field("genreIds") != null ? error.field("genreIds") : error.field("genres");
        if (genres != null) showError(b.genresError, genres);
        if (error.field("poster") != null) showError(b.posterError, error.field("poster"));
    }

    private void setSaving(boolean value) {
        saving = value;
        b.save.setEnabled(!value);
        b.save.setText(value ? "Guardando…" : id != null ? "Guardar cambios" : "Crear");
    }
}
