package com.finsentinel.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finsentinel.common.api.ApiErrorResponse;
import com.finsentinel.common.api.RequestIdFilter;
import com.finsentinel.common.audit.AuditService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final ConcurrentHashMap<String, Deque<Long>> buckets = new ConcurrentHashMap<>();
    private final AtomicLong rateLimitedEvents = new AtomicLong(0);
    private final ObjectMapper objectMapper;
    private final AuditService auditService;

    public RateLimitFilter(ObjectMapper objectMapper, AuditService auditService) {
        this.objectMapper = objectMapper;
        this.auditService = auditService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        int limit = resolveLimit(path);
        if (limit > 0) {
            String key = resolveKey(request, path);
            if (!allowRequest(key, limit)) {
                rateLimitedEvents.incrementAndGet();
                auditService.record(null, "ANONYMOUS", "RATE_LIMIT_EXCEEDED", "HTTP_ENDPOINT", path, "BLOCKED", Map.of("keyPrefix", key.split(":")[0]));
                response.setStatus(429);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                ApiErrorResponse err = ApiErrorResponse.of("RATE_LIMITED", "Too many requests; please wait a moment and retry.", MDC.get(RequestIdFilter.MDC_KEY));
                response.getWriter().write(objectMapper.writeValueAsString(err));
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private int resolveLimit(String path) {
        if (path.startsWith("/api/v1/auth/login") || path.startsWith("/api/v1/auth/register") || path.startsWith("/api/v1/auth/forgot-password")) {
            return 30;
        }
        if (path.startsWith("/api/v1/ai/")) {
            return 25;
        }
        return 0;
    }

    private String resolveKey(HttpServletRequest request, String path) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserSessionPrincipal principal) {
            return "user:" + principal.userId() + ":" + (path.startsWith("/api/v1/ai/") ? "ai" : "auth");
        }
        String ip = request.getRemoteAddr();
        return "ip:" + (ip != null ? ip : "unknown") + ":auth";
    }

    private boolean allowRequest(String key, int maxPerMinute) {
        long now = Instant.now().toEpochMilli();
        long cutoff = now - 60_000L;
        Deque<Long> window = buckets.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (window) {
            while (!window.isEmpty() && window.peekFirst() < cutoff) {
                window.pollFirst();
            }
            if (window.size() >= maxPerMinute) {
                return false;
            }
            window.addLast(now);
            return true;
        }
    }

    public long getRateLimitedEvents() {
        return rateLimitedEvents.get();
    }
}
