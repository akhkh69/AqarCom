package com.aqarcom.app.ui.common;

import androidx.fragment.app.FragmentManager;

import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.DateValidatorPointForward;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;

import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

/** Material date picker followed by a time picker; returns a local Date (sent to the API as UTC ISO). */
public final class DateTimePicker {
    private DateTimePicker() {}

    public interface Callback { void onPicked(Date date); }

    public static void pick(FragmentManager fm, String title, Date initial, Callback cb) {
        Calendar start = Calendar.getInstance();
        if (initial != null) start.setTime(initial);
        else start.add(Calendar.DAY_OF_MONTH, 7);

        // MaterialDatePicker works in UTC milliseconds for the selected day
        Calendar utcDay = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        utcDay.clear();
        utcDay.set(start.get(Calendar.YEAR), start.get(Calendar.MONTH), start.get(Calendar.DAY_OF_MONTH));

        MaterialDatePicker<Long> dp = MaterialDatePicker.Builder.datePicker()
                .setTitleText(title)
                .setSelection(utcDay.getTimeInMillis())
                .setCalendarConstraints(new CalendarConstraints.Builder().setValidator(DateValidatorPointForward.now()).build())
                .build();
        dp.addOnPositiveButtonClickListener(sel -> {
            Calendar picked = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            picked.setTimeInMillis(sel);
            MaterialTimePicker tp = new MaterialTimePicker.Builder()
                    .setTimeFormat(TimeFormat.CLOCK_12H)
                    .setHour(start.get(Calendar.HOUR_OF_DAY))
                    .setMinute(start.get(Calendar.MINUTE))
                    .setTitleText(title)
                    .build();
            tp.addOnPositiveButtonClickListener(v -> {
                Calendar local = Calendar.getInstance();
                local.clear();
                local.set(picked.get(Calendar.YEAR), picked.get(Calendar.MONTH), picked.get(Calendar.DAY_OF_MONTH), tp.getHour(), tp.getMinute(), 0);
                cb.onPicked(local.getTime());
            });
            tp.show(fm, "time");
        });
        dp.show(fm, "date");
    }
}
