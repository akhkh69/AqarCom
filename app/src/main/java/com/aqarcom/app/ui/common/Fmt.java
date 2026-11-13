package com.aqarcom.app.ui.common;

import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Formatting helpers. Numbers use Western digits for prices; dates are shown in local time. */
public final class Fmt {
    private Fmt() {}

    private static final String[] ISO = {"yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", "yyyy-MM-dd'T'HH:mm:ss'Z'"};

    public static String number(double v) {
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.US);
        nf.setMaximumFractionDigits(2);
        return nf.format(v);
    }

    public static String money(double v, String currency) {
        String c = "USD".equals(currency) ? "$" : "SYP".equals(currency) ? "ل.س" : (currency == null ? "" : currency);
        return "USD".equals(currency) ? c + number(v) : number(v) + " " + c;
    }

    public static String area(double v) { return number(v) + " م²"; }

    public static Date parse(String iso) {
        if (iso == null) return null;
        for (String p : ISO) {
            try {
                SimpleDateFormat f = new SimpleDateFormat(p, Locale.US);
                f.setTimeZone(TimeZone.getTimeZone("UTC"));
                return f.parse(iso);
            } catch (ParseException ignored) { }
        }
        return null;
    }

    /** UTC ISO-8601 string for the API. */
    public static String iso(Date d) {
        SimpleDateFormat f = new SimpleDateFormat(ISO[0], Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(d);
    }

    public static String date(String iso) {
        Date d = parse(iso);
        return d == null ? "—" : new SimpleDateFormat("d MMM yyyy", new Locale("ar")).format(d);
    }

    public static String dateTime(String iso) {
        Date d = parse(iso);
        return d == null ? "—" : new SimpleDateFormat("d MMM yyyy، hh:mm a", new Locale("ar")).format(d);
    }

    public static String dateTime(Date d) {
        return d == null ? "—" : new SimpleDateFormat("d MMM yyyy، hh:mm a", new Locale("ar")).format(d);
    }

    /** "منذ 5 دقائق" style relative time. */
    public static String ago(String iso) {
        Date d = parse(iso);
        if (d == null) return "";
        long s = (System.currentTimeMillis() - d.getTime()) / 1000;
        if (s < 60) return "الآن";
        if (s < 3600) return "منذ " + (s / 60) + " د";
        if (s < 86400) return "منذ " + (s / 3600) + " س";
        if (s < 86400 * 7) return "منذ " + (s / 86400) + " يوم";
        return date(iso);
    }

    /** Countdown text from seconds (computed by the server — BR-11). */
    public static String countdown(long seconds) {
        if (seconds <= 0) return "انتهى موعد الإغلاق";
        long d = seconds / 86400, h = (seconds % 86400) / 3600, m = (seconds % 3600) / 60, s = seconds % 60;
        if (d > 0) return String.format(Locale.US, "%d يوم  %02d:%02d:%02d", d, h, m, s);
        return String.format(Locale.US, "%02d:%02d:%02d", h, m, s);
    }

    public static String percent(double v) { return number(v) + "%"; }
}
