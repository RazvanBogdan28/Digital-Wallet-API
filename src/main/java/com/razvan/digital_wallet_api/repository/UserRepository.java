package com.razvan.digital_wallet_api.repository;

import com.razvan.digital_wallet_api.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    @Query("""
            SELECT COUNT(u) > 0
            FROM User u
            WHERE LOWER(TRIM(u.email)) = LOWER(TRIM(:email))
            """)
    boolean existsByEmail(@Param("email") String email);

    @Query("""
            SELECT u
            FROM User u
            WHERE LOWER(TRIM(u.email)) = LOWER(TRIM(:email))
            """)
    Optional<User> findByEmail(@Param("email") String email);
}