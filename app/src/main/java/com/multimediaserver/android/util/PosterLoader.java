package com.multimediaserver.android.util;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.LruCache;

import androidx.annotation.Nullable;

import com.multimediaserver.android.api.Api;
import com.multimediaserver.android.api.ApiClient;
import com.multimediaserver.android.api.ApiError;

import java.util.HashSet;
import java.util.Set;

/**
 * Loads posters with the Authorization header (the endpoint rejects anonymous requests) and
 * keeps decoded bitmaps in a memory cache keyed by title id (the usePosterUrl hook).
 */
public final class PosterLoader {
    private static final int MAX_SIDE = 720;

    private static final LruCache<Long, Bitmap> CACHE = new LruCache<Long, Bitmap>(
            (int) Math.min(Runtime.getRuntime().maxMemory() / 8, 48L * 1024 * 1024)) {
        @Override
        protected int sizeOf(Long key, Bitmap value) {
            return value.getByteCount();
        }
    };
    /** Titles whose poster answered 404: do not ask again until invalidated. */
    private static final Set<Long> MISSING = new HashSet<>();

    public interface Callback {
        void onPoster(@Nullable Bitmap bitmap);
    }

    private PosterLoader() {
    }

    @Nullable
    public static Bitmap cached(long id) {
        return CACHE.get(id);
    }

    public static void load(long id, Callback callback) {
        Bitmap hit = CACHE.get(id);
        if (hit != null) {
            callback.onPoster(hit);
            return;
        }
        synchronized (MISSING) {
            if (MISSING.contains(id)) {
                callback.onPoster(null);
                return;
            }
        }
        Async.background(() -> {
            Bitmap bitmap = null;
            try {
                bitmap = decode(ApiClient.bytes(Api.MoviesSeries.posterPath(id)));
                if (bitmap != null) CACHE.put(id, bitmap);
            } catch (ApiError e) {
                if (e.status == 404) {
                    synchronized (MISSING) {
                        MISSING.add(id);
                    }
                }
            }
            final Bitmap result = bitmap;
            Async.main(() -> callback.onPoster(result));
        });
    }

    /** Call after a poster is replaced or its title deleted. */
    public static void invalidate(long id) {
        CACHE.remove(id);
        synchronized (MISSING) {
            MISSING.remove(id);
        }
    }

    @Nullable
    public static Bitmap decode(byte[] data) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, bounds);
        int sample = 1;
        while (Math.max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        return BitmapFactory.decodeByteArray(data, 0, data.length, options);
    }
}
