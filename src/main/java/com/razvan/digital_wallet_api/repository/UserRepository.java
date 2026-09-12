package com.razvan.digital_wallet_api.repository;

import com.razvan.digital_wallet_api.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    boolean existsByEmail(String email);
    Optional<User> findByEmail(String email);
}
