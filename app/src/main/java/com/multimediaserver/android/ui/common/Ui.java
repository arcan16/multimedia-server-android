package com.multimediaserver.android.ui.common;

import android.content.Context;
import android.content.res.Resources;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;
import com.multimediaserver.android.R;
import com.multimediaserver.android.api.ApiError;
import com.multimediaserver.android.databinding.ViewPaginationBinding;
import com.multimediaserver.android.databinding.ViewStateBinding;

/** Small UI primitives shared by every screen (Alert, Spinner/EmptyState/ErrorState, Pagination…). */
public final class Ui {
    public enum Alert { ERROR, INFO, SUCCESS }

    private Ui() {
    }

    public static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    public static int dp(int value) {
        return Math.round(value * Resources.getSystem().getDisplayMetrics().density);
    }

    // ------------------------------------------------------------------ Alert

    public static void showAlert(TextView view, Alert variant, @Nullable CharSequence message) {
        if (message == null || message.length() == 0) {
            view.setVisibility(View.GONE);
            return;
        }
        int bg;
        int fg;
        switch (variant) {
            case INFO:
                bg = R.drawable.bg_alert_info;
                fg = R.color.info_text;
                break;
            case SUCCESS:
                bg = R.drawable.bg_alert_success;
                fg = R.color.success_text;
                break;
            default:
                bg = R.drawable.bg_alert_error;
                fg = R.color.danger_text;
        }
        view.setBackgroundResource(bg);
        view.setTextColor(ContextCompat.getColor(view.getContext(), fg));
        view.setText(message);
        view.setVisibility(View.VISIBLE);
        view.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
    }

    public static void hide(View view) {
        view.setVisibility(View.GONE);
    }

    // ------------------------------------------------------------------ States

    public static void loading(ViewStateBinding state, @Nullable String label) {
        state.getRoot().setVisibility(View.VISIBLE);
        state.stateProgress.setVisibility(View.VISIBLE);
        state.stateTitle.setVisibility(View.GONE);
        state.stateMessage.setText(label != null ? label : "Cargando…");
        state.stateMessage.setVisibility(View.VISIBLE);
        state.stateAction.setVisibility(View.GONE);
    }

    public static void error(ViewStateBinding state, ApiError error, @Nullable Runnable retry) {
        state.getRoot().setVisibility(View.VISIBLE);
        state.stateProgress.setVisibility(View.GONE);
        state.stateTitle.setText(error.status == 0 ? "Sin conexión" : "No se pudo cargar");
        state.stateTitle.setVisibility(View.VISIBLE);
        state.stateMessage.setText(error.getMessage());
        state.stateMessage.setVisibility(View.VISIBLE);
        if (retry != null) {
            state.stateAction.setText("Reintentar");
            state.stateAction.setOnClickListener(v -> retry.run());
            state.stateAction.setVisibility(View.VISIBLE);
        } else {
            state.stateAction.setVisibility(View.GONE);
        }
    }

    public static void empty(ViewStateBinding state, String title, @Nullable String message,
                             @Nullable String action, @Nullable Runnable onAction) {
        state.getRoot().setVisibility(View.VISIBLE);
        state.stateProgress.setVisibility(View.GONE);
        state.stateTitle.setText(title);
        state.stateTitle.setVisibility(View.VISIBLE);
        state.stateMessage.setText(message);
        state.stateMessage.setVisibility(message != null ? View.VISIBLE : View.GONE);
        if (action != null && onAction != null) {
            state.stateAction.setText(action);
            state.stateAction.setOnClickListener(v -> onAction.run());
            state.stateAction.setVisibility(View.VISIBLE);
        } else {
            state.stateAction.setVisibility(View.GONE);
        }
    }

    public static void hide(ViewStateBinding state) {
        state.getRoot().setVisibility(View.GONE);
    }

    // ------------------------------------------------------------------ Pagination

    public interface PageListener {
        void onPage(int page);
    }

    /** No total count in the API: "fewer items than size" means there is no next page. */
    public static void pagination(ViewPaginationBinding view, int page, boolean hasNext, PageListener listener) {
        boolean visible = page > 0 || hasNext;
        view.getRoot().setVisibility(visible ? View.VISIBLE : View.GONE);
        view.pageLabel.setText("Página " + (page + 1));
        view.pagePrev.setEnabled(page > 0);
        view.pageNext.setEnabled(hasNext);
        view.pagePrev.setOnClickListener(v -> listener.onPage(page - 1));
        view.pageNext.setOnClickListener(v -> listener.onPage(page + 1));
    }

    // ------------------------------------------------------------------ Dialogs

    public interface Confirm {
        void run(androidx.appcompat.app.AlertDialog dialog);
    }

    /**
     * Confirmation dialog that stays open while the action runs, so errors can be shown in it
     * (ConfirmDialog.jsx). Call {@code dialog.dismiss()} from the action when it succeeds.
     */
    public static androidx.appcompat.app.AlertDialog confirm(Context context, String title, CharSequence message,
                                                             String confirmLabel, Confirm onConfirm) {
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setMessage(message)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton(confirmLabel, null)
                .create();
        dialog.setOnShowListener(d -> {
            View positive = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE);
            positive.setOnClickListener(v -> onConfirm.run(dialog));
            ((TextView) positive).setTextColor(ContextCompat.getColor(context, R.color.danger_text));
        });
        dialog.show();
        return dialog;
    }

    /** Puts the dialog in its busy state (buttons disabled, label changed). */
    public static void setBusy(androidx.appcompat.app.AlertDialog dialog, boolean busy, String idleLabel) {
        TextView positive = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE);
        View negative = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE);
        positive.setEnabled(!busy);
        negative.setEnabled(!busy);
        positive.setText(busy ? "Eliminando…" : idleLabel);
        dialog.setCancelable(!busy);
    }

    // ------------------------------------------------------------------ Forms

    public static String text(EditText input) {
        return input.getText() != null ? input.getText().toString() : "";
    }

    public static String text(TextInputLayout layout) {
        return layout.getEditText() != null ? text(layout.getEditText()) : "";
    }

    public static void setText(TextInputLayout layout, @Nullable String value) {
        if (layout.getEditText() != null) layout.getEditText().setText(value != null ? value : "");
    }

    public static void setError(TextInputLayout layout, @Nullable String error) {
        layout.setError(error);
        layout.setErrorEnabled(error != null);
    }

    /** Clears the field error as soon as the user edits the field. */
    public static void clearErrorOnEdit(TextInputLayout layout, @Nullable Runnable alsoRun) {
        if (layout.getEditText() == null) return;
        layout.getEditText().addTextChangedListener(new SimpleWatcher(s -> {
            if (layout.getError() != null) setError(layout, null);
            if (alsoRun != null) alsoRun.run();
        }));
    }

    public static void hideKeyboard(View view) {
        InputMethodManager imm = (InputMethodManager) view.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    public static class SimpleWatcher implements TextWatcher {
        public interface OnChange {
            void changed(String text);
        }

        private final OnChange onChange;

        public SimpleWatcher(OnChange onChange) {
            this.onChange = onChange;
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

        @Override
        public void afterTextChanged(Editable s) {
            onChange.changed(s.toString());
        }
    }
}
