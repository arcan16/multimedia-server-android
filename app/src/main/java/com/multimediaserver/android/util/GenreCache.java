package com.multimediaserver.android.util;

import androidx.lifecycle.LifecycleOwner;

import com.multimediaserver.android.api.Api;
import com.multimediaserver.android.model.Genre;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Shared genre list (the useGenres hook). Loaded once and refreshed after admin changes. */
public final class GenreCache {
    private static List<Genre> genres;

    public interface Listener {
        void onGenres(List<Genre> genres, Map<Long, String> byId);
    }

    private GenreCache() {
    }

    public static void load(LifecycleOwner owner, Listener listener) {
        if (genres != null) {
            listener.onGenres(genres, byId(genres));
            return;
        }
        Async.run(owner, Api.Genres::list, list -> {
            genres = list;
            listener.onGenres(list, byId(list));
        }, error -> listener.onGenres(new ArrayList<>(), new LinkedHashMap<>()));
    }

    public static void invalidate() {
        genres = null;
    }

    private static Map<Long, String> byId(List<Genre> list) {
        Map<Long, String> map = new LinkedHashMap<>();
        for (Genre g : list) map.put(g.id, g.genre);
        return map;
    }
}
