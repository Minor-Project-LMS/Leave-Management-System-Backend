package com.lms.Leave_Management_System_Backend.config;

import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.boot.actuate.info.Info;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ConfigTest {

    @Nested
    class Cors {
        @Test
        @DisplayName("allows the configured origins with credentials and the expected methods")
        void corsConfiguration() {
            CorsConfig config = new CorsConfig();
            ReflectionTestUtils.setField(config, "allowedOrigins", "http://localhost:5173,https://lms.example.com");

            CorsConfigurationSource source = config.corsConfigurationSource();
            CorsConfiguration cors = source.getCorsConfiguration(new MockHttpServletRequest("GET", "/api/v1/users/me"));

            assertThat(cors).isNotNull();
            assertThat(cors.getAllowedOrigins()).containsExactly("http://localhost:5173", "https://lms.example.com");
            assertThat(cors.getAllowCredentials()).isTrue();
            assertThat(cors.getAllowedMethods()).contains("GET", "POST", "PATCH", "DELETE", "OPTIONS");
            assertThat(cors.getAllowedHeaders()).contains("Authorization", "Content-Type");
            assertThat(cors.getMaxAge()).isEqualTo(3600L);
        }
    }

    @Nested
    class InfoContributor {
        @Test
        @SuppressWarnings("unchecked")
        void contributesApplicationDetails() {
            Info.Builder builder = new Info.Builder();
            new ApplicationInfoContributor().contribute(builder);

            Map<String, Object> details = (Map<String, Object>) builder.build().getDetails().get("application");
            assertThat(details).containsEntry("version", "1.0.0").containsKey("startupTime");
        }
    }

    @Nested
    class Logging {

        @AfterEach
        void clear() {
            RequestContextHolder.resetRequestAttributes();
        }

        private void bindRequest(String uri) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
            request.addHeader("X-Forwarded-For", "10.0.0.1, 10.0.0.2");
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        }

        @Test
        void proceedsAndClearsMdc() throws Throwable {
            bindRequest("/api/v1/leave-requests");
            ProceedingJoinPoint jp = mock(ProceedingJoinPoint.class);
            when(jp.getArgs()).thenReturn(new Object[]{"arg"});
            when(jp.proceed()).thenReturn("ok");

            assertThat(new LoggingAspect().logAround(jp)).isEqualTo("ok");
            assertThat(MDC.get("requestId")).isNull();
        }

        @Test
        @DisplayName("arguments of sensitive endpoints (login/password) are never read for logging")
        void sensitiveArgumentsAreRedacted() throws Throwable {
            bindRequest("/api/v1/auth/login");
            ProceedingJoinPoint jp = mock(ProceedingJoinPoint.class);

            new LoggingAspect().logAround(jp);

            verify(jp, never()).getArgs();
        }

        @Test
        void exceptionsAreRethrownAndMdcCleared() throws Throwable {
            bindRequest("/api/v1/leave-requests");
            ProceedingJoinPoint jp = mock(ProceedingJoinPoint.class);
            when(jp.proceed()).thenThrow(new IllegalStateException("boom"));

            assertThatThrownBy(() -> new LoggingAspect().logAround(jp)).isInstanceOf(IllegalStateException.class);
            assertThat(MDC.get("requestId")).isNull();
        }
    }
}
