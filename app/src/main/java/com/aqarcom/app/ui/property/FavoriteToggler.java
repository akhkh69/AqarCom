package com.aqarcom.app.ui.property;

import android.view.View;

import androidx.fragment.app.Fragment;

import com.aqarcom.app.R;
import com.aqarcom.app.data.model.ApiError;
import com.aqarcom.app.data.model.MessageResult;
import com.aqarcom.app.data.model.PageMeta;
import com.aqarcom.app.data.model.Property;
import com.aqarcom.app.data.network.ApiCallback;
import com.aqarcom.app.data.network.ApiClient;
import com.aqarcom.app.data.network.TokenManager;
import com.aqarcom.app.ui.auth.AuthRouter;
import com.aqarcom.app.ui.common.Ui;

/** Optimistic favorite toggle (FR-09). Visitors are asked to log in first. */
public final class FavoriteToggler {
    private FavoriteToggler() {}

    public static void toggle(Fragment host, View anchor, Property p, Runnable changed) {
        if (!TokenManager.get(host.requireContext()).isLoggedIn()) {
            Ui.confirm(host.requireContext(), host.getString(R.string.login_required_title),
                    host.getString(R.string.login_required_message), host.getString(R.string.login),
                    () -> AuthRouter.openLogin(host.requireContext()));
            return;
        }
        final boolean wasFavorite = p.isFavorite;
        p.isFavorite = !wasFavorite;
        changed.run();
        retrofit2.Call<com.aqarcom.app.data.model.ApiResponse<MessageResult>> call =
                wasFavorite ? ApiClient.properties().removeFavorite(p.id) : ApiClient.properties().addFavorite(p.id);
        call.enqueue(new ApiCallback<MessageResult>() {
            @Override public void onSuccess(MessageResult data, PageMeta meta) {
                if (host.isAdded()) Ui.snack(anchor, host.getString(wasFavorite ? R.string.removed_from_favorites : R.string.added_to_favorites));
            }

            @Override public void onError(ApiError e) {
                p.isFavorite = wasFavorite; // roll back
                changed.run();
                if (host.isAdded()) Ui.error(anchor, e);
            }
        });
    }
}
