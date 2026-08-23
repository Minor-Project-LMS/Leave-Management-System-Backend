package com.lms.Leave_Management_System_Backend.config;

import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Arrays;
import java.util.UUID;

@Aspect
@Component
public class LoggingAspect {

    private static final Logger log = LoggerFactory.getLogger(LoggingAspect.class);

    @Around("within(@org.springframework.web.bind.annotation.RestController *)")
    public Object logAround(ProceedingJoinPoint joinPoint) throws Throwable {
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
        
        // Generate unique request ID for tracing
        String requestId = UUID.randomUUID().toString();
        MDC.put("requestId", requestId);
        MDC.put("method", request.getMethod());
        MDC.put("uri", request.getRequestURI());
        MDC.put("clientIp", getClientIp(request));

        long startTime = System.currentTimeMillis();
        
        try {
            log.info("Request started: {} {} - Arguments: {}", 
                    request.getMethod(), 
                    request.getRequestURI(),
                    Arrays.toString(joinPoint.getArgs()));

            Object result = joinPoint.proceed();

            long duration = System.currentTimeMillis() - startTime;
            log.info("Request completed: {} {} - Status: SUCCESS - Duration: {}ms", 
                    request.getMethod(), 
                    request.getRequestURI(),
                    duration);

            return result;
        } catch (Exception ex) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("Request failed: {} {} - Duration: {}ms - Error: {}", 
                    request.getMethod(), 
                    request.getRequestURI(),
                    duration,
                    ex.getMessage(), ex);
            throw ex;
        } finally {
            MDC.clear();
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0];
    }
}