package com.aqarcom.app.ui.property;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.aqarcom.app.R;
import com.aqarcom.app.ui.common.Images;

import java.util.ArrayList;
import java.util.List;

/** Swipeable image gallery for the property detail screen. */
public class ImagePagerAdapter extends RecyclerView.Adapter<ImagePagerAdapter.VH> {
    private final List<String> urls = new ArrayList<>();

    public void setUrls(List<String> list) {
        urls.clear();
        urls.addAll(list);
        notifyDataSetChanged();
    }

    @Override public int getItemCount() { return Math.max(1, urls.size()); }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH((ImageView) LayoutInflater.from(parent.getContext()).inflate(R.layout.item_image_page, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Images.load(h.iv, urls.isEmpty() ? null : urls.get(position));
    }

    static class VH extends RecyclerView.ViewHolder {
        final ImageView iv;
        VH(ImageView iv) { super(iv); this.iv = iv; }
    }
}
