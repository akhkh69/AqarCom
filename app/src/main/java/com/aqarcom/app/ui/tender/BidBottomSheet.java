package com.aqarcom.app.ui.tender;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.aqarcom.app.R;
import com.aqarcom.app.data.model.ApiError;
import com.aqarcom.app.data.model.Bid;
import com.aqarcom.app.data.model.BidRequest;
import com.aqarcom.app.data.model.PageMeta;
import com.aqarcom.app.data.network.ApiCallback;
import com.aqarcom.app.data.network.ApiClient;
import com.aqarcom.app.databinding.SheetBidBinding;
import com.aqarcom.app.ui.common.Fmt;
import com.aqarcom.app.ui.common.Ui;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.Locale;

/** Submit (FR-12) or modify a bid before the deadline. The activity implements {@link Host}. */
public class BidBottomSheet extends BottomSheetDialogFragment {

    public interface Host { void onBidSaved(String message); }

    private static final String A_TENDER = "tender", A_BID = "bid", A_PRICE = "price", A_DAYS = "days", A_NOTES = "notes", A_PW = "pw", A_DW = "dw";
    private SheetBidBinding b;

    public static BidBottomSheet newBid(String tenderId, int priceWeight, int durationWeight) {
        BidBottomSheet s = new BidBottomSheet();
        Bundle a = new Bundle();
        a.putString(A_TENDER, tenderId);
        a.putInt(A_PW, priceWeight);
        a.putInt(A_DW, durationWeight);
        s.setArguments(a);
        return s;
    }

    public static BidBottomSheet editBid(Bid bid, int priceWeight, int durationWeight) {
        BidBottomSheet s = new BidBottomSheet();
        Bundle a = new Bundle();
        a.putString(A_BID, bid.id);
        a.putDouble(A_PRICE, bid.totalPrice);
        a.putInt(A_DAYS, bid.durationDays);
        a.putString(A_NOTES, bid.notes);
        a.putInt(A_PW, priceWeight);
        a.putInt(A_DW, durationWeight);
        s.setArguments(a);
        return s;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        b = SheetBidBinding.inflate(inflater, container, false);
        Bundle a = requireArguments();
        boolean edit = a.getString(A_BID) != null;
        b.tvTitle.setText(edit ? R.string.edit_bid : R.string.submit_bid);
        b.btnSubmit.setText(edit ? R.string.save : R.string.submit_bid);
        b.tvWeights.setText(String.format(Locale.US,
                "تُرتَّب العروض بعد الإغلاق: وزن السعر %d%% ووزن المدة %d%%. الأقل سعراً والأقصر مدة يحصل على نتيجة أعلى.",
                a.getInt(A_PW), a.getInt(A_DW)));
        if (edit) {
            b.etPrice.setText(Fmt.number(a.getDouble(A_PRICE)).replace(",", ""));
            b.etDays.setText(String.valueOf(a.getInt(A_DAYS)));
            b.etNotes.setText(a.getString(A_NOTES));
        }
        b.btnSubmit.setOnClickListener(v -> submit(edit));
        return b.getRoot();
    }

    private void submit(boolean edit) {
        Ui.clearErrors(b.tilPrice, b.tilDays);
        Double price = Ui.parseDouble(Ui.text(b.etPrice));
        Integer days = Ui.parseInt(Ui.text(b.etDays));
        boolean ok = true;
        if (price == null || price <= 0) { b.tilPrice.setError("السعر الإجمالي يجب أن يكون أكبر من الصفر"); ok = false; }
        if (days == null || days < 1) { b.tilDays.setError("مدة التنفيذ يوم واحد على الأقل"); ok = false; }
        if (!ok) return;
        String notes = Ui.text(b.etNotes);
        BidRequest req = new BidRequest(price, days, notes.isEmpty() ? null : notes);
        Ui.busy(b.btnSubmit, true);
        ApiCallback<Bid> cb = new ApiCallback<Bid>() {
            @Override public void onSuccess(Bid bid, PageMeta meta) {
                if (getActivity() instanceof Host) ((Host) getActivity()).onBidSaved(getString(edit ? R.string.bid_updated : R.string.bid_submitted));
                dismissAllowingStateLoss();
            }

            @Override public void onError(ApiError e) {
                if (b == null) return;
                Ui.busy(b.btnSubmit, false);
                Ui.error(b.getRoot(), e);
            }
        };
        Bundle a = requireArguments();
        if (edit) ApiClient.bids().update(a.getString(A_BID), req).enqueue(cb);
        else ApiClient.tenders().submitBid(a.getString(A_TENDER), req).enqueue(cb);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }
}
