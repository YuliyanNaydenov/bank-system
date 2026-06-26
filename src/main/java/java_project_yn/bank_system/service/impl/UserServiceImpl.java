package java_project_yn.bank_system.service.impl;

import java_project_yn.bank_system.data.entity.Role;
import java_project_yn.bank_system.data.entity.User;
import java_project_yn.bank_system.data.repo.RoleRepository;
import java_project_yn.bank_system.data.repo.UserRepository;
import java_project_yn.bank_system.dto.CreateEmployeeDTO;
import java_project_yn.bank_system.dto.UserSummaryDTO;
import java_project_yn.bank_system.exception.BusinessRuleException;
import java_project_yn.bank_system.exception.DuplicateEntityException;
import java_project_yn.bank_system.service.AuditService;
import java_project_yn.bank_system.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Потребител '" + username + "' не е намерен!"));
    }

    @Override
    public boolean usernameExists(String username) {
        return userRepository.existsByUsername(username);
    }

    @Override
    @PreAuthorize("hasAuthority('admin')")
    public List<UserSummaryDTO> getEmployees() {
        return userRepository.findByAuthorities_Authority("employee").stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Override
    @PreAuthorize("hasAuthority('admin')")
    public void createEmployee(CreateEmployeeDTO dto) {
        if (!dto.getPassword().equals(dto.getConfirmPassword())) {
            throw new BusinessRuleException("Паролите не съвпадат!");
        }
        if (userRepository.existsByUsername(dto.getUsername())) {
            throw new DuplicateEntityException(
                    "Потребителско име '" + dto.getUsername() + "' вече съществува!");
        }

        Role employeeRole = roleRepository.findByAuthority("employee")
                .orElseGet(() -> roleRepository.save(Role.builder().authority("employee").build()));

        User user = User.builder()
                .username(dto.getUsername())
                .password(passwordEncoder.encode(dto.getPassword()))
                .build();
        user.addRole(employeeRole);
        userRepository.save(user);
        auditService.log("Нов служител", dto.getUsername());
    }

    @Override
    @PreAuthorize("hasAuthority('admin')")
    public void deleteUser(long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessRuleException("Потребител с id=" + id + " не е намерен!"));

        boolean isAdmin = user.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("admin"));
        if (isAdmin) {
            throw new BusinessRuleException("Администраторски акаунт не може да бъде изтрит оттук!");
        }
        userRepository.deleteById(id);
        auditService.log("Изтрит служител", user.getUsername());
    }

    @Override
    @PreAuthorize("hasAuthority('admin')")
    public void setEmployeeEnabled(long id, boolean enabled) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessRuleException("Потребител с id=" + id + " не е намерен!"));

        boolean isAdmin = user.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("admin"));
        if (isAdmin) {
            throw new BusinessRuleException("Администраторски акаунт не може да бъде променян оттук!");
        }
        user.setEnabled(enabled);
        userRepository.save(user);
        auditService.log(enabled ? "Активиран служител" : "Деактивиран служител", user.getUsername());
    }

    @Override
    @PreAuthorize("hasAnyAuthority('admin', 'employee')")
    public void createClientLogin(String username, String rawPassword) {
        if (userRepository.existsByUsername(username)) {
            throw new DuplicateEntityException("Потребителско име '" + username + "' вече съществува!");
        }
        Role clientRole = roleRepository.findByAuthority("client")
                .orElseGet(() -> roleRepository.save(Role.builder().authority("client").build()));
        User user = User.builder()
                .username(username)
                .password(passwordEncoder.encode(rawPassword))
                .build();
        user.addRole(clientRole);
        userRepository.save(user);
        auditService.log("Нов клиентски вход", username);
    }

    @Override
    @PreAuthorize("isAuthenticated()")
    public void changePassword(String username, String currentPassword, String newPassword) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessRuleException("Потребителят не е намерен!"));
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new BusinessRuleException("Текущата парола е грешна!");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        auditService.log(username, "Смяна на парола", "Потребителят смени паролата си");
    }

    private UserSummaryDTO toDto(User user) {
        String roles = user.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(", "));
        return UserSummaryDTO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .roles(roles)
                .enabled(user.isEnabled())
                .build();
    }
}
