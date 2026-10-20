package com.multimediaserver.android.ui.common;

import android.app.Activity;
import android.view.View;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * Keeps the content clear of the status bar, the navigation bar, display cutouts and the keyboard.
 * With targetSdk 35+ Android draws every app edge to edge, so without this the toolbar ends up
 * under the clock and battery icons. Call it right after setContentView.
 */
public final class SystemBars {
    private SystemBars() {
    }

    public static void fit(Activity activity) {
        // Same behavior on every Android version: draw edge to edge and pad the content ourselves.
        WindowCompat.setDecorFitsSystemWindows(activity.getWindow(), false);
        // Dark UI: light status and navigation bar icons.
        WindowCompat.getInsetsController(activity.getWindow(), activity.getWindow().getDecorView())
                .setAppearanceLightStatusBars(false);
        WindowCompat.getInsetsController(activity.getWindow(), activity.getWindow().getDecorView())
                .setAppearanceLightNavigationBars(false);

        View content = activity.findViewById(android.R.id.content);
        ViewCompat.setOnApplyWindowInsetsListener(content, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            // adjustResize no longer resizes an edge-to-edge window: lift the content above the keyboard.
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            view.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, ime.bottom));
            return WindowInsetsCompat.CONSUMED;
        });
        ViewCompat.requestApplyInsets(content);
    }
}
