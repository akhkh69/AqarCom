package com.aqarcom.app.ui.tender;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.aqarcom.app.R;
import com.aqarcom.app.data.model.ApiError;
import com.aqarcom.app.data.model.AwardRequest;
import com.aqarcom.app.data.model.PageMeta;
import com.aqarcom.app.data.model.Shortlist;
import com.aqarcom.app.data.model.Tender;
import com.aqarcom.app.data.network.ApiCallback;
import com.aqarcom.app.data.network.ApiClient;
import com.aqarcom.app.databinding.ActivityShortlistBinding;
import com.aqarcom.app.databinding.ItemShortlistBinding;
import com.aqarcom.app.ui.base.BaseActivity;
import com.aqarcom.app.ui.common.Fmt;
import com.aqarcom.app.ui.common.StateView;
import com.aqarcom.app.ui.common.Ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** FR-18 — the top 5 offers ranked by WSM score, with the engine label (FR-19); FR-20 award (final). */
public class ShortlistActivity extends BaseActivity {
    private static final String EXTRA_ID = "id";

    private ActivityShortlistBinding b;
    private StateView state;
    private String id;
    private Shortlist data;
    private final Adapter adapter = new Adapter();

    public static Intent intent(Context c, String id) {
        return new Intent(c, ShortlistActivity.class).putExtra(EXTRA_ID, id);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityShortlistBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        setupToolbar(b.toolbar, null, true);
        id = getIntent().getStringExtra(EXTRA_ID);
        state = new StateView(b.state);
        b.list.setLayoutManager(new LinearLayoutManager(this));
        b.list.setAdapter(adapter);
        load();
    }

    private void load() {
        state.loading();
        ApiClient.tenders().shortlist(id).enqueue(new ApiCallback<Shortlist>() {
            @Override public void onSuccess(Shortlist s, PageMeta meta) {
                data = s;
                state.hide();
                String engine = getString("fallback".equals(s.engine) ? R.string.engine_fallback : R.string.engine_wsm);
                String w = s.weights == null ? "" : String.format(Locale.US, " · السعر %d%% / المدة %d%%", s.weights.price, s.weights.duration);
                b.tvHeader.setText(engine + w + "\nعدد العروض المفروزة: " + s.bidsConsidered + " · " + Fmt.dateTime(s.evaluatedAt));
                b.tvHeader.setVisibility(View.VISIBLE);
                adapter.set(s.results);
                if (s.results == null || s.results.isEmpty()) state.empty(R.drawable.ic_star, getString(R.string.view_shortlist), null);
            }

            @Override public void onError(ApiError e) { state.error(e, ShortlistActivity.this::load); }
        });
    }

    private void award(Shortlist.Item item) {
        String who = item.contractor == null ? "" : (item.contractor.companyName != null ? item.contractor.companyName : item.contractor.fullName);
        Ui.confirm(this, getString(R.string.award_confirm_title), who + "\n\n" + getString(R.string.award_confirm_message), getString(R.string.award), () ->
                ApiClient.tenders().award(id, new AwardRequest(item.bidId)).enqueue(new ApiCallback<Tender>() {
                    @Override public void onSuccess(Tender t, PageMeta meta) {
                        Ui.snack(b.getRoot(), getString(R.string.awarded));
                        load();
                    }

                    @Override public void onError(ApiError e) { Ui.error(b.getRoot(), e); }
                }));
    }

    private class Adapter extends RecyclerView.Adapter<Adapter.VH> {
        private final List<Shortlist.Item> items = new ArrayList<>();

        void set(List<Shortlist.Item> list) {
            items.clear();
            if (list != null) items.addAll(list);
            notifyDataSetChanged();
        }

        @Override public int getItemCount() { return items.size(); }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(ItemShortlistBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            Shortlist.Item it = items.get(position);
            ItemShortlistBinding v = h.b;
            v.tvRank.setText(String.valueOf(it.rank));
            v.tvRank.setBackgroundResource(it.rank == 1 ? R.drawable.bg_rank_gold : R.drawable.bg_rank);
            String company = it.contractor == null ? "—" : (it.contractor.companyName != null ? it.contractor.companyName : it.contractor.fullName);
            v.tvCompany.setText(company);
            v.tvContact.setText(it.contractor == null ? "" : it.contractor.phone + "  ·  " + it.contractor.email);
            v.tvScore.setText(Fmt.percent(it.scorePercent));
            v.barScore.setProgress((int) Math.round(it.scorePercent));
            v.tvPrice.setText(getString(R.string.total_price) + ": " + Fmt.number(it.totalPrice));
            v.tvDays.setText(getString(R.string.duration_days) + ": " + it.durationDays);
            v.tvBreakdown.setText(String.format(Locale.US, "r(السعر) = %.3f  ·  r(المدة) = %.3f", it.normalizedPrice, it.normalizedDuration));
            v.tvNotes.setVisibility(TextUtils.isEmpty(it.notes) ? View.GONE : View.VISIBLE);
            v.tvNotes.setText(it.notes);

            boolean awardedThis = data.awardedBidId != null && data.awardedBidId.equals(it.bidId);
            if (awardedThis) Ui.pill(v.tvAwarded, getString(R.string.awarded), Ui.Tone.ACTIVE);
            else v.tvAwarded.setVisibility(View.GONE);
            v.card.setStrokeColor(getColor(awardedThis ? R.color.aq_emerald : R.color.aq_line));
            v.card.setStrokeWidth(Ui.dp(ShortlistActivity.this, awardedThis ? 2 : 1));

            boolean canAward = "shortlist_ready".equals(data.status) && data.awardedBidId == null;
            v.btnAward.setVisibility(canAward ? View.VISIBLE : View.GONE);
            v.btnAward.setOnClickListener(x -> award(it));
        }

        class VH extends RecyclerView.ViewHolder {
            final ItemShortlistBinding b;
            VH(ItemShortlistBinding b) { super(b.getRoot()); this.b = b; }
        }
    }
}
