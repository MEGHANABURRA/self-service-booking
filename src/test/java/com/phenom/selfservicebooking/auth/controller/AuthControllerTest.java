package com.phenom.selfservicebooking.auth.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.phenom.selfservicebooking.auth.dto.LoginResponse;
import com.phenom.selfservicebooking.auth.dto.RefreshTokenRequest;
import com.phenom.selfservicebooking.auth.dto.SendOtpRequest;
import com.phenom.selfservicebooking.auth.dto.SendOtpResponse;
import com.phenom.selfservicebooking.auth.dto.UserResponse;
import com.phenom.selfservicebooking.auth.dto.UsernameAvailabilityResponse;
import com.phenom.selfservicebooking.auth.entity.UserRole;
import com.phenom.selfservicebooking.auth.exception.InvalidOtpException;
import com.phenom.selfservicebooking.auth.exception.OtpRateLimitException;
import com.phenom.selfservicebooking.auth.service.AuthService;
import com.phenom.selfservicebooking.auth.service.JwtService;
import com.phenom.selfservicebooking.common.email.EmailDeliveryException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import java.util.Locale;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
@Import(JwtService.class) // needed by AuthInterceptor; verifies these endpoints stay public
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthService authService;

    @Test
    void returnsAvailability() throws Exception {
        when(authService.checkUsernameAvailability("john.doe@example.com"))
                .thenReturn(new UsernameAvailabilityResponse("john.doe@example.com", false));

        mockMvc.perform(get("/api/auth/username-availability").param("userName", "john.doe@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userName").value("john.doe@example.com"))
                .andExpect(jsonPath("$.available").value(false));
    }

    @Test
    void rejectsBlankUserName() throws Exception {
        mockMvc.perform(get("/api/auth/username-availability").param("userName", "  "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail")
                        .value("userName: must be a valid email address; userName: must not be blank"));
    }

    @Test
    void rejectsNonEmailUserName() throws Exception {
        mockMvc.perform(get("/api/auth/username-availability").param("userName", "jdoe"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("userName: must be a valid email address"));
    }

    @Test
    void rejectsMissingUserName() throws Exception {
        mockMvc.perform(get("/api/auth/username-availability"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void refreshTokenEndpointIsPublic() throws Exception {
        UserResponse user = new UserResponse(7L, null, "John", "john.doe@example.com", "UTC", UserRole.HOST, null);
        when(authService.refresh(new RefreshTokenRequest("refresh-1")))
                .thenReturn(new LoginResponse("access-2", "Bearer", 3600, "refresh-2", 604800, user));

        // No Authorization header: the access token may already have expired
        mockMvc.perform(post("/api/auth/refresh-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"refresh-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-2"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-2"));
    }

    @Test
    void refreshTokenIsRequired() throws Exception {
        mockMvc.perform(post("/api/auth/refresh-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sendEmailOtpIsPublicAndUsesAcceptLanguage() throws Exception {
        when(authService.sendRegistrationOtp(new SendOtpRequest("john.doe@example.com"), Locale.forLanguageTag("hi")))
                .thenReturn(new SendOtpResponse("john.doe@example.com", 600, 60));

        mockMvc.perform(post("/api/auth/email-otp")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "hi")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"john.doe@example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresInSeconds").value(600))
                .andExpect(jsonPath("$.resendAfterSeconds").value(60));
    }

    @Test
    void sendEmailOtpReturns429WithRetryAfterDuringCooldown() throws Exception {
        when(authService.sendRegistrationOtp(any(), any())).thenThrow(new OtpRateLimitException(42));

        mockMvc.perform(post("/api/auth/email-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"john.doe@example.com\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "42"))
                .andExpect(jsonPath("$.detail").value("Please wait 42 seconds before requesting another OTP"));
    }

    @Test
    void sendEmailOtpRejectsInvalidEmail() throws Exception {
        mockMvc.perform(post("/api/auth/email-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("email: must be a valid email address"));
    }

    @Test
    void registerRequiresOtp() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"John","userName":"john.doe@example.com","password":"S3cret!Pass",
                                 "timeZone":"UTC","userRole":"HOST"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("otp: must not be blank"));
    }

    @Test
    void registerReturns400ForWrongOtp() throws Exception {
        when(authService.register(any())).thenThrow(new InvalidOtpException("Invalid OTP. 4 attempt(s) left"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"John","userName":"john.doe@example.com","password":"S3cret!Pass",
                                 "timeZone":"UTC","userRole":"HOST","otp":"999999"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Invalid OTP. 4 attempt(s) left"));
    }

    @Test
    void emailFailureReturns503WithoutProviderDetails() throws Exception {
        when(authService.sendRegistrationOtp(any(), any())).thenThrow(new EmailDeliveryException(
                "Could not send email to john.doe@example.com: 535 Authentication failed", null));

        mockMvc.perform(post("/api/auth/email-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"john.doe@example.com\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value("Could not send the email. Please try again later"));
    }
}
