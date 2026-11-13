package com.aqarcom.app.ui.main;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.aqarcom.app.R;
import com.aqarcom.app.data.model.ApiError;
import com.aqarcom.app.data.model.MapPin;
import com.aqarcom.app.data.model.PageMeta;
import com.aqarcom.app.data.network.ApiCallback;
import com.aqarcom.app.data.network.ApiClient;
import com.aqarcom.app.databinding.FragmentMapBinding;
import com.aqarcom.app.ui.common.Fmt;
import com.aqarcom.app.ui.common.Images;
import com.aqarcom.app.ui.common.Labels;
import com.aqarcom.app.ui.common.Ui;
import com.aqarcom.app.ui.property.PropertyDetailActivity;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.chip.Chip;

import org.osmdroid.events.DelayedMapListener;
import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.events.MapListener;
import org.osmdroid.events.ScrollEvent;
import org.osmdroid.events.ZoomEvent;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.CustomZoomButtonsController;
import org.osmdroid.views.overlay.MapEventsOverlay;
import org.osmdroid.views.overlay.Marker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * FR-07 — interactive OpenStreetMap: properties inside the visible area appear as pins colored by
 * purpose; tapping a pin opens a preview card; moving / zooming the map reloads the pins.
 */
public class MapFragment extends Fragment {
    private static final GeoPoint DAMASCUS = new GeoPoint(33.5138, 36.2765);

    private FragmentMapBinding b;
    private final List<Marker> pins = new ArrayList<>();
    private Marker myLocation;
    private String purpose;
    private retrofit2.Call<?> inFlight;

    private final ActivityResultLauncher<String> permission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) goToMyLocation();
                else if (b != null) Ui.snack(b.getRoot(), getString(R.string.location_permission_needed));
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        b = FragmentMapBinding.inflate(inflater, container, false);

        b.map.setTileSource(TileSourceFactory.MAPNIK);
        b.map.setMultiTouchControls(true);
        b.map.getZoomController().setVisibility(CustomZoomButtonsController.Visibility.NEVER);
        b.map.setMinZoomLevel(6.0);
        b.map.setMaxZoomLevel(19.0);
        b.map.setTilesScaledToDpi(true);
        b.map.getController().setZoom(13.0);
        b.map.getController().setCenter(DAMASCUS);

        // tap on empty map → hide the preview card
        b.map.getOverlays().add(new MapEventsOverlay(new MapEventsReceiver() {
            @Override public boolean singleTapConfirmedHelper(GeoPoint p) { hidePreview(); return true; }
            @Override public boolean longPressHelper(GeoPoint p) { return false; }
        }));
        // reload pins 400 ms after the user stops moving / zooming
        b.map.addMapListener(new DelayedMapListener(new MapListener() {
            @Override public boolean onScroll(ScrollEvent event) { loadPins(); return true; }
            @Override public boolean onZoom(ZoomEvent event) { loadPins(); return true; }
        }, 400));

        b.fabZoomIn.setOnClickListener(v -> b.map.getController().zoomIn());
        b.fabZoomOut.setOnClickListener(v -> b.map.getController().zoomOut());
        b.fabLocation.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) goToMyLocation();
            else permission.launch(Manifest.permission.ACCESS_FINE_LOCATION);
        });
        b.btnClosePreview.setOnClickListener(v -> hidePreview());

        addChip(inflater, getString(R.string.all), null, true);
        for (String k : Labels.PURPOSES.keySet()) addChip(inflater, Labels.PURPOSES.get(k), k, false);
        b.chipsPurpose.setOnCheckedStateChangeListener((g, ids) -> {
            if (ids.isEmpty()) return;
            Chip c = g.findViewById(ids.get(0));
            purpose = (String) c.getTag();
            hidePreview();
            loadPins();
        });

        b.map.post(this::loadPins); // first load once the map has its size
        return b.getRoot();
    }

    private void addChip(LayoutInflater inflater, String text, String tag, boolean checked) {
        Chip c = (Chip) inflater.inflate(R.layout.item_filter_chip, b.chipsPurpose, false);
        c.setId(View.generateViewId());
        c.setText(text);
        c.setTag(tag);
        c.setChecked(checked);
        c.setElevation(4f);
        b.chipsPurpose.addView(c);
    }

    private void loadPins() {
        if (b == null || b.map.getWidth() == 0) return;
        BoundingBox box = b.map.getBoundingBox();
        Map<String, String> q = new HashMap<>();
        if (purpose != null) q.put("purpose", purpose);
        q.put("limit", "300");
        if (inFlight != null) inFlight.cancel();
        b.progress.setVisibility(View.VISIBLE);
        retrofit2.Call<com.aqarcom.app.data.model.ApiResponse<List<MapPin>>> call = ApiClient.properties().mapPins(
                box.getLatSouth(), box.getLonWest(), box.getLatNorth(), box.getLonEast(), q);
        inFlight = call;
        call.enqueue(new ApiCallback<List<MapPin>>() {
            @Override public void onSuccess(List<MapPin> list, PageMeta meta) {
                if (b == null) return;
                b.progress.setVisibility(View.GONE);
                showPins(list);
            }

            @Override public void onError(ApiError e) {
                if (b == null) return;
                b.progress.setVisibility(View.GONE);
                Ui.error(b.getRoot(), e);
            }
        });
    }

    private void showPins(List<MapPin> list) {
        b.map.getOverlays().removeAll(pins);
        pins.clear();
        for (MapPin p : list) {
            Marker m = new Marker(b.map);
            m.setPosition(new GeoPoint(p.latitude, p.longitude));
            m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            m.setIcon(icon(pinFor(p.purpose)));
            m.setTitle(p.title);
            m.setRelatedObject(p);
            m.setOnMarkerClickListener((marker, mapView) -> {
                showPreview((MapPin) marker.getRelatedObject());
                mapView.getController().animateTo(marker.getPosition());
                return true;
            });
            pins.add(m);
        }
        b.map.getOverlays().addAll(pins);
        b.map.invalidate();
        b.tvCount.setVisibility(View.VISIBLE);
        b.tvCount.setText(list.isEmpty() ? getString(R.string.map_no_results) : getString(R.string.results_count, list.size()));
    }

    @DrawableRes
    private static int pinFor(String purpose) {
        if ("rent".equals(purpose)) return R.drawable.ic_map_pin_blue;
        if ("investment".equals(purpose)) return R.drawable.ic_map_pin_green;
        return R.drawable.ic_map_pin_gold;
    }

    private Drawable icon(@DrawableRes int res) {
        return ContextCompat.getDrawable(requireContext(), res);
    }

    private void showPreview(MapPin p) {
        Images.load(b.ivPreview, p.thumbnailUrl);
        b.tvPreviewTitle.setText(p.title);
        b.tvPreviewPrice.setText(Fmt.money(p.price, p.currency) + ("rent".equals(p.purpose) ? " / شهرياً" : ""));
        b.tvPreviewPurpose.setText(Labels.of(Labels.PURPOSES, p.purpose));
        String meta = Labels.of(Labels.PROPERTY_TYPES, p.propertyType) + " · " + Fmt.area(p.area)
                + (p.rooms > 0 ? " · " + p.rooms + " غرف" : "") + (p.city != null ? " · " + p.city : "");
        b.tvPreviewMeta.setText(meta);
        View.OnClickListener open = v -> startActivity(PropertyDetailActivity.intent(requireContext(), p.id));
        b.cardPreview.setOnClickListener(open);
        b.btnPreviewOpen.setOnClickListener(open);
        b.cardPreview.setVisibility(View.VISIBLE);
    }

    private void hidePreview() {
        if (b != null) b.cardPreview.setVisibility(View.GONE);
    }

    @SuppressLint("MissingPermission")
    private void goToMyLocation() {
        LocationServices.getFusedLocationProviderClient(requireActivity()).getLastLocation().addOnSuccessListener(loc -> {
            if (b == null) return;
            if (loc == null) {
                Ui.snack(b.getRoot(), getString(R.string.location_permission_needed));
                return;
            }
            GeoPoint here = new GeoPoint(loc.getLatitude(), loc.getLongitude());
            if (myLocation == null) {
                myLocation = new Marker(b.map);
                myLocation.setIcon(icon(R.drawable.ic_my_location_dot));
                myLocation.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
                myLocation.setInfoWindow(null);
                b.map.getOverlays().add(myLocation);
            }
            myLocation.setPosition(here);
            b.map.getController().setZoom(15.0);
            b.map.getController().animateTo(here);
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        if (b != null) b.map.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (b != null) b.map.onPause();
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (b == null) return;
        if (hidden) b.map.onPause();
        else { b.map.onResume(); loadPins(); }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (inFlight != null) inFlight.cancel();
        if (b != null) b.map.onDetach();
        pins.clear();
        myLocation = null;
        b = null;
    }
}
