package java_project_yn.bank_system.web.view;

import jakarta.validation.Valid;
import java_project_yn.bank_system.dto.CreateAccountDTO;
import java_project_yn.bank_system.service.AccountService;
import java_project_yn.bank_system.service.ClientService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/accounts")
@RequiredArgsConstructor
public class AccountViewController {

    private final AccountService accountService;
    private final ClientService clientService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("accounts", accountService.getAllAccounts());
        return "accounts/accounts";
    }

    @GetMapping("/new")
    public String newAccount(Model model) {
        model.addAttribute("account", new CreateAccountDTO());
        model.addAttribute("allClients", clientService.getAllClients());
        return "accounts/account-form";
    }

    @PostMapping("/new")
    public String open(@Valid @ModelAttribute("account") CreateAccountDTO dto,
                       BindingResult bindingResult, Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("allClients", clientService.getAllClients());
            return "accounts/account-form";
        }
        accountService.openAccount(dto);
        return "redirect:/accounts?opened";
    }

    @PostMapping("/{id}/close")
    public String close(@PathVariable long id) {
        accountService.closeAccount(id);
        return "redirect:/accounts?closed";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable long id) {
        accountService.deleteAccount(id);
        return "redirect:/accounts?deleted";
    }
}
