package com.aqarcom.app.ui.tender;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.aqarcom.app.R;
import com.aqarcom.app.data.model.ApiError;
import com.aqarcom.app.data.model.PageMeta;
import com.aqarcom.app.data.model.Tender;
import com.aqarcom.app.data.model.TenderDocument;
import com.aqarcom.app.data.model.TenderRequest;
import com.aqarcom.app.data.network.ApiCallback;
import com.aqarcom.app.data.network.ApiClient;
import com.aqarcom.app.data.network.MultipartUtil;
import com.aqarcom.app.databinding.ActivityTenderFormBinding;
import com.aqarcom.app.ui.base.BaseActivity;
import com.aqarcom.app.ui.common.DateTimePicker;
import com.aqarcom.app.ui.common.Fmt;
import com.aqarcom.app.ui.common.Labels;
import com.aqarcom.app.ui.common.Ui;

import java.util.Date;
import java.util.Locale;

/**
 * FR-10 / FR-11 — create a tender (draft) with weights that always sum to 100 (BR-07),
 * a future deadline, and a spec book; optionally publish right away. Also edits drafts / open tenders.
 */
public class TenderFormActivity extends BaseActivity {
    private static final String EXTRA_ID = "id";

    private ActivityTenderFormBinding b;
    private String editId;
    private Tender editing;
    private Date deadline;
    private Uri docUri;

    private final ActivityResultLauncher<String[]> pickDoc =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri == null) return;
                long size = MultipartUtil.size(this, uri);
                if (size > DocumentFiles.MAX_BYTES) {
                    Ui.snack(b.getRoot(), "حجم الملف يتجاوز 20 ميغابايت");
                    return;
                }
                docUri = uri;
                b.tvDoc.setText(MultipartUtil.displayName(this, uri) + "  ·  " + Fmt.number(size / 1048576.0) + " MB");
                b.tvDoc.setTextColor(getColor(R.color.aq_emerald));
                b.cbPublish.setEnabled(true);
                updateSaveText();
            });

    public static Intent editIntent(Context c, String id) {
        return new Intent(c, TenderFormActivity.class).putExtra(EXTRA_ID, id);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityTenderFormBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        editId = getIntent().getStringExtra(EXTRA_ID);
        setupToolbar(b.toolbar, getString(editId == null ? R.string.new_tender : R.string.edit), true);

        Ui.dropdown(b.ddType, Labels.TENDER_TYPES);
        Ui.dropdown(b.ddCity, Labels.CITIES);
        b.sliderWeight.addOnChangeListener((s, value, fromUser) -> showWeights((int) value));
        showWeights(60);

        b.btnDeadline.setOnClickListener(v -> DateTimePicker.pick(getSupportFragmentManager(), getString(R.string.pick_deadline), deadline, d -> {
            if (!d.after(new Date())) {
                Ui.snack(b.getRoot(), "موعد الإغلاق يجب أن يكون لاحقاً للحظة النشر");
                return;
            }
            deadline = d;
            b.btnDeadline.setText(Fmt.dateTime(d));
        }));
        b.btnPickDoc.setOnClickListener(v -> pickDoc.launch(DocumentFiles.MIME_TYPES));
        b.cbPublish.setOnCheckedChangeListener((c, checked) -> updateSaveText());
        b.btnSave.setOnClickListener(v -> save());

        if (editId != null) {
            // spec-book versions are managed from the details screen
            b.labelDoc.setVisibility(View.GONE);
            b.groupDoc.setVisibility(View.GONE);
            b.btnSave.setText(R.string.save);
            load();
        }
    }

    private void showWeights(int price) {
        b.tvPriceWeight.setText(String.format(Locale.US, "%d%%", price));
        b.tvDurationWeight.setText(String.format(Locale.US, "%d%%", 100 - price));
    }

    private void updateSaveText() {
        if (editId != null) return;
        b.btnSave.setText(docUri != null && b.cbPublish.isChecked() ? R.string.publish : R.string.save_draft);
    }

    private void load() {
        b.btnSave.setEnabled(false);
        ApiClient.tenders().get(editId).enqueue(new ApiCallback<Tender>() {
            @Override public void onSuccess(Tender t, PageMeta meta) {
                editing = t;
                b.btnSave.setEnabled(true);
                b.etTitle.setText(t.title);
                b.etDescription.setText(t.description);
                b.etAddress.setText(t.address);
                if (t.estimatedBudget != null) b.etBudget.setText(Fmt.number(t.estimatedBudget).replace(",", ""));
                Ui.dropdownSelect(b.ddType, Labels.TENDER_TYPES, t.tenderType);
                Ui.dropdownSelect(b.ddCity, Labels.CITIES, t.city);
                b.sliderWeight.setValue(Math.round(t.priceWeight / 5f) * 5f);
                showWeights(t.priceWeight);
                deadline = Fmt.parse(t.deadline);
                b.btnDeadline.setText(Fmt.dateTime(deadline));
                // after publishing: deadline changes only through "extend"; type is fixed
                boolean draft = "draft".equals(t.status);
                b.btnDeadline.setEnabled(draft);
                b.ddType.setEnabled(draft);
            }

            @Override public void onError(ApiError e) { Ui.error(b.getRoot(), e); }
        });
    }

    private TenderRequest collect() {
        Ui.clearErrors(b.tilTitle, b.tilType);
        boolean ok = Ui.require(b.tilTitle, b.etTitle, getString(R.string.error_required));
        TenderRequest r = new TenderRequest();
        r.title = Ui.text(b.etTitle);
        r.description = Ui.text(b.etDescription);
        r.tenderType = Ui.dropdownKey(b.ddType, Labels.TENDER_TYPES);
        r.city = Ui.dropdownKey(b.ddCity, Labels.CITIES);
        String addr = Ui.text(b.etAddress);
        r.address = addr.isEmpty() ? null : addr;
        r.estimatedBudget = Ui.parseDouble(Ui.text(b.etBudget));
        int pw = Math.round(b.sliderWeight.getValue());
        r.priceWeight = pw;
        r.durationWeight = 100 - pw;
        if (r.tenderType == null) { b.tilType.setError(getString(R.string.error_required)); ok = false; }
        if (deadline == null) { Ui.snack(b.getRoot(), getString(R.string.pick_deadline)); ok = false; }
        boolean draft = editing == null || "draft".equals(editing.status);
        if (draft && deadline != null) r.deadline = Fmt.iso(deadline);
        if (editing != null && !draft) {
            r.tenderType = null; // fixed after publishing
            if (editing.priceWeight == pw) { r.priceWeight = null; r.durationWeight = null; } // BR-08: unchanged weights are not resent
        }
        return ok ? r : null;
    }

    private void save() {
        Ui.hideKeyboard(this);
        TenderRequest r = collect();
        if (r == null) return;
        Ui.busy(b.btnSave, true);
        ApiCallback<Tender> cb = new ApiCallback<Tender>() {
            @Override public void onSuccess(Tender t, PageMeta meta) {
                if (editId != null) {
                    Ui.toast(TenderFormActivity.this, getString(R.string.tender_saved));
                    finish();
                } else if (docUri != null) uploadDoc(t.id);
                else openDetails(t.id, getString(R.string.tender_saved));
            }

            @Override public void onError(ApiError e) {
                Ui.busy(b.btnSave, false);
                Ui.error(b.getRoot(), e);
            }
        };
        if (editId == null) ApiClient.tenders().create(r).enqueue(cb);
        else ApiClient.tenders().update(editId, r).enqueue(cb);
    }

    private void uploadDoc(String tenderId) {
        b.progress.setVisibility(View.VISIBLE);
        DocumentFiles.upload(this, tenderId, docUri, null, pct -> b.progress.setProgressCompat(pct, true), new DocumentFiles.Done<TenderDocument>() {
            @Override public void onSuccess(TenderDocument d) {
                if (b.cbPublish.isChecked()) publish(tenderId);
                else openDetails(tenderId, getString(R.string.tender_saved));
            }

            @Override public void onError(ApiError e) {
                // draft exists; let the owner retry the upload from the details screen
                openDetails(tenderId, e.message);
            }
        });
    }

    private void publish(String tenderId) {
        ApiClient.tenders().publish(tenderId).enqueue(new ApiCallback<Tender>() {
            @Override public void onSuccess(Tender t, PageMeta meta) { openDetails(tenderId, getString(R.string.tender_published)); }
            @Override public void onError(ApiError e) { openDetails(tenderId, e.message); }
        });
    }

    private void openDetails(String tenderId, String message) {
        Ui.toast(this, message);
        startActivity(TenderDetailActivity.intent(this, tenderId));
        finish();
    }
}
