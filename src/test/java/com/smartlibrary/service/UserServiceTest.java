package com.smartlibrary.service;

import com.smartlibrary.dto.UserDto;
import com.smartlibrary.entity.Role;
import com.smartlibrary.entity.User;
import com.smartlibrary.exception.BusinessRuleException;
import com.smartlibrary.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private UserService userService;

    private UserDto userDto;

    @BeforeEach
    void setUp() {
        userDto = new UserDto();
        userDto.setUsername("alex_test");
        userDto.setPassword("plainPass123");
        userDto.setFullName("Alex Morgan");
        userDto.setEmail("alex@example.com");
        userDto.setPhone("+1-555-0123");
    }

    @Test
    @DisplayName("Student registration hashes password and strictly assigns ROLE_STUDENT")
    void testRegisterStudentSuccess() {
        when(userRepository.existsByUsername("alex_test")).thenReturn(false);
        when(userRepository.existsByEmail("alex@example.com")).thenReturn(false);
        when(passwordEncoder.encode("plainPass123")).thenReturn("$2a$10$hashedPasswordSample");
        when(userRepository.save(any(User.class))).thenAnswer(i -> {
            User u = i.getArgument(0);
            u.setId(10L);
            return u;
        });

        User user = userService.registerStudent(userDto);

        assertThat(user).isNotNull();
        assertThat(user.getRole()).isEqualTo(Role.ROLE_STUDENT);
        assertThat(user.getPassword()).isEqualTo("$2a$10$hashedPasswordSample");
        assertThat(user.getStatus()).isEqualTo("ACTIVE");
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("Student registration rejects duplicate username")
    void testRegisterDuplicateUsername() {
        when(userRepository.existsByUsername("alex_test")).thenReturn(true);

        assertThatThrownBy(() -> userService.registerStudent(userDto))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already taken");
    }

    @Test
    @DisplayName("Admin creates staff account with specified role")
    void testCreateUserByAdmin() {
        userDto.setRole(Role.ROLE_STAFF);
        when(userRepository.existsByUsername("alex_test")).thenReturn(false);
        when(userRepository.existsByEmail("alex@example.com")).thenReturn(false);
        when(passwordEncoder.encode("plainPass123")).thenReturn("$2a$10$encoded");
        when(userRepository.save(any(User.class))).thenAnswer(i -> {
            User u = i.getArgument(0);
            u.setId(20L);
            return u;
        });

        User user = userService.createUserByAdmin(userDto, "admin");

        assertThat(user.getRole()).isEqualTo(Role.ROLE_STAFF);
    }

    @Test
    @DisplayName("Toggle user status suspends and activates accounts safely")
    void testToggleUserStatus() {
        User target = new User("target_user", "pwd", "Target User", "target@test.com", Role.ROLE_STUDENT, "123");
        target.setId(5L);
        target.setStatus("ACTIVE");

        when(userRepository.findById(5L)).thenReturn(Optional.of(target));

        userService.toggleUserStatus(5L, "admin");

        assertThat(target.getStatus()).isEqualTo("SUSPENDED");
        verify(userRepository).save(target);
    }

    @Test
    @DisplayName("Admin cannot suspend their own active account")
    void testAdminCannotSuspendSelf() {
        User adminUser = new User("admin", "pwd", "Admin", "admin@test.com", Role.ROLE_ADMIN, "123");
        adminUser.setId(1L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(adminUser));

        assertThatThrownBy(() -> userService.toggleUserStatus(1L, "admin"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("cannot suspend their own active account");
    }
}
