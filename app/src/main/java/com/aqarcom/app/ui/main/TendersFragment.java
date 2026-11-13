package com.aqarcom.app.ui.main;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.aqarcom.app.R;
import com.aqarcom.app.data.model.ApiError;
import com.aqarcom.app.data.model.PageMeta;
import com.aqarcom.app.data.model.Tender;
import com.aqarcom.app.data.model.User;
import com.aqarcom.app.data.network.ApiCallback;
import com.aqarcom.app.data.network.ApiClient;
import com.aqarcom.app.data.network.TokenManager;
import com.aqarcom.app.databinding.FragmentTendersBinding;
import com.aqarcom.app.ui.account.VerificationActivity;
import com.aqarcom.app.ui.common.EndlessScroll;
import com.aqarcom.app.ui.common.Labels;
import com.aqarcom.app.ui.common.StateView;
import com.aqarcom.app.ui.common.Ui;
import com.aqarcom.app.ui.tender.TenderAdapter;
import com.aqarcom.app.ui.tender.TenderDetailActivity;
import com.aqarcom.app.ui.tender.TenderFormActivity;
import com.google.android.material.chip.Chip;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * B2B section (hidden from individual users — NFR-04).
 * Contractor: open tenders + "verify your account" banner until verified (BR-09).
 * Project owner: toggle between open tenders and "my tenders" (all statuses), FAB to create one.
 */
public class TendersFragment extends Fragment implements TenderAdapter.Listener {
    private FragmentTendersBinding b;
    private TenderAdapter adapter;
    private StateView state;
    private EndlessScroll scroll;
    private boolean mine;
    private String type;
    private int page = 1;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        b = FragmentTendersBinding.inflate(inflater, container, false);
        state = new StateView(b.state);
        adapter = new TenderAdapter(this);
        b.list.setLayoutManager(new LinearLayoutManager(requireContext()));
        b.list.setAdapter(adapter);
        scroll = new EndlessScroll() { @Override public void loadMore() { load(false); } };
        b.list.addOnScrollListener(scroll);
        b.swipe.setColorSchemeResources(R.color.aq_blue, R.color.aq_gold);
        b.swipe.setOnRefreshListener(() -> load(true));

        User u = TokenManager.get(requireContext()).getUser();
        boolean projectOwner = u != null && TokenManager.Roles.PROJECT_OWNER.equals(u.role);
        b.toggleScope.setVisibility(projectOwner ? View.VISIBLE : View.GONE);
        b.fabNew.setVisibility(projectOwner ? View.VISIBLE : View.GONE);
        b.fabNew.setOnClickListener(v -> startActivity(new Intent(requireContext(), TenderFormActivity.class)));
        b.toggleScope.addOnButtonCheckedListener((g, id, checked) -> {
            if (!checked) return;
            mine = id == R.id.btn_scope_mine;
            load(true);
        });
        b.btnVerify.setOnClickListener(v -> startActivity(new Intent(requireContext(), VerificationActivity.class)));

        addChip(inflater, getString(R.string.all), null, true);
        for (String k : Labels.TENDER_TYPES.keySet()) addChip(inflater, Labels.TENDER_TYPES.get(k), k, false);
        b.chipsType.setOnCheckedStateChangeListener((g, ids) -> {
            if (ids.isEmpty()) return;
            Chip c = g.findViewById(ids.get(0));
            type = (String) c.getTag();
            load(true);
        });
        return b.getRoot();
    }

    private void addChip(LayoutInflater inflater, String text, String tag, boolean checked) {
        Chip c = (Chip) inflater.inflate(R.layout.item_filter_chip, b.chipsType, false);
        c.setId(View.generateViewId());
        c.setText(text);
        c.setTag(tag);
        c.setChecked(checked);
        b.chipsType.addView(c);
    }

    @Override
    public void onResume() {
        super.onResume();
        updateBanner();
        load(true);
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden) { updateBanner(); load(true); }
    }

    private void updateBanner() {
        User u = TokenManager.get(requireContext()).getUser();
        boolean show = u != null && TokenManager.Roles.CONTRACTOR.equals(u.role) && !"verified".equals(u.verificationStatus);
        b.bannerVerify.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show && "pending".equals(u.verificationStatus)) {
            b.tvVerify.setText("وثائقك قيد المراجعة من قبل الإدارة");
            b.btnVerify.setVisibility(View.GONE);
        } else {
            b.tvVerify.setText(R.string.verify_to_bid);
            b.btnVerify.setVisibility(View.VISIBLE);
        }
    }

    private void load(boolean reset) {
        if (b == null) return;
        if (reset) {
            page = 1;
            scroll.reset();
            if (adapter.getItemCount() == 0) state.loading();
        }
        final int requested = page;
        Map<String, String> q = new HashMap<>();
        if (type != null) q.put("tenderType", type);
        q.put("page", String.valueOf(requested));
        q.put("limit", "20");
        retrofit2.Call<com.aqarcom.app.data.model.ApiResponse<List<Tender>>> call = mine ? ApiClient.tenders().mine(q) : ApiClient.tenders().list(q);
        call.enqueue(new ApiCallback<List<Tender>>() {
            @Override public void onSuccess(List<Tender> list, PageMeta meta) {
                if (b == null) return;
                b.swipe.setRefreshing(false);
                if (requested == 1) adapter.setItems(list); else adapter.addItems(list);
                page = requested + 1;
                scroll.setLoading(false);
                scroll.setHasMore(meta != null && meta.page < meta.totalPages);
                if (adapter.getItemCount() == 0) state.empty(R.drawable.ic_gavel, getString(R.string.no_tenders), null);
                else state.hide();
            }

            @Override public void onError(ApiError e) {
                if (b == null) return;
                b.swipe.setRefreshing(false);
                scroll.setLoading(false);
                if (adapter.getItemCount() == 0) state.error(e, () -> load(true));
                else Ui.error(b.getRoot(), e);
            }
        });
    }

    @Override
    public void onOpen(Tender t) {
        startActivity(TenderDetailActivity.intent(requireContext(), t.id));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }
}
