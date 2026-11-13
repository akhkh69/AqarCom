package com.aqarcom.app.ui.auth;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import com.aqarcom.app.data.model.MessageResult;
import com.aqarcom.app.data.model.PageMeta;
import com.aqarcom.app.data.model.ApiError;
import com.aqarcom.app.data.model.RefreshRequest;
import com.aqarcom.app.data.model.User;
import com.aqarcom.app.data.network.ApiCallback;
import com.aqarcom.app.data.network.ApiClient;
import com.aqarcom.app.data.network.TokenManager;
import com.aqarcom.app.ui.main.MainActivity;

/** Decides where a user goes after login / registration / app start. */
public final class AuthRouter {
    private AuthRouter() {}

    public static void goHome(Activity a, User u) {
        Intent i = new Intent(a, u != null && u.isPendingApproval() ? PendingApprovalActivity.class : MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        a.startActivity(i);
        a.finish();
    }

    public static void goGuest(Activity a) {
        Intent i = new Intent(a, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        a.startActivity(i);
        a.finish();
    }

    public static void openLogin(Context c) {
        c.startActivity(new Intent(c, LoginActivity.class));
    }

    /** Revokes the refresh token on the server, clears the local session and returns to login. */
    public static void logout(Activity a) {
        TokenManager tm = TokenManager.get(a);
        String refresh = tm.getRefreshToken();
        tm.clear();
        if (refresh != null) {
            ApiClient.auth().logout(new RefreshRequest(refresh)).enqueue(new ApiCallback<MessageResult>() {
                @Override public void onSuccess(MessageResult data, PageMeta meta) { }
                @Override public void onError(ApiError error) { }
            });
        }
        Intent i = new Intent(a, LoginActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        a.startActivity(i);
        a.finish();
    }
}
