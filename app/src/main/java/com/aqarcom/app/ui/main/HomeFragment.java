package com.aqarcom.app.ui.main;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.PopupMenu;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.aqarcom.app.R;
import com.aqarcom.app.data.model.ApiError;
import com.aqarcom.app.data.model.PageMeta;
import com.aqarcom.app.data.model.Property;
import com.aqarcom.app.data.model.User;
import com.aqarcom.app.data.network.ApiCallback;
import com.aqarcom.app.data.network.ApiClient;
import com.aqarcom.app.data.network.TokenManager;
import com.aqarcom.app.databinding.FragmentHomeBinding;
import com.aqarcom.app.ui.common.EndlessScroll;
import com.aqarcom.app.ui.common.Labels;
import com.aqarcom.app.ui.common.StateView;
import com.aqarcom.app.ui.common.Ui;
import com.aqarcom.app.ui.property.FavoriteToggler;
import com.aqarcom.app.ui.property.FilterBottomSheet;
import com.aqarcom.app.ui.property.PropertyAdapter;
import com.aqarcom.app.ui.property.PropertyDetailActivity;
import com.aqarcom.app.ui.property.PropertyFilters;
import com.aqarcom.app.ui.property.PropertyFormActivity;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.chip.Chip;

import java.util.List;

/** Property search: text, purpose chips, advanced filters, sort incl. "nearest", infinite scroll (FR-08). */
public class HomeFragment extends Fragment implements FilterBottomSheet.Host, PropertyAdapter.Listener {

    private FragmentHomeBinding b;
    private PropertyAdapter adapter;
    private StateView state;
    private final PropertyFilters filters = new PropertyFilters();
    private int page = 1;
    private EndlessScroll scroll;

    private final ActivityResultLauncher<String> locationPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) useLocationAndSort();
                else Ui.snack(b.getRoot(), getString(R.string.location_permission_needed));
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        b = FragmentHomeBinding.inflate(inflater, container, false);
        state = new StateView(b.state);
        adapter = new PropertyAdapter(this, false);
        b.list.setLayoutManager(new LinearLayoutManager(requireContext()));
        b.list.setAdapter(adapter);
        scroll = new EndlessScroll() {
            @Override public void loadMore() { load(false); }
        };
        b.list.addOnScrollListener(scroll);

        User u = TokenManager.get(requireContext()).getUser();
        if (u != null) b.tvGreeting.setText("أهلاً " + u.fullName);
        boolean canPublish = u != null && (TokenManager.Roles.PROPERTY_OWNER.equals(u.role) || TokenManager.Roles.ADMIN.equals(u.role));
        b.fabAdd.setVisibility(canPublish ? View.VISIBLE : View.GONE);
        b.fabAdd.setOnClickListener(v -> startActivity(new Intent(requireContext(), PropertyFormActivity.class)));

        setupPurposeChips(inflater);
        b.etSearch.setOnEditorActionListener((v, actionId, e) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                filters.q = Ui.text(b.etSearch);
                Ui.hideKeyboard(requireActivity());
                load(true);
                return true;
            }
            return false;
        });
        b.btnFilters.setOnClickListener(v -> FilterBottomSheet.newInstance(filters).show(getChildFragmentManager(), "filters"));
        b.btnSort.setOnClickListener(this::showSortMenu);
        b.swipe.setColorSchemeResources(R.color.aq_blue, R.color.aq_gold);
        b.swipe.setOnRefreshListener(() -> load(true));

        load(true);
        return b.getRoot();
    }

    private void setupPurposeChips(LayoutInflater inflater) {
        addChip(inflater, getString(R.string.all), null, true);
        for (String key : Labels.PURPOSES.keySet()) addChip(inflater, Labels.PURPOSES.get(key), key, false);
        b.chipsPurpose.setOnCheckedStateChangeListener((group, ids) -> {
            if (ids.isEmpty()) return;
            Chip c = group.findViewById(ids.get(0));
            filters.purpose = (String) c.getTag();
            load(true);
        });
    }

    private void addChip(LayoutInflater inflater, String text, String tag, boolean checked) {
        Chip c = (Chip) inflater.inflate(R.layout.item_filter_chip, b.chipsPurpose, false);
        c.setId(View.generateViewId());
        c.setText(text);
        c.setTag(tag);
        c.setChecked(checked);
        b.chipsPurpose.addView(c);
    }

    private void showSortMenu(View anchor) {
        PopupMenu pm = new PopupMenu(requireContext(), anchor);
        pm.inflate(R.menu.menu_sort);
        pm.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.sort_nearest) {
                if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) useLocationAndSort();
                else locationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION);
                return true;
            }
            filters.sort = id == R.id.sort_price_asc ? "price_asc" : id == R.id.sort_price_desc ? "price_desc" : "newest";
            b.btnSort.setText(item.getTitle());
            load(true);
            return true;
        });
        pm.show();
    }

    @SuppressLint("MissingPermission")
    private void useLocationAndSort() {
        LocationServices.getFusedLocationProviderClient(requireActivity()).getLastLocation().addOnSuccessListener(loc -> {
            if (b == null) return;
            if (loc == null) {
                Ui.snack(b.getRoot(), getString(R.string.location_permission_needed));
                return;
            }
            filters.sort = "nearest";
            filters.latitude = loc.getLatitude();
            filters.longitude = loc.getLongitude();
            b.btnSort.setText(R.string.sort_nearest);
            load(true);
        });
    }

    @Override
    public void onFiltersApplied(PropertyFilters f) {
        if (f != filters) filters.copyAdvancedFrom(f);
        int n = filters.activeCount();
        b.tvFilterCount.setVisibility(n > 0 ? View.VISIBLE : View.GONE);
        b.tvFilterCount.setText(String.valueOf(n));
        load(true);
    }

    private void load(boolean reset) {
        if (reset) {
            page = 1;
            scroll.reset();
            if (adapter.getItemCount() == 0) state.loading();
        }
        final int requested = page;
        ApiClient.properties().search(filters.toQuery(requested)).enqueue(new ApiCallback<List<Property>>() {
            @Override public void onSuccess(List<Property> list, PageMeta meta) {
                if (b == null) return;
                b.swipe.setRefreshing(false);
                if (requested == 1) adapter.setItems(list); else adapter.addItems(list);
                page = requested + 1;
                scroll.setLoading(false);
                scroll.setHasMore(meta != null && meta.page < meta.totalPages);
                b.tvCount.setText(getString(R.string.results_count, meta == null ? list.size() : meta.total));
                if (adapter.getItemCount() == 0) state.empty(R.drawable.ic_search, getString(R.string.no_properties), getString(R.string.no_properties_hint));
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
    public void onOpen(Property p) {
        startActivity(PropertyDetailActivity.intent(requireContext(), p.id));
    }

    @Override
    public void onToggleFavorite(Property p, int position) {
        FavoriteToggler.toggle(this, b.getRoot(), p, () -> adapter.notifyItemChanged(position));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }
}
