package com.razvan.digital_wallet_api.security;

import com.razvan.digital_wallet_api.entity.User;
import com.razvan.digital_wallet_api.repository.UserRepository;
import com.razvan.digital_wallet_api.service.JwtService;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log =
            LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(
            JwtService jwtService,
            UserRepository userRepository
    ) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null
                || !authHeader.startsWith("Bearer ")
                || SecurityContextHolder.getContext()
                .getAuthentication() != null) {

            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);
        String email;
        boolean validAccessToken;

        // Only token parsing and validation belong in this try block.
        try {
            email = jwtService.extractEmail(token);

            validAccessToken =
                    email != null
                            && !email.isBlank()
                            && jwtService.isAccessToken(token)
                            && jwtService.isTokenValid(token, email);

        } catch (JwtException | IllegalArgumentException ex) {
            // Invalid or expired tokens continue without authentication.
            // Protected endpoints will return 401.
            filterChain.doFilter(request, response);
            return;
        }

        if (!validAccessToken) {
            filterChain.doFilter(request, response);
            return;
        }

        User user;

        try {
            user = userRepository.findByEmail(email).orElse(null);

        } catch (DataAccessException ex) {
            log.error(
                    "Database error while loading the authenticated user",
                    ex
            );

            response.sendError(
                    HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "Authentication service temporarily unavailable"
            );

            return;
        }

        if (user != null) {
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            user.getEmail(),
                            null,
                            List.of(
                                    new SimpleGrantedAuthority(
                                            "ROLE_" + user.getRole().name()
                                    )
                            )
                    );

            SecurityContextHolder.getContext()
                    .setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);
    }
}