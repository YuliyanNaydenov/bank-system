package java_project_yn.bank_system.service.impl;

import java_project_yn.bank_system.data.entity.IndividualClient;
import java_project_yn.bank_system.data.entity.Role;
import java_project_yn.bank_system.data.entity.User;
import java_project_yn.bank_system.data.repo.ClientRepository;
import java_project_yn.bank_system.data.repo.RoleRepository;
import java_project_yn.bank_system.data.repo.UserRepository;
import java_project_yn.bank_system.dto.RegisterDTO;
import java_project_yn.bank_system.exception.BusinessRuleException;
import java_project_yn.bank_system.exception.DuplicateEntityException;
import java_project_yn.bank_system.service.AuditService;
import java_project_yn.bank_system.service.RegistrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RegistrationServiceImpl implements RegistrationService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final ClientRepository clientRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    @Override
    @Transactional
    public void registerClient(RegisterDTO dto) {
        if (!dto.getPassword().equals(dto.getConfirmPassword())) {
            throw new BusinessRuleException("Паролите не съвпадат!");
        }
        if (userRepository.existsByUsername(dto.getUsername())) {
            throw new DuplicateEntityException(
                    "Потребителско име '" + dto.getUsername() + "' вече съществува!");
        }

        Role clientRole = roleRepository.findByAuthority("client")
                .orElseGet(() -> roleRepository.save(
                        Role.builder().authority("client").build()));

        User user = User.builder()
                .username(dto.getUsername())
                .password(passwordEncoder.encode(dto.getPassword()))
                .build();
        user.addRole(clientRole);
        userRepository.save(user);

        IndividualClient client = new IndividualClient();
        client.setFirstName(dto.getFirstName());
        client.setLastName(dto.getLastName());
        client.setEgn(dto.getEgn());
        client.setUsername(dto.getUsername());
        clientRepository.save(client);

        auditService.log(dto.getUsername(), "Регистрация", "Нов клиент се регистрира: "
                + dto.getFirstName() + " " + dto.getLastName());
    }
}
