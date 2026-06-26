package java_project_yn.bank_system.config;

import java_project_yn.bank_system.service.UserService;
import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Конфигурация на сигурността — само локален form login (потребители от БД).
 * Роли: admin, employee, client.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(securedEnabled = true)
@AllArgsConstructor
public class SecurityConfig {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;

    @Bean
    public DaoAuthenticationProvider daoAuthenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .authenticationProvider(daoAuthenticationProvider())
                .authorizeHttpRequests(authz -> authz
                        // Публични ресурси
                        .requestMatchers("/login", "/register",
                                "/css/**", "/js/**", "/images/**").permitAll()

                        // ── REST API ────────────────────────────────────────
                        // Кредитни видове: четене за всички логнати, промяна само admin
                        .requestMatchers(HttpMethod.GET, "/api/credit-types/**").authenticated()
                        .requestMatchers("/api/credit-types/**").hasAuthority("admin")

                        // Клиенти
                        .requestMatchers(HttpMethod.GET, "/api/clients/**").hasAnyAuthority("admin", "employee", "client")
                        .requestMatchers(HttpMethod.DELETE, "/api/clients/**").hasAuthority("admin")
                        .requestMatchers("/api/clients/**").hasAnyAuthority("admin", "employee")

                        // Сметки
                        .requestMatchers(HttpMethod.GET, "/api/accounts/**").hasAnyAuthority("admin", "employee", "client")
                        .requestMatchers(HttpMethod.DELETE, "/api/accounts/**").hasAuthority("admin")
                        .requestMatchers("/api/accounts/**").hasAnyAuthority("admin", "employee")

                        // Кредити
                        .requestMatchers(HttpMethod.GET, "/api/credits/**").hasAnyAuthority("admin", "employee", "client")
                        .requestMatchers(HttpMethod.DELETE, "/api/credits/**").hasAuthority("admin")
                        .requestMatchers("/api/credits/**").hasAnyAuthority("admin", "employee")

                        // ── View endpoints ──────────────────────────────────
                        .requestMatchers("/", "/index").authenticated()
                        .requestMatchers("/employees/**").hasAuthority("admin")
                        .requestMatchers("/audit/**").hasAuthority("admin")
                        .requestMatchers("/credit-types/**").hasAuthority("admin")
                        .requestMatchers("/clients/**", "/accounts/**", "/credits/**", "/statistics/**").hasAnyAuthority("admin", "employee")
                        .requestMatchers("/my/**").hasAuthority("client")
                        .anyRequest().authenticated()
                )
                // CSRF остава включен за формите; изключен само за REST API-то
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"))
                .formLogin(form -> form
                        .loginPage("/login")
                        .defaultSuccessUrl("/", true)
                        .failureUrl("/login?error")
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID")
                )
                .exceptionHandling(ex -> ex
                        .accessDeniedHandler((request, response, denied) -> {
                            if (request.getRequestURI().startsWith("/api/")) {
                                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                                response.setContentType("application/json;charset=UTF-8");
                                response.getWriter().write("{\"error\":\"Нямате права за достъп\"}");
                            } else {
                                response.sendRedirect(request.getContextPath() + "/");
                            }
                        })
                );
        return http.build();
    }
}
