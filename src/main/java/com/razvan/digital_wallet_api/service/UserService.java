package com.razvan.digital_wallet_api.service;

import com.razvan.digital_wallet_api.dto.CreateUserRequest;
import com.razvan.digital_wallet_api.dto.UserResponse;
import com.razvan.digital_wallet_api.entity.Role;
import com.razvan.digital_wallet_api.entity.User;
import com.razvan.digital_wallet_api.exception.UserAlreadyExistsException;
import com.razvan.digital_wallet_api.exception.UserNotFoundException;
import com.razvan.digital_wallet_api.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public UserResponse createUser(CreateUserRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new UserAlreadyExistsException(
                    "A user with this email already exists"
            );
        }

        String encodedPassword =
                passwordEncoder.encode(request.getPassword());

        User user = new User(
                request.getFirstName(),
                request.getLastName(),
                request.getEmail(),
                encodedPassword
        );

        user.setRole(Role.USER);

        User savedUser = userRepository.save(user);

        return mapToResponse(savedUser);
    }

    public List<UserResponse> getAllUsers() {
        User authenticatedUser = getAuthenticatedUser();

        if (authenticatedUser.getRole() != Role.ADMIN) {
            throw new AccessDeniedException(
                    "Admin role required"
            );
        }

        return userRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    public UserResponse getUserById(Long id) {
        User authenticatedUser = getAuthenticatedUser();

        boolean isAdmin =
                authenticatedUser.getRole() == Role.ADMIN;

        boolean isOwnProfile =
                authenticatedUser.getId().equals(id);

        if (!isAdmin && !isOwnProfile) {
            throw new AccessDeniedException(
                    "You do not have access to this user profile"
            );
        }

        User user = userRepository.findById(id)
                .orElseThrow(() ->
                        new UserNotFoundException(
                                "User not found with id: " + id
                        )
                );

        return mapToResponse(user);
    }

    private UserResponse mapToResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail()
        );
    }

    private User getAuthenticatedUser() {
        Authentication authentication =
                SecurityContextHolder.getContext()
                        .getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()) {
            throw new AccessDeniedException(
                    "Authentication required"
            );
        }

        String email = authentication.getName();

        return userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new UserNotFoundException(
                                "Authenticated user not found"
                        )
                );
    }
}