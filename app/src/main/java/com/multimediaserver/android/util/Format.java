package com.multimediaserver.android.util;

import androidx.annotation.Nullable;

import com.multimediaserver.android.model.Multimedia;
import com.multimediaserver.android.model.User;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/** Labels, durations and grouping by season (port of Frontend/src/utils/format.js). */
public final class Format {
    private Format() {
    }

    public static String typeLabel(String type) {
        return "SERIE".equals(type) ? "SERIE" : "PELÍCULA";
    }

    public static String statusLabel(String status) {
        if ("ACTIVE".equals(status)) return "Activo";
        if ("INACTIVE".equals(status)) return "Inactivo";
        if ("DELETED".equals(status)) return "Eliminado";
        return status != null ? status : "—";
    }

    /** 3120 -> "52 min"; under a minute -> "45 s"; unknown -> "—". */
    public static String duration(@Nullable Double seconds) {
        if (seconds == null) return "—";
        if (seconds < 60) return Math.round(seconds) + " s";
        long minutes = Math.round(seconds / 60);
        if (minutes < 60) return minutes + " min";
        long h = minutes / 60;
        long m = minutes % 60;
        return m > 0 ? h + " h " + m + " min" : h + " h";
    }

    /** "T1 · E3" for series episodes, "" for a movie video. */
    public static String episodeCode(Multimedia item) {
        if (item == null || item.season == null) return "";
        return "T" + item.season + " · E" + item.episodeNumber;
    }

    public static String episodeTitle(Multimedia item) {
        if (item.title != null && !item.title.isEmpty()) return item.title;
        return "Episodio " + item.episodeNumber;
    }

    public static String seasonLabel(int season) {
        return season == 0 ? "Especiales" : "Temporada " + season;
    }

    /** Groups episodes (already sorted by the API) by season, in ascending order. */
    public static TreeMap<Integer, List<Multimedia>> groupBySeason(List<Multimedia> items) {
        TreeMap<Integer, List<Multimedia>> map = new TreeMap<>();
        for (Multimedia item : items) {
            int key = item.season != null ? item.season : 0;
            List<Multimedia> list = map.get(key);
            if (list == null) {
                list = new ArrayList<>();
                map.put(key, list);
            }
            list.add(item);
        }
        return map;
    }

    public static String pluralize(int count, String singular) {
        return pluralize(count, singular, singular + "s");
    }

    public static String pluralize(int count, String singular, String plural) {
        return count + " " + (count == 1 ? singular : plural);
    }

    public static String initials(@Nullable User user) {
        if (user == null) return "?";
        StringBuilder out = new StringBuilder();
        if (user.name != null && !user.name.isEmpty()) out.append(user.name.charAt(0));
        if (user.lastname != null && !user.lastname.isEmpty()) out.append(user.lastname.charAt(0));
        if (out.length() > 0) return out.toString().toUpperCase(Locale.ROOT);
        String username = user.username != null ? user.username : "?";
        return username.substring(0, Math.min(2, username.length())).toUpperCase(Locale.ROOT);
    }

    public static String fullName(@Nullable User user) {
        if (user == null) return "";
        StringBuilder out = new StringBuilder();
        if (user.name != null && !user.name.isEmpty()) out.append(user.name);
        if (user.lastname != null && !user.lastname.isEmpty()) {
            if (out.length() > 0) out.append(' ');
            out.append(user.lastname);
        }
        return out.toString();
    }

    /** Placeholder tones for titles without a poster (from the mockups). */
    private static final int[] TONES = {
            0xFF3A2F4F, 0xFF24414A, 0xFF4A3326, 0xFF2E3F2C, 0xFF41283A,
            0xFF2B3550, 0xFF4A4026, 0xFF253B3B, 0xFF3E2A2A, 0xFF2F2F45};

    public static int toneFor(long id) {
        return TONES[(int) (Math.abs(id) % TONES.length)];
    }

    /** genreIds -> "Drama, Aventura" using a map of id -> name. */
    public static String genreNames(@Nullable List<Long> ids, Map<Long, String> byId) {
        if (ids == null) return "";
        StringBuilder out = new StringBuilder();
        for (Long id : ids) {
            String name = byId.get(id);
            if (name == null) continue;
            if (out.length() > 0) out.append(", ");
            out.append(name);
        }
        return out.toString();
    }

    public static String text(@Nullable String value) {
        return value != null ? value : "";
    }

    public static String orDash(@Nullable String value) {
        return value == null || value.isEmpty() ? "—" : value;
    }
}
