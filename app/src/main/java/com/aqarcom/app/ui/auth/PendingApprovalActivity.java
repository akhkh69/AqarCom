package com.aqarcom.app.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import com.aqarcom.app.R;
import com.aqarcom.app.data.model.ApiError;
import com.aqarcom.app.data.model.PageMeta;
import com.aqarcom.app.data.model.User;
import com.aqarcom.app.data.network.ApiCallback;
import com.aqarcom.app.data.network.ApiClient;
import com.aqarcom.app.data.network.TokenManager;
import com.aqarcom.app.databinding.ActivityPendingApprovalBinding;
import com.aqarcom.app.ui.account.VerificationActivity;
import com.aqarcom.app.ui.base.BaseActivity;
import com.aqarcom.app.ui.common.Labels;
import com.aqarcom.app.ui.common.Ui;

/** Shown while the account status is "pending" — privileges come only from the admin dashboard. */
public class PendingApprovalActivity extends BaseActivity {
    private ActivityPendingApprovalBinding b;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityPendingApprovalBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        User u = TokenManager.get(this).getUser();
        b.tvRole.setText(Labels.of(Labels.ROLES, u == null ? null : u.role));
        boolean contractor = u != null && TokenManager.Roles.CONTRACTOR.equals(u.role);
        b.tvContractorHint.setVisibility(contractor ? View.VISIBLE : View.GONE);
        b.btnVerification.setVisibility(contractor ? View.VISIBLE : View.GONE);

        b.btnVerification.setOnClickListener(v -> startActivity(new Intent(this, VerificationActivity.class)));
        b.btnCheck.setOnClickListener(v -> check());
        b.btnBrowse.setOnClickListener(v -> AuthRouter.goGuest(this));
        b.btnLogout.setOnClickListener(v -> AuthRouter.logout(this));
    }

    private void check() {
        Ui.busy(b.btnCheck, true);
        ApiClient.auth().me().enqueue(new ApiCallback<User>() {
            @Override public void onSuccess(User u, PageMeta meta) {
                Ui.busy(b.btnCheck, false);
                TokenManager.get(PendingApprovalActivity.this).saveUser(u);
                if (u.isPendingApproval()) Ui.snack(b.getRoot(), getString(R.string.still_pending));
                else AuthRouter.goHome(PendingApprovalActivity.this, u);
            }

            @Override public void onError(ApiError e) {
                Ui.busy(b.btnCheck, false);
                if ("ACCOUNT_REJECTED".equals(e.code) || "ACCOUNT_SUSPENDED".equals(e.code)) {
                    TokenManager.get(PendingApprovalActivity.this).clear();
                    Intent i = new Intent(PendingApprovalActivity.this, LoginActivity.class)
                            .putExtra(LoginActivity.EXTRA_MESSAGE, e.message)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(i);
                    finish();
                } else Ui.error(b.getRoot(), e);
            }
        });
    }
}
