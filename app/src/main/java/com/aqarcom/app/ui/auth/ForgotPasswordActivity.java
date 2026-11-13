package com.aqarcom.app.ui.auth;

import android.os.Bundle;
import android.util.Patterns;
import android.view.View;

import com.aqarcom.app.R;
import com.aqarcom.app.data.model.ApiError;
import com.aqarcom.app.data.model.EmailRequest;
import com.aqarcom.app.data.model.MessageResult;
import com.aqarcom.app.data.model.PageMeta;
import com.aqarcom.app.data.model.ResetPasswordRequest;
import com.aqarcom.app.data.network.ApiCallback;
import com.aqarcom.app.data.network.ApiClient;
import com.aqarcom.app.databinding.ActivityForgotPasswordBinding;
import com.aqarcom.app.ui.base.BaseActivity;
import com.aqarcom.app.ui.common.Ui;

/** FR-03 — step 1: email → 6-digit one-time code; step 2: code + new password. */
public class ForgotPasswordActivity extends BaseActivity {
    public static final String EXTRA_EMAIL = "email";
    private ActivityForgotPasswordBinding b;
    private boolean step2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityForgotPasswordBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        setupToolbar(b.toolbar, null, true);
        String email = getIntent().getStringExtra(EXTRA_EMAIL);
        if (email != null) b.etEmail.setText(email);
        b.btnSubmit.setOnClickListener(v -> { if (step2) reset(); else sendCode(); });
        b.btnResend.setOnClickListener(v -> sendCode());
    }

    private void sendCode() {
        String email = Ui.text(b.etEmail);
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) { b.tilEmail.setError(getString(R.string.error_email)); return; }
        b.tilEmail.setError(null);
        Ui.busy(b.btnSubmit, true);
        ApiClient.auth().forgotPassword(new EmailRequest(email)).enqueue(new ApiCallback<MessageResult>() {
            @Override public void onSuccess(MessageResult r, PageMeta meta) {
                Ui.busy(b.btnSubmit, false);
                step2 = true;
                b.tvStep.setText(R.string.reset_step2);
                b.groupStep2.setVisibility(View.VISIBLE);
                b.etEmail.setEnabled(false);
                b.btnSubmit.setText(R.string.change_password);
                if (r != null && r.message != null) Ui.snack(b.getRoot(), r.message);
            }

            @Override public void onError(ApiError e) {
                Ui.busy(b.btnSubmit, false);
                Ui.error(b.getRoot(), e);
            }
        });
    }

    private void reset() {
        Ui.clearErrors(b.tilCode, b.tilPassword, b.tilPassword2);
        String code = Ui.text(b.etCode);
        String pw = Ui.text(b.etPassword);
        boolean ok = true;
        if (!code.matches("^\\d{6}$")) { b.tilCode.setError(getString(R.string.error_required)); ok = false; }
        if (pw.length() < 8 || !pw.matches(".*[A-Za-z].*") || !pw.matches(".*\\d.*")) { b.tilPassword.setError(getString(R.string.error_password_short)); ok = false; }
        if (!pw.equals(Ui.text(b.etPassword2))) { b.tilPassword2.setError(getString(R.string.error_password_mismatch)); ok = false; }
        if (!ok) return;
        Ui.busy(b.btnSubmit, true);
        ApiClient.auth().resetPassword(new ResetPasswordRequest(Ui.text(b.etEmail), code, pw)).enqueue(new ApiCallback<MessageResult>() {
            @Override public void onSuccess(MessageResult r, PageMeta meta) {
                Ui.toast(ForgotPasswordActivity.this, r != null && r.message != null ? r.message : getString(R.string.password_changed));
                finish();
            }

            @Override public void onError(ApiError e) {
                Ui.busy(b.btnSubmit, false);
                if ("RESET_CODE_INVALID".equals(e.code)) b.tilCode.setError(e.message);
                else Ui.error(b.getRoot(), e);
            }
        });
    }
}
