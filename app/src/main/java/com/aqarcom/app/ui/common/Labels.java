package com.aqarcom.app.ui.common;

import java.util.LinkedHashMap;
import java.util.Map;

/** Arabic labels for every enum value the API returns. Insertion order = display order in pickers. */
public final class Labels {
    private Labels() {}

    public static final Map<String, String> ROLES = map(
            "user", "مستخدم فردي (باحث عن عقار)",
            "property_owner", "مالك عقار",
            "project_owner", "صاحب مشروع",
            "contractor", "متعهد / شركة هندسية",
            "admin", "مدير النظام");

    public static final Map<String, String> ACCOUNT = map(
            "pending", "بانتظار الموافقة", "active", "مفعّل", "rejected", "مرفوض", "suspended", "موقوف");

    public static final Map<String, String> VERIFICATION = map(
            "none", "غير موثّق", "pending", "قيد المراجعة", "verified", "موثّق", "rejected", "مرفوض",
            "approved", "معتمد");

    public static final Map<String, String> PROPERTY_TYPES = map(
            "apartment", "شقة", "house", "بيت", "villa", "فيلا", "land", "أرض", "shop", "محل",
            "office", "مكتب", "building", "بناء", "farm", "مزرعة", "warehouse", "مستودع");

    public static final Map<String, String> PURPOSES = map("sale", "بيع", "rent", "إيجار", "investment", "استثمار");

    public static final Map<String, String> CURRENCIES = map("USD", "دولار أمريكي", "SYP", "ليرة سورية");

    public static final Map<String, String> TENDER_TYPES = map("construction", "عمار", "finishing", "إكساء", "partnership", "مشاركة");

    public static final Map<String, String> TENDER_STATUS = map(
            "draft", "مسودة", "open", "مفتوحة", "evaluating", "قيد الفرز", "shortlist_ready", "قائمة الترشيح جاهزة",
            "awarded", "مُرسّاة", "closed_no_bids", "منتهية دون عروض", "cancelled", "ملغاة");

    public static final Map<String, String> CONTENT = map("active", "ظاهر", "hidden", "مخفي", "deleted", "محذوف");

    public static final Map<String, String> OUTCOME = map("pending", "قيد الانتظار", "won", "فائز", "lost", "لم يُختر");

    public static final Map<String, String> DOC_TYPES = map(
            "engineers_syndicate", "شهادة نقابة المهندسين", "commercial_register", "السجل التجاري", "other", "وثيقة أخرى");

    public static final Map<String, String> CITIES = map(
            "دمشق", "دمشق", "ريف دمشق", "ريف دمشق", "حلب", "حلب", "حمص", "حمص", "حماة", "حماة", "اللاذقية", "اللاذقية",
            "طرطوس", "طرطوس", "إدلب", "إدلب", "درعا", "درعا", "السويداء", "السويداء", "القنيطرة", "القنيطرة",
            "دير الزور", "دير الزور", "الحسكة", "الحسكة", "الرقة", "الرقة");

    public static String of(Map<String, String> m, String key) {
        if (key == null) return "—";
        String v = m.get(key);
        return v == null ? key : v;
    }

    /** Reverse lookup: Arabic label → API key (for dropdowns). */
    public static String keyOf(Map<String, String> m, String label) {
        for (Map.Entry<String, String> e : m.entrySet()) if (e.getValue().equals(label)) return e.getKey();
        return null;
    }

    private static Map<String, String> map(String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
        return m;
    }
}
