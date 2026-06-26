package java_project_yn.bank_system.config;

import java_project_yn.bank_system.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

/**
 * Записва успешните влизания в журнала.
 */
@Component
@RequiredArgsConstructor
public class LoginAuditListener {

    private final AuditService auditService;

    @EventListener
    public void onAuthenticationSuccess(AuthenticationSuccessEvent event) {
        auditService.log(event.getAuthentication().getName(), "Вход", "Успешно влизане в системата");
    }
}
