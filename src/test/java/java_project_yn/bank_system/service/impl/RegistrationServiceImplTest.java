package java_project_yn.bank_system.service.impl;

import java_project_yn.bank_system.data.entity.Client;
import java_project_yn.bank_system.data.entity.Role;
import java_project_yn.bank_system.data.entity.User;
import java_project_yn.bank_system.data.repo.ClientRepository;
import java_project_yn.bank_system.data.repo.RoleRepository;
import java_project_yn.bank_system.data.repo.UserRepository;
import java_project_yn.bank_system.dto.RegisterDTO;
import java_project_yn.bank_system.exception.BusinessRuleException;
import java_project_yn.bank_system.exception.DuplicateEntityException;
import java_project_yn.bank_system.service.AuditService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RegistrationServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private ClientRepository clientRepository;
    @Mock private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    @Mock private AuditService auditService;

    @InjectMocks
    private RegistrationServiceImpl registrationService;

    private RegisterDTO dto(String pass, String confirm) {
        return RegisterDTO.builder()
                .username("newclient").password(pass).confirmPassword(confirm)
                .firstName("Иван").lastName("Петров").egn("8001011234").build();
    }

    @Test
    void registerClient_passwordMismatch_throws() {
        assertThrows(BusinessRuleException.class,
                () -> registrationService.registerClient(dto("pass1", "other")));
    }

    @Test
    void registerClient_duplicateUsername_throws() {
        given(userRepository.existsByUsername("newclient")).willReturn(true);
        assertThrows(DuplicateEntityException.class,
                () -> registrationService.registerClient(dto("pass1", "pass1")));
    }

    @Test
    void registerClient_success_savesUserAndClient() {
        given(userRepository.existsByUsername("newclient")).willReturn(false);
        given(roleRepository.findByAuthority("client"))
                .willReturn(Optional.of(Role.builder().authority("client").build()));
        given(passwordEncoder.encode("pass1")).willReturn("ENC");

        registrationService.registerClient(dto("pass1", "pass1"));

        verify(userRepository).save(any(User.class));
        verify(clientRepository).save(any(Client.class));
    }
}
