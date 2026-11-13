package com.aqarcom.app.ui.main;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.aqarcom.app.BuildConfig;
import com.aqarcom.app.R;
import com.aqarcom.app.data.model.ApiError;
import com.aqarcom.app.data.model.PageMeta;
import com.aqarcom.app.data.model.User;
import com.aqarcom.app.data.network.ApiCallback;
import com.aqarcom.app.data.network.ApiClient;
import com.aqarcom.app.data.network.TokenManager;
import com.aqarcom.app.databinding.FragmentProfileBinding;
import com.aqarcom.app.databinding.ItemMenuRowBinding;
import com.aqarcom.app.ui.account.ChangePasswordActivity;
import com.aqarcom.app.ui.account.EditProfileActivity;
import com.aqarcom.app.ui.account.VerificationActivity;
import com.aqarcom.app.ui.auth.AuthRouter;
import com.aqarcom.app.ui.auth.LoginActivity;
import com.aqarcom.app.ui.auth.RegisterActivity;
import com.aqarcom.app.ui.common.Labels;
import com.aqarcom.app.ui.common.Ui;
import com.aqarcom.app.ui.property.MyPropertiesActivity;
import com.aqarcom.app.ui.property.PropertyFormActivity;
import com.aqarcom.app.ui.tender.MyBidsActivity;
import com.aqarcom.app.ui.tender.TenderFormActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** Account tab: identity, status pills, and role-specific shortcuts. */
public class ProfileFragment extends Fragment {
    private FragmentProfileBinding b;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        b = FragmentProfileBinding.inflate(inflater, container, false);
        b.btnLogin.setOnClickListener(v -> startActivity(new Intent(requireContext(), LoginActivity.class)));
        b.btnRegister.setOnClickListener(v -> startActivity(new Intent(requireContext(), RegisterActivity.class)));
        b.tvVersion.setText(getString(R.string.app_name) + " · v" + BuildConfig.VERSION_NAME);
        return b.getRoot();
    }

    @Override
    public void onResume() {
        super.onResume();
        bind(TokenManager.get(requireContext()).getUser());
        refresh();
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden) { bind(TokenManager.get(requireContext()).getUser()); refresh(); }
    }

    /** Status / role may have changed on the admin dashboard — fetch the latest user. */
    private void refresh() {
        if (!TokenManager.get(requireContext()).isLoggedIn()) return;
        ApiClient.auth().me().enqueue(new ApiCallback<User>() {
            @Override public void onSuccess(User u, PageMeta meta) {
                if (!isAdded()) return;
                User old = TokenManager.get(requireContext()).getUser();
                TokenManager.get(requireContext()).saveUser(u);
                bind(u);
                // role changed by the admin → rebuild the bottom navigation
                if (old != null && !old.role.equals(u.role)) requireActivity().recreate();
            }
            @Override public void onError(ApiError e) { }
        });
    }

    private void bind(User u) {
        if (b == null) return;
        boolean guest = u == null;
        b.groupGuest.setVisibility(guest ? View.VISIBLE : View.GONE);
        b.groupPills.setVisibility(guest ? View.GONE : View.VISIBLE);
        b.tvName.setText(guest ? getString(R.string.hello_guest) : u.fullName);
        b.tvEmail.setText(guest ? "" : u.email);
        b.tvAvatar.setText(guest ? "؟" : u.fullName.substring(0, 1));
        b.menuContainer.removeAllViews();

        if (!guest) {
            b.tvRole.setText(Labels.of(Labels.ROLES, u.role));
            Ui.accountPill(b.tvAccount, u.status);
            boolean contractor = TokenManager.Roles.CONTRACTOR.equals(u.role);
            if (contractor) Ui.verificationPill(b.tvVerification, u.verificationStatus);
            else b.tvVerification.setVisibility(View.GONE);
            b.tvPending.setVisibility(u.isPendingApproval() ? View.VISIBLE : View.GONE);

            switch (u.role) {
                case TokenManager.Roles.PROPERTY_OWNER:
                    row(R.drawable.ic_home, getString(R.string.my_properties), () -> open(MyPropertiesActivity.class));
                    row(R.drawable.ic_add, getString(R.string.add_property), () -> open(PropertyFormActivity.class));
                    break;
                case TokenManager.Roles.PROJECT_OWNER:
                    row(R.drawable.ic_gavel, getString(R.string.my_tenders), () -> ((MainActivity) requireActivity()).selectTab(R.id.nav_tenders));
                    row(R.drawable.ic_add, getString(R.string.new_tender), () -> open(TenderFormActivity.class));
                    break;
                case TokenManager.Roles.CONTRACTOR:
                    row(R.drawable.ic_gavel, getString(R.string.my_bids), () -> open(MyBidsActivity.class));
                    row(R.drawable.ic_verified, getString(R.string.verification), () -> open(VerificationActivity.class));
                    break;
                default:
                    break;
            }
            if (TokenManager.get(requireContext()).canSeeTenders()) {
                // B2B users have no favorites tab — reach the wishlist from here
                row(R.drawable.ic_favorite, getString(R.string.nav_favorites), () -> open(FavoritesActivity.class));
            }
            row(R.drawable.ic_person, getString(R.string.edit_profile), () -> open(EditProfileActivity.class));
            row(R.drawable.ic_lock, getString(R.string.change_password), () -> open(ChangePasswordActivity.class));
        }
        row(R.drawable.ic_info, getString(R.string.about_app), () -> new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.app_name).setMessage(R.string.about_text).setPositiveButton(R.string.close, null).show());
        if (!guest) {
            ItemMenuRowBinding out = row(R.drawable.ic_logout, getString(R.string.logout), () ->
                    Ui.confirm(requireContext(), getString(R.string.logout), getString(R.string.logout_confirm), getString(R.string.logout),
                            () -> AuthRouter.logout(requireActivity())));
            out.tvLabel.setTextColor(ContextCompat.getColor(requireContext(), R.color.aq_red));
            out.ivIcon.setColorFilter(ContextCompat.getColor(requireContext(), R.color.aq_red));
            out.ivChevron.setVisibility(View.GONE);
        }
    }

    private ItemMenuRowBinding row(@DrawableRes int icon, String label, Runnable action) {
        ItemMenuRowBinding r = ItemMenuRowBinding.inflate(getLayoutInflater(), b.menuContainer, true);
        r.ivIcon.setImageResource(icon);
        r.tvLabel.setText(label);
        r.getRoot().setOnClickListener(v -> action.run());
        return r;
    }

    private void open(Class<?> c) {
        startActivity(new Intent(requireContext(), c));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }
}
