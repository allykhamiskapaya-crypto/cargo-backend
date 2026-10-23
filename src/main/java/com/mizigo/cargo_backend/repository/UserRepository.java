package com.mizigo.cargo_backend.repository;

import com.mizigo.cargo_backend.model.User;
import com.mizigo.cargo_backend.model.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.List;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findByPhone(String phone);

    List<User> findByRole(UserRole role);

    boolean existsByEmail(String email);

    boolean existsByPhone(String phone);
}
