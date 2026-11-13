package com.aqarcom.app.ui.property;

import android.content.Intent;
import android.os.Bundle;

import androidx.recyclerview.widget.LinearLayoutManager;

import com.aqarcom.app.R;
import com.aqarcom.app.data.model.ApiError;
import com.aqarcom.app.data.model.PageMeta;
import com.aqarcom.app.data.model.Property;
import com.aqarcom.app.data.network.ApiCallback;
import com.aqarcom.app.data.network.ApiClient;
import com.aqarcom.app.databinding.FragmentListBinding;
import com.aqarcom.app.ui.base.BaseActivity;
import com.aqarcom.app.ui.common.StateView;

import java.util.List;

/** Property owner's own listings, including ones hidden by the admin (with the reason inside). */
public class MyPropertiesActivity extends BaseActivity implements PropertyAdapter.Listener {
    private FragmentListBinding b;
    private PropertyAdapter adapter;
    private StateView state;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = FragmentListBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        setupToolbar(b.toolbar, getString(R.string.my_properties), true);
        b.toolbar.inflateMenu(R.menu.menu_add);
        b.toolbar.setOnMenuItemClickListener(i -> {
            startActivity(new Intent(this, PropertyFormActivity.class));
            return true;
        });
        state = new StateView(b.state);
        adapter = new PropertyAdapter(this, true);
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
        if (adapter.getItemCount() == 0) state.loading();
        ApiClient.properties().mine(1, 100, null).enqueue(new ApiCallback<List<Property>>() {
            @Override public void onSuccess(List<Property> list, PageMeta meta) {
                b.swipe.setRefreshing(false);
                adapter.setItems(list);
                if (list.isEmpty()) state.action(R.drawable.ic_home, getString(R.string.no_my_properties), null,
                        getString(R.string.add_property), () -> startActivity(new Intent(MyPropertiesActivity.this, PropertyFormActivity.class)));
                else state.hide();
            }

            @Override public void onError(ApiError e) {
                b.swipe.setRefreshing(false);
                state.error(e, MyPropertiesActivity.this::load);
            }
        });
    }

    @Override public void onOpen(Property p) { startActivity(PropertyDetailActivity.intent(this, p.id)); }

    @Override public void onToggleFavorite(Property p, int position) { }
}
