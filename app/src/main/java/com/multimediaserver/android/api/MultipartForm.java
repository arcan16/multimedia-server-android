package com.multimediaserver.android.api;

import androidx.annotation.Nullable;

import okhttp3.MultipartBody;
import okhttp3.RequestBody;

/** Builds a multipart body, repeating list values and skipping empty ones (like toFormData). */
public class MultipartForm {
    private final MultipartBody.Builder builder = new MultipartBody.Builder().setType(MultipartBody.FORM);
    private boolean empty = true;

    public MultipartForm add(String name, @Nullable Object value) {
        if (value == null) return this;
        if (value instanceof Iterable) {
            for (Object item : (Iterable<?>) value) add(name, item);
            return this;
        }
        String text = String.valueOf(value);
        if (text.isEmpty()) return this;
        builder.addFormDataPart(name, text);
        empty = false;
        return this;
    }

    public MultipartForm file(String name, String fileName, @Nullable RequestBody body) {
        if (body == null) return this;
        builder.addFormDataPart(name, fileName, body);
        empty = false;
        return this;
    }

    public RequestBody build() {
        // MultipartBody needs at least one part.
        if (empty) builder.addFormDataPart("_", "");
        return builder.build();
    }
}
