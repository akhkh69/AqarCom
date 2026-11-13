package com.aqarcom.app.ui.property;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/** Search filters for GET /properties (FR-08). */
public class PropertyFilters implements Serializable {
    public String q;
    public String city;
    public String propertyType;
    public String purpose;
    public Double minPrice, maxPrice, minArea, maxArea;
    public Integer minRooms;
    /** newest | price_asc | price_desc | nearest */
    public String sort = "newest";
    public Double latitude, longitude;

    public int activeCount() {
        int n = 0;
        if (city != null) n++;
        if (propertyType != null) n++;
        if (minPrice != null || maxPrice != null) n++;
        if (minArea != null || maxArea != null) n++;
        if (minRooms != null) n++;
        return n;
    }

    public void clearAdvanced() {
        city = null; propertyType = null; minPrice = null; maxPrice = null; minArea = null; maxArea = null; minRooms = null;
    }

    /** Copy the bottom-sheet fields (the sheet may hand back a deserialized copy). */
    public void copyAdvancedFrom(PropertyFilters o) {
        city = o.city; propertyType = o.propertyType; minPrice = o.minPrice; maxPrice = o.maxPrice;
        minArea = o.minArea; maxArea = o.maxArea; minRooms = o.minRooms;
    }

    public Map<String, String> toQuery(int page) {
        Map<String, String> m = new HashMap<>();
        put(m, "q", q);
        put(m, "city", city);
        put(m, "propertyType", propertyType);
        put(m, "purpose", purpose);
        put(m, "minPrice", minPrice);
        put(m, "maxPrice", maxPrice);
        put(m, "minArea", minArea);
        put(m, "maxArea", maxArea);
        put(m, "minRooms", minRooms);
        put(m, "sort", sort);
        if ("nearest".equals(sort)) {
            put(m, "latitude", latitude);
            put(m, "longitude", longitude);
        }
        m.put("page", String.valueOf(page));
        m.put("limit", "20");
        return m;
    }

    private static void put(Map<String, String> m, String k, Object v) {
        if (v == null) return;
        String s = v instanceof Double && ((Double) v) % 1 == 0 ? String.valueOf(((Double) v).longValue()) : String.valueOf(v);
        if (!s.trim().isEmpty()) m.put(k, s.trim());
    }
}
