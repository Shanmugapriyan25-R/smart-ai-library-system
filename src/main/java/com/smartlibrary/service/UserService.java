package com.smartlibrary.service;

import com.smartlibrary.dto.UserDto;
import com.smartlibrary.entity.Role;
import com.smartlibrary.entity.User;
import com.smartlibrary.exception.BusinessRuleException;
import com.smartlibrary.exception.ResourceNotFoundException;
import com.smartlibrary.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, AuditLogService auditLogService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + id));
    }

    @Transactional(readOnly = true)
    public User findByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with username: " + username));
    }

    @Transactional
    public User registerStudent(UserDto userDto) {
        if (userRepository.existsByUsername(userDto.getUsername())) {
            throw new BusinessRuleException("Username '" + userDto.getUsername() + "' is already taken.");
        }
        if (userRepository.existsByEmail(userDto.getEmail())) {
            throw new BusinessRuleException("Email '" + userDto.getEmail() + "' is already registered.");
        }

        User user = new User();
        user.setUsername(userDto.getUsername().trim());
        user.setPassword(passwordEncoder.encode(userDto.getPassword()));
        user.setFullName(userDto.getFullName().trim());
        user.setEmail(userDto.getEmail().trim().toLowerCase());
        user.setRole(Role.ROLE_STUDENT); // Self-registration is restricted strictly to students
        user.setPhone(userDto.getPhone());
        user.setStatus("ACTIVE");

        User savedUser = userRepository.save(user);
        String entityId = savedUser.getId() != null ? savedUser.getId().toString() : "NEW";
        auditLogService.log(savedUser.getUsername(), "REGISTER_STUDENT", "User", entityId, "New student self-registered");
        return savedUser;
    }

    @Transactional
    public User createUserByAdmin(UserDto userDto, String adminUsername) {
        if (userRepository.existsByUsername(userDto.getUsername())) {
            throw new BusinessRuleException("Username '" + userDto.getUsername() + "' is already taken.");
        }
        if (userRepository.existsByEmail(userDto.getEmail())) {
            throw new BusinessRuleException("Email '" + userDto.getEmail() + "' is already registered.");
        }
        if (userDto.getPassword() == null || userDto.getPassword().trim().isEmpty()) {
            throw new BusinessRuleException("Password is required for new user creation.");
        }

        User user = new User();
        user.setUsername(userDto.getUsername().trim());
        user.setPassword(passwordEncoder.encode(userDto.getPassword()));
        user.setFullName(userDto.getFullName().trim());
        user.setEmail(userDto.getEmail().trim().toLowerCase());
        user.setRole(userDto.getRole() != null ? userDto.getRole() : Role.ROLE_STUDENT);
        user.setPhone(userDto.getPhone());
        user.setStatus(userDto.getStatus() != null ? userDto.getStatus() : "ACTIVE");

        User savedUser = userRepository.save(user);
        String entityId = savedUser.getId() != null ? savedUser.getId().toString() : "NEW";
        auditLogService.log(adminUsername, "CREATE_USER", "User", entityId,
                "Created user " + savedUser.getUsername() + " with role " + savedUser.getRole());
        return savedUser;
    }

    @Transactional
    public User updateUserByAdmin(Long id, UserDto userDto, String adminUsername) {
        User user = findById(id);

        if (!user.getEmail().equalsIgnoreCase(userDto.getEmail()) && userRepository.existsByEmail(userDto.getEmail())) {
            throw new BusinessRuleException("Email '" + userDto.getEmail() + "' is already registered to another account.");
        }

        user.setFullName(userDto.getFullName().trim());
        user.setEmail(userDto.getEmail().trim().toLowerCase());
        user.setPhone(userDto.getPhone());
        if (userDto.getRole() != null) {
            user.setRole(userDto.getRole());
        }
        if (userDto.getStatus() != null) {
            user.setStatus(userDto.getStatus());
        }

        if (userDto.getPassword() != null && !userDto.getPassword().trim().isEmpty()) {
            user.setPassword(passwordEncoder.encode(userDto.getPassword().trim()));
        }

        User updatedUser = userRepository.save(user);
        auditLogService.log(adminUsername, "UPDATE_USER", "User", updatedUser.getId().toString(),
                "Updated user account " + updatedUser.getUsername());
        return updatedUser;
    }

    @Transactional
    public void updateUserProfile(String username, String fullName, String email, String phone, String newPassword) {
        User user = findByUsername(username);

        if (!user.getEmail().equalsIgnoreCase(email) && userRepository.existsByEmail(email)) {
            throw new BusinessRuleException("Email '" + email + "' is already in use by another account.");
        }

        user.setFullName(fullName.trim());
        user.setEmail(email.trim().toLowerCase());
        user.setPhone(phone);

        if (newPassword != null && !newPassword.trim().isEmpty()) {
            user.setPassword(passwordEncoder.encode(newPassword.trim()));
        }

        userRepository.save(user);
        auditLogService.log(username, "UPDATE_PROFILE", "User", user.getId().toString(), "User updated profile details");
    }

    @Transactional
    public void toggleUserStatus(Long id, String adminUsername) {
        User user = findById(id);
        if (user.getUsername().equalsIgnoreCase(adminUsername)) {
            throw new BusinessRuleException("Administrator cannot suspend their own active account.");
        }
        String newStatus = "ACTIVE".equalsIgnoreCase(user.getStatus()) ? "SUSPENDED" : "ACTIVE";
        user.setStatus(newStatus);
        userRepository.save(user);
        auditLogService.log(adminUsername, "TOGGLE_USER_STATUS", "User", user.getId().toString(),
                "Changed status of user " + user.getUsername() + " to " + newStatus);
    }

    @Transactional(readOnly = true)
    public Page<User> findUsersByRole(Role role, Pageable pageable) {
        return userRepository.findByRole(role, pageable);
    }

    @Transactional(readOnly = true)
    public List<User> searchUsers(String query) {
        return userRepository.searchUsers(query);
    }
}
