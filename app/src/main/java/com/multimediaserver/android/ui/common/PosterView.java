package com.multimediaserver.android.ui.common;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Outline;
import android.graphics.drawable.ColorDrawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.multimediaserver.android.R;
import com.multimediaserver.android.model.MovieSeries;
import com.multimediaserver.android.util.Format;
import com.multimediaserver.android.util.PosterLoader;

/**
 * 2:3 poster with a toned placeholder showing the name while the image loads or when the title
 * has no poster (Frontend/src/features/catalog/components/Poster.jsx).
 */
public class PosterView extends FrameLayout {
    private final ImageView image;
    private final TextView label;
    private long boundId = Long.MIN_VALUE;

    public PosterView(Context context) {
        this(context, null);
    }

    public PosterView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        float radius = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10, getResources().getDisplayMetrics());
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
            }
        });
        setClipToOutline(true);
        setBackgroundColor(ContextCompat.getColor(context, R.color.surface_2));

        int pad = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, getResources().getDisplayMetrics());
        label = new TextView(context);
        label.setTextColor(ContextCompat.getColor(context, R.color.text));
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        label.setGravity(Gravity.CENTER);
        label.setPadding(pad, pad, pad, pad);
        label.setMaxLines(4);
        addView(label, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        image = new ImageView(context);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(image, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = width * 3 / 2;
        super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
    }

    /** Hides the name label (for small thumbnails). */
    public void setLabelVisible(boolean visible) {
        label.setVisibility(visible ? VISIBLE : GONE);
    }

    public void bind(MovieSeries title) {
        bind(title.id, title.name, title.posterPath != null && !title.posterPath.isEmpty());
    }

    public void bind(long id, String name, boolean hasPoster) {
        boundId = id;
        setBackground(new ColorDrawable(Format.toneFor(id)));
        label.setText(name);
        setContentDescription("Póster de " + name);
        image.setImageDrawable(null);
        if (!hasPoster) return;
        Bitmap cached = PosterLoader.cached(id);
        if (cached != null) {
            image.setImageBitmap(cached);
            return;
        }
        PosterLoader.load(id, bitmap -> {
            if (boundId == id && bitmap != null) image.setImageBitmap(bitmap);
        });
    }

    /** Shows a local preview (a picked file) instead of the server poster. */
    public void showPreview(String name, Bitmap preview) {
        label.setText(name);
        image.setImageBitmap(preview);
    }
}
