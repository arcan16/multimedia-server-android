package com.multimediaserver.android.util;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import androidx.annotation.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/** A file picked with the system document picker: its Uri, display name, MIME type and size. */
public class PickedFile {
    public final Uri uri;
    public final String name;
    @Nullable
    public final String mimeType;
    /** Size in bytes, or -1 when unknown. */
    public final long size;

    private PickedFile(Uri uri, String name, @Nullable String mimeType, long size) {
        this.uri = uri;
        this.name = name;
        this.mimeType = mimeType;
        this.size = size;
    }

    public static PickedFile from(Context context, Uri uri) {
        ContentResolver resolver = context.getContentResolver();
        String name = uri.getLastPathSegment();
        long size = -1;
        try (Cursor cursor = resolver.query(uri, new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE},
                null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (nameIndex >= 0 && !cursor.isNull(nameIndex)) name = cursor.getString(nameIndex);
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex);
            }
        } catch (RuntimeException ignored) {
            // Keep the defaults.
        }
        return new PickedFile(uri, name != null ? name : "archivo", resolver.getType(uri), size);
    }

    /** Reads the whole file; only for small files such as posters. */
    public byte[] readBytes(Context context) throws IOException {
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IOException("No se pudo abrir el archivo.");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            return out.toByteArray();
        }
    }
}
