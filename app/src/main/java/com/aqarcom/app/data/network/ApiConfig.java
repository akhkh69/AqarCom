package com.aqarcom.app.data.network;

/**
 * Server address.
 *  - Android emulator → the host PC is 10.0.2.2
 *  - Real phone on the same Wi-Fi → use your PC's LAN IP (e.g. http://192.168.1.10:3000/api/v1/)
 *  - Production → https://api.your-domain/api/v1/
 * Must end with "/".
 */
public final class ApiConfig {
    /** Set API_BASE_URL in local.properties to override (e.g. your PC's LAN IP for a real phone). */
    public static final String BASE_URL = com.aqarcom.app.BuildConfig.API_BASE_URL;
    public static final int CONNECT_TIMEOUT_SEC = 20;
    public static final int READ_TIMEOUT_SEC = 60;
    public static final int WRITE_TIMEOUT_SEC = 120; // large spec-book uploads (NFR-01)

    private ApiConfig() {}
}
