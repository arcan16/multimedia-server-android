package com.multimediaserver.android.ui.player;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.MediaController;

import androidx.annotation.NonNull;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.multimediaserver.android.ui.common.SystemBars;
import com.multimediaserver.android.api.Api;
import com.multimediaserver.android.databinding.ActivityPlayerBinding;
import com.multimediaserver.android.databinding.ItemNextEpisodeBinding;
import com.multimediaserver.android.model.MovieSeries;
import com.multimediaserver.android.model.Multimedia;
import com.multimediaserver.android.ui.catalog.TitleDetailActivity;
import com.multimediaserver.android.ui.common.SessionActivity;
import com.multimediaserver.android.ui.common.Ui;
import com.multimediaserver.android.util.Async;
import com.multimediaserver.android.util.Format;

import java.util.ArrayList;
import java.util.List;

/**
 * Player (PlayerPage.jsx): the platform VideoView streaming /multimedia/{id}/video?token=, plus
 * the "Siguientes episodios" list. Landscape goes full screen; the next episode starts when one ends.
 */
public class PlayerActivity extends SessionActivity {
    private static final String EXTRA_ID = "multimediaId";
    private static final int NEXT_LIMIT = 6;

    private ActivityPlayerBinding b;
    private long multimediaId;
    private Multimedia media;
    private MovieSeries title;
    private final List<Multimedia> next = new ArrayList<>();
    private int resumePosition;

    public static Intent intent(Context context, long multimediaId) {
        return new Intent(context, PlayerActivity.class).putExtra(EXTRA_ID, multimediaId);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!hasSession) return;
        b = ActivityPlayerBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        SystemBars.fit(this);
        multimediaId = getIntent().getLongExtra(EXTRA_ID, 0);
        if (savedInstanceState != null) resumePosition = savedInstanceState.getInt("position", 0);

        b.toolbar.setNavigationOnClickListener(v -> {
            if (title != null) startActivity(TitleDetailActivity.intent(this, title.id)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
            finish();
        });

        MediaController controller = new MediaController(this);
        controller.setAnchorView(b.frame);
        b.video.setMediaController(controller);
        b.video.setOnPreparedListener(mp -> {
            b.buffering.setVisibility(View.GONE);
            mp.setOnInfoListener((player, what, extra) -> {
                if (what == android.media.MediaPlayer.MEDIA_INFO_BUFFERING_START) b.buffering.setVisibility(View.VISIBLE);
                if (what == android.media.MediaPlayer.MEDIA_INFO_BUFFERING_END) b.buffering.setVisibility(View.GONE);
                return false;
            });
            if (resumePosition > 0) b.video.seekTo(resumePosition);
            b.video.start();
        });
        b.video.setOnCompletionListener(mp -> {
            if (!next.isEmpty()) playOther(next.get(0).id);
        });
        b.video.setOnErrorListener((mp, what, extra) -> {
            b.buffering.setVisibility(View.GONE);
            b.videoError.setText("No se pudo reproducir este vídeo.");
            b.videoError.setVisibility(View.VISIBLE);
            return true;
        });

        applyOrientation(getResources().getConfiguration());
        load();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (b != null && b.video.isPlaying()) {
            resumePosition = b.video.getCurrentPosition();
            b.video.pause();
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        if (b != null) out.putInt("position", Math.max(resumePosition, b.video.getCurrentPosition()));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (b != null) b.video.stopPlayback();
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration config) {
        super.onConfigurationChanged(config);
        applyOrientation(config);
    }

    /** Landscape: only the video, full screen, system bars hidden. */
    private void applyOrientation(Configuration config) {
        boolean landscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE;
        b.toolbar.setVisibility(landscape ? View.GONE : View.VISIBLE);
        b.below.setVisibility(landscape ? View.GONE : View.VISIBLE);
        ViewGroup.LayoutParams holder = b.frameHolder.getLayoutParams();
        holder.height = landscape ? ViewGroup.LayoutParams.MATCH_PARENT : ViewGroup.LayoutParams.WRAP_CONTENT;
        ((LinearLayout.LayoutParams) holder).weight = landscape ? 1 : 0;
        b.frameHolder.setLayoutParams(holder);

        androidx.constraintlayout.widget.ConstraintLayout.LayoutParams frame =
                (androidx.constraintlayout.widget.ConstraintLayout.LayoutParams) b.frame.getLayoutParams();
        frame.dimensionRatio = landscape ? null : "16:9";
        frame.height = landscape ? ViewGroup.LayoutParams.MATCH_PARENT : 0;
        frame.bottomToBottom = landscape ? androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
                : androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.UNSET;
        b.frame.setLayoutParams(frame);

        WindowInsetsControllerCompat insets = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (landscape) {
            insets.hide(WindowInsetsCompat.Type.systemBars());
            insets.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        } else {
            insets.show(WindowInsetsCompat.Type.systemBars());
        }
    }

    private void load() {
        Ui.loading(b.state, "Cargando vídeo…");
        b.buffering.setVisibility(View.VISIBLE);
        b.videoError.setVisibility(View.GONE);
        Async.run(this, () -> {
            Multimedia m = Api.Media.get(multimediaId);
            MovieSeries t = Api.MoviesSeries.get(m.movieSeriesId);
            List<Multimedia> files = Api.Media.listByTitle(m.movieSeriesId);
            return new Object[]{m, t, files};
        }, result -> {
            media = (Multimedia) result[0];
            title = (MovieSeries) result[1];
            @SuppressWarnings("unchecked")
            List<Multimedia> files = (List<Multimedia>) result[2];
            render(files);
        }, error -> {
            b.buffering.setVisibility(View.GONE);
            Ui.error(b.state, error, this::load);
        });
    }

    private void render(List<Multimedia> files) {
        Ui.hide(b.state);
        b.toolbar.setTitle(title.name);
        boolean isSeries = title.isSeries();

        List<String> meta = new ArrayList<>();
        if (isSeries && media.season != null) meta.add(Format.seasonLabel(media.season));
        if (isSeries) meta.add("Episodio " + media.episodeNumber);
        if (media.durationSeconds != null) meta.add(Format.duration(media.durationSeconds));
        b.meta.setText(String.join(" · ", meta));
        b.meta.setVisibility(meta.isEmpty() ? View.GONE : View.VISIBLE);
        b.heading.setText(isSeries ? Format.episodeTitle(media) : title.name);

        next.clear();
        int index = -1;
        for (int i = 0; i < files.size(); i++) {
            if (files.get(i).id == media.id) index = i;
        }
        for (int i = index + 1; i < files.size() && next.size() < NEXT_LIMIT; i++) next.add(files.get(i));

        b.nextSection.setVisibility(isSeries ? View.VISIBLE : View.GONE);
        b.nextList.removeAllViews();
        b.nextEmpty.setVisibility(next.isEmpty() ? View.VISIBLE : View.GONE);
        for (Multimedia ep : next) {
            ItemNextEpisodeBinding row = ItemNextEpisodeBinding.inflate(getLayoutInflater(), b.nextList, false);
            row.code.setText(Format.episodeCode(ep));
            row.title.setText(Format.episodeTitle(ep));
            row.duration.setText(Format.duration(ep.durationSeconds));
            row.getRoot().setOnClickListener(v -> playOther(ep.id));
            b.nextList.addView(row.getRoot());
        }

        // The URL carries the token (only this endpoint accepts it) and is never stored.
        b.video.setVideoURI(Api.Media.videoUri(media.id));
    }

    private void playOther(long id) {
        b.video.stopPlayback();
        multimediaId = id;
        resumePosition = 0;
        getIntent().putExtra(EXTRA_ID, id);
        load();
    }
}
