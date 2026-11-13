package com.aqarcom.app.ui.base;

import android.content.Context;
import android.content.res.Configuration;

import java.util.Locale;

/** Forces Arabic + right-to-left layout regardless of the phone's language. */
public final class LocaleHelper {
    private LocaleHelper() {}

    public static Context wrap(Context base) {
        Locale locale = new Locale("ar");
        Locale.setDefault(locale);
        Configuration config = new Configuration(base.getResources().getConfiguration());
        config.setLocale(locale);
        config.setLayoutDirection(locale);
        return base.createConfigurationContext(config);
    }
}
