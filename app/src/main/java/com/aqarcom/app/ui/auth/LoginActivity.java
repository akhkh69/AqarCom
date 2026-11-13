package com.aqarcom.app.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.view.View;
import android.view.inputmethod.EditorInfo;

import com.aqarcom.app.R;
import com.aqarcom.app.data.model.ApiError;
import com.aqarcom.app.data.model.AuthResult;
import com.aqarcom.app.data.model.LoginRequest;
import com.aqarcom.app.data.model.PageMeta;
import com.aqarcom.app.data.network.ApiCallback;
import com.aqarcom.app.data.network.ApiClient;
import com.aqarcom.app.data.network.TokenManager;
import com.aqarcom.app.databinding.ActivityLoginBinding;
import com.aqarcom.app.ui.base.BaseActivity;
import com.aqarcom.app.ui.common.Ui;

/** FR-01 login. Errors: INVALID_CREDENTIALS (generic), ACCOUNT_LOCKED (FR-04), ACCOUNT_REJECTED / SUSPENDED. */
public class LoginActivity extends BaseActivity {
    public static final String EXTRA_SESSION_EXPIRED = "session_expired";
    public static final String EXTRA_MESSAGE = "message";

    private ActivityLoginBinding b;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        if (getIntent().getBooleanExtra(EXTRA_SESSION_EXPIRED, false)) showError(getString(R.string.session_expired));
        String msg = getIntent().getStringExtra(EXTRA_MESSAGE);
        if (msg != null) showError(msg);

        b.btnLogin.setOnClickListener(v -> submit());
        b.etPassword.setOnEditorActionListener((v, actionId, e) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) { submit(); return true; }
            return false;
        });
        b.btnRegister.setOnClickListener(v -> startActivity(new Intent(this, RegisterActivity.class)));
        b.btnForgot.setOnClickListener(v -> {
            Intent i = new Intent(this, ForgotPasswordActivity.class);
            i.putExtra(ForgotPasswordActivity.EXTRA_EMAIL, Ui.text(b.etEmail));
            startActivity(i);
        });
        b.btnGuest.setOnClickListener(v -> AuthRouter.goGuest(this));
    }

    private void showError(String m) {
        b.tvError.setText(m);
        b.tvError.setVisibility(View.VISIBLE);
    }

    private void submit() {
        Ui.hideKeyboard(this);
        b.tvError.setVisibility(View.GONE);
        String email = Ui.text(b.etEmail);
        String password = Ui.text(b.etPassword);
        boolean ok = true;
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) { b.tilEmail.setError(getString(R.string.error_email)); ok = false; } else b.tilEmail.setError(null);
        if (password.isEmpty()) { b.tilPassword.setError(getString(R.string.error_required)); ok = false; } else b.tilPassword.setError(null);
        if (!ok) return;

        Ui.busy(b.btnLogin, true);
        ApiClient.auth().login(new LoginRequest(email, password)).enqueue(new ApiCallback<AuthResult>() {
            @Override public void onSuccess(AuthResult r, PageMeta meta) {
                TokenManager.get(LoginActivity.this).save(r);
                AuthRouter.goHome(LoginActivity.this, r.user);
            }

            @Override public void onError(ApiError e) {
                Ui.busy(b.btnLogin, false);
                showError(e.message);
            }
        });
    }
}
