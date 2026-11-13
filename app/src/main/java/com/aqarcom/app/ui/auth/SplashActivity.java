package com.aqarcom.app.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import com.aqarcom.app.data.model.ApiError;
import com.aqarcom.app.data.model.PageMeta;
import com.aqarcom.app.data.model.User;
import com.aqarcom.app.data.network.ApiCallback;
import com.aqarcom.app.data.network.ApiClient;
import com.aqarcom.app.data.network.TokenManager;
import com.aqarcom.app.databinding.ActivitySplashBinding;
import com.aqarcom.app.ui.base.BaseActivity;

/** Start screen: refreshes the stored user (status may have changed on the dashboard) then routes. */
public class SplashActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(ActivitySplashBinding.inflate(getLayoutInflater()).getRoot());
        new Handler(Looper.getMainLooper()).postDelayed(this::route, 700);
    }

    private void route() {
        TokenManager tm = TokenManager.get(this);
        if (!tm.isLoggedIn()) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }
        ApiClient.auth().me().enqueue(new ApiCallback<User>() {
            @Override public void onSuccess(User u, PageMeta meta) {
                tm.saveUser(u);
                AuthRouter.goHome(SplashActivity.this, u);
            }

            @Override public void onError(ApiError e) {
                if ("ACCOUNT_REJECTED".equals(e.code) || "ACCOUNT_SUSPENDED".equals(e.code) || e.httpStatus == 401) {
                    tm.clear();
                    Intent i = new Intent(SplashActivity.this, LoginActivity.class).putExtra(LoginActivity.EXTRA_MESSAGE, e.message);
                    startActivity(i);
                    finish();
                } else {
                    // offline → continue with the cached user
                    AuthRouter.goHome(SplashActivity.this, tm.getUser());
                }
            }
        });
    }
}
