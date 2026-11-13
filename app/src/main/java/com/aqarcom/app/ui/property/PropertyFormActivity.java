package com.aqarcom.app.ui.property;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.aqarcom.app.R;
import com.aqarcom.app.data.model.ApiError;
import com.aqarcom.app.data.model.ImageItem;
import com.aqarcom.app.data.model.PageMeta;
import com.aqarcom.app.data.model.Property;
import com.aqarcom.app.data.model.PropertyRequest;
import com.aqarcom.app.data.network.ApiCallback;
import com.aqarcom.app.data.network.ApiClient;
import com.aqarcom.app.data.network.ErrorParser;
import com.aqarcom.app.databinding.ActivityPropertyFormBinding;
import com.aqarcom.app.ui.base.BaseActivity;
import com.aqarcom.app.ui.common.Fmt;
import com.aqarcom.app.ui.common.ImageCompressor;
import com.aqarcom.app.ui.common.Labels;
import com.aqarcom.app.ui.common.Ui;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;

/** FR-05 publish / FR-06 edit a listing: details, location on map (BR-04), up to 10 photos (OI-03). */
public class PropertyFormActivity extends BaseActivity {
    private static final String EXTRA_ID = "id";
    private static final int MAX_PHOTOS = 10;

    private ActivityPropertyFormBinding b;
    private PhotoAdapter photos;
    private String editId;
    private Double lat, lng;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private final ActivityResultLauncher<PickVisualMediaRequest> pickImages =
            registerForActivityResult(new ActivityResultContracts.PickMultipleVisualMedia(MAX_PHOTOS), uris -> {
                for (Uri u : uris) {
                    if (photos.getItemCount() >= MAX_PHOTOS) break;
                    photos.add(new PhotoAdapter.Photo(null, null, u));
                }
            });

    private final ActivityResultLauncher<Intent> pickLocation =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), r -> {
                if (r.getResultCode() == Activity.RESULT_OK && r.getData() != null) {
                    lat = r.getData().getDoubleExtra(LocationPickerActivity.EXTRA_LAT, 0);
                    lng = r.getData().getDoubleExtra(LocationPickerActivity.EXTRA_LNG, 0);
                    showLocation();
                }
            });

    public static Intent editIntent(Context c, String id) {
        return new Intent(c, PropertyFormActivity.class).putExtra(EXTRA_ID, id);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityPropertyFormBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        editId = getIntent().getStringExtra(EXTRA_ID);
        setupToolbar(b.toolbar, getString(editId == null ? R.string.new_listing : R.string.edit_listing), true);

        Ui.dropdown(b.ddType, Labels.PROPERTY_TYPES);
        Ui.dropdown(b.ddPurpose, Labels.PURPOSES);
        Ui.dropdown(b.ddCurrency, Labels.CURRENCIES);
        Ui.dropdown(b.ddCity, Labels.CITIES);
        Ui.dropdownSelect(b.ddCurrency, Labels.CURRENCIES, "USD");

        photos = new PhotoAdapter(this::removePhoto);
        b.listPhotos.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        b.listPhotos.setAdapter(photos);
        b.btnAddPhotos.setOnClickListener(v -> pickImages.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE).build()));
        b.btnLocation.setOnClickListener(v -> pickLocation.launch(LocationPickerActivity.intent(this, lat, lng)));
        b.btnSave.setOnClickListener(v -> save());

        if (editId != null) {
            b.btnSave.setText(R.string.save);
            loadExisting();
        }
    }

    private void loadExisting() {
        b.btnSave.setEnabled(false);
        ApiClient.properties().get(editId).enqueue(new ApiCallback<Property>() {
            @Override public void onSuccess(Property p, PageMeta meta) {
                b.btnSave.setEnabled(true);
                b.etTitle.setText(p.title);
                b.etDescription.setText(p.description);
                Ui.dropdownSelect(b.ddType, Labels.PROPERTY_TYPES, p.propertyType);
                Ui.dropdownSelect(b.ddPurpose, Labels.PURPOSES, p.purpose);
                Ui.dropdownSelect(b.ddCurrency, Labels.CURRENCIES, p.currency);
                Ui.dropdownSelect(b.ddCity, Labels.CITIES, p.city);
                b.etPrice.setText(Fmt.number(p.price).replace(",", ""));
                b.etArea.setText(Fmt.number(p.area).replace(",", ""));
                b.etRooms.setText(String.valueOf(p.rooms));
                b.etBaths.setText(String.valueOf(p.bathrooms));
                if (p.floor != null) b.etFloor.setText(String.valueOf(p.floor));
                b.etDistrict.setText(p.district);
                b.etAddress.setText(p.address);
                lat = p.latitude;
                lng = p.longitude;
                showLocation();
                if (p.images != null) for (ImageItem i : p.images) photos.add(new PhotoAdapter.Photo(i.id, i.url, null));
            }

            @Override public void onError(ApiError e) {
                Ui.error(b.getRoot(), e);
            }
        });
    }

    private void showLocation() {
        if (lat == null || lng == null) return;
        b.tvLocation.setText(getString(R.string.location_selected) + String.format(Locale.US, "  (%.5f, %.5f)", lat, lng));
        b.tvLocation.setTextColor(getColor(R.color.aq_emerald));
        b.btnLocation.setText(R.string.pick_location);
    }

    private void removePhoto(PhotoAdapter.Photo p, int pos) {
        if (p.id == null) { photos.removeAt(pos); return; }
        // already uploaded → delete on the server
        Ui.confirm(this, getString(R.string.delete), getString(R.string.photos), getString(R.string.delete), () ->
                ApiClient.properties().deleteImage(editId, p.id).enqueue(new ApiCallback<Property>() {
                    @Override public void onSuccess(Property data, PageMeta meta) { photos.removeAt(photos.items().indexOf(p)); }
                    @Override public void onError(ApiError e) { Ui.error(b.getRoot(), e); }
                }));
    }

    private PropertyRequest collect() {
        Ui.clearErrors(b.tilTitle, b.tilType, b.tilPurpose, b.tilPrice, b.tilArea, b.tilCity);
        boolean ok = Ui.require(b.tilTitle, b.etTitle, getString(R.string.error_required));
        PropertyRequest r = new PropertyRequest();
        r.title = Ui.text(b.etTitle);
        r.description = Ui.text(b.etDescription);
        r.propertyType = Ui.dropdownKey(b.ddType, Labels.PROPERTY_TYPES);
        r.purpose = Ui.dropdownKey(b.ddPurpose, Labels.PURPOSES);
        r.currency = Ui.dropdownKey(b.ddCurrency, Labels.CURRENCIES);
        r.city = Ui.dropdownKey(b.ddCity, Labels.CITIES);
        r.price = Ui.parseDouble(Ui.text(b.etPrice));
        r.area = Ui.parseDouble(Ui.text(b.etArea));
        r.rooms = Ui.parseInt(Ui.text(b.etRooms));
        r.bathrooms = Ui.parseInt(Ui.text(b.etBaths));
        r.floor = Ui.parseInt(Ui.text(b.etFloor));
        r.district = Ui.text(b.etDistrict);
        r.address = Ui.text(b.etAddress);
        r.latitude = lat;
        r.longitude = lng;
        String req = getString(R.string.error_required);
        if (r.propertyType == null) { b.tilType.setError(req); ok = false; }
        if (r.purpose == null) { b.tilPurpose.setError(req); ok = false; }
        if (r.price == null) { b.tilPrice.setError(req); ok = false; }
        if (r.area == null || r.area <= 0) { b.tilArea.setError(req); ok = false; }
        if (r.city == null) { b.tilCity.setError(req); ok = false; }
        if (lat == null || lng == null) {
            // BR-04 — no listing without a location
            b.tvLocation.setTextColor(getColor(R.color.aq_red));
            Ui.snack(b.getRoot(), getString(R.string.location_required));
            ok = false;
        }
        return ok ? r : null;
    }

    private void save() {
        Ui.hideKeyboard(this);
        PropertyRequest r = collect();
        if (r == null) return;
        Ui.busy(b.btnSave, true);
        ApiCallback<Property> cb = new ApiCallback<Property>() {
            @Override public void onSuccess(Property p, PageMeta meta) {
                List<Uri> uris = photos.newUris();
                if (uris.isEmpty()) done();
                else uploadPhotos(p.id, uris);
            }

            @Override public void onError(ApiError e) {
                Ui.busy(b.btnSave, false);
                Map<String, TextInputLayout> f = new HashMap<>();
                f.put("title", b.tilTitle);
                f.put("price", b.tilPrice);
                f.put("area", b.tilArea);
                f.put("city", b.tilCity);
                Ui.showFieldErrors(e, f);
                Ui.error(b.getRoot(), e);
            }
        };
        if (editId == null) ApiClient.properties().create(r).enqueue(cb);
        else ApiClient.properties().update(editId, r).enqueue(cb);
    }

    /** Compress on a background thread, then upload all new photos in one multipart request. */
    private void uploadPhotos(String propertyId, List<Uri> uris) {
        b.progress.setVisibility(View.VISIBLE);
        b.progress.setIndeterminate(true);
        b.btnSave.setText(R.string.uploading_photos);
        io.execute(() -> {
            try {
                List<MultipartBody.Part> parts = new ArrayList<>();
                int i = 0;
                for (Uri u : uris) {
                    byte[] jpg = ImageCompressor.compress(this, u);
                    parts.add(MultipartBody.Part.createFormData("images", "photo_" + (i++) + ".jpg",
                            RequestBody.create(jpg, MediaType.parse("image/jpeg"))));
                }
                retrofit2.Response<com.aqarcom.app.data.model.ApiResponse<Property>> res =
                        ApiClient.properties().uploadImages(propertyId, parts).execute();
                main.post(() -> {
                    if (res.isSuccessful()) done();
                    else {
                        // listing saved but photos failed → open it in edit mode so the user can retry
                        Ui.toast(this, ErrorParser.parse(res).message);
                        startActivity(editIntent(this, propertyId));
                        finish();
                    }
                });
            } catch (Exception ex) {
                main.post(() -> {
                    Ui.toast(this, ErrorParser.fromThrowable(ex).message);
                    startActivity(editIntent(this, propertyId));
                    finish();
                });
            }
        });
    }

    private void done() {
        Ui.toast(this, getString(R.string.listing_saved));
        setResult(RESULT_OK);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        io.shutdown();
    }
}
