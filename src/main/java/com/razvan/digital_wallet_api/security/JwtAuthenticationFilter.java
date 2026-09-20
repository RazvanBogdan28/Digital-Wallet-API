package com.razvan.digital_wallet_api.security;

import com.razvan.digital_wallet_api.entity.User;
import com.razvan.digital_wallet_api.repository.UserRepository;
import com.razvan.digital_wallet_api.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

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

        System.out.println("[JWT-DEBUG] " + request.getMethod() + " " + request.getRequestURI()
                + " | Authorization header present: " + (authHeader != null));

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            System.out.println("[JWT-DEBUG] No Bearer header, skipping auth for this request.");
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);

        try {
            String email = jwtService.extractEmail(token);
            System.out.println("[JWT-DEBUG] Extracted email from token: " + email);

            if (email != null &&
                    SecurityContextHolder.getContext().getAuthentication() == null) {

                User user = userRepository.findByEmail(email)
                        .orElse(null);

                System.out.println("[JWT-DEBUG] User found in DB: " + (user != null)
                        + (user != null ? " | role=" + user.getRole() : ""));

                boolean valid = user != null && jwtService.isTokenValid(token, user.getEmail());
                boolean isAccess = user != null && jwtService.isAccessToken(token);

                System.out.println("[JWT-DEBUG] isTokenValid=" + valid + " | isAccessToken=" + isAccess);

                if (user != null && valid && isAccess) {

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

                    System.out.println("[JWT-DEBUG] Authentication SET successfully for " + user.getEmail());
                } else {
                    System.out.println("[JWT-DEBUG] Authentication NOT set (conditions failed).");
                }
            }

        } catch (Exception e) {
            System.out.println("[JWT-DEBUG] EXCEPTION while processing token: " + e.getClass().getSimpleName() + " - " + e.getMessage());
        }

        filterChain.doFilter(request, response);
    }
}