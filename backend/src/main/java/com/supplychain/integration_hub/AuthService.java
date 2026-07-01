package com.supplychain.integration_hub;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    private static final int MAX_FAILED_ATTEMPTS = 5;

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail()).orElse(null);

        if (user == null) {
            throw new RuntimeException("Invalid credentials.");
        }
        if (Boolean.TRUE.equals(user.getIsLocked())) {
            throw new RuntimeException("Account is locked. Contact your administrator.");
        }
        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new RuntimeException("Account is inactive. Contact your administrator.");
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            int attempts = (user.getFailedLoginAttempts() == null ? 0 : user.getFailedLoginAttempts()) + 1;
            user.setFailedLoginAttempts(attempts);
            if (attempts >= MAX_FAILED_ATTEMPTS) {
                user.setIsLocked(true);
                user.setLockedAt(LocalDateTime.now());
            }
            userRepository.save(user);
            throw new RuntimeException("Invalid credentials.");
        }

        user.setFailedLoginAttempts(0);
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        String token = jwtUtil.generateToken(
        user.getEmail(),
        user.getRole().name(),
        user.getUserId() != null ? user.getUserId() : "",
        user.getSystemId() != null
        ? user.getSystemId().name()
        : "SYSTEM1"
        );

        return AuthResponse.builder()
            .token(token)
            .role(user.getRole().name())
            .userId(user.getUserId() != null ? user.getUserId() : "")
            .name(user.getName() != null ? user.getName() : "")
            .entityId(user.getEntityId() != null ? user.getEntityId() : "")
            .entityType(user.getEntityType() != null ? user.getEntityType() : "")
            .systemId(user.getSystemId() != null? user.getSystemId().name(): "SYSTEM1")
            .build();
    }

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new RuntimeException("Email already registered.");
        }

        SystemId sid = SystemId.SYSTEM1;
        if (request.getSystemId() != null && !request.getSystemId().isBlank()) {
            try { sid = SystemId.valueOf(request.getSystemId().trim().toUpperCase()); }
            catch (IllegalArgumentException ignored) { /* keep default SYSTEM1 */ }
        }

        User user = User.builder()
            .userId("USR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
            .email(request.getEmail())
            .password(passwordEncoder.encode(request.getPassword()))
            .role(User.Role.valueOf(request.getRole()))
            .name(request.getName())
            .entityId(request.getEntityId())
            .entityType(request.getEntityType())
            .systemId(sid)
            .isActive(true)
            .isLocked(false)
            .failedLoginAttempts(0)
            .build();

        User saved = userRepository.save(user);

        String token = jwtUtil.generateToken(
        user.getEmail(),
        user.getRole().name(),
        user.getUserId() != null ? user.getUserId() : "",
        user.getSystemId() != null
        ? user.getSystemId().name()
        : "SYSTEM1"
        );

        return AuthResponse.builder()
            .token(token)
            .role(saved.getRole().name())
            .userId(saved.getUserId() != null ? saved.getUserId() : "")
            .name(saved.getName() != null ? saved.getName() : "")
            .entityId(saved.getEntityId() != null ? saved.getEntityId() : "")
            .entityType(saved.getEntityType() != null ? saved.getEntityType() : "")
            .systemId(saved.getSystemId() != null? saved.getSystemId().name(): "SYSTEM1")
            .build();
    }
}