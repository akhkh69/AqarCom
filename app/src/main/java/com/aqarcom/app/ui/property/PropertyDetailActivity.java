package com.aqarcom.app.ui.property;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.viewpager2.widget.ViewPager2;

import com.aqarcom.app.R;
import com.aqarcom.app.data.model.ApiError;
import com.aqarcom.app.data.model.ContactInfo;
import com.aqarcom.app.data.model.ImageItem;
import com.aqarcom.app.data.model.MessageResult;
import com.aqarcom.app.data.model.PageMeta;
import com.aqarcom.app.data.model.Property;
import com.aqarcom.app.data.network.ApiCallback;
import com.aqarcom.app.data.network.ApiClient;
import com.aqarcom.app.data.network.TokenManager;
import com.aqarcom.app.databinding.ActivityPropertyDetailBinding;
import com.aqarcom.app.ui.auth.AuthRouter;
import com.aqarcom.app.ui.base.BaseActivity;
import com.aqarcom.app.ui.common.Fmt;
import com.aqarcom.app.ui.common.Labels;
import com.aqarcom.app.ui.common.StateView;
import com.aqarcom.app.ui.common.Ui;

import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.CustomZoomButtonsController;
import org.osmdroid.views.overlay.Marker;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Listing details: gallery, specs, location, owner contact (FR-25 / BR-05), favorite, owner actions (FR-06). */
public class PropertyDetailActivity extends BaseActivity {
    private static final String EXTRA_ID = "id";

    private ActivityPropertyDetailBinding b;
    private StateView state;
    private final ImagePagerAdapter pager = new ImagePagerAdapter();
    private Property property;
    private String id;

    public static Intent intent(Context c, String id) {
        return new Intent(c, PropertyDetailActivity.class).putExtra(EXTRA_ID, id);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityPropertyDetailBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        id = getIntent().getStringExtra(EXTRA_ID);
        state = new StateView(b.state);

        b.pager.setAdapter(pager);
        b.pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override public void onPageSelected(int position) { updateCounter(position); }
        });
        b.btnBack.setOnClickListener(v -> finish());
        b.btnFavorite.setOnClickListener(v -> toggleFavorite());
        b.btnCall.setOnClickListener(v -> contact(false));
        b.btnWhatsapp.setOnClickListener(v -> contact(true));
        b.tvContactLocked.setOnClickListener(v -> AuthRouter.openLogin(this));
        b.btnOpenMap.setOnClickListener(v -> openInMaps());
        b.btnEdit.setOnClickListener(v -> startActivity(PropertyFormActivity.editIntent(this, id)));
        b.btnDelete.setOnClickListener(v -> Ui.confirm(this, getString(R.string.delete_listing_title),
                getString(R.string.delete_listing_message), getString(R.string.delete), this::delete));
    }

    @Override
    protected void onResume() {
        super.onResume();
        b.miniMap.onResume();
        load();
    }

    @Override
    protected void onPause() {
        super.onPause();
        b.miniMap.onPause();
    }

    private void load() {
        if (property == null) state.loading();
        ApiClient.properties().get(id).enqueue(new ApiCallback<Property>() {
            @Override public void onSuccess(Property p, PageMeta meta) {
                property = p;
                state.hide();
                b.content.setVisibility(View.VISIBLE);
                bind(p);
            }

            @Override public void onError(ApiError e) {
                b.content.setVisibility(View.INVISIBLE);
                state.error(e, () -> load());
            }
        });
    }

    private void bind(@NonNull Property p) {
        List<String> urls = new ArrayList<>();
        if (p.images != null) for (ImageItem i : p.images) urls.add(i.url);
        pager.setUrls(urls);
        updateCounter(b.pager.getCurrentItem());

        b.tvTitle.setText(p.title);
        b.tvPrice.setText(Fmt.money(p.price, p.currency) + ("rent".equals(p.purpose) ? " / شهرياً" : ""));
        b.tvPurpose.setText(Labels.of(Labels.PURPOSES, p.purpose));
        b.tvType.setText(Labels.of(Labels.PROPERTY_TYPES, p.propertyType));
        b.tvViews.setText(getString(R.string.views_count, p.viewsCount));
        String loc = p.city == null ? "" : p.city;
        if (!TextUtils.isEmpty(p.district)) loc += " · " + p.district;
        b.tvLocation.setText(loc);
        b.tvAddress.setText(TextUtils.isEmpty(p.address) ? loc : p.address);
        showMiniMap(p);

        b.tvArea.setText(Fmt.number(p.area));
        b.tvRooms.setText(String.valueOf(p.rooms));
        b.tvBaths.setText(String.valueOf(p.bathrooms));
        b.tvFloor.setText(p.floor == null ? "—" : String.valueOf(p.floor));

        boolean hasDesc = !TextUtils.isEmpty(p.description);
        b.labelDescription.setVisibility(hasDesc ? View.VISIBLE : View.GONE);
        b.tvDescription.setVisibility(hasDesc ? View.VISIBLE : View.GONE);
        b.tvDescription.setText(p.description);

        // owner card — BR-05: phone only for signed-in users
        String name = p.owner == null || p.owner.fullName == null ? "—" : p.owner.fullName;
        b.tvOwnerName.setText(name);
        b.tvOwnerInitial.setText(name.substring(0, 1));
        b.tvOwnerPhone.setText(p.contactLocked || p.owner == null ? "" : p.owner.phone);
        b.tvContactLocked.setVisibility(p.contactLocked ? View.VISIBLE : View.GONE);
        b.groupContact.setVisibility(p.contactLocked || p.isMine ? View.GONE : View.VISIBLE);

        b.btnFavorite.setVisibility(p.isMine ? View.GONE : View.VISIBLE);
        b.btnFavorite.setImageResource(p.isFavorite ? R.drawable.ic_favorite : R.drawable.ic_favorite_border);

        boolean canManage = p.isMine || isAdmin();
        b.groupOwnerActions.setVisibility(canManage ? View.VISIBLE : View.GONE);
        if (!"active".equals(p.status)) {
            String reason = p.moderation != null && p.moderation.reason != null ? ": " + p.moderation.reason : "";
            b.tvModeration.setText(getString(R.string.listing_hidden_by_admin) + reason);
            b.tvModeration.setVisibility(View.VISIBLE);
        } else b.tvModeration.setVisibility(View.GONE);
    }

    private boolean isAdmin() {
        com.aqarcom.app.data.model.User u = TokenManager.get(this).getUser();
        return u != null && TokenManager.Roles.ADMIN.equals(u.role);
    }

    private void updateCounter(int position) {
        int total = property == null || property.images == null ? 0 : property.images.size();
        b.tvCounter.setVisibility(total > 1 ? View.VISIBLE : View.GONE);
        b.tvCounter.setText(String.format(Locale.US, "%d / %d", position + 1, total));
    }

    private void toggleFavorite() {
        if (property == null) return;
        if (!TokenManager.get(this).isLoggedIn()) { AuthRouter.openLogin(this); return; }
        final boolean was = property.isFavorite;
        property.isFavorite = !was;
        b.btnFavorite.setImageResource(property.isFavorite ? R.drawable.ic_favorite : R.drawable.ic_favorite_border);
        retrofit2.Call<com.aqarcom.app.data.model.ApiResponse<MessageResult>> call =
                was ? ApiClient.properties().removeFavorite(id) : ApiClient.properties().addFavorite(id);
        call.enqueue(new ApiCallback<MessageResult>() {
            @Override public void onSuccess(MessageResult r, PageMeta meta) {
                Ui.snack(b.getRoot(), getString(was ? R.string.removed_from_favorites : R.string.added_to_favorites));
            }

            @Override public void onError(ApiError e) {
                property.isFavorite = was;
                b.btnFavorite.setImageResource(was ? R.drawable.ic_favorite : R.drawable.ic_favorite_border);
                Ui.error(b.getRoot(), e);
            }
        });
    }

    /** FR-25 — dial or open WhatsApp with a ready-made message. */
    private void contact(boolean whatsapp) {
        ApiClient.properties().contact(id).enqueue(new ApiCallback<ContactInfo>() {
            @Override public void onSuccess(ContactInfo c, PageMeta meta) {
                try {
                    Intent i = whatsapp ? new Intent(Intent.ACTION_VIEW, Uri.parse(c.whatsappUrl))
                            : new Intent(Intent.ACTION_DIAL, Uri.parse(c.telUri));
                    startActivity(i);
                } catch (android.content.ActivityNotFoundException ex) {
                    Ui.snack(b.getRoot(), c.phone);
                }
            }

            @Override public void onError(ApiError e) { Ui.error(b.getRoot(), e); }
        });
    }

    /** Small, non-interactive OpenStreetMap preview of the listing position; tap → open in a maps app. */
    private void showMiniMap(@NonNull Property p) {
        if (p.latitude == 0 && p.longitude == 0) { b.miniMap.setVisibility(View.GONE); return; }
        GeoPoint at = new GeoPoint(p.latitude, p.longitude);
        b.miniMap.setTileSource(TileSourceFactory.MAPNIK);
        b.miniMap.setTilesScaledToDpi(true);
        b.miniMap.setMultiTouchControls(false);
        b.miniMap.getZoomController().setVisibility(CustomZoomButtonsController.Visibility.NEVER);
        b.miniMap.getController().setZoom(16.0);
        b.miniMap.getController().setCenter(at);
        b.miniMap.getOverlays().clear();
        Marker m = new Marker(b.miniMap);
        m.setPosition(at);
        m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        m.setIcon(androidx.core.content.ContextCompat.getDrawable(this,
                "rent".equals(p.purpose) ? R.drawable.ic_map_pin_blue
                        : "investment".equals(p.purpose) ? R.drawable.ic_map_pin_green : R.drawable.ic_map_pin_gold));
        m.setInfoWindow(null);
        b.miniMap.getOverlays().add(m);
        // consume touches so the page scrolls normally; a tap opens the full map app
        b.miniMap.setOnTouchListener((v, e) -> {
            if (e.getAction() == android.view.MotionEvent.ACTION_UP) openInMaps();
            return true;
        });
        b.miniMap.invalidate();
    }

    private void openInMaps() {
        if (property == null) return;
        String label = Uri.encode(property.title);
        Uri uri = Uri.parse(String.format(Locale.US, "geo:%f,%f?q=%f,%f(%s)",
                property.latitude, property.longitude, property.latitude, property.longitude, label));
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (android.content.ActivityNotFoundException ex) {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(String.format(Locale.US,
                    "https://www.google.com/maps/search/?api=1&query=%f,%f", property.latitude, property.longitude))));
        }
    }

    private void delete() {
        ApiClient.properties().delete(id).enqueue(new ApiCallback<MessageResult>() {
            @Override public void onSuccess(MessageResult r, PageMeta meta) {
                Ui.toast(PropertyDetailActivity.this, getString(R.string.listing_deleted));
                finish();
            }

            @Override public void onError(ApiError e) { Ui.error(b.getRoot(), e); }
        });
    }
}
