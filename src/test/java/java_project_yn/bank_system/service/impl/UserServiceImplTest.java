package java_project_yn.bank_system.service.impl;

import java_project_yn.bank_system.data.entity.Role;
import java_project_yn.bank_system.data.entity.User;
import java_project_yn.bank_system.data.repo.RoleRepository;
import java_project_yn.bank_system.data.repo.UserRepository;
import java_project_yn.bank_system.dto.CreateEmployeeDTO;
import java_project_yn.bank_system.exception.BusinessRuleException;
import java_project_yn.bank_system.exception.DuplicateEntityException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private java_project_yn.bank_system.service.AuditService auditService;

    @InjectMocks
    private UserServiceImpl userService;

    @Test
    void loadUserByUsername_notFound_throws() {
        given(userRepository.findByUsername("ghost")).willReturn(Optional.empty());
        assertThrows(UsernameNotFoundException.class, () -> userService.loadUserByUsername("ghost"));
    }

    @Test
    void createEmployee_duplicateUsername_throws() {
        CreateEmployeeDTO dto = CreateEmployeeDTO.builder()
                .username("emp").password("pass1").confirmPassword("pass1").build();
        given(userRepository.existsByUsername("emp")).willReturn(true);

        assertThrows(DuplicateEntityException.class, () -> userService.createEmployee(dto));
    }

    @Test
    void createEmployee_passwordMismatch_throws() {
        CreateEmployeeDTO dto = CreateEmployeeDTO.builder()
                .username("emp").password("pass1").confirmPassword("other").build();

        assertThrows(BusinessRuleException.class, () -> userService.createEmployee(dto));
    }

    @Test
    void createEmployee_success_savesWithRole() {
        CreateEmployeeDTO dto = CreateEmployeeDTO.builder()
                .username("emp").password("pass1").confirmPassword("pass1").build();
        given(userRepository.existsByUsername("emp")).willReturn(false);
        given(roleRepository.findByAuthority("employee"))
                .willReturn(Optional.of(Role.builder().authority("employee").build()));
        given(passwordEncoder.encode("pass1")).willReturn("ENC");

        userService.createEmployee(dto);

        verify(userRepository).save(any(User.class));
    }

    @Test
    void setEmployeeEnabled_onAdmin_throws() {
        User admin = User.builder().username("admin").build();
        admin.addRole(Role.builder().authority("admin").build());
        given(userRepository.findById(1L)).willReturn(Optional.of(admin));

        assertThrows(BusinessRuleException.class, () -> userService.setEmployeeEnabled(1L, false));
    }

    @Test
    void deleteUser_admin_throws() {
        User admin = User.builder().username("admin").build();
        admin.addRole(Role.builder().authority("admin").build());
        given(userRepository.findById(1L)).willReturn(Optional.of(admin));

        assertThrows(BusinessRuleException.class, () -> userService.deleteUser(1L));
    }

    @Test
    void changePassword_wrongCurrent_throws() {
        User user = User.builder().username("u").password("ENC_OLD").build();
        given(userRepository.findByUsername("u")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("wrong", "ENC_OLD")).willReturn(false);

        assertThrows(BusinessRuleException.class,
                () -> userService.changePassword("u", "wrong", "newpass"));
    }

    @Test
    void changePassword_success_encodesAndSaves() {
        User user = User.builder().username("u").password("ENC_OLD").build();
        given(userRepository.findByUsername("u")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("old", "ENC_OLD")).willReturn(true);
        given(passwordEncoder.encode("newpass")).willReturn("ENC_NEW");

        userService.changePassword("u", "old", "newpass");

        assertEquals("ENC_NEW", user.getPassword());
        verify(userRepository).save(user);
    }
}
