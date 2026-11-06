package com.linkedin.userservice.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.linkedin.userservice.entity.User;

public interface UserRepository extends JpaRepository<User, String> {
    boolean existsByEmail(String email);

    Optional<User> findByEmail(String email);
}
