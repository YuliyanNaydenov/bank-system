package java_project_yn.bank_system.web.view;

import java_project_yn.bank_system.dto.CreditDTO;
import java_project_yn.bank_system.exception.CreditNotFoundException;
import java_project_yn.bank_system.service.AccountService;
import java_project_yn.bank_system.service.ClientService;
import java_project_yn.bank_system.service.CreditService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Самообслужване за клиент с роля "client" — вижда само собствените си
 * сметки, кредити и погасителни планове.
 */
@Controller
@RequestMapping("/my")
@RequiredArgsConstructor
public class MyController {

    private final AccountService accountService;
    private final CreditService creditService;
    private final ClientService clientService;

    @GetMapping
    public String dashboard(Authentication authentication, Model model) {
        String username = authentication.getName();
        model.addAttribute("client", clientService.getClientByUsername(username));
        model.addAttribute("accounts", accountService.getAccountsByUsername(username));
        model.addAttribute("credits", creditService.getCreditsByUsername(username));
        return "my/dashboard";
    }

    @GetMapping("/credits/{id}")
    public String creditDetail(@PathVariable long id, Authentication authentication, Model model) {
        CreditDTO credit = creditService.getCreditById(id);
        // Проверка за собственост — клиентът вижда само своите кредити
        boolean owns = creditService.getCreditsByUsername(authentication.getName()).stream()
                .anyMatch(c -> c.getId() == id);
        if (!owns) {
            throw new CreditNotFoundException("Кредит с id=" + id + " не е намерен!");
        }
        model.addAttribute("credit", credit);
        return "credits/credit-detail";
    }
}
