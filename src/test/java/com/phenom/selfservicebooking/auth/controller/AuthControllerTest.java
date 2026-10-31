package com.phenom.selfservicebooking.auth.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.phenom.selfservicebooking.auth.dto.UsernameAvailabilityResponse;
import com.phenom.selfservicebooking.auth.service.AuthService;
import com.phenom.selfservicebooking.auth.service.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
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
}
