package com.aqarcom.app.ui.account;

import android.os.Bundle;

import com.aqarcom.app.R;
import com.aqarcom.app.data.model.ApiError;
import com.aqarcom.app.data.model.ChangePasswordRequest;
import com.aqarcom.app.data.model.MessageResult;
import com.aqarcom.app.data.model.PageMeta;
import com.aqarcom.app.data.network.ApiCallback;
import com.aqarcom.app.data.network.ApiClient;
import com.aqarcom.app.databinding.ActivityChangePasswordBinding;
import com.aqarcom.app.ui.base.BaseActivity;
import com.aqarcom.app.ui.common.Ui;

/** Changing the password revokes every refresh token on the server (other devices must log in again). */
public class ChangePasswordActivity extends BaseActivity {
    private ActivityChangePasswordBinding b;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityChangePasswordBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        setupToolbar(b.toolbar, null, true);
        b.btnSubmit.setOnClickListener(v -> save());
    }

    private void save() {
        Ui.clearErrors(b.tilCurrent, b.tilPassword, b.tilPassword2);
        boolean ok = Ui.require(b.tilCurrent, b.etCurrent, getString(R.string.error_required));
        String pw = Ui.text(b.etPassword);
        if (pw.length() < 8 || !pw.matches(".*[A-Za-z].*") || !pw.matches(".*\\d.*")) { b.tilPassword.setError(getString(R.string.error_password_short)); ok = false; }
        if (!pw.equals(Ui.text(b.etPassword2))) { b.tilPassword2.setError(getString(R.string.error_password_mismatch)); ok = false; }
        if (!ok) return;
        Ui.busy(b.btnSubmit, true);
        ApiClient.auth().changePassword(new ChangePasswordRequest(Ui.text(b.etCurrent), pw)).enqueue(new ApiCallback<MessageResult>() {
            @Override public void onSuccess(MessageResult r, PageMeta meta) {
                Ui.toast(ChangePasswordActivity.this, getString(R.string.password_changed));
                finish();
            }

            @Override public void onError(ApiError e) {
                Ui.busy(b.btnSubmit, false);
                if ("WRONG_PASSWORD".equals(e.code)) b.tilCurrent.setError(e.message);
                else Ui.error(b.getRoot(), e);
            }
        });
    }
}
