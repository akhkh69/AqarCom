package com.aqarcom.app.ui.property;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;

import com.aqarcom.app.R;
import com.aqarcom.app.databinding.ActivityLocationPickerBinding;
import com.aqarcom.app.ui.base.BaseActivity;
import com.aqarcom.app.ui.common.Ui;
import com.google.android.gms.location.LocationServices;

import org.osmdroid.api.IGeoPoint;
import org.osmdroid.events.MapListener;
import org.osmdroid.events.ScrollEvent;
import org.osmdroid.events.ZoomEvent;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.CustomZoomButtonsController;

import java.util.Locale;

/** Pick the listing coordinates (FR-07 / BR-04): move the OpenStreetMap under a fixed center pin. */
public class LocationPickerActivity extends BaseActivity {
    public static final String EXTRA_LAT = "lat";
    public static final String EXTRA_LNG = "lng";
    private static final GeoPoint DAMASCUS = new GeoPoint(33.5138, 36.2765);

    private ActivityLocationPickerBinding b;

    private final ActivityResultLauncher<String> permission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) myLocation();
                else Ui.snack(b.getRoot(), getString(R.string.location_permission_needed));
            });

    public static Intent intent(Context c, Double lat, Double lng) {
        Intent i = new Intent(c, LocationPickerActivity.class);
        if (lat != null && lng != null) i.putExtra(EXTRA_LAT, lat).putExtra(EXTRA_LNG, lng);
        return i;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityLocationPickerBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        setupToolbar(b.toolbar, null, true);

        boolean hasStart = getIntent().hasExtra(EXTRA_LAT);
        GeoPoint start = hasStart
                ? new GeoPoint(getIntent().getDoubleExtra(EXTRA_LAT, 0), getIntent().getDoubleExtra(EXTRA_LNG, 0))
                : DAMASCUS;

        b.map.setTileSource(TileSourceFactory.MAPNIK);
        b.map.setMultiTouchControls(true);
        b.map.setTilesScaledToDpi(true);
        b.map.getZoomController().setVisibility(CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT);
        b.map.getController().setZoom(hasStart ? 17.0 : 13.0);
        b.map.getController().setCenter(start);
        b.map.addMapListener(new MapListener() {
            @Override public boolean onScroll(ScrollEvent event) { showCoords(); return false; }
            @Override public boolean onZoom(ZoomEvent event) { showCoords(); return false; }
        });
        showCoords(start);

        b.btnMyLocation.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) myLocation();
            else permission.launch(Manifest.permission.ACCESS_FINE_LOCATION);
        });
        b.btnConfirm.setOnClickListener(v -> confirm());
    }

    private void showCoords() { showCoords(b.map.getMapCenter()); }

    private void showCoords(IGeoPoint p) {
        b.tvCoords.setText(String.format(Locale.US, "%.6f , %.6f", p.getLatitude(), p.getLongitude()));
    }

    @SuppressLint("MissingPermission")
    private void myLocation() {
        LocationServices.getFusedLocationProviderClient(this).getLastLocation().addOnSuccessListener(loc -> {
            if (loc == null) { Ui.snack(b.getRoot(), getString(R.string.location_permission_needed)); return; }
            b.map.getController().setZoom(17.0);
            b.map.getController().animateTo(new GeoPoint(loc.getLatitude(), loc.getLongitude()));
        });
    }

    private void confirm() {
        IGeoPoint c = b.map.getMapCenter();
        setResult(RESULT_OK, new Intent().putExtra(EXTRA_LAT, c.getLatitude()).putExtra(EXTRA_LNG, c.getLongitude()));
        finish();
    }

    @Override protected void onResume() { super.onResume(); b.map.onResume(); }

    @Override protected void onPause() { super.onPause(); b.map.onPause(); }
}
