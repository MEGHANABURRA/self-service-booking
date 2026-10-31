package com.phenom.selfservicebooking.common.constants;

/**
 * All API paths, plus the access level of each group:
 * <ul>
 *   <li>{@link #PUBLIC_PATHS}: no access token needed.</li>
 *   <li>{@link #COMMON_PATHS}: access token required, any role (no HOST/CANDIDATE check).</li>
 *   <li>{@link #HOST_BASE} / {@link #CANDIDATE_BASE}: access token of that role required.</li>
 *   <li>Anything else: access token required, plus the HOST/CANDIDATE check on its path.</li>
 * </ul>
 */
public final class APIConstants {

    public static final String API_BASE = "/api";

    // ---- Auth ----
    public static final String AUTH_BASE = API_BASE + "/auth";
    public static final String REGISTER = "/register";
    public static final String LOGIN = "/login";
    public static final String USERNAME_AVAILABILITY = "/username-availability";

    // ---- Test ----
    public static final String TEST_BASE = API_BASE + "/test";
    public static final String TEST_ME = "/me";

    // ---- Role-specific APIs ----
    public static final String HOST_BASE = API_BASE + "/host";
    public static final String CANDIDATE_BASE = API_BASE + "/candidate";

    /** Spring's error page; must stay public so error responses aren't turned into 401s. */
    public static final String ERROR = "/error";

    /** No access token needed. */
    public static final String[] PUBLIC_PATHS = {
            AUTH_BASE + REGISTER,
            AUTH_BASE + LOGIN,
            AUTH_BASE + USERNAME_AVAILABILITY,
            ERROR
    };

    /** Common to every user: access token is validated, but HOST/CANDIDATE authorization is skipped. */
    public static final String[] COMMON_PATHS = {
            AUTH_BASE + "/**",
            TEST_BASE + "/**"
    };

    private APIConstants() {
    }
}
