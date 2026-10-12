package com.lms.Leave_Management_System_Backend.security;

import com.lms.Leave_Management_System_Backend.service.JwtUtil;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class JwtAuthenticationFilterTest {

    private JwtUtil jwtUtil;
    private JwtAuthenticationFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockFilterChain chain;

    @BeforeEach
    void setUp() {
        jwtUtil = mock(JwtUtil.class);
        filter = new JwtAuthenticationFilter(jwtUtil);
        request = new MockHttpServletRequest("GET", "/api/v1/users/me");
        response = new MockHttpServletResponse();
        chain = new MockFilterChain();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("passes through untouched when there is no Authorization header")
    void noHeader() throws Exception {
        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(jwtUtil);
    }

    @Test
    @DisplayName("passes through untouched for a non-Bearer header")
    void nonBearerHeader() throws Exception {
        request.addHeader("Authorization", "Basic abc");

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        verifyNoInteractions(jwtUtil);
    }

    @Test
    @DisplayName("returns 401 and stops the chain for an invalid token")
    void invalidToken() throws Exception {
        request.addHeader("Authorization", "Bearer bad-token");
        when(jwtUtil.validateToken("bad-token")).thenReturn(false);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("Invalid or expired token");
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    @DisplayName("populates the SecurityContext with email and ROLE_ authority for a valid token")
    void validToken() throws Exception {
        request.addHeader("Authorization", "Bearer good-token");
        Claims claims = mock(Claims.class);
        when(jwtUtil.validateToken("good-token")).thenReturn(true);
        when(jwtUtil.getEmailFromToken("good-token")).thenReturn("emp@lms.com");
        when(jwtUtil.getClaims("good-token")).thenReturn(claims);
        when(claims.get("role", String.class)).thenReturn("EMPLOYEE");

        filter.doFilter(request, response, chain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getName()).isEqualTo("emp@lms.com");
        assertThat(auth.getAuthorities()).extracting("authority").containsExactly("ROLE_EMPLOYEE");
        assertThat(chain.getRequest()).isNotNull();
    }
}
