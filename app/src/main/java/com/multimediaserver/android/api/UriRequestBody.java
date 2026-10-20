package com.multimediaserver.android.api;

import android.content.ContentResolver;
import android.net.Uri;

import androidx.annotation.Nullable;

import java.io.IOException;
import java.io.InputStream;

import okhttp3.MediaType;
import okhttp3.RequestBody;
import okio.BufferedSink;

/**
 * Streams a picked file (content:// Uri) without loading it in memory and reports progress,
 * the Android equivalent of XMLHttpRequest's upload.onprogress.
 */
public class UriRequestBody extends RequestBody {

    public interface ProgressListener {
        /** Called from the upload thread with a value from 0 to 100. */
        void onProgress(int percent);
    }

    private final ContentResolver resolver;
    private final Uri uri;
    private final MediaType mediaType;
    private final long length;
    @Nullable
    private final ProgressListener listener;

    public UriRequestBody(ContentResolver resolver, Uri uri, @Nullable String mimeType, long length,
                          @Nullable ProgressListener listener) {
        this.resolver = resolver;
        this.uri = uri;
        this.mediaType = mimeType != null ? MediaType.parse(mimeType) : null;
        this.length = length;
        this.listener = listener;
    }

    @Override
    public MediaType contentType() {
        return mediaType;
    }

    @Override
    public long contentLength() {
        return length;
    }

    @Override
    public void writeTo(BufferedSink sink) throws IOException {
        try (InputStream in = resolver.openInputStream(uri)) {
            if (in == null) throw new IOException("No se pudo abrir el archivo.");
            byte[] buffer = new byte[64 * 1024];
            long written = 0;
            int lastPercent = -1;
            int read;
            while ((read = in.read(buffer)) != -1) {
                sink.write(buffer, 0, read);
                written += read;
                if (listener != null && length > 0) {
                    int percent = (int) Math.min(100, written * 100 / length);
                    if (percent != lastPercent) {
                        lastPercent = percent;
                        listener.onProgress(percent);
                    }
                }
            }
        }
    }
}
