package com.multimediaserver.android.ui.admin;

import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.multimediaserver.android.R;
import com.multimediaserver.android.api.Api;
import com.multimediaserver.android.api.ApiError;
import com.multimediaserver.android.api.MultipartForm;
import com.multimediaserver.android.api.UriRequestBody;
import com.multimediaserver.android.databinding.FragmentMultimediaBinding;
import com.multimediaserver.android.databinding.ItemEpisodeBinding;
import com.multimediaserver.android.model.MovieSeries;
import com.multimediaserver.android.model.Multimedia;
import com.multimediaserver.android.ui.MainActivity;
import com.multimediaserver.android.ui.common.Dropdown;
import com.multimediaserver.android.ui.common.Ui;
import com.multimediaserver.android.ui.player.PlayerActivity;
import com.multimediaserver.android.util.Async;
import com.multimediaserver.android.util.Format;
import com.multimediaserver.android.util.PickedFile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import okhttp3.Call;

/**
 * Episodes and video (MultimediaAdminPage.jsx + VideoUploadForm.jsx): pick a title, see its files
 * grouped by season, and upload or replace videos with progress.
 * - SERIE: season + episode + optional title; video required when creating.
 * - MOVIE: just the video; once it exists the form replaces it.
 */
public class MultimediaFragment extends Fragment {
    private static final List<String> VIDEO_TYPES = Arrays.asList("video/mp4", "video/webm");
    private static final long VIDEO_MAX_BYTES = 10L * 1024 * 1024 * 1024;

    private FragmentMultimediaBinding b;
    private List<MovieSeries> titles = new ArrayList<>();
    private Long titleId;
    private Long editId;
    private MovieSeries title;
    private List<Multimedia> files = new ArrayList<>();
    private Multimedia editing;
    private PickedFile video;
    private Call upload;

    private final ActivityResultLauncher<String[]> pickVideo =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::onVideoPicked);

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        b = FragmentMultimediaBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        Bundle args = state != null ? state : getArguments();
        if (args != null) {
            if (args.containsKey(MainActivity.EXTRA_TITLE_ID)) titleId = args.getLong(MainActivity.EXTRA_TITLE_ID);
            if (args.containsKey(MainActivity.EXTRA_EDIT_ID)) editId = args.getLong(MainActivity.EXTRA_EDIT_ID);
        }
        b.dropzone.setOnClickListener(v -> {
            if (upload == null) pickVideo.launch(VIDEO_TYPES.toArray(new String[0]));
        });
        b.submit.setOnClickListener(v -> submit());
        Ui.clearErrorOnEdit(b.seasonLayout, null);
        Ui.clearErrorOnEdit(b.episodeLayout, null);
        Ui.clearErrorOnEdit(b.episodeTitleLayout, null);
        b.titlePicker.setOnItemClickListener((parent, v, position, id) -> {
            if (upload != null) return;
            Ui.hide(b.notice);
            titleId = titles.get(position).id;
            editId = null;
            showTitle();
        });
        loadTitles();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        if (titleId != null) out.putLong(MainActivity.EXTRA_TITLE_ID, titleId);
        if (editId != null) out.putLong(MainActivity.EXTRA_EDIT_ID, editId);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Leaving the screen aborts a running upload (like unmounting the form).
        if (upload != null) upload.cancel();
        upload = null;
        b = null;
    }

    // ------------------------------------------------------------------ Titles and files

    private void loadTitles() {
        Ui.loading(b.state, null);
        b.titleLayout.setEnabled(false);
        Async.run(getViewLifecycleOwner(), () -> Api.MoviesSeries.list(null, null, null, 0, 500), list -> {
            titles = list;
            b.titleLayout.setEnabled(true);
            List<String> labels = new ArrayList<>();
            for (MovieSeries t : list) labels.add(t.name + " (" + (t.isSeries() ? "serie" : "película") + ")");
            Dropdown.setOptions(b.titlePicker, labels);
            showTitle();
        }, error -> Ui.error(b.state, error, this::loadTitles));
    }

    private void showTitle() {
        title = null;
        for (MovieSeries t : titles) if (titleId != null && t.id == titleId) title = t;
        b.seasonsList.removeAllViews();
        b.uploadForm.setVisibility(View.GONE);

        if (titleId == null) {
            b.titlePicker.setText("", false);
            if (titles.isEmpty()) {
                Ui.empty(b.state, "No hay títulos", "Crea una película o serie antes de subir vídeos.",
                        "Crear película o serie", () -> startActivity(MovieSeriesFormActivity.intent(requireContext(), null)));
            } else {
                Ui.empty(b.state, "Elige un título", "Selecciona una película o serie para ver y subir sus vídeos.", null, null);
            }
            return;
        }
        if (title == null) {
            Ui.hide(b.state);
            Ui.showAlert(b.notice, Ui.Alert.ERROR, "El título seleccionado no existe.");
            return;
        }
        b.titlePicker.setText(title.name + " (" + (title.isSeries() ? "serie" : "película") + ")", false);
        loadFiles();
    }

    private void loadFiles() {
        if (b == null || title == null) return;
        final long id = title.id;
        Ui.loading(b.state, "Cargando archivos…");
        b.seasonsList.removeAllViews();
        Async.run(getViewLifecycleOwner(), () -> Api.Media.listByTitle(id), list -> {
            if (title == null || title.id != id) return;
            files = list;
            renderFiles();
            resetForm();
        }, error -> {
            b.uploadForm.setVisibility(View.GONE);
            Ui.error(b.state, error, this::loadFiles);
        });
    }

    private void renderFiles() {
        b.seasonsList.removeAllViews();
        boolean isSeries = title.isSeries();
        if (files.isEmpty()) {
            Ui.empty(b.state, isSeries ? "Sin episodios" : "Sin vídeo",
                    isSeries ? "Sube el primer episodio con el formulario." : "Sube el vídeo de la película con el formulario.",
                    null, null);
            return;
        }
        Ui.hide(b.state);
        LayoutInflater inflater = getLayoutInflater();
        for (Map.Entry<Integer, List<Multimedia>> season : Format.groupBySeason(files).entrySet()) {
            TextView header = (TextView) inflater.inflate(R.layout.item_season_header, b.seasonsList, false);
            header.setText(isSeries ? Format.seasonLabel(season.getKey()) : "Vídeo");
            b.seasonsList.addView(header);

            ViewGroup card = (ViewGroup) inflater.inflate(R.layout.view_card_list, b.seasonsList, false);
            for (Multimedia ep : season.getValue()) {
                ItemEpisodeBinding row = ItemEpisodeBinding.inflate(inflater, card, false);
                String label = isSeries ? Format.episodeTitle(ep) : title.name;
                row.code.setText(isSeries ? "E" + ep.episodeNumber : "");
                row.code.setVisibility(isSeries ? View.VISIBLE : View.GONE);
                row.title.setText(label);
                row.duration.setText(Format.duration(ep.durationSeconds));
                if (editId != null && ep.id == editId) row.getRoot().setBackgroundResource(R.drawable.bg_row_selected);
                row.getRoot().setContentDescription("Reproducir " + label);
                row.getRoot().setOnClickListener(v -> startActivity(PlayerActivity.intent(requireContext(), ep.id)));
                if (isSeries) {
                    row.editEpisode.setVisibility(View.VISIBLE);
                    row.editEpisode.setContentDescription("Editar " + label);
                    row.editEpisode.setOnClickListener(v -> setEditing(ep.id));
                }
                row.deleteEpisode.setVisibility(View.VISIBLE);
                row.deleteEpisode.setContentDescription("Eliminar " + label);
                row.deleteEpisode.setOnClickListener(v -> confirmDelete(ep, label));
                card.addView(row.getRoot());
            }
            b.seasonsList.addView(card);
        }
    }

    private void setEditing(@Nullable Long id) {
        if (upload != null) return;
        editId = id;
        renderFiles();
        resetForm();
        if (id != null) b.scroll.post(() -> b.scroll.smoothScrollTo(0, b.uploadForm.getTop()));
    }

    private void confirmDelete(Multimedia ep, String label) {
        Ui.confirm(requireContext(), "¿Eliminar «" + label + "»?",
                "Dejará de estar disponible para reproducir. El archivo se conserva en el servidor.",
                "Eliminar", dialog -> {
                    Ui.setBusy(dialog, true, "Eliminar");
                    Async.run(getViewLifecycleOwner(), () -> {
                        Api.Media.remove(ep.id);
                        return true;
                    }, ok -> {
                        dialog.dismiss();
                        if (editId != null && ep.id == editId) editId = null;
                        Ui.showAlert(b.notice, Ui.Alert.SUCCESS, "Registro eliminado correctamente.");
                        loadFiles();
                    }, error -> {
                        Ui.setBusy(dialog, false, "Eliminar");
                        dialog.setMessage(error.getMessage());
                    });
                });
    }

    // ------------------------------------------------------------------ Upload form

    /** Resets the form for the current title / episode (the web remounts it with a key). */
    private void resetForm() {
        boolean isSeries = title.isSeries();
        editing = null;
        if (isSeries) {
            for (Multimedia f : files) if (editId != null && f.id == editId) editing = f;
        } else if (!files.isEmpty()) {
            editing = files.get(0);
        }
        video = null;
        b.uploadForm.setVisibility(View.VISIBLE);
        b.episodeFields.setVisibility(isSeries ? View.VISIBLE : View.GONE);
        Ui.setError(b.seasonLayout, null);
        Ui.setError(b.episodeLayout, null);
        Ui.setError(b.episodeTitleLayout, null);
        b.videoError.setVisibility(View.GONE);
        Ui.hide(b.formError);
        b.progressBox.setVisibility(View.GONE);

        if (editing != null) {
            Ui.setText(b.seasonLayout, editing.season != null ? String.valueOf(editing.season) : "");
            Ui.setText(b.episodeLayout, editing.episodeNumber != null ? String.valueOf(editing.episodeNumber) : "");
            Ui.setText(b.episodeTitleLayout, editing.title);
        } else {
            // Suggest the next episode after the last one.
            int season = 1;
            int episode = 1;
            if (!files.isEmpty()) {
                Multimedia last = files.get(files.size() - 1);
                season = last.season != null ? last.season : 1;
                episode = (last.episodeNumber != null ? last.episodeNumber : 0) + 1;
            }
            Ui.setText(b.seasonLayout, String.valueOf(season));
            Ui.setText(b.episodeLayout, String.valueOf(episode));
            Ui.setText(b.episodeTitleLayout, "");
        }

        String heading;
        if (!isSeries) heading = editing != null ? "Reemplazar vídeo" : "Subir vídeo";
        else heading = editing != null ? "Editar T" + editing.season + " · E" + editing.episodeNumber : "Nuevo episodio";
        b.uploadHeading.setText(heading);
        setUploading(false);
    }

    private void onVideoPicked(@Nullable Uri uri) {
        if (uri == null || b == null) return;
        video = PickedFile.from(requireContext(), uri);
        b.videoError.setVisibility(View.GONE);
        updateDropzone();
    }

    private void updateDropzone() {
        if (video != null) b.dropzoneTitle.setText(video.name);
        else if (editing != null) b.dropzoneTitle.setText("Elige un vídeo nuevo para reemplazarlo (opcional)");
        else b.dropzoneTitle.setText("Toca para elegir el vídeo");
    }

    private boolean validate() {
        boolean ok = true;
        if (title.isSeries()) {
            Integer season = parse(Ui.text(b.seasonLayout));
            Integer episode = parse(Ui.text(b.episodeLayout));
            if (season == null || season < 0) {
                Ui.setError(b.seasonLayout, "La temporada debe ser 0 o mayor.");
                ok = false;
            }
            if (episode == null || episode < 1) {
                Ui.setError(b.episodeLayout, "El episodio debe ser 1 o mayor.");
                ok = false;
            }
            if (Ui.text(b.episodeTitleLayout).length() > 100) {
                Ui.setError(b.episodeTitleLayout, "El título admite como máximo 100 caracteres.");
                ok = false;
            }
        }
        String videoError = null;
        if (editing == null && video == null) videoError = "Elige un archivo de vídeo.";
        else if (video != null && (video.mimeType == null || !VIDEO_TYPES.contains(video.mimeType)))
            videoError = "El vídeo debe ser MP4 o WEBM.";
        else if (video != null && video.size > VIDEO_MAX_BYTES) videoError = "El vídeo no puede superar los 10GB.";
        if (videoError != null) {
            b.videoError.setText(videoError);
            b.videoError.setVisibility(View.VISIBLE);
            ok = false;
        }
        return ok;
    }

    @Nullable
    private static Integer parse(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void submit() {
        if (upload != null) {
            upload.cancel();
            return;
        }
        Ui.hide(b.formError);
        Ui.hideKeyboard(b.submit);
        if (!validate()) return;

        boolean isSeries = title.isSeries();
        MultipartForm form = new MultipartForm();
        if (editing != null) form.add("id", editing.id);
        else form.add("movieSeriesId", title.id);
        if (isSeries) {
            form.add("season", parse(Ui.text(b.seasonLayout)))
                    .add("episodeNumber", parse(Ui.text(b.episodeLayout)))
                    .add("title", Ui.text(b.episodeTitleLayout).trim());
        }
        final PickedFile file = video;
        if (file != null) {
            form.file("video", file.name, new UriRequestBody(requireContext().getContentResolver(), file.uri,
                    file.mimeType, file.size, percent -> Async.main(() -> showProgress(file, percent))));
        }
        final boolean isEdit = editing != null;
        final Call call;
        try {
            call = Api.Media.uploadCall(isEdit, form.build());
        } catch (ApiError e) {
            Ui.showAlert(b.formError, Ui.Alert.ERROR, e.getMessage());
            return;
        }
        upload = call;
        setUploading(true);
        showProgress(file, 0);

        Async.run(getViewLifecycleOwner(), () -> Api.Media.executeUpload(call), ok -> {
            upload = null;
            String message = isEdit ? "Cambios guardados." : isSeries ? "Episodio añadido." : "Vídeo subido.";
            Ui.showAlert(b.notice, Ui.Alert.SUCCESS, message);
            editId = null;
            b.scroll.smoothScrollTo(0, 0);
            loadFiles();
        }, error -> {
            upload = null;
            setUploading(false);
            b.progressBox.setVisibility(View.GONE);
            Ui.setError(b.seasonLayout, error.field("season"));
            Ui.setError(b.episodeLayout, error.field("episodeNumber"));
            Ui.setError(b.episodeTitleLayout, error.field("title"));
            if (error.field("video") != null) {
                b.videoError.setText(error.field("video"));
                b.videoError.setVisibility(View.VISIBLE);
            }
            Ui.showAlert(b.formError, Ui.Alert.ERROR, error.getMessage());
        });
    }

    private void showProgress(@Nullable PickedFile file, int percent) {
        if (b == null || upload == null) return;
        b.progressBox.setVisibility(View.VISIBLE);
        b.progressName.setText(file != null ? file.name : "Guardando cambios");
        boolean sending = file != null && percent < 100;
        b.progressPct.setText(file != null ? percent + "%" : "");
        if (sending) {
            b.progress.setIndeterminate(false);
            b.progress.setProgressCompat(percent, true);
            b.progressHint.setText("La duración se calcula en el servidor al terminar la subida.");
        } else {
            if (!b.progress.isIndeterminate()) {
                b.progress.setVisibility(View.INVISIBLE);
                b.progress.setIndeterminate(true);
                b.progress.setVisibility(View.VISIBLE);
            }
            b.progressHint.setText("Procesando en el servidor…");
        }
    }

    private void setUploading(boolean uploading) {
        boolean isSeries = title != null && title.isSeries();
        b.seasonLayout.setEnabled(!uploading);
        b.episodeLayout.setEnabled(!uploading);
        b.episodeTitleLayout.setEnabled(!uploading);
        b.dropzone.setEnabled(!uploading);
        b.titleLayout.setEnabled(!uploading);
        updateDropzone();

        // While uploading, the main button turns into "Cancelar subida".
        if (uploading) {
            b.cancel.setVisibility(View.GONE);
            b.submit.setText("Cancelar subida");
        } else {
            b.submit.setText(editing != null ? "Guardar cambios" : isSeries ? "Subir episodio" : "Subir vídeo");
            b.cancel.setVisibility(editing != null && isSeries ? View.VISIBLE : View.GONE);
            b.cancel.setText("Cancelar");
            b.cancel.setOnClickListener(v -> setEditing(null));
        }
    }
}
