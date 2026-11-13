package com.aqarcom.app.ui.tender;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.aqarcom.app.data.model.Tender;
import com.aqarcom.app.databinding.ItemTenderBinding;
import com.aqarcom.app.ui.common.Fmt;
import com.aqarcom.app.ui.common.Labels;
import com.aqarcom.app.ui.common.Ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class TenderAdapter extends RecyclerView.Adapter<TenderAdapter.VH> {

    public interface Listener { void onOpen(Tender t); }

    private final List<Tender> items = new ArrayList<>();
    private final Listener listener;

    public TenderAdapter(Listener l) { this.listener = l; }

    public void setItems(List<Tender> list) {
        items.clear();
        if (list != null) items.addAll(list);
        notifyDataSetChanged();
    }

    public void addItems(List<Tender> list) {
        int s = items.size();
        items.addAll(list);
        notifyItemRangeInserted(s, list.size());
    }

    @Override public int getItemCount() { return items.size(); }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(ItemTenderBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Tender t = items.get(position);
        ItemTenderBinding v = h.b;
        Ui.tenderPill(v.tvStatus, t.status);
        v.tvType.setText(Labels.of(Labels.TENDER_TYPES, t.tenderType));
        v.tvTitle.setText(t.title);
        String owner = t.owner == null ? "" : (t.owner.companyName != null ? t.owner.companyName : t.owner.fullName);
        if (t.city != null) owner = owner + (owner.isEmpty() ? "" : " · ") + t.city;
        v.tvOwner.setText(owner);
        v.tvDeadline.setText("open".equals(t.status) ? "يُغلق بعد " + Fmt.countdown(t.secondsRemaining) : "الإغلاق: " + Fmt.dateTime(t.deadline));
        v.tvWeights.setText(String.format(Locale.US, "السعر %d%% · المدة %d%%", t.priceWeight, t.durationWeight));

        if (t.myBid != null) {
            Ui.pill(v.tvBadge, "عرضي: " + Labels.of(Labels.OUTCOME, t.myBid.outcome), "won".equals(t.myBid.outcome) ? Ui.Tone.ACTIVE : Ui.Tone.PENDING);
        } else if (t.isMine && t.activeBidsCount != null) {
            Ui.pill(v.tvBadge, t.activeBidsCount + " عروض", Ui.Tone.BLUE);
        } else v.tvBadge.setVisibility(View.GONE);

        v.getRoot().setOnClickListener(x -> listener.onOpen(t));
    }

    static class VH extends RecyclerView.ViewHolder {
        final ItemTenderBinding b;
        VH(ItemTenderBinding b) { super(b.getRoot()); this.b = b; }
    }
}
