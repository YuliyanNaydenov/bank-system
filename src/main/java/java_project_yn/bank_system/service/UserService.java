package java_project_yn.bank_system.service;

import java_project_yn.bank_system.dto.CreateEmployeeDTO;
import java_project_yn.bank_system.dto.UserSummaryDTO;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.util.List;

public interface UserService extends UserDetailsService {
    boolean usernameExists(String username);

    /** Връща всички служители (потребители с роля "employee"). */
    List<UserSummaryDTO> getEmployees();

    /** Създава нов служител с роля "employee". */
    void createEmployee(CreateEmployeeDTO dto);

    /** Изтрива потребител (само служители). */
    void deleteUser(long id);

    /** Активира / деактивира служител (деактивиран не може да влиза). */
    void setEmployeeEnabled(long id, boolean enabled);

    /** Смяна на собствената парола след проверка на текущата. */
    void changePassword(String username, String currentPassword, String newPassword);

    /** Създава вход (роля "client") за клиент, добавен от служител/админ. */
    void createClientLogin(String username, String rawPassword);
}
