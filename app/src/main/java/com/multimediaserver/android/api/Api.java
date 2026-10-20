package com.multimediaserver.android.api;

import android.net.Uri;

import androidx.annotation.Nullable;

import com.google.gson.JsonElement;
import com.multimediaserver.android.model.Genre;
import com.multimediaserver.android.model.LoginResponse;
import com.multimediaserver.android.model.MovieSeries;
import com.multimediaserver.android.model.Multimedia;
import com.multimediaserver.android.model.User;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.Call;
import okhttp3.RequestBody;

/** One group of calls per resource, mirroring Frontend/src/api/*.api.js. All calls block. */
public final class Api {
    private Api() {
    }

    public static final class Auth {
        private Auth() {
        }

        public static LoginResponse login(String username, String password) throws ApiError {
            Map<String, String> body = new HashMap<>();
            body.put("username", username);
            body.put("password", password);
            JsonElement data = ApiClient.request("POST", "/login", null, body, false);
            return ApiClient.GSON.fromJson(data, LoginResponse.class);
        }

        public static User me() throws ApiError {
            return ApiClient.get("/users/me", null, User.class);
        }
    }

    public static final class Users {
        private Users() {
        }

        public static List<User> list() throws ApiError {
            return ApiClient.list("/users", ApiClient.query("size", 500, "sort", "username,asc"), User.class);
        }

        public static User get(long id) throws ApiError {
            return ApiClient.get("/users/" + id, null, User.class);
        }

        public static User create(Map<String, Object> user) throws ApiError {
            return ApiClient.GSON.fromJson(ApiClient.request("POST", "/users", null, user, true), User.class);
        }

        /** {@code changes} must include {@code id}; omitted fields are left unchanged. */
        public static void update(Map<String, Object> changes) throws ApiError {
            ApiClient.request("PUT", "/users", null, changes, true);
        }

        public static void remove(long id) throws ApiError {
            ApiClient.request("DELETE", "/users/" + id, null, null, true);
        }
    }

    public static final class Genres {
        private Genres() {
        }

        public static List<Genre> list() throws ApiError {
            return ApiClient.list("/genres", ApiClient.query("size", 200, "sort", "genre,asc"), Genre.class);
        }

        public static void create(String genre) throws ApiError {
            Map<String, Object> body = new HashMap<>();
            body.put("genre", genre);
            ApiClient.request("POST", "/genres", null, body, true);
        }

        public static void update(long id, String genre) throws ApiError {
            Map<String, Object> body = new HashMap<>();
            body.put("id", id);
            body.put("genre", genre);
            ApiClient.request("PUT", "/genres", null, body, true);
        }

        public static void remove(long id) throws ApiError {
            ApiClient.request("DELETE", "/genres/" + id, null, null, true);
        }
    }

    public static final class MoviesSeries {
        private MoviesSeries() {
        }

        /** All filters are optional: {@code name} substring, {@code type} MOVIE/SERIE, {@code genreId}. */
        public static List<MovieSeries> list(@Nullable String name, @Nullable String type, @Nullable Long genreId,
                                             int page, int size) throws ApiError {
            return ApiClient.list("/movies-series", ApiClient.query(
                    "name", name, "type", type, "genreId", genreId,
                    "page", page, "size", size, "sort", "name,asc"), MovieSeries.class);
        }

        public static MovieSeries get(long id) throws ApiError {
            return ApiClient.get("/movies-series/" + id, null, MovieSeries.class);
        }

        public static String posterPath(long id) {
            return "/movies-series/" + id + "/poster";
        }

        /** Fields: name, synopsis, releaseYear, classification, type, genreIds, poster (file part). */
        public static MovieSeries create(RequestBody multipart) throws ApiError {
            return ApiClient.GSON.fromJson(ApiClient.request("POST", "/movies-series", null, multipart, true), MovieSeries.class);
        }

        /** Must include {@code id}; {@code genreIds} replaces all genres. */
        public static void update(RequestBody multipart) throws ApiError {
            ApiClient.request("PUT", "/movies-series", null, multipart, true);
        }

        public static void remove(long id) throws ApiError {
            ApiClient.request("DELETE", "/movies-series/" + id, null, null, true);
        }
    }

    public static final class Media {
        private Media() {
        }

        /** Episode lists are small; ask for all of them in one page. Sorted by season, episode. */
        public static List<Multimedia> listByTitle(long movieSeriesId) throws ApiError {
            return ApiClient.list("/multimedia", ApiClient.query("movieSeriesId", movieSeriesId, "size", 500), Multimedia.class);
        }

        public static Multimedia get(long id) throws ApiError {
            return ApiClient.get("/multimedia/" + id, null, Multimedia.class);
        }

        /** POST (create) or PUT (update) upload. Keep the Call to cancel it. */
        public static Call uploadCall(boolean update, RequestBody multipart) throws ApiError {
            return ApiClient.newUploadCall(update ? "PUT" : "POST", "/multimedia", multipart);
        }

        /** Runs a call from {@link #uploadCall}. The server runs ffprobe, so a large upload takes a while. */
        public static JsonElement executeUpload(Call call) throws ApiError {
            return ApiClient.executeUpload(call);
        }

        public static void remove(long id) throws ApiError {
            ApiClient.request("DELETE", "/multimedia/" + id, null, null, true);
        }

        /**
         * Only this endpoint accepts the token as a query parameter (the player cannot always send
         * headers). The URL is built when playback starts and never stored.
         */
        public static Uri videoUri(long id) {
            String token = Session.getToken();
            return Uri.parse(ApiClient.url("/multimedia/" + id + "/video",
                    ApiClient.query("token", token != null ? token : "")).toString());
        }
    }
}
