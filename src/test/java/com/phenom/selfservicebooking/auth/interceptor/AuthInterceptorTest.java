package com.phenom.selfservicebooking.auth.interceptor;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.phenom.selfservicebooking.auth.entity.User;
import com.phenom.selfservicebooking.auth.entity.UserRole;
import com.phenom.selfservicebooking.auth.service.JwtService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(AuthInterceptorTest.ProtectedController.class)
@Import({JwtService.class, AuthInterceptorTest.ProtectedController.class})
class AuthInterceptorTest {

    /** Stand-in for any real, protected API. */
    @RestController
    static class ProtectedController {
        @GetMapping("/api/protected")
        String whoAmI(@RequestAttribute(AuthInterceptor.USER_ID) Long userId,
                      @RequestAttribute(AuthInterceptor.USER_ROLE) UserRole role) {
            return userId + ":" + role;
        }

        @GetMapping("/api/host/ping")
        String hostOnly() {
            return "host-ok";
        }

        @GetMapping("/api/candidate/ping")
        String candidateOnly() {
            return "candidate-ok";
        }

        /** A common API whose path happens to contain "host" and "candidate". */
        @GetMapping("/api/test/host/candidate/ping")
        String commonApi() {
            return "common-ok";
        }

        @GetMapping("/api/hosting/ping")
        String notAHostPath() {
            return "hosting-ok";
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Value("${security.jwt.secret}")
    private String secret;

    @Test
    void validTokenReachesApiWithUserIdAndRole() throws Exception {
        mockMvc.perform(get("/api/protected").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(7L)))
                .andExpect(status().isOk())
                .andExpect(content().string("7:HOST"));
    }

    @Test
    void missingTokenIsRejected() throws Exception {
        mockMvc.perform(get("/api/protected"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.detail").value("Missing access token. Send 'Authorization: Bearer <token>'"));
    }

    @Test
    void nonBearerHeaderIsRejected() throws Exception {
        mockMvc.perform(get("/api/protected").header(HttpHeaders.AUTHORIZATION, "Basic abc"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void garbageTokenIsRejected() throws Exception {
        mockMvc.perform(get("/api/protected").header(HttpHeaders.AUTHORIZATION, "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid access token"));
    }

    @Test
    void tamperedTokenIsRejected() throws Exception {
        String token = tokenFor(7L);
        String tampered = token.substring(0, token.length() - 4) + "AAAA";
        mockMvc.perform(get("/api/protected").header(HttpHeaders.AUTHORIZATION, "Bearer " + tampered))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid access token"));
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() throws Exception {
        String foreign = Jwts.builder().subject("7").claim("role", "HOST")
                .signWith(Keys.hmacShaKeyFor(new byte[32])).compact();
        mockMvc.perform(get("/api/protected").header(HttpHeaders.AUTHORIZATION, "Bearer " + foreign))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        Instant past = Instant.now().minus(Duration.ofHours(2));
        String expired = Jwts.builder().subject("7").claim("role", "HOST")
                .issuedAt(Date.from(past)).expiration(Date.from(past.plus(Duration.ofHours(1))))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret))).compact();
        mockMvc.perform(get("/api/protected").header(HttpHeaders.AUTHORIZATION, "Bearer " + expired))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Access token has expired"));
    }

    @Test
    void hostTokenCanCallHostApi() throws Exception {
        mockMvc.perform(get("/api/host/ping").header(HttpHeaders.AUTHORIZATION, bearer(UserRole.HOST)))
                .andExpect(status().isOk())
                .andExpect(content().string("host-ok"));
    }

    @Test
    void candidateTokenCannotCallHostApi() throws Exception {
        mockMvc.perform(get("/api/host/ping").header(HttpHeaders.AUTHORIZATION, bearer(UserRole.CANDIDATE)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("This API is only for HOST users"));
    }

    @Test
    void candidateTokenCanCallCandidateApi() throws Exception {
        mockMvc.perform(get("/api/candidate/ping").header(HttpHeaders.AUTHORIZATION, bearer(UserRole.CANDIDATE)))
                .andExpect(status().isOk())
                .andExpect(content().string("candidate-ok"));
    }

    @Test
    void hostTokenCannotCallCandidateApi() throws Exception {
        mockMvc.perform(get("/api/candidate/ping").header(HttpHeaders.AUTHORIZATION, bearer(UserRole.HOST)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("This API is only for CANDIDATE users"));
    }

    @Test
    void roleCheckCannotBeBypassedWithPathTricks() throws Exception {
        // Both of these are routed to /api/host/ping by Spring, so they must be blocked for candidates too
        mockMvc.perform(get("/api/host;x=1/ping").header(HttpHeaders.AUTHORIZATION, bearer(UserRole.CANDIDATE)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(URI.create("/api/%68ost/ping")).header(HttpHeaders.AUTHORIZATION, bearer(UserRole.CANDIDATE)))
                .andExpect(status().isForbidden());
    }

    @Test
    void commonApiSkipsRoleCheckForBothRoles() throws Exception {
        for (UserRole role : UserRole.values()) {
            mockMvc.perform(get("/api/test/host/candidate/ping").header(HttpHeaders.AUTHORIZATION, bearer(role)))
                    .andExpect(status().isOk())
                    .andExpect(content().string("common-ok"));
        }
    }

    @Test
    void commonApiStillRequiresValidToken() throws Exception {
        mockMvc.perform(get("/api/test/host/candidate/ping"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/test/host/candidate/ping").header(HttpHeaders.AUTHORIZATION, "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void onlyWholePathSegmentsCount() throws Exception {
        // "hosting" is not "host", so any logged-in user may call it
        mockMvc.perform(get("/api/hosting/ping").header(HttpHeaders.AUTHORIZATION, bearer(UserRole.CANDIDATE)))
                .andExpect(status().isOk());
    }

    @Test
    void corsPreflightIsNotBlocked() throws Exception {
        mockMvc.perform(options("/api/protected"))
                .andExpect(status().isOk());
    }

    private String tokenFor(Long userId) {
        return tokenFor(userId, UserRole.HOST);
    }

    private String tokenFor(Long userId, UserRole role) {
        User user = new User();
        ReflectionTestUtils.setField(user, "userId", userId);
        user.setUserRole(role);
        return jwtService.generateToken(user);
    }

    private String bearer(UserRole role) {
        return "Bearer " + tokenFor(7L, role);
    }
}
