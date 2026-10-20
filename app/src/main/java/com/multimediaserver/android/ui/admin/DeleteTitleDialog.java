package com.multimediaserver.android.ui.admin;

import android.content.Context;
import android.text.Html;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.LifecycleOwner;

import com.multimediaserver.android.api.Api;
import com.multimediaserver.android.model.MovieSeries;
import com.multimediaserver.android.ui.common.Ui;
import com.multimediaserver.android.util.Async;
import com.multimediaserver.android.util.Format;
import com.multimediaserver.android.util.PosterLoader;

/** Cascade-delete confirmation: deleting a title also deletes its multimedia. */
public final class DeleteTitleDialog {
    private static final String TAIL = " Los archivos se conservan en el servidor y la acción queda registrada en la auditoría.";

    private DeleteTitleDialog() {
    }

    public static void show(Context context, LifecycleOwner owner, MovieSeries title, Runnable onDeleted) {
        AlertDialog dialog = Ui.confirm(context, "¿Eliminar «" + title.name + "»?",
                "Se eliminará también su contenido multimedia." + TAIL, "Eliminar", d -> {
                    Ui.setBusy(d, true, "Eliminar");
                    Async.run(owner, () -> {
                        Api.MoviesSeries.remove(title.id);
                        return true;
                    }, ok -> {
                        PosterLoader.invalidate(title.id);
                        d.dismiss();
                        onDeleted.run();
                    }, error -> {
                        Ui.setBusy(d, false, "Eliminar");
                        d.setMessage(error.getMessage());
                    });
                });

        // Tell exactly what goes with it once the file count is known.
        Async.run(owner, () -> Api.Media.listByTitle(title.id), files -> {
            String cascade;
            if (files.isEmpty()) cascade = "No tiene vídeos asociados.";
            else if (!title.isSeries()) cascade = "También se eliminará <b>su vídeo</b>.";
            else cascade = "También se eliminarán sus <b>" + Format.pluralize(files.size(), "episodio") + "</b>.";
            TextView message = dialog.findViewById(android.R.id.message);
            if (message != null && dialog.isShowing()) {
                message.setText(Html.fromHtml(cascade + TAIL, Html.FROM_HTML_MODE_LEGACY));
            }
        }, error -> {
        });
    }
}
