package com.phenom.selfservicebooking.auth.controller;

import com.phenom.selfservicebooking.auth.interceptor.AuthInterceptor;
import com.phenom.selfservicebooking.common.constants.APIConstants;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Test endpoint for checking access tokens. A common API: any valid token, either role. */
@RestController
@RequestMapping(APIConstants.TEST_BASE)
public class TokenTestController {

    /** Returns the userId taken from the caller's access token. */
    @GetMapping(APIConstants.TEST_ME)
    public Map<String, Long> me(@RequestAttribute(AuthInterceptor.USER_ID) Long userId) {
        return Map.of("userId", userId);
    }
}
