package com.aqarcom.app.ui.auth;

import android.os.Bundle;
import android.util.Patterns;
import android.view.View;

import com.aqarcom.app.R;
import com.aqarcom.app.data.model.ApiError;
import com.aqarcom.app.data.model.AuthResult;
import com.aqarcom.app.data.model.PageMeta;
import com.aqarcom.app.data.model.RegisterRequest;
import com.aqarcom.app.data.network.ApiCallback;
import com.aqarcom.app.data.network.ApiClient;
import com.aqarcom.app.data.network.TokenManager;
import com.aqarcom.app.databinding.ActivityRegisterBinding;
import com.aqarcom.app.ui.base.BaseActivity;
import com.aqarcom.app.ui.common.Labels;
import com.aqarcom.app.ui.common.Ui;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputLayout;

import java.util.HashMap;
import java.util.Map;

/** FR-01 registration with one of four roles. Privileged roles start "pending" until approved on the dashboard. */
public class RegisterActivity extends BaseActivity {
    private ActivityRegisterBinding b;
    private String role = TokenManager.Roles.USER;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityRegisterBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        setupToolbar(b.toolbar, null, true);

        Ui.dropdown(b.ddCity, Labels.CITIES);
        bindRole(b.cardUser, TokenManager.Roles.USER);
        bindRole(b.cardPropertyOwner, TokenManager.Roles.PROPERTY_OWNER);
        bindRole(b.cardProjectOwner, TokenManager.Roles.PROJECT_OWNER);
        bindRole(b.cardContractor, TokenManager.Roles.CONTRACTOR);
        selectRole(TokenManager.Roles.USER);

        b.btnSubmit.setOnClickListener(v -> submit());
        b.btnLogin.setOnClickListener(v -> finish());
    }

    private void bindRole(MaterialCardView card, String r) {
        card.setOnClickListener(v -> selectRole(r));
    }

    private void selectRole(String r) {
        role = r;
        b.cardUser.setChecked(TokenManager.Roles.USER.equals(r));
        b.cardPropertyOwner.setChecked(TokenManager.Roles.PROPERTY_OWNER.equals(r));
        b.cardProjectOwner.setChecked(TokenManager.Roles.PROJECT_OWNER.equals(r));
        b.cardContractor.setChecked(TokenManager.Roles.CONTRACTOR.equals(r));
        b.tilCompany.setVisibility(TokenManager.Roles.CONTRACTOR.equals(r) || TokenManager.Roles.PROJECT_OWNER.equals(r) ? View.VISIBLE : View.GONE);
        b.tvApprovalNote.setVisibility(TokenManager.Roles.USER.equals(r) ? View.GONE : View.VISIBLE);
    }

    private boolean validate() {
        Ui.clearErrors(b.tilName, b.tilEmail, b.tilPhone, b.tilPassword, b.tilPassword2, b.tilCompany);
        boolean ok = Ui.require(b.tilName, b.etName, getString(R.string.error_required));
        if (!Patterns.EMAIL_ADDRESS.matcher(Ui.text(b.etEmail)).matches()) { b.tilEmail.setError(getString(R.string.error_email)); ok = false; }
        if (!Ui.text(b.etPhone).matches("^\\+?[0-9]{7,15}$")) { b.tilPhone.setError(getString(R.string.phone_hint)); ok = false; }
        String pw = Ui.text(b.etPassword);
        if (pw.length() < 8 || !pw.matches(".*[A-Za-z].*") || !pw.matches(".*\\d.*")) { b.tilPassword.setError(getString(R.string.error_password_short)); ok = false; }
        if (!pw.equals(Ui.text(b.etPassword2))) { b.tilPassword2.setError(getString(R.string.error_password_mismatch)); ok = false; }
        if (TokenManager.Roles.CONTRACTOR.equals(role)) ok &= Ui.require(b.tilCompany, b.etCompany, getString(R.string.error_required));
        return ok;
    }

    private void submit() {
        Ui.hideKeyboard(this);
        if (!validate()) return;
        RegisterRequest r = new RegisterRequest();
        r.fullName = Ui.text(b.etName);
        r.email = Ui.text(b.etEmail);
        r.phone = Ui.text(b.etPhone);
        String wa = Ui.text(b.etWhatsapp);
        r.whatsapp = wa.isEmpty() ? null : wa;
        r.password = Ui.text(b.etPassword);
        r.role = role;
        String city = Ui.text(b.ddCity);
        r.city = city.isEmpty() ? null : city;
        String company = Ui.text(b.etCompany);
        r.companyName = b.tilCompany.getVisibility() == View.VISIBLE && !company.isEmpty() ? company : null;

        Ui.busy(b.btnSubmit, true);
        ApiClient.auth().register(r).enqueue(new ApiCallback<AuthResult>() {
            @Override public void onSuccess(AuthResult res, PageMeta meta) {
                TokenManager.get(RegisterActivity.this).save(res);
                AuthRouter.goHome(RegisterActivity.this, res.user);
            }

            @Override public void onError(ApiError e) {
                Ui.busy(b.btnSubmit, false);
                Map<String, TextInputLayout> f = new HashMap<>();
                f.put("fullName", b.tilName);
                f.put("email", b.tilEmail);
                f.put("phone", b.tilPhone);
                f.put("password", b.tilPassword);
                f.put("companyName", b.tilCompany);
                Ui.showFieldErrors(e, f);
                Ui.error(b.getRoot(), e);
            }
        });
    }
}
