package com.mizigo.cargo_backend.controller;

import com.mizigo.cargo_backend.model.User;
import com.mizigo.cargo_backend.model.UserRole;
import com.mizigo.cargo_backend.model.DriverApprovalStatus;
import com.mizigo.cargo_backend.repository.UserRepository;
import com.mizigo.cargo_backend.model.NotificationPriority;
import com.mizigo.cargo_backend.service.AdminNotificationService;
import com.mizigo.cargo_backend.security.SecurityIdentity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdminNotificationService notificationService;

    public UserController(UserRepository userRepository, PasswordEncoder passwordEncoder,
            AdminNotificationService notificationService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.notificationService = notificationService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getUserById(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        if (!isAdmin(jwt) && !id.equals(SecurityIdentity.userId(jwt))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return userRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @Transactional
    public ResponseEntity<?> createUser(@Valid @RequestBody RegisterRequest request) {
        if (request.role() == UserRole.ADMIN) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (userRepository.existsByEmail(request.email())) {
            return ResponseEntity.badRequest().body("Email tayari imesajiliwa");
        }
        if (userRepository.existsByPhone(request.phone())) {
            return ResponseEntity.badRequest().body("Namba ya simu tayari imesajiliwa");
        }

        User user = new User();
        user.setFullName(request.fullName());
        user.setEmail(request.email());
        user.setPhone(request.phone());
        user.setPassword(passwordEncoder.encode(request.password()));
        UserRole role = request.role() == null ? UserRole.CUSTOMER : request.role();
        user.setRole(role);
        user.setDriverApprovalStatus(role == UserRole.DRIVER
                ? DriverApprovalStatus.PENDING_APPROVAL
                : DriverApprovalStatus.APPROVED);
        User savedUser = userRepository.save(user);
        if (role == UserRole.DRIVER) {
            notificationService.notifyOnce("DRIVER_REGISTERED", NotificationPriority.MEDIUM,
                    "A new driver registered and is awaiting approval.",
                    "DRIVER", savedUser.getId().toString(),
                    "/admin-dashboard?section=drivers&driverId=" + savedUser.getId(),
                    "DRIVER_REGISTERED:" + savedUser.getId());
        }
        return ResponseEntity.ok(savedUser);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<User> updateUser(@PathVariable Long id, @RequestBody User updatedUser) {
        return userRepository.findById(id)
                .map(user -> {
                    user.setFullName(updatedUser.getFullName());
                    user.setEmail(updatedUser.getEmail());
                    user.setPhone(updatedUser.getPhone());
                    user.setRole(updatedUser.getRole());
                    return ResponseEntity.ok(userRepository.save(user));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        if (!userRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        userRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private boolean isAdmin(Jwt jwt) {
        return "ADMIN".equals(jwt.getClaimAsString("role"));
    }

    public record RegisterRequest(
            @NotBlank @Size(max = 150) String fullName,
            @NotBlank @Email @Size(max = 150) String email,
            @NotBlank @Size(max = 20) String phone,
            @NotBlank @Size(min = 8, max = 72) String password,
            UserRole role) {}
}