package com.aqarcom.app.ui.property;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.aqarcom.app.R;
import com.aqarcom.app.data.model.Property;
import com.aqarcom.app.databinding.ItemPropertyBinding;
import com.aqarcom.app.ui.common.Fmt;
import com.aqarcom.app.ui.common.Images;
import com.aqarcom.app.ui.common.Labels;
import com.aqarcom.app.ui.common.Ui;

import java.util.ArrayList;
import java.util.List;

/** Property cards for Home, Favorites, Map fallback and My Properties. */
public class PropertyAdapter extends RecyclerView.Adapter<PropertyAdapter.VH> {

    public interface Listener {
        void onOpen(Property p);
        void onToggleFavorite(Property p, int position);
    }

    private final List<Property> items = new ArrayList<>();
    private final Listener listener;
    private final boolean showStatus;

    public PropertyAdapter(Listener listener, boolean showStatus) {
        this.listener = listener;
        this.showStatus = showStatus;
    }

    public void setItems(List<Property> list) {
        items.clear();
        if (list != null) items.addAll(list);
        notifyDataSetChanged();
    }

    public void addItems(List<Property> list) {
        int start = items.size();
        items.addAll(list);
        notifyItemRangeInserted(start, list.size());
    }

    public void removeAt(int pos) {
        if (pos < 0 || pos >= items.size()) return;
        items.remove(pos);
        notifyItemRemoved(pos);
    }

    public Property get(int pos) { return items.get(pos); }

    @Override public int getItemCount() { return items.size(); }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(ItemPropertyBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Property p = items.get(position);
        ItemPropertyBinding v = h.b;
        Images.load(v.ivImage, p.thumbnailUrl);
        v.tvTitle.setText(p.title);
        v.tvPrice.setText(Fmt.money(p.price, p.currency) + ("rent".equals(p.purpose) ? " / شهرياً" : ""));
        v.tvPurpose.setText(Labels.of(Labels.PURPOSES, p.purpose));
        v.tvType.setText(Labels.of(Labels.PROPERTY_TYPES, p.propertyType));
        String loc = p.city == null ? "" : p.city;
        if (p.district != null && !p.district.isEmpty()) loc += " · " + p.district;
        v.tvLocation.setText(loc);
        v.tvArea.setText(Fmt.area(p.area));
        v.tvRooms.setVisibility(p.rooms > 0 ? View.VISIBLE : View.GONE);
        v.tvRooms.setText(String.valueOf(p.rooms));
        if (p.distanceMeters != null) {
            v.tvDistance.setVisibility(View.VISIBLE);
            v.tvDistance.setText(p.distanceMeters >= 1000 ? Fmt.number(p.distanceMeters / 1000.0) + " كم" : p.distanceMeters + " م");
        } else v.tvDistance.setVisibility(View.GONE);

        v.btnFavorite.setVisibility(p.isMine ? View.GONE : View.VISIBLE);
        v.btnFavorite.setImageResource(p.isFavorite ? R.drawable.ic_favorite : R.drawable.ic_favorite_border);
        v.btnFavorite.setOnClickListener(x -> listener.onToggleFavorite(p, h.getBindingAdapterPosition()));

        if (showStatus && p.status != null && !"active".equals(p.status)) {
            Ui.pill(v.tvStatus, Labels.of(Labels.CONTENT, p.status), Ui.Tone.DANGER);
        } else v.tvStatus.setVisibility(View.GONE);

        v.getRoot().setOnClickListener(x -> listener.onOpen(p));
    }

    static class VH extends RecyclerView.ViewHolder {
        final ItemPropertyBinding b;
        VH(ItemPropertyBinding b) { super(b.getRoot()); this.b = b; }
    }
}
