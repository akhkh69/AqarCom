package com.aqarcom.app.ui.main;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.aqarcom.app.R;
import com.aqarcom.app.data.model.ApiError;
import com.aqarcom.app.data.model.AppNotification;
import com.aqarcom.app.data.model.MessageResult;
import com.aqarcom.app.data.model.PageMeta;
import com.aqarcom.app.data.model.User;
import com.aqarcom.app.data.network.ApiCallback;
import com.aqarcom.app.data.network.ApiClient;
import com.aqarcom.app.data.network.TokenManager;
import com.aqarcom.app.databinding.FragmentListBinding;
import com.aqarcom.app.databinding.ItemNotificationBinding;
import com.aqarcom.app.ui.auth.AuthRouter;
import com.aqarcom.app.ui.common.Fmt;
import com.aqarcom.app.ui.common.StateView;
import com.aqarcom.app.ui.property.PropertyDetailActivity;
import com.aqarcom.app.ui.property.MyPropertiesActivity;
import com.aqarcom.app.ui.tender.TenderDetailActivity;

import java.util.ArrayList;
import java.util.List;

/** In-app notification store (works with or without push). Tapping opens the related tender / listing. */
public class NotificationsFragment extends Fragment {
    private FragmentListBinding b;
    private StateView state;
    private final List<AppNotification> items = new ArrayList<>();
    private RecyclerView.Adapter<VH> adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        b = FragmentListBinding.inflate(inflater, container, false);
        b.toolbar.setTitle(R.string.nav_notifications);
        b.toolbar.inflateMenu(R.menu.menu_notifications);
        b.toolbar.setOnMenuItemClickListener(i -> { readAll(); return true; });
        state = new StateView(b.state);
        adapter = new RecyclerView.Adapter<VH>() {
            @NonNull
            @Override
            public VH onCreateViewHolder(@NonNull ViewGroup p, int t) {
                return new VH(ItemNotificationBinding.inflate(LayoutInflater.from(p.getContext()), p, false));
            }

            @Override
            public void onBindViewHolder(@NonNull VH h, int pos) { bind(h.b, items.get(pos)); }

            @Override public int getItemCount() { return items.size(); }
        };
        b.list.setLayoutManager(new LinearLayoutManager(requireContext()));
        b.list.addItemDecoration(new DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL));
        b.list.setAdapter(adapter);
        b.swipe.setColorSchemeResources(R.color.aq_blue, R.color.aq_gold);
        b.swipe.setOnRefreshListener(this::load);
        return b.getRoot();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!isHidden()) load();
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden) load();
    }

    private void load() {
        if (b == null) return;
        if (!TokenManager.get(requireContext()).isLoggedIn()) {
            b.swipe.setRefreshing(false);
            state.action(R.drawable.ic_notifications, getString(R.string.login_required_title), getString(R.string.login_required_message),
                    getString(R.string.login), () -> AuthRouter.openLogin(requireContext()));
            return;
        }
        if (items.isEmpty()) state.loading();
        ApiClient.notifications().list(1, 50, null).enqueue(new ApiCallback<List<AppNotification>>() {
            @Override public void onSuccess(List<AppNotification> list, PageMeta meta) {
                if (b == null) return;
                b.swipe.setRefreshing(false);
                items.clear();
                items.addAll(list);
                adapter.notifyDataSetChanged();
                if (items.isEmpty()) state.empty(R.drawable.ic_notifications, getString(R.string.no_notifications), null);
                else state.hide();
                badge();
            }

            @Override public void onError(ApiError e) {
                if (b == null) return;
                b.swipe.setRefreshing(false);
                state.error(e, NotificationsFragment.this::load);
            }
        });
    }

    private void bind(ItemNotificationBinding v, AppNotification n) {
        v.tvTitle.setText(n.title);
        v.tvBody.setText(n.body);
        v.tvTime.setText(Fmt.ago(n.createdAt));
        v.dotUnread.setVisibility(n.isRead() ? View.GONE : View.VISIBLE);
        v.row.setBackgroundColor(ContextCompat.getColor(requireContext(), n.isRead() ? R.color.aq_sand : R.color.aq_paper));
        v.ivIcon.setImageResource(iconFor(n.type));
        int tint = n.type != null && (n.type.endsWith("rejected") || n.type.endsWith("suspended") || n.type.startsWith("content_") || "bid_lost".equals(n.type))
                ? R.color.aq_red : n.type != null && (n.type.endsWith("approved") || "bid_won".equals(n.type)) ? R.color.aq_emerald : R.color.aq_blue;
        v.ivIcon.setColorFilter(ContextCompat.getColor(requireContext(), tint));
        v.row.setOnClickListener(x -> open(n));
    }

    @DrawableRes
    private int iconFor(String type) {
        if (type == null) return R.drawable.ic_notifications;
        if (type.startsWith("bid_") || type.startsWith("new_bid") || type.startsWith("shortlist")) return R.drawable.ic_gavel;
        if (type.startsWith("tender_")) return R.drawable.ic_description;
        if (type.startsWith("verification_") || type.startsWith("account_approved")) return R.drawable.ic_verified;
        if (type.startsWith("content_")) return R.drawable.ic_home;
        if (type.startsWith("account_")) return R.drawable.ic_person;
        return R.drawable.ic_notifications;
    }

    private void open(AppNotification n) {
        if (!n.isRead()) {
            n.readAt = "now";
            adapter.notifyDataSetChanged();
            ApiClient.notifications().markRead(n.id).enqueue(new ApiCallback<AppNotification>() {
                @Override public void onSuccess(AppNotification d, PageMeta m) { badge(); }
                @Override public void onError(ApiError e) { }
            });
        }
        String type = n.type == null ? "" : n.type;
        // account / verification decisions change the stored user → refresh it
        if (type.startsWith("account_") || type.startsWith("verification_") || "role_changed".equals(type)) refreshMe();
        if (n.data != null && n.data.has("tenderId")) {
            startActivity(TenderDetailActivity.intent(requireContext(), n.data.get("tenderId").getAsString()));
        } else if (n.data != null && n.data.has("targetType") && "property".equals(n.data.get("targetType").getAsString())) {
            startActivity(PropertyDetailActivity.intent(requireContext(), n.data.get("targetId").getAsString()));
        } else if (type.startsWith("content_")) {
            startActivity(new android.content.Intent(requireContext(), MyPropertiesActivity.class));
        }
    }

    private void refreshMe() {
        ApiClient.auth().me().enqueue(new ApiCallback<User>() {
            @Override public void onSuccess(User u, PageMeta m) {
                if (isAdded()) TokenManager.get(requireContext()).saveUser(u);
            }
            @Override public void onError(ApiError e) { }
        });
    }

    private void readAll() {
        ApiClient.notifications().markAllRead().enqueue(new ApiCallback<MessageResult>() {
            @Override public void onSuccess(MessageResult r, PageMeta m) { load(); }
            @Override public void onError(ApiError e) { }
        });
    }

    private void badge() {
        if (getActivity() instanceof MainActivity) ((MainActivity) getActivity()).refreshBadge();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }

    static class VH extends RecyclerView.ViewHolder {
        final ItemNotificationBinding b;
        VH(ItemNotificationBinding b) { super(b.getRoot()); this.b = b; }
    }
}
