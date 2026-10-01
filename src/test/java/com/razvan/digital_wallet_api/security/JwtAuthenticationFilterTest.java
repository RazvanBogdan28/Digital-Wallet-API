package com.razvan.digital_wallet_api.security;

import com.razvan.digital_wallet_api.entity.Role;
import com.razvan.digital_wallet_api.entity.User;
import com.razvan.digital_wallet_api.repository.UserRepository;
import com.razvan.digital_wallet_api.service.JwtService;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void databaseFailureShouldReturn503AndStopRequest() throws Exception {
        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/api/wallets/1");

        MockHttpServletResponse response =
                new MockHttpServletResponse();

        request.addHeader("Authorization", "Bearer valid-token");

        mockValidAccessToken();

        when(userRepository.findByEmail("user@test.com"))
                .thenThrow(
                        new DataAccessResourceFailureException(
                                "Database unavailable"
                        )
                );

        filter.doFilter(request, response, filterChain);

        assertEquals(503, response.getStatus());
        assertEquals(
                "Authentication service temporarily unavailable",
                response.getErrorMessage()
        );

        assertNull(
                SecurityContextHolder.getContext().getAuthentication()
        );

        verifyNoInteractions(filterChain);
    }

    @Test
    void expiredTokenShouldContinueWithoutAuthentication() throws Exception {
        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/api/wallets/1");

        MockHttpServletResponse response =
                new MockHttpServletResponse();

        request.addHeader("Authorization", "Bearer expired-token");

        when(jwtService.extractEmail("expired-token"))
                .thenThrow(
                        new ExpiredJwtException(
                                null,
                                null,
                                "Token expired"
                        )
                );

        filter.doFilter(request, response, filterChain);

        assertNull(
                SecurityContextHolder.getContext().getAuthentication()
        );

        verifyNoInteractions(userRepository);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void validAccessTokenShouldAuthenticateUser() throws Exception {
        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/api/wallets/1");

        MockHttpServletResponse response =
                new MockHttpServletResponse();

        request.addHeader("Authorization", "Bearer valid-token");

        mockValidAccessToken();

        User user = new User(
                "Razvan",
                "Test",
                "user@test.com",
                "hashedPassword"
        );

        user.setRole(Role.USER);

        when(userRepository.findByEmail("user@test.com"))
                .thenReturn(Optional.of(user));

        filter.doFilter(request, response, filterChain);

        var authentication =
                SecurityContextHolder.getContext().getAuthentication();

        assertNotNull(authentication);
        assertTrue(authentication.isAuthenticated());
        assertEquals("user@test.com", authentication.getName());

        assertTrue(
                authentication.getAuthorities()
                        .stream()
                        .anyMatch(authority ->
                                authority.getAuthority().equals("ROLE_USER")
                        )
        );

        verify(filterChain).doFilter(request, response);
    }

    private void mockValidAccessToken() {
        when(jwtService.extractEmail("valid-token"))
                .thenReturn("user@test.com");

        when(jwtService.isAccessToken("valid-token"))
                .thenReturn(true);

        when(jwtService.isTokenValid("valid-token", "user@test.com"))
                .thenReturn(true);
    }
}