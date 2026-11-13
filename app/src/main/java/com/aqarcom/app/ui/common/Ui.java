package com.aqarcom.app.ui.common;

import android.app.Activity;
import android.content.Context;
import android.text.InputType;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.DrawableRes;
import androidx.core.content.ContextCompat;

import com.aqarcom.app.R;
import com.aqarcom.app.data.model.ApiError;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Small UI helpers shared by every screen. */
public final class Ui {
    private Ui() {}

    public interface TextCallback { void onText(String text); }

    public static int dp(Context c, int v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    public static void toast(Context c, CharSequence msg) {
        Toast.makeText(c, msg, Toast.LENGTH_LONG).show();
    }

    public static void snack(View anchor, CharSequence msg) {
        Snackbar.make(anchor, msg, Snackbar.LENGTH_LONG).show();
    }

    public static void error(View anchor, ApiError e) {
        Snackbar s = Snackbar.make(anchor, e.message == null ? "حدث خطأ" : e.message, Snackbar.LENGTH_LONG);
        s.setBackgroundTint(ContextCompat.getColor(anchor.getContext(), R.color.aq_red));
        s.show();
    }

    /** Highlight form fields named by the server (VALIDATION_ERROR details.fields[]). */
    public static boolean showFieldErrors(ApiError e, Map<String, TextInputLayout> fields) {
        boolean any = false;
        for (String name : e.fieldNames()) {
            TextInputLayout til = fields.get(name);
            if (til != null) {
                til.setError(" ");
                any = true;
            }
        }
        return any;
    }

    public static void clearErrors(TextInputLayout... fields) {
        for (TextInputLayout t : fields) t.setError(null);
    }

    public static String text(TextInputEditText et) {
        return et.getText() == null ? "" : et.getText().toString().trim();
    }

    public static String text(TextView tv) {
        return tv.getText() == null ? "" : tv.getText().toString().trim();
    }

    /** Required-field check that sets an inline error. */
    public static boolean require(TextInputLayout til, TextInputEditText et, String message) {
        if (TextUtils.isEmpty(text(et))) {
            til.setError(message);
            return false;
        }
        til.setError(null);
        return true;
    }

    public static Double parseDouble(String s) {
        try { return TextUtils.isEmpty(s) ? null : Double.parseDouble(s.replace(",", "")); } catch (NumberFormatException e) { return null; }
    }

    public static Integer parseInt(String s) {
        try { return TextUtils.isEmpty(s) ? null : Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return null; }
    }

    /** Busy state for a primary button. */
    public static void busy(MaterialButton b, boolean busy) {
        if (busy) {
            if (b.getTag(R.id.tag_original_text) == null) b.setTag(R.id.tag_original_text, b.getText());
            b.setText(R.string.loading);
            b.setEnabled(false);
        } else {
            Object t = b.getTag(R.id.tag_original_text);
            if (t != null) b.setText((CharSequence) t);
            b.setEnabled(true);
        }
    }

    public static void hideKeyboard(Activity a) {
        View v = a.getCurrentFocus();
        if (v != null) {
            InputMethodManager imm = (InputMethodManager) a.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
        }
    }

    /** Fill an exposed dropdown from an Arabic label map. */
    public static void dropdown(MaterialAutoCompleteTextView view, Map<String, String> labels) {
        List<String> items = new ArrayList<>(labels.values());
        view.setAdapter(new ArrayAdapter<>(view.getContext(), R.layout.item_dropdown, items));
    }

    public static void dropdownSelect(MaterialAutoCompleteTextView view, Map<String, String> labels, String key) {
        if (key != null && labels.containsKey(key)) view.setText(labels.get(key), false);
    }

    public static String dropdownKey(MaterialAutoCompleteTextView view, Map<String, String> labels) {
        return Labels.keyOf(labels, view.getText() == null ? "" : view.getText().toString());
    }

    /* ---------------- status pills ---------------- */

    public enum Tone { PENDING, ACTIVE, DANGER, NEUTRAL, BLUE }

    public static void pill(TextView tv, String text, Tone tone) {
        int bg, fg;
        switch (tone) {
            case PENDING: bg = R.drawable.bg_pill_pending; fg = R.color.aq_amber; break;
            case ACTIVE: bg = R.drawable.bg_pill_active; fg = R.color.aq_emerald; break;
            case DANGER: bg = R.drawable.bg_pill_danger; fg = R.color.aq_red; break;
            case BLUE: bg = R.drawable.bg_pill_blue; fg = R.color.aq_blue; break;
            default: bg = R.drawable.bg_pill_neutral; fg = R.color.aq_ink_2;
        }
        tv.setText(text);
        tv.setBackgroundResource(bg);
        tv.setTextColor(ContextCompat.getColor(tv.getContext(), fg));
        tv.setVisibility(View.VISIBLE);
    }

    public static void tenderPill(TextView tv, String status) {
        Tone t;
        switch (status == null ? "" : status) {
            case "open": case "evaluating": case "shortlist_ready": t = Tone.BLUE; break;
            case "awarded": t = Tone.ACTIVE; break;
            case "cancelled": t = Tone.DANGER; break;
            case "draft": t = Tone.PENDING; break;
            default: t = Tone.NEUTRAL;
        }
        pill(tv, Labels.of(Labels.TENDER_STATUS, status), t);
    }

    public static void verificationPill(TextView tv, String status) {
        Tone t = "verified".equals(status) || "approved".equals(status) ? Tone.ACTIVE
                : "pending".equals(status) ? Tone.PENDING : "rejected".equals(status) ? Tone.DANGER : Tone.NEUTRAL;
        pill(tv, Labels.of(Labels.VERIFICATION, status), t);
    }

    public static void accountPill(TextView tv, String status) {
        Tone t = "active".equals(status) ? Tone.ACTIVE : "pending".equals(status) ? Tone.PENDING : Tone.DANGER;
        pill(tv, Labels.of(Labels.ACCOUNT, status), t);
    }

    /* ---------------- dialogs ---------------- */

    public static void confirm(Context c, String title, String message, String ok, Runnable onOk) {
        new MaterialAlertDialogBuilder(c)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(ok, (d, w) -> onOk.run())
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** Dialog with a text field (reason, note, etc.). */
    public static void prompt(Context c, String title, String hint, boolean required, TextCallback cb) {
        View v = LayoutInflater.from(c).inflate(R.layout.dialog_input, null, false);
        TextInputLayout til = v.findViewById(R.id.input_layout);
        TextInputEditText et = v.findViewById(R.id.input_text);
        til.setHint(hint);
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        androidx.appcompat.app.AlertDialog d = new MaterialAlertDialogBuilder(c)
                .setTitle(title)
                .setView(v)
                .setPositiveButton(R.string.confirm, null)
                .setNegativeButton(R.string.cancel, null)
                .create();
        d.setOnShowListener(x -> d.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(b -> {
            String t = text(et);
            if (required && t.length() < 3) {
                til.setError(c.getString(R.string.error_reason_required));
                return;
            }
            d.dismiss();
            cb.onText(t);
        }));
        d.show();
    }

    public static void setIconStart(MaterialButton b, @DrawableRes int icon) {
        b.setIconResource(icon);
    }
}
