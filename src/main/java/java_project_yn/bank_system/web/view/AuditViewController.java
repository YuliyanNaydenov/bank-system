package java_project_yn.bank_system.web.view;

import java_project_yn.bank_system.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/audit")
@RequiredArgsConstructor
public class AuditViewController {

    private final AuditService auditService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("logs", auditService.getRecent());
        return "audit/audit";
    }

    @PostMapping("/clear")
    public String clear() {
        auditService.clearAll();
        return "redirect:/audit?cleared";
    }
}
