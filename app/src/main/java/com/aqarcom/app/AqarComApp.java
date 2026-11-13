package com.aqarcom.app;

import android.app.Application;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

import com.aqarcom.app.data.network.ApiClient;
import com.aqarcom.app.ui.auth.LoginActivity;

import org.osmdroid.config.Configuration;

/** Registered in AndroidManifest as android:name=".AqarComApp". */
public class AqarComApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // OpenStreetMap tiles: identify the app (tile usage policy) and cache tiles in app storage
        Configuration.getInstance().load(this, getSharedPreferences("osmdroid", MODE_PRIVATE));
        Configuration.getInstance().setUserAgentValue(BuildConfig.APPLICATION_ID);
        ApiClient.init(this, () -> new Handler(Looper.getMainLooper()).post(() -> {
            // refresh token expired / revoked (e.g. account suspended) → back to login
            Intent i = new Intent(this, LoginActivity.class)
                    .putExtra(LoginActivity.EXTRA_SESSION_EXPIRED, true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
        }));
    }
}
