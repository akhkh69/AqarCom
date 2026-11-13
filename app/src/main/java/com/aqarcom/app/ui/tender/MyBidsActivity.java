package com.aqarcom.app.ui.tender;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.aqarcom.app.R;
import com.aqarcom.app.data.model.ApiError;
import com.aqarcom.app.data.model.BidWithTender;
import com.aqarcom.app.data.model.PageMeta;
import com.aqarcom.app.data.network.ApiCallback;
import com.aqarcom.app.data.network.ApiClient;
import com.aqarcom.app.databinding.FragmentListBinding;
import com.aqarcom.app.databinding.ItemMyBidBinding;
import com.aqarcom.app.ui.base.BaseActivity;
import com.aqarcom.app.ui.common.Fmt;
import com.aqarcom.app.ui.common.Labels;
import com.aqarcom.app.ui.common.StateView;
import com.aqarcom.app.ui.common.Ui;

import java.util.ArrayList;
import java.util.List;

/** Contractor's bids (including withdrawn ones) with their outcome. */
public class MyBidsActivity extends BaseActivity {
    private FragmentListBinding b;
    private StateView state;
    private final List<BidWithTender> items = new ArrayList<>();
    private RecyclerView.Adapter<VH> adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = FragmentListBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        setupToolbar(b.toolbar, getString(R.string.my_bids), true);
        state = new StateView(b.state);
        adapter = new RecyclerView.Adapter<VH>() {
            @NonNull
            @Override
            public VH onCreateViewHolder(@NonNull ViewGroup p, int t) {
                return new VH(ItemMyBidBinding.inflate(LayoutInflater.from(p.getContext()), p, false));
            }

            @Override
            public void onBindViewHolder(@NonNull VH h, int pos) {
                BidWithTender bid = items.get(pos);
                ItemMyBidBinding v = h.b;
                if (bid.tender != null) {
                    Ui.tenderPill(v.tvStatus, bid.tender.status);
                    v.tvTitle.setText(bid.tender.title);
                } else v.tvTitle.setText("—");
                if (bid.isWithdrawn) Ui.pill(v.tvOutcome, "مسحوب", Ui.Tone.NEUTRAL);
                else Ui.pill(v.tvOutcome, Labels.of(Labels.OUTCOME, bid.outcome),
                        "won".equals(bid.outcome) ? Ui.Tone.ACTIVE : "lost".equals(bid.outcome) ? Ui.Tone.DANGER : Ui.Tone.PENDING);
                v.tvOffer.setText(getString(R.string.total_price) + ": " + Fmt.number(bid.totalPrice) + "  ·  " + bid.durationDays + " " + getString(R.string.days_unit));
                v.tvTime.setText(Fmt.dateTime(bid.submittedAt));
                v.getRoot().setOnClickListener(x -> {
                    if (bid.tender != null) startActivity(TenderDetailActivity.intent(MyBidsActivity.this, bid.tender.id));
                });
            }

            @Override public int getItemCount() { return items.size(); }
        };
        b.list.setLayoutManager(new LinearLayoutManager(this));
        b.list.setAdapter(adapter);
        b.swipe.setColorSchemeResources(R.color.aq_blue, R.color.aq_gold);
        b.swipe.setOnRefreshListener(this::load);
    }

    @Override
    protected void onResume() {
        super.onResume();
        load();
    }

    private void load() {
        if (items.isEmpty()) state.loading();
        ApiClient.bids().mine(1, 100, true).enqueue(new ApiCallback<List<BidWithTender>>() {
            @Override public void onSuccess(List<BidWithTender> list, PageMeta meta) {
                b.swipe.setRefreshing(false);
                items.clear();
                items.addAll(list);
                adapter.notifyDataSetChanged();
                if (items.isEmpty()) state.empty(R.drawable.ic_gavel, getString(R.string.no_bids), null);
                else state.hide();
            }

            @Override public void onError(ApiError e) {
                b.swipe.setRefreshing(false);
                state.error(e, MyBidsActivity.this::load);
            }
        });
    }

    static class VH extends RecyclerView.ViewHolder {
        final ItemMyBidBinding b;
        VH(ItemMyBidBinding b) { super(b.getRoot()); this.b = b; }
    }
}
