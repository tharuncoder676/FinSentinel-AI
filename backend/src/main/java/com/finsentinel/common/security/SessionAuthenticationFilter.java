package com.finsentinel.common.security;

import com.finsentinel.auth.AuthSessionEntity;
import com.finsentinel.auth.AuthSessionRepository;
import com.finsentinel.auth.UserEntity;
import com.finsentinel.auth.UserRepository;
import com.finsentinel.common.config.AppProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Optional;

@Component
public class SessionAuthenticationFilter extends OncePerRequestFilter {

    private final AuthSessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final AppProperties appProperties;

    public SessionAuthenticationFilter(AuthSessionRepository sessionRepository,
                                       UserRepository userRepository,
                                       AppProperties appProperties) {
        this.sessionRepository = sessionRepository;
        this.userRepository = userRepository;
        this.appProperties = appProperties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String rawToken = extractCookie(request, appProperties.security().sessionCookieName());
        if (rawToken != null && !rawToken.isBlank()) {
            String tokenHash = TokenUtil.sha256Hex(rawToken);
            Optional<AuthSessionEntity> sessionOpt = sessionRepository.findByTokenHashAndRevokedFalse(tokenHash);
            if (sessionOpt.isPresent()) {
                AuthSessionEntity session = sessionOpt.get();
                if (session.getExpiresAt().isAfter(Instant.now())) {
                    Optional<UserEntity> userOpt = userRepository.findById(session.getUserId());
                    if (userOpt.isPresent() && "ACTIVE".equals(userOpt.get().getStatus())) {
                        UserEntity user = userOpt.get();
                        UserSessionPrincipal principal = new UserSessionPrincipal(
                                user.getId(),
                                user.getEmail(),
                                user.getFullName(),
                                user.getRole(),
                                user.isDemoUser(),
                                user.isEmailVerified(),
                                session.getId()
                        );
                        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                                principal, null, principal.getAuthorities());
                        SecurityContextHolder.getContext().setAuthentication(auth);
                    }
                }
            }
        }
        filterChain.doFilter(request, response);
    }

    private String extractCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
