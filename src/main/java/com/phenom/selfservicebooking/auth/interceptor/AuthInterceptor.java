package com.phenom.selfservicebooking.auth.interceptor;

import com.phenom.selfservicebooking.auth.entity.UserRole;
import com.phenom.selfservicebooking.auth.exception.ForbiddenException;
import com.phenom.selfservicebooking.auth.exception.UnauthorizedException;
import com.phenom.selfservicebooking.auth.service.JwtService;
import com.phenom.selfservicebooking.common.constants.APIConstants;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.PathContainer;
import org.springframework.http.server.RequestPath;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.ServletRequestPathUtils;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

/**
 * Runs before every API (except the public paths in {@code WebMvcConfig}) and rejects the request
 * with 401 unless it carries a valid {@code Authorization: Bearer <token>} header. APIs under a
 * {@code /host/} or {@code /candidate/} path also require the matching role, otherwise 403. Common APIs
 * ({@link APIConstants#COMMON_PATHS}) only need a valid token; the role check is skipped for them.
 *
 * <p>On success the caller's identity is stored as request attributes, so controllers can read it with
 * {@code @RequestAttribute(AuthInterceptor.USER_ID) Long userId}.
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    public static final String USER_ID = "authUserId";
    public static final String USER_ROLE = "authUserRole";

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String HOST_SEGMENT = "host";
    private static final String CANDIDATE_SEGMENT = "candidate";

    private static final List<PathPattern> COMMON_PATTERNS = Arrays.stream(APIConstants.COMMON_PATHS)
            .map(PathPatternParser.defaultInstance::parse)
            .toList();

    private final JwtService jwtService;

    public AuthInterceptor(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // CORS preflight requests never carry the Authorization header
        if (HttpMethod.OPTIONS.name().equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            throw new UnauthorizedException("Missing access token. Send 'Authorization: Bearer <token>'");
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty()) {
            throw new UnauthorizedException("Missing access token. Send 'Authorization: Bearer <token>'");
        }

        Claims claims;
        try {
            claims = jwtService.parseToken(token);
        } catch (ExpiredJwtException e) {
            throw new UnauthorizedException("Access token has expired");
        } catch (JwtException | IllegalArgumentException e) {
            throw new UnauthorizedException("Invalid access token");
        }

        Long userId = parseUserId(claims.getSubject());
        UserRole role = parseRole(claims.get("role", String.class));

        RequestPath path = requestPath(request);
        if (!isCommonApi(path)) {
            checkRoleAllowedForPath(path, role);
        }

        request.setAttribute(USER_ID, userId);
        request.setAttribute(USER_ROLE, role);
        return true;
    }

    /**
     * APIs with a {@code host} path segment (e.g. /api/host/...) are for HOST tokens only, and APIs with a
     * {@code candidate} segment are for CANDIDATE tokens only. Other APIs accept any logged-in user.
     *
     * <p>Uses the decoded path Spring matches controllers against (no ";params", no %-escapes), so
     * variants like /api/host;x/... or /api/%68ost/... can't slip past the check.
     */
    private static void checkRoleAllowedForPath(RequestPath path, UserRole role) {
        for (PathContainer.Element element : path.pathWithinApplication().elements()) {
            if (element instanceof PathContainer.PathSegment segment) {
                String value = segment.valueToMatch();
                if (HOST_SEGMENT.equalsIgnoreCase(value) && role != UserRole.HOST) {
                    throw new ForbiddenException("This API is only for HOST users");
                }
                if (CANDIDATE_SEGMENT.equalsIgnoreCase(value) && role != UserRole.CANDIDATE) {
                    throw new ForbiddenException("This API is only for CANDIDATE users");
                }
            }
        }
    }

    /** Common APIs are open to every logged-in user, whatever their role. */
    private static boolean isCommonApi(RequestPath path) {
        PathContainer pathWithinApp = path.pathWithinApplication();
        return COMMON_PATTERNS.stream().anyMatch(pattern -> pattern.matches(pathWithinApp));
    }

    /** The path exactly as Spring matches it against controllers (decoded, without ";params"). */
    private static RequestPath requestPath(HttpServletRequest request) {
        return ServletRequestPathUtils.hasParsedRequestPath(request)
                ? ServletRequestPathUtils.getParsedRequestPath(request)
                : ServletRequestPathUtils.parseAndCache(request);
    }

    private static Long parseUserId(String subject) {
        try {
            return Long.valueOf(subject);
        } catch (NumberFormatException e) {
            throw new UnauthorizedException("Invalid access token");
        }
    }

    private static UserRole parseRole(String role) {
        try {
            return UserRole.valueOf(role);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new UnauthorizedException("Invalid access token");
        }
    }
}
