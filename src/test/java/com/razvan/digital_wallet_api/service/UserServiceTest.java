package com.razvan.digital_wallet_api.service;

import com.razvan.digital_wallet_api.dto.CreateUserRequest;
import com.razvan.digital_wallet_api.dto.UserResponse;
import com.razvan.digital_wallet_api.entity.User;
import com.razvan.digital_wallet_api.exception.UserAlreadyExistsException;
import com.razvan.digital_wallet_api.exception.UserNotFoundException;
import com.razvan.digital_wallet_api.repository.UserRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

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
                    ReflectionTestUtils.setField(user, "id", 1L);
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
    void getByIdShouldReturnUser() {

        User user = new User(
                "Razvan",
                "Test",
                "razvan@test.com",
                "hashedPassword"
        );

        ReflectionTestUtils.setField(user, "id", 1L);

        when(userRepository.findById(1L))
                .thenReturn(java.util.Optional.of(user));

        UserResponse response = userService.getUserById(1L);

        assertEquals(1L, response.getId());
        assertEquals("Razvan", response.getFirstName());
        assertEquals("Test", response.getLastName());
        assertEquals("razvan@test.com", response.getEmail());
    }

    @Test
    void getByIdShouldThrowExceptionWhenUserNotFound() {

        when(userRepository.findById(999L))
                .thenReturn(java.util.Optional.empty());

        assertThrows(
                UserNotFoundException.class,
                () -> userService.getUserById(999L)
        );
    }
}