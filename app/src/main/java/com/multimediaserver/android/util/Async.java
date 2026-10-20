package com.multimediaserver.android.util;

import android.os.Handler;
import android.os.Looper;

import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;

import com.multimediaserver.android.api.ApiClient;
import com.multimediaserver.android.api.ApiError;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Runs blocking API calls on a background thread and delivers the result on the main thread,
 * only while {@code owner} is still alive (use a fragment's view lifecycle owner).
 */
public final class Async {
    private static final ExecutorService POOL = Executors.newCachedThreadPool();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public interface Work<T> {
        T run() throws Exception;
    }

    public interface Ok<T> {
        void accept(T value);
    }

    public interface Fail {
        void accept(ApiError error);
    }

    private Async() {
    }

    public static <T> void run(LifecycleOwner owner, Work<T> work, Ok<T> ok, Fail fail) {
        POOL.execute(() -> {
            T value = null;
            ApiError error = null;
            try {
                value = work.run();
            } catch (ApiError e) {
                error = e;
            } catch (Exception e) {
                error = new ApiError(0, ApiClient.genericMessage(0));
            }
            final T result = value;
            final ApiError failure = error;
            MAIN.post(() -> {
                if (!owner.getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.CREATED)) return;
                if (failure != null) {
                    if (fail != null) fail.accept(failure);
                } else if (ok != null) {
                    ok.accept(result);
                }
            });
        });
    }

    public static void main(Runnable runnable) {
        MAIN.post(runnable);
    }

    public static void background(Runnable runnable) {
        POOL.execute(runnable);
    }
}
