package com.multimediaserver.android.api;

import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * The only code that talks HTTP. Every response quirk of the API is handled here
 * (see "Response quirks" in Frontend/CLAUDE.md):
 * - list endpoints answer an empty list with {"message": "..."}: {@link #list} returns [];
 * - message bodies may arrive as text/plain JSON: bodies are read as text and parsed;
 * - PUT answers 200 with no body: callers refetch.
 * Calls are synchronous; run them off the main thread with {@code util.Async}.
 */
public final class ApiClient {
    public static final Gson GSON = new Gson();
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private static final Map<Integer, String> GENERIC_ERRORS = new HashMap<>();

    static {
        GENERIC_ERRORS.put(0, "No se pudo conectar con el servidor.");
        GENERIC_ERRORS.put(401, "Tu sesión ha caducado. Vuelve a iniciar sesión.");
        GENERIC_ERRORS.put(403, "No tienes permiso para realizar esta acción.");
        GENERIC_ERRORS.put(404, "El registro no existe.");
    }

    private static final OkHttpClient HTTP = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();

    /** Uploads can be gigabytes and the server runs ffprobe before answering. */
    private static final OkHttpClient UPLOAD_HTTP = HTTP.newBuilder()
            .writeTimeout(0, TimeUnit.MILLISECONDS)
            .readTimeout(30, TimeUnit.MINUTES)
            .build();

    private static volatile Runnable unauthorizedHandler;

    private ApiClient() {
    }

    /** Called (from any thread) when the session must end because of a 401 or an expired token. */
    public static void setUnauthorizedHandler(Runnable handler) {
        unauthorizedHandler = handler;
    }

    public static String genericMessage(int status) {
        return GENERIC_ERRORS.get(status);
    }

    private static void notifyUnauthorized() {
        Runnable handler = unauthorizedHandler;
        if (handler != null) handler.run();
    }

    // ---------------------------------------------------------------- URLs and requests

    public static HttpUrl url(String path, @Nullable Map<String, ?> query) {
        HttpUrl base = HttpUrl.parse(ServerConfig.getBaseUrl() + path);
        if (base == null) throw new IllegalArgumentException("URL de servidor no válida");
        HttpUrl.Builder builder = base.newBuilder();
        if (query != null) {
            for (Map.Entry<String, ?> entry : query.entrySet()) {
                Object value = entry.getValue();
                if (value == null || "".equals(value)) continue;
                if (value instanceof Iterable) {
                    for (Object item : (Iterable<?>) value) builder.addQueryParameter(entry.getKey(), String.valueOf(item));
                } else {
                    builder.addQueryParameter(entry.getKey(), String.valueOf(value));
                }
            }
        }
        return builder.build();
    }

    /** Builds a query map from alternating key/value pairs, skipping empty values later. */
    public static Map<String, Object> query(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) map.put(String.valueOf(pairs[i]), pairs[i + 1]);
        return map;
    }

    private static Request.Builder builder(HttpUrl url, boolean auth) throws ApiError {
        Request.Builder builder = new Request.Builder().url(url);
        if (auth) {
            // An expired token is never sent: the session ends here, as if the server had answered 401.
            if (Session.hasExpiredToken()) {
                notifyUnauthorized();
                throw new ApiError(401, GENERIC_ERRORS.get(401));
            }
            String token = Session.getToken();
            if (token != null) builder.header("Authorization", "Bearer " + token);
        }
        return builder;
    }

    /**
     * Low-level request. {@code body} may be null, a {@link RequestBody} (multipart) or any object
     * serialized as JSON. Use {@code auth = false} only for the login call.
     */
    public static JsonElement request(String method, String path, @Nullable Map<String, ?> query,
                                      @Nullable Object body, boolean auth) throws ApiError {
        RequestBody payload = null;
        if (body instanceof RequestBody) payload = (RequestBody) body;
        else if (body != null) payload = RequestBody.create(GSON.toJson(body), JSON);
        else if (!"GET".equals(method) && !"DELETE".equals(method)) payload = RequestBody.create(new byte[0], null);

        Request request = builder(url(path, query), auth).method(method, payload).build();
        return execute(HTTP.newCall(request), auth);
    }

    public static JsonElement get(String path, @Nullable Map<String, ?> query) throws ApiError {
        return request("GET", path, query, null, true);
    }

    public static <T> T get(String path, @Nullable Map<String, ?> query, Type type) throws ApiError {
        return GSON.fromJson(get(path, query), type);
    }

    /** GET for list endpoints: an empty list may come as {"message": ...}; normalize it to []. */
    public static <T> List<T> list(String path, @Nullable Map<String, ?> query, Class<T> itemType) throws ApiError {
        JsonElement data = get(path, query);
        List<T> result = new ArrayList<>();
        if (data != null && data.isJsonArray()) {
            for (JsonElement item : data.getAsJsonArray()) result.add(GSON.fromJson(item, itemType));
        }
        return result;
    }

    /** Fetches a protected binary resource (posters need the Authorization header). */
    public static byte[] bytes(String path) throws ApiError {
        Request request = builder(url(path, null), true).get().build();
        try (Response response = HTTP.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                if (response.code() == 401) notifyUnauthorized();
                String message = GENERIC_ERRORS.get(response.code());
                throw new ApiError(response.code(), message != null ? message : "No se pudo cargar el archivo.");
            }
            ResponseBody body = response.body();
            return body != null ? body.bytes() : new byte[0];
        } catch (IOException e) {
            throw new ApiError(0, GENERIC_ERRORS.get(0));
        }
    }

    /**
     * Prepares a multipart upload on the long-timeout client. Keep the returned Call to cancel it
     * and run it with {@link #executeUpload(Call)} off the main thread.
     */
    public static Call newUploadCall(String method, String path, RequestBody body) throws ApiError {
        Request request = builder(url(path, null), true).method(method, body).build();
        return UPLOAD_HTTP.newCall(request);
    }

    public static JsonElement executeUpload(Call call) throws ApiError {
        return execute(call, true);
    }

    private static JsonElement execute(Call call, boolean auth) throws ApiError {
        try (Response response = call.execute()) {
            JsonElement data = readBody(response);
            if (!response.isSuccessful()) {
                if (response.code() == 401 && auth) notifyUnauthorized();
                throw toApiError(response.code(), data);
            }
            return data;
        } catch (IOException e) {
            if (call.isCanceled()) throw new ApiError(0, "Subida cancelada.");
            throw new ApiError(0, GENERIC_ERRORS.get(0));
        }
    }

    /** Reads a body that may be JSON, JSON sent as text/plain, plain text or empty. */
    private static JsonElement readBody(Response response) throws IOException {
        ResponseBody body = response.body();
        String text = body != null ? body.string() : "";
        if (text.isEmpty()) return null;
        try {
            return JsonParser.parseString(text);
        } catch (JsonParseException e) {
            return new JsonPrimitive(text);
        }
    }

    private static ApiError toApiError(int status, @Nullable JsonElement body) {
        if (body != null && body.isJsonArray()) {
            Map<String, String> fieldErrors = new HashMap<>();
            String first = null;
            JsonArray items = body.getAsJsonArray();
            for (JsonElement item : items) {
                if (!item.isJsonObject()) continue;
                JsonObject obj = item.getAsJsonObject();
                String message = stringOrNull(obj, "message");
                String field = stringOrNull(obj, "field");
                if (field != null && !fieldErrors.containsKey(field)) fieldErrors.put(field, message);
                if (first == null) first = message;
            }
            return new ApiError(status, first != null ? first : "Revisa los datos del formulario.", fieldErrors);
        }
        String message = null;
        if (body != null && body.isJsonObject()) message = stringOrNull(body.getAsJsonObject(), "message");
        else if (body != null && body.isJsonPrimitive()) message = body.getAsString();
        if (message == null || message.isEmpty()) message = GENERIC_ERRORS.get(status);
        if (message == null) message = "Ha ocurrido un error inesperado.";
        return new ApiError(status, message);
    }

    private static String stringOrNull(JsonObject obj, String key) {
        JsonElement value = obj.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
    }
}
