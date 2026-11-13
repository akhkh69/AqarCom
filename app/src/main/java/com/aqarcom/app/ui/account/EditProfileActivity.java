package com.aqarcom.app.ui.account;

import android.os.Bundle;
import android.view.View;

import com.aqarcom.app.R;
import com.aqarcom.app.data.model.ApiError;
import com.aqarcom.app.data.model.PageMeta;
import com.aqarcom.app.data.model.UpdateProfileRequest;
import com.aqarcom.app.data.model.User;
import com.aqarcom.app.data.network.ApiCallback;
import com.aqarcom.app.data.network.ApiClient;
import com.aqarcom.app.data.network.TokenManager;
import com.aqarcom.app.databinding.ActivityEditProfileBinding;
import com.aqarcom.app.ui.base.BaseActivity;
import com.aqarcom.app.ui.common.Labels;
import com.aqarcom.app.ui.common.Ui;

public class EditProfileActivity extends BaseActivity {
    private ActivityEditProfileBinding b;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityEditProfileBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        setupToolbar(b.toolbar, null, true);
        Ui.dropdown(b.ddCity, Labels.CITIES);

        User u = TokenManager.get(this).getUser();
        if (u != null) {
            b.etName.setText(u.fullName);
            b.etPhone.setText(u.phone);
            b.etWhatsapp.setText(u.whatsapp);
            b.etCompany.setText(u.companyName);
            Ui.dropdownSelect(b.ddCity, Labels.CITIES, u.city);
            boolean company = TokenManager.Roles.CONTRACTOR.equals(u.role) || TokenManager.Roles.PROJECT_OWNER.equals(u.role);
            b.tilCompany.setVisibility(company ? View.VISIBLE : View.GONE);
        }
        b.btnSubmit.setOnClickListener(v -> save());
    }

    private void save() {
        Ui.clearErrors(b.tilName, b.tilPhone);
        boolean ok = Ui.require(b.tilName, b.etName, getString(R.string.error_required));
        if (!Ui.text(b.etPhone).matches("^\\+?[0-9]{7,15}$")) { b.tilPhone.setError(getString(R.string.phone_hint)); ok = false; }
        String wa = Ui.text(b.etWhatsapp);
        if (!wa.isEmpty() && !wa.matches("^\\+?[0-9]{7,15}$")) { b.tilWhatsapp.setError(getString(R.string.phone_hint)); ok = false; }
        if (!ok) return;
        UpdateProfileRequest r = new UpdateProfileRequest();
        r.fullName = Ui.text(b.etName);
        r.phone = Ui.text(b.etPhone);
        r.whatsapp = wa.isEmpty() ? null : wa;
        String company = Ui.text(b.etCompany);
        if (b.tilCompany.getVisibility() == View.VISIBLE && !company.isEmpty()) r.companyName = company;
        r.city = Ui.dropdownKey(b.ddCity, Labels.CITIES);
        Ui.busy(b.btnSubmit, true);
        ApiClient.auth().updateMe(r).enqueue(new ApiCallback<User>() {
            @Override public void onSuccess(User u, PageMeta meta) {
                TokenManager.get(EditProfileActivity.this).saveUser(u);
                Ui.toast(EditProfileActivity.this, getString(R.string.profile_saved));
                finish();
            }

            @Override public void onError(ApiError e) {
                Ui.busy(b.btnSubmit, false);
                Ui.error(b.getRoot(), e);
            }
        });
    }
}
