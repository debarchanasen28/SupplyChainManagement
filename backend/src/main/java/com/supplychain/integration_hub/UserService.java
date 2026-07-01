package com.supplychain.integration_hub;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public User getUserById(String id) {
        return userRepository.findById(id).orElse(null);
    }

    public User updateUser(String id, UpdateUserRequest request) {
        User user = userRepository.findById(id).orElse(null);
        if (user == null) return null;

        if (request.getName() != null) {
            user.setName(request.getName());
        }
        if (request.getRole() != null) {
            user.setRole(User.Role.valueOf(request.getRole()));
        }
        if (request.getEntityId() != null) {
            user.setEntityId(request.getEntityId());
        }
        if (request.getEntityType() != null) {
            user.setEntityType(request.getEntityType());
        }
        if (request.getIsActive() != null) {
            user.setIsActive(request.getIsActive());
        }
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }

        user.setUpdatedAt(LocalDateTime.now());
        return userRepository.save(user);
    }

    public User toggleActive(String id) {
        User user = userRepository.findById(id).orElse(null);
        if (user == null) return null;
        user.setIsActive(!Boolean.TRUE.equals(user.getIsActive()));
        user.setUpdatedAt(LocalDateTime.now());
        return userRepository.save(user);
    }

    public User unlockUser(String id) {
        User user = userRepository.findById(id).orElse(null);
        if (user == null) return null;
        user.setIsLocked(false);
        user.setFailedLoginAttempts(0);
        user.setLockedAt(null);
        user.setUpdatedAt(LocalDateTime.now());
        return userRepository.save(user);
    }

    public boolean deleteUser(String id) {
        if (!userRepository.existsById(id)) return false;
        userRepository.deleteById(id);
        return true;
    }
}