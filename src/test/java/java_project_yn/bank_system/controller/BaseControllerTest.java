package java_project_yn.bank_system.controller;

import java_project_yn.bank_system.service.UserService;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Базов клас за @WebMvcTest тестове.
 * SecurityConfig изисква UserService и PasswordEncoder по конструктор — тук ги мокваме.
 */
abstract class BaseControllerTest {

    @MockBean
    protected UserService userService;

    @MockBean
    protected PasswordEncoder passwordEncoder;
}
