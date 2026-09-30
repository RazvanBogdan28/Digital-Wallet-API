package com.razvan.digital_wallet_api.service;

import com.razvan.digital_wallet_api.dto.CreateUserRequest;
import com.razvan.digital_wallet_api.dto.UserResponse;
import com.razvan.digital_wallet_api.entity.Role;
import com.razvan.digital_wallet_api.entity.User;
import com.razvan.digital_wallet_api.exception.UserAlreadyExistsException;
import com.razvan.digital_wallet_api.exception.UserNotFoundException;
import com.razvan.digital_wallet_api.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private User authenticate(Long id, Role role) {
        User user = new User(
                "Razvan",
                "Test",
                "razvan@test.com",
                "hashedPassword"
        );

        user.setId(id);
        user.setRole(role);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        user.getEmail(),
                        null,
                        List.of()
                )
        );

        when(userRepository.findByEmail(user.getEmail()))
                .thenReturn(Optional.of(user));

        return user;
    }

    @Test
    void createUserShouldCreateUserSuccessfully() {
        CreateUserRequest request = new CreateUserRequest();
        request.setFirstName("Razvan");
        request.setLastName("Test");
        request.setEmail("razvan@test.com");
        request.setPassword("password123");

        when(userRepository.existsByEmail("razvan@test.com"))
                .thenReturn(false);

        when(passwordEncoder.encode("password123"))
                .thenReturn("hashedPassword");

        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> {
                    User user = invocation.getArgument(0);
                    user.setId(1L);
                    return user;
                });

        UserResponse response = userService.createUser(request);

        assertEquals(1L, response.getId());
        assertEquals("Razvan", response.getFirstName());
        assertEquals("Test", response.getLastName());
        assertEquals("razvan@test.com", response.getEmail());
    }

    @Test
    void createUserShouldThrowExceptionWhenEmailAlreadyExists() {
        CreateUserRequest request = new CreateUserRequest();
        request.setFirstName("Razvan");
        request.setLastName("Test");
        request.setEmail("razvan@test.com");
        request.setPassword("password123");

        when(userRepository.existsByEmail("razvan@test.com"))
                .thenReturn(true);

        assertThrows(
                UserAlreadyExistsException.class,
                () -> userService.createUser(request)
        );
    }

    @Test
    void getByIdShouldReturnOwnProfile() {
        User user = authenticate(1L, Role.USER);

        when(userRepository.findById(1L))
                .thenReturn(Optional.of(user));

        UserResponse response = userService.getUserById(1L);

        assertEquals(1L, response.getId());
        assertEquals("Razvan", response.getFirstName());
        assertEquals("Test", response.getLastName());
        assertEquals("razvan@test.com", response.getEmail());
    }

    @Test
    void getByIdShouldDenyAnotherUsersProfile() {
        authenticate(1L, Role.USER);

        assertThrows(
                AccessDeniedException.class,
                () -> userService.getUserById(2L)
        );

        verify(userRepository, never()).findById(2L);
    }

    @Test
    void getByIdShouldAllowAdminToReadAnotherProfile() {
        authenticate(1L, Role.ADMIN);

        User otherUser = new User(
                "John",
                "Test",
                "john@test.com",
                "hashedPassword"
        );

        otherUser.setId(2L);

        when(userRepository.findById(2L))
                .thenReturn(Optional.of(otherUser));

        UserResponse response = userService.getUserById(2L);

        assertEquals(2L, response.getId());
        assertEquals("John", response.getFirstName());
        assertEquals("Test", response.getLastName());
        assertEquals("john@test.com", response.getEmail());
    }

    @Test
    void getByIdShouldThrowWhenUserNotFoundForAdmin() {
        authenticate(1L, Role.ADMIN);

        when(userRepository.findById(999L))
                .thenReturn(Optional.empty());

        assertThrows(
                UserNotFoundException.class,
                () -> userService.getUserById(999L)
        );
    }

    @Test
    void getByIdShouldDenyMissingAuthentication() {
        SecurityContextHolder.clearContext();

        assertThrows(
                AccessDeniedException.class,
                () -> userService.getUserById(1L)
        );

        verify(userRepository, never()).findById(1L);
    }
}