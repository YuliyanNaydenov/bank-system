package java_project_yn.bank_system.web.view;

import jakarta.validation.Valid;
import java_project_yn.bank_system.dto.AccountDTO;
import java_project_yn.bank_system.dto.CreateAccountDTO;
import java_project_yn.bank_system.service.AccountService;
import java_project_yn.bank_system.service.ClientService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/accounts")
@RequiredArgsConstructor
public class AccountViewController {

    private final AccountService accountService;
    private final ClientService clientService;

    @GetMapping
    public String list(@RequestParam(required = false) String q,
                       @RequestParam(required = false) String status,
                       Model model) {
        List<AccountDTO> accounts = accountService.getAllAccounts();
        if (q != null && !q.isBlank()) {
            String s = q.trim().toLowerCase();
            accounts = accounts.stream()
                    .filter(a -> contains(a.getIban(), s) || contains(a.getOwnerName(), s))
                    .collect(Collectors.toList());
        }
        if (status != null && !status.isBlank()) {
            accounts = accounts.stream()
                    .filter(a -> status.equals(a.getStatus()))
                    .collect(Collectors.toList());
        }
        model.addAttribute("accounts", accounts);
        model.addAttribute("q", q);
        model.addAttribute("status", status);
        return "accounts/accounts";
    }

    private boolean contains(String value, String search) {
        return value != null && value.toLowerCase().contains(search);
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
