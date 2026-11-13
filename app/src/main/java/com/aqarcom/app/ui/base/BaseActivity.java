package com.aqarcom.app.ui.base;

import android.content.Context;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.aqarcom.app.R;
import com.google.android.material.appbar.MaterialToolbar;

/** All screens extend this: Arabic RTL locale + a standard toolbar with a back arrow. */
public abstract class BaseActivity extends AppCompatActivity {

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.wrap(newBase));
    }

    protected void setupToolbar(MaterialToolbar toolbar, @Nullable CharSequence title, boolean showBack) {
        if (title != null) toolbar.setTitle(title);
        if (showBack) {
            toolbar.setNavigationIcon(R.drawable.ic_arrow_back);
            toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        }
    }
}
