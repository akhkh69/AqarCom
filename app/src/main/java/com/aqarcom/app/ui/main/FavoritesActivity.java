package com.aqarcom.app.ui.main;

import android.os.Bundle;

import com.aqarcom.app.R;
import com.aqarcom.app.ui.base.BaseActivity;

/** Hosts {@link FavoritesFragment} for B2B users (their bottom bar shows Tenders instead of Favorites). */
public class FavoritesActivity extends BaseActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_container);
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.container, FavoritesActivity.withBack(new FavoritesFragment()))
                    .commit();
        }
    }

    private static FavoritesFragment withBack(FavoritesFragment f) {
        Bundle a = new Bundle();
        a.putBoolean(FavoritesFragment.ARG_SHOW_BACK, true);
        f.setArguments(a);
        return f;
    }
}
