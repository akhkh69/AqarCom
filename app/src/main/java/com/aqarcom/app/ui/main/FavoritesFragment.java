package com.aqarcom.app.ui.main;

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
import com.aqarcom.app.data.model.Property;
import com.aqarcom.app.data.network.ApiCallback;
import com.aqarcom.app.data.network.ApiClient;
import com.aqarcom.app.data.network.TokenManager;
import com.aqarcom.app.databinding.FragmentListBinding;
import com.aqarcom.app.ui.auth.AuthRouter;
import com.aqarcom.app.ui.common.StateView;
import com.aqarcom.app.ui.property.FavoriteToggler;
import com.aqarcom.app.ui.property.PropertyAdapter;
import com.aqarcom.app.ui.property.PropertyDetailActivity;

import java.util.List;

/** FR-09 wishlist. */
public class FavoritesFragment extends Fragment implements PropertyAdapter.Listener {
    public static final String ARG_SHOW_BACK = "show_back";
    private FragmentListBinding b;
    private PropertyAdapter adapter;
    private StateView state;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        b = FragmentListBinding.inflate(inflater, container, false);
        b.toolbar.setTitle(R.string.nav_favorites);
        if (getArguments() != null && getArguments().getBoolean(ARG_SHOW_BACK)) {
            b.toolbar.setNavigationIcon(R.drawable.ic_arrow_back);
            b.toolbar.setNavigationOnClickListener(v -> requireActivity().finish());
        }
        state = new StateView(b.state);
        adapter = new PropertyAdapter(this, false);
        b.list.setLayoutManager(new LinearLayoutManager(requireContext()));
        b.list.setAdapter(adapter);
        b.swipe.setColorSchemeResources(R.color.aq_blue, R.color.aq_gold);
        b.swipe.setOnRefreshListener(this::load);
        return b.getRoot();
    }

    @Override
    public void onResume() {
        super.onResume();
        load();
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
            state.action(R.drawable.ic_favorite_border, getString(R.string.login_required_title),
                    getString(R.string.login_required_message), getString(R.string.login),
                    () -> AuthRouter.openLogin(requireContext()));
            return;
        }
        if (adapter.getItemCount() == 0) state.loading();
        ApiClient.properties().favorites(1, 100).enqueue(new ApiCallback<List<Property>>() {
            @Override public void onSuccess(List<Property> list, PageMeta meta) {
                if (b == null) return;
                b.swipe.setRefreshing(false);
                adapter.setItems(list);
                if (list.isEmpty()) state.empty(R.drawable.ic_favorite_border, getString(R.string.favorites_empty), getString(R.string.favorites_empty_hint));
                else state.hide();
            }

            @Override public void onError(ApiError e) {
                if (b == null) return;
                b.swipe.setRefreshing(false);
                state.error(e, FavoritesFragment.this::load);
            }
        });
    }

    @Override
    public void onOpen(Property p) {
        startActivity(PropertyDetailActivity.intent(requireContext(), p.id));
    }

    @Override
    public void onToggleFavorite(Property p, int position) {
        FavoriteToggler.toggle(this, b.getRoot(), p, () -> {
            if (!p.isFavorite) adapter.removeAt(position);
            else adapter.notifyItemChanged(position);
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }
}
