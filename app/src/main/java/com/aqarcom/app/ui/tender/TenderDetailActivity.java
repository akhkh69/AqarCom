package com.aqarcom.app.ui.tender;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.aqarcom.app.R;
import com.aqarcom.app.data.model.ApiError;
import com.aqarcom.app.data.model.Bid;
import com.aqarcom.app.data.model.DeadlineRequest;
import com.aqarcom.app.data.model.PageMeta;
import com.aqarcom.app.data.model.ReasonRequest;
import com.aqarcom.app.data.model.Tender;
import com.aqarcom.app.data.model.TenderDocument;
import com.aqarcom.app.data.model.User;
import com.aqarcom.app.data.network.ApiCallback;
import com.aqarcom.app.data.network.ApiClient;
import com.aqarcom.app.data.network.TokenManager;
import com.aqarcom.app.databinding.ActivityTenderDetailBinding;
import com.aqarcom.app.databinding.ItemDocumentBinding;
import com.aqarcom.app.ui.account.VerificationActivity;
import com.aqarcom.app.ui.base.BaseActivity;
import com.aqarcom.app.ui.common.DateTimePicker;
import com.aqarcom.app.ui.common.Fmt;
import com.aqarcom.app.ui.common.Labels;
import com.aqarcom.app.ui.common.StateView;
import com.aqarcom.app.ui.common.Ui;

import java.io.File;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Tender details for every B2B role.
 *  Contractor: submit / modify / withdraw a bid before the server deadline (FR-12, FR-13, FR-16).
 *  Project owner: publish, upload a new spec-book version (FR-15), extend once (FR-14), cancel, open the shortlist (FR-18).
 */
public class TenderDetailActivity extends BaseActivity implements BidBottomSheet.Host {
    private static final String EXTRA_ID = "id";

    private ActivityTenderDetailBinding b;
    private StateView state;
    private Tender tender;
    private String id;
    private CountDownTimer timer;

    private final ActivityResultLauncher<String[]> pickDoc =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::onDocPicked);

    public static Intent intent(Context c, String id) {
        return new Intent(c, TenderDetailActivity.class).putExtra(EXTRA_ID, id);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityTenderDetailBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        setupToolbar(b.toolbar, null, true);
        id = getIntent().getStringExtra(EXTRA_ID);
        state = new StateView(b.state);
        b.swipe.setColorSchemeResources(R.color.aq_blue, R.color.aq_gold);
        b.swipe.setOnRefreshListener(this::load);

        b.btnSubmitBid.setOnClickListener(v -> BidBottomSheet.newBid(id, tender.priceWeight, tender.durationWeight).show(getSupportFragmentManager(), "bid"));
        b.btnEditBid.setOnClickListener(v -> BidBottomSheet.editBid(tender.myBid, tender.priceWeight, tender.durationWeight).show(getSupportFragmentManager(), "bid"));
        b.btnWithdrawBid.setOnClickListener(v -> Ui.confirm(this, getString(R.string.withdraw_bid),
                "سيُستبعد عرضك من الفرز ويبقى سجله محفوظاً. يمكنك تقديم عرض جديد قبل الإغلاق.", getString(R.string.withdraw_bid), this::withdraw));
        b.tvVerifyHint.setOnClickListener(v -> startActivity(new Intent(this, VerificationActivity.class)));

        b.btnUploadDoc.setOnClickListener(v -> pickDoc.launch(DocumentFiles.MIME_TYPES));
        b.btnShortlist.setOnClickListener(v -> startActivity(ShortlistActivity.intent(this, id)));
        b.btnPublish.setOnClickListener(v -> publish());
        b.btnEditTender.setOnClickListener(v -> startActivity(TenderFormActivity.editIntent(this, id)));
        b.btnExtend.setOnClickListener(v -> extend());
        b.btnCancelTender.setOnClickListener(v -> Ui.prompt(this, getString(R.string.cancel_tender), "سبب الإلغاء (يصل للمتعهدين)", false, this::cancelTender));
    }

    @Override
    protected void onResume() {
        super.onResume();
        load();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (timer != null) timer.cancel();
    }

    private void load() {
        if (tender == null) state.loading();
        ApiClient.tenders().get(id).enqueue(new ApiCallback<Tender>() {
            @Override public void onSuccess(Tender t, PageMeta meta) {
                b.swipe.setRefreshing(false);
                tender = t;
                state.hide();
                b.content.setVisibility(View.VISIBLE);
                bind(t);
                loadDocuments();
            }

            @Override public void onError(ApiError e) {
                b.swipe.setRefreshing(false);
                if (tender == null) state.error(e, TenderDetailActivity.this::load);
                else Ui.error(b.getRoot(), e);
            }
        });
    }

    private void bind(Tender t) {
        b.toolbar.setTitle(Labels.of(Labels.TENDER_TYPES, t.tenderType));
        Ui.tenderPill(b.tvStatus, t.status);
        b.tvType.setText(Labels.of(Labels.TENDER_TYPES, t.tenderType));
        b.tvTitle.setText(t.title);
        String owner = t.owner == null ? "" : (t.owner.companyName != null ? t.owner.companyName : t.owner.fullName);
        if (t.city != null) owner += " · " + t.city;
        b.tvOwner.setText(owner);
        b.tvBudget.setVisibility(t.estimatedBudget != null ? View.VISIBLE : View.GONE);
        if (t.estimatedBudget != null) b.tvBudget.setText(getString(R.string.estimated_budget) + ": " + Fmt.number(t.estimatedBudget));

        // countdown from the server's own clock (BR-11)
        if (timer != null) timer.cancel();
        boolean open = "open".equals(t.status);
        b.tvCountdownLabel.setText(open ? getString(R.string.time_remaining) : Labels.of(Labels.TENDER_STATUS, t.status));
        b.tvDeadline.setText(getString(R.string.deadline) + ": " + Fmt.dateTime(t.deadline) + (t.extensionUsed ? "  ·  " + getString(R.string.extension_used) : ""));
        if (open && t.secondsRemaining > 0) {
            timer = new CountDownTimer(t.secondsRemaining * 1000L, 1000) {
                @Override public void onTick(long ms) { b.tvCountdown.setText(Fmt.countdown(ms / 1000)); }
                @Override public void onFinish() {
                    b.tvCountdown.setText(Fmt.countdown(0));
                    b.getRoot().postDelayed(TenderDetailActivity.this::load, 2500); // server closes & evaluates
                }
            }.start();
        } else {
            b.tvCountdown.setText(open ? Fmt.countdown(0) : Fmt.dateTime(t.closedAt != null ? t.closedAt : t.deadline));
        }

        b.tvPriceWeight.setText(String.format(Locale.US, "%s: %d%%", getString(R.string.price_weight), t.priceWeight));
        b.tvDurationWeight.setText(String.format(Locale.US, "%s: %d%%", getString(R.string.duration_weight), t.durationWeight));
        b.barPrice.setProgress(t.priceWeight);
        b.barDuration.setProgress(t.durationWeight);

        boolean hasDesc = !TextUtils.isEmpty(t.description);
        b.labelDescription.setVisibility(hasDesc ? View.VISIBLE : View.GONE);
        b.tvDescription.setVisibility(hasDesc ? View.VISIBLE : View.GONE);
        b.tvDescription.setText(t.description);

        User me = TokenManager.get(this).getUser();
        boolean contractor = me != null && TokenManager.Roles.CONTRACTOR.equals(me.role);
        bindContractor(t, contractor, me);
        bindOwner(t);
    }

    private void bindContractor(Tender t, boolean contractor, User me) {
        b.groupContractor.setVisibility(contractor ? View.VISIBLE : View.GONE);
        if (!contractor) return;
        boolean open = "open".equals(t.status) && t.secondsRemaining > 0;
        boolean verified = "verified".equals(me.verificationStatus);
        Bid bid = t.myBid;
        b.cardMyBid.setVisibility(bid != null ? View.VISIBLE : View.GONE);
        if (bid != null) {
            b.tvBidPrice.setText(Fmt.number(bid.totalPrice));
            b.tvBidDays.setText(bid.durationDays + " " + getString(R.string.days_unit));
            b.tvBidTime.setText(Fmt.dateTime(bid.submittedAt));
            Ui.Tone tone = "won".equals(bid.outcome) ? Ui.Tone.ACTIVE : "lost".equals(bid.outcome) ? Ui.Tone.NEUTRAL : Ui.Tone.PENDING;
            Ui.pill(b.tvBidOutcome, Labels.of(Labels.OUTCOME, bid.outcome), tone);
            b.groupBidActions.setVisibility(open ? View.VISIBLE : View.GONE);
        }
        b.tvVerifyHint.setVisibility(open && !verified ? View.VISIBLE : View.GONE);
        b.btnSubmitBid.setVisibility(open && verified && bid == null ? View.VISIBLE : View.GONE);
    }

    private void bindOwner(Tender t) {
        b.groupOwner.setVisibility(t.isMine ? View.VISIBLE : View.GONE);
        boolean editableDoc = t.isMine && ("draft".equals(t.status) || ("open".equals(t.status) && t.secondsRemaining > 0));
        b.btnUploadDoc.setVisibility(editableDoc ? View.VISIBLE : View.GONE);
        b.btnUploadDoc.setText(t.currentDocumentVersion == 0 ? R.string.choose_file : R.string.upload_new_version);
        if (!t.isMine) return;

        String s = t.status;
        String info;
        switch (s) {
            case "draft": info = t.currentDocumentVersion == 0 ? "ارفع دفتر الشروط ثم انشر المناقصة لتبدأ باستقبال العروض." : "المناقصة جاهزة للنشر."; break;
            case "open": info = getString(R.string.active_bids) + ": " + (t.activeBidsCount == null ? 0 : t.activeBidsCount) + "\nتظهر تفاصيل العروض وقائمة الترشيح بعد الإغلاق."; break;
            case "evaluating": info = "جارٍ فرز العروض…"; break;
            case "shortlist_ready": info = "قائمة أفضل 5 عروض جاهزة. اختر العرض الفائز لإرساء المناقصة."; break;
            case "awarded": info = "تم إرساء المناقصة."; break;
            case "closed_no_bids": info = "أُغلقت المناقصة دون ورود أي عرض."; break;
            case "cancelled": info = "أُلغيت المناقصة" + (t.cancelReason != null && !t.cancelReason.isEmpty() ? ": " + t.cancelReason : "."); break;
            default: info = "";
        }
        b.tvOwnerInfo.setText(info);
        b.btnShortlist.setVisibility("shortlist_ready".equals(s) || "awarded".equals(s) ? View.VISIBLE : View.GONE);
        b.btnPublish.setVisibility("draft".equals(s) ? View.VISIBLE : View.GONE);
        b.btnPublish.setEnabled(t.currentDocumentVersion > 0);
        b.btnEditTender.setVisibility("draft".equals(s) || "open".equals(s) ? View.VISIBLE : View.GONE);
        b.btnExtend.setVisibility("open".equals(s) && !t.extensionUsed && t.secondsRemaining > 0 ? View.VISIBLE : View.GONE);
        b.btnCancelTender.setVisibility("draft".equals(s) || "open".equals(s) || "shortlist_ready".equals(s) ? View.VISIBLE : View.GONE);
    }

    private void loadDocuments() {
        ApiClient.tenders().documents(id).enqueue(new ApiCallback<List<TenderDocument>>() {
            @Override public void onSuccess(List<TenderDocument> docs, PageMeta meta) {
                b.listDocuments.removeAllViews();
                if (docs.isEmpty()) {
                    ItemDocumentBinding row = ItemDocumentBinding.inflate(LayoutInflater.from(TenderDetailActivity.this), b.listDocuments, true);
                    row.tvVersion.setText(R.string.spec_book);
                    row.tvMeta.setText(R.string.no_file_selected);
                    row.btnDownload.setVisibility(View.GONE);
                    return;
                }
                for (TenderDocument d : docs) {
                    ItemDocumentBinding row = ItemDocumentBinding.inflate(LayoutInflater.from(TenderDetailActivity.this), b.listDocuments, true);
                    String label = getString(R.string.version_n, d.version) + (d.version == docs.get(0).version ? "  (الأحدث)" : "");
                    row.tvVersion.setText(label);
                    row.tvMeta.setText(d.originalName + " · " + Fmt.number(d.sizeMb) + " MB · " + Fmt.date(d.createdAt));
                    row.tvNote.setVisibility(TextUtils.isEmpty(d.note) ? View.GONE : View.VISIBLE);
                    row.tvNote.setText(d.note);
                    row.btnDownload.setOnClickListener(v -> download(d));
                }
            }

            @Override public void onError(ApiError e) { }
        });
    }

    private void download(TenderDocument d) {
        b.progress.setVisibility(View.VISIBLE);
        b.progress.setIndeterminate(true);
        DocumentFiles.downloadAndOpen(this, id, d.version, d.originalName, d.mimeType, new DocumentFiles.Done<File>() {
            @Override public void onSuccess(File f) { b.progress.setVisibility(View.GONE); }
            @Override public void onError(ApiError e) { b.progress.setVisibility(View.GONE); Ui.error(b.getRoot(), e); }
        });
    }

    private void onDocPicked(Uri uri) {
        if (uri == null) return;
        if (tender != null && tender.currentDocumentVersion > 0 && "open".equals(tender.status)) {
            // FR-15: a clarification note travels with the new version to everyone who viewed / bid
            Ui.prompt(this, getString(R.string.upload_new_version), getString(R.string.notes) + " " + getString(R.string.optional), false,
                    note -> upload(uri, note));
        } else upload(uri, null);
    }

    private void upload(Uri uri, String note) {
        b.progress.setVisibility(View.VISIBLE);
        b.progress.setIndeterminate(false);
        b.progress.setProgress(0);
        b.btnUploadDoc.setEnabled(false);
        DocumentFiles.upload(this, id, uri, note, pct -> b.progress.setProgressCompat(pct, true), new DocumentFiles.Done<TenderDocument>() {
            @Override public void onSuccess(TenderDocument d) {
                b.progress.setVisibility(View.GONE);
                b.btnUploadDoc.setEnabled(true);
                Ui.snack(b.getRoot(), getString(R.string.version_n, d.version));
                load();
            }

            @Override public void onError(ApiError e) {
                b.progress.setVisibility(View.GONE);
                b.btnUploadDoc.setEnabled(true);
                Ui.error(b.getRoot(), e);
            }
        });
    }

    private void publish() {
        Ui.busy(b.btnPublish, true);
        ApiClient.tenders().publish(id).enqueue(new ApiCallback<Tender>() {
            @Override public void onSuccess(Tender t, PageMeta meta) {
                Ui.busy(b.btnPublish, false);
                Ui.snack(b.getRoot(), getString(R.string.tender_published));
                load();
            }

            @Override public void onError(ApiError e) {
                Ui.busy(b.btnPublish, false);
                Ui.error(b.getRoot(), e);
            }
        });
    }

    /** FR-14 — once only, and only to a later date. */
    private void extend() {
        Date current = Fmt.parse(tender.deadline);
        DateTimePicker.pick(getSupportFragmentManager(), getString(R.string.extend_deadline), current, date -> {
            if (current != null && !date.after(current)) {
                Ui.snack(b.getRoot(), "الموعد الجديد يجب أن يكون لاحقاً للموعد الحالي");
                return;
            }
            Ui.confirm(this, getString(R.string.extend_deadline),
                    "الموعد الجديد: " + Fmt.dateTime(date) + "\nالتمديد مسموح مرة واحدة فقط ولا يمكن التراجع عنه.",
                    getString(R.string.confirm), () ->
                            ApiClient.tenders().extend(id, new DeadlineRequest(Fmt.iso(date))).enqueue(new ApiCallback<Tender>() {
                                @Override public void onSuccess(Tender t, PageMeta meta) { load(); }
                                @Override public void onError(ApiError e) { Ui.error(b.getRoot(), e); }
                            }));
        });
    }

    private void cancelTender(String reason) {
        ApiClient.tenders().cancel(id, new ReasonRequest(reason)).enqueue(new ApiCallback<Tender>() {
            @Override public void onSuccess(Tender t, PageMeta meta) { load(); }
            @Override public void onError(ApiError e) { Ui.error(b.getRoot(), e); }
        });
    }

    private void withdraw() {
        ApiClient.bids().withdraw(tender.myBid.id).enqueue(new ApiCallback<Bid>() {
            @Override public void onSuccess(Bid bid, PageMeta meta) {
                Ui.snack(b.getRoot(), getString(R.string.bid_withdrawn));
                load();
            }

            @Override public void onError(ApiError e) { Ui.error(b.getRoot(), e); }
        });
    }

    @Override
    public void onBidSaved(String message) {
        Ui.snack(b.getRoot(), message);
        load();
    }
}
