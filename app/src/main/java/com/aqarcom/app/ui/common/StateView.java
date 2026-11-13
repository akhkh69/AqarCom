package com.aqarcom.app.ui.common;

import android.view.View;

import androidx.annotation.DrawableRes;

import com.aqarcom.app.R;
import com.aqarcom.app.data.model.ApiError;
import com.aqarcom.app.databinding.ViewStateBinding;

/** Loading / empty / error overlay included in list screens (layout view_state.xml). */
public class StateView {
    private final ViewStateBinding b;
    private Runnable retry;

    public StateView(ViewStateBinding binding) {
        this.b = binding;
        b.stateRetry.setOnClickListener(v -> { if (retry != null) retry.run(); });
    }

    public void loading() {
        b.getRoot().setVisibility(View.VISIBLE);
        b.stateProgress.setVisibility(View.VISIBLE);
        b.stateIcon.setVisibility(View.GONE);
        b.stateTitle.setVisibility(View.GONE);
        b.stateMessage.setVisibility(View.GONE);
        b.stateRetry.setVisibility(View.GONE);
    }

    public void hide() {
        b.getRoot().setVisibility(View.GONE);
    }

    public void empty(@DrawableRes int icon, CharSequence title, CharSequence message) {
        show(icon, title, message, null, null);
    }

    public void error(ApiError e, Runnable onRetry) {
        show(R.drawable.ic_info, b.getRoot().getContext().getString(R.string.error_title), e.message, b.getRoot().getContext().getString(R.string.retry), onRetry);
    }

    public void action(@DrawableRes int icon, CharSequence title, CharSequence message, CharSequence actionText, Runnable onAction) {
        show(icon, title, message, actionText, onAction);
    }

    private void show(int icon, CharSequence title, CharSequence message, CharSequence actionText, Runnable onAction) {
        b.getRoot().setVisibility(View.VISIBLE);
        b.stateProgress.setVisibility(View.GONE);
        b.stateIcon.setVisibility(View.VISIBLE);
        b.stateIcon.setImageResource(icon);
        b.stateTitle.setVisibility(View.VISIBLE);
        b.stateTitle.setText(title);
        b.stateMessage.setVisibility(message == null ? View.GONE : View.VISIBLE);
        b.stateMessage.setText(message);
        retry = onAction;
        b.stateRetry.setVisibility(onAction == null ? View.GONE : View.VISIBLE);
        if (actionText != null) b.stateRetry.setText(actionText);
    }
}
