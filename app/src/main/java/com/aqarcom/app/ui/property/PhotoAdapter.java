package com.aqarcom.app.ui.property;

import android.net.Uri;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.aqarcom.app.databinding.ItemPhotoThumbBinding;
import com.aqarcom.app.ui.common.Images;

import java.util.ArrayList;
import java.util.List;

/** Thumbnails in the listing form: already-uploaded images (id + url) and newly picked ones (uri). */
public class PhotoAdapter extends RecyclerView.Adapter<PhotoAdapter.VH> {

    public static class Photo {
        public final String id;   // existing image id (edit mode), null for new
        public final String url;
        public final Uri uri;     // new local image, null for existing

        public Photo(String id, String url, Uri uri) { this.id = id; this.url = url; this.uri = uri; }
    }

    public interface Listener { void onRemove(Photo p, int position); }

    private final List<Photo> items = new ArrayList<>();
    private final Listener listener;

    public PhotoAdapter(Listener l) { this.listener = l; }

    public List<Photo> items() { return items; }

    public void add(Photo p) {
        items.add(p);
        notifyItemInserted(items.size() - 1);
    }

    public void removeAt(int pos) {
        if (pos < 0 || pos >= items.size()) return;
        items.remove(pos);
        notifyItemRemoved(pos);
    }

    public List<Uri> newUris() {
        List<Uri> out = new ArrayList<>();
        for (Photo p : items) if (p.uri != null) out.add(p.uri);
        return out;
    }

    @Override public int getItemCount() { return items.size(); }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(ItemPhotoThumbBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Photo p = items.get(position);
        if (p.uri != null) h.b.ivPhoto.setImageURI(p.uri);
        else Images.load(h.b.ivPhoto, p.url);
        h.b.btnRemove.setOnClickListener(v -> listener.onRemove(p, h.getBindingAdapterPosition()));
    }

    static class VH extends RecyclerView.ViewHolder {
        final ItemPhotoThumbBinding b;
        VH(ItemPhotoThumbBinding b) { super(b.getRoot()); this.b = b; }
    }
}
