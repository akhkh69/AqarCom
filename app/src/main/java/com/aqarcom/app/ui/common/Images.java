package com.aqarcom.app.ui.common;

import android.widget.ImageView;

import com.aqarcom.app.R;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;

/** Glide wrapper for remote property images. */
public final class Images {
    private Images() {}

    public static void load(ImageView iv, String url) {
        if (url == null || url.isEmpty()) {
            iv.setImageResource(R.drawable.placeholder_property);
            return;
        }
        Glide.with(iv)
                .load(url)
                .placeholder(R.drawable.placeholder_property)
                .error(R.drawable.placeholder_property)
                .centerCrop()
                .transition(DrawableTransitionOptions.withCrossFade())
                .into(iv);
    }
}
