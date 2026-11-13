package com.aqarcom.app.ui.main;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.IdRes;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.aqarcom.app.R;
import com.aqarcom.app.data.model.ApiError;
import com.aqarcom.app.data.model.MessageResult;
import com.aqarcom.app.data.model.PageMeta;
import com.aqarcom.app.data.model.User;
import com.aqarcom.app.data.network.ApiCallback;
import com.aqarcom.app.data.network.ApiClient;
import com.aqarcom.app.data.network.TokenManager;
import com.aqarcom.app.databinding.ActivityMainBinding;
import com.aqarcom.app.ui.auth.LoginActivity;
import com.aqarcom.app.ui.base.BaseActivity;
import com.google.android.material.badge.BadgeDrawable;

/**
 * Home shell with a bottom navigation bar.
 * Individual users / property owners / guests: Home · Map · Favorites · Notifications · Account
 * Project owners / contractors (B2B — NFR-04): Home · Map · Tenders · Notifications · Account
 */
public class MainActivity extends BaseActivity {
    public static final String EXTRA_TAB = "tab";
    public static final String TAB_TENDERS = "tenders";
    public static final String TAB_NOTIFICATIONS = "notifications";

    private ActivityMainBinding b;
    private Fragment active;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        boolean b2b = TokenManager.get(this).canSeeTenders();
        b.bottomNav.inflateMenu(b2b ? R.menu.bottom_nav_b2b : R.menu.bottom_nav_consumer);
        b.bottomNav.setOnItemSelectedListener(item -> {
            show(item.getItemId());
            return true;
        });

        // fragments survive rotation through the FragmentManager; restore the visible one
        if (savedInstanceState == null) {
            String tab = getIntent().getStringExtra(EXTRA_TAB);
            int start = R.id.nav_home;
            if (TAB_TENDERS.equals(tab) && b2b) start = R.id.nav_tenders;
            if (TAB_NOTIFICATIONS.equals(tab)) start = R.id.nav_notifications;
            // check the item without firing the listener, then show it once
            b.bottomNav.getMenu().findItem(start).setChecked(true);
            show(start);
        } else {
            for (Fragment f : getSupportFragmentManager().getFragments()) if (!f.isHidden()) active = f;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshBadge();
        refreshUser();
    }

    /**
     * The account may have been approved, suspended or given a new role on the admin dashboard
     * since the last launch — re-read it and react.
     */
    private void refreshUser() {
        TokenManager tm = TokenManager.get(this);
        if (!tm.isLoggedIn()) return;
        final User before = tm.getUser();
        ApiClient.auth().me().enqueue(new ApiCallback<User>() {
            @Override public void onSuccess(User u, PageMeta meta) {
                tm.saveUser(u);
                boolean b2bBefore = before != null && isB2b(before.role);
                if (before != null && b2bBefore != isB2b(u.role)) recreate(); // bottom bar depends on the role
            }

            @Override public void onError(ApiError e) {
                if ("ACCOUNT_SUSPENDED".equals(e.code) || "ACCOUNT_REJECTED".equals(e.code)) {
                    tm.clear();
                    startActivity(new Intent(MainActivity.this, LoginActivity.class)
                            .putExtra(LoginActivity.EXTRA_MESSAGE, e.message)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
                    finish();
                }
            }
        });
    }

    private static boolean isB2b(String role) {
        return TokenManager.Roles.PROJECT_OWNER.equals(role) || TokenManager.Roles.CONTRACTOR.equals(role) || TokenManager.Roles.ADMIN.equals(role);
    }

    /** Switch tab programmatically (e.g. from Profile → Favorites). */
    public void selectTab(@IdRes int id) {
        b.bottomNav.setSelectedItemId(id);
    }

    private void show(@IdRes int id) {
        String tag = "tab_" + id;
        FragmentManager fm = getSupportFragmentManager();
        Fragment f = fm.findFragmentByTag(tag);
        FragmentTransaction tx = fm.beginTransaction().setReorderingAllowed(true);
        if (active != null && active != f) tx.hide(active);
        if (f == null) {
            f = create(id);
            tx.add(R.id.fragment_container, f, tag);
        } else {
            tx.show(f);
        }
        tx.commit();
        active = f;
    }

    private Fragment create(@IdRes int id) {
        if (id == R.id.nav_map) return new MapFragment();
        if (id == R.id.nav_favorites) return new FavoritesFragment();
        if (id == R.id.nav_tenders) return new TendersFragment();
        if (id == R.id.nav_notifications) return new NotificationsFragment();
        if (id == R.id.nav_profile) return new ProfileFragment();
        return new HomeFragment();
    }

    /** Unread notifications badge. */
    public void refreshBadge() {
        if (!TokenManager.get(this).isLoggedIn()) return;
        ApiClient.notifications().unreadCount().enqueue(new ApiCallback<MessageResult>() {
            @Override public void onSuccess(MessageResult r, PageMeta meta) {
                int n = r == null || r.unread == null ? 0 : r.unread;
                if (n > 0) {
                    BadgeDrawable badge = b.bottomNav.getOrCreateBadge(R.id.nav_notifications);
                    badge.setNumber(n);
                    badge.setBackgroundColor(getColor(R.color.aq_gold));
                    badge.setVisible(true);
                } else {
                    b.bottomNav.removeBadge(R.id.nav_notifications);
                }
            }

            @Override public void onError(ApiError e) { }
        });
    }
}
