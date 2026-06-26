package java_project_yn.bank_system.web.view;

import java_project_yn.bank_system.dto.AccountDTO;
import java_project_yn.bank_system.dto.CreditDTO;
import java_project_yn.bank_system.exception.AccountNotFoundException;
import java_project_yn.bank_system.exception.CreditNotFoundException;
import java_project_yn.bank_system.service.AccountService;
import java_project_yn.bank_system.service.ClientService;
import java_project_yn.bank_system.service.CreditService;
import java_project_yn.bank_system.service.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

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
    private final TransactionService transactionService;

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
        model.addAttribute("payAccounts", accountService.getAccountsByUsername(authentication.getName()).stream()
                .filter(a -> "ACTIVE".equals(a.getStatus()))
                .toList());
        return "credits/credit-detail";
    }

    @PostMapping("/credits/{cid}/installments/{iid}/pay")
    public String payInstallmentFromAccount(@PathVariable long cid, @PathVariable long iid,
                                            @RequestParam long accountId, Authentication authentication) {
        boolean owns = creditService.getCreditsByUsername(authentication.getName()).stream()
                .anyMatch(c -> c.getId() == cid);
        if (!owns) {
            throw new CreditNotFoundException("Кредит с id=" + cid + " не е намерен!");
        }
        creditService.payInstallmentFromAccount(cid, iid, accountId);
        return "redirect:/my/credits/" + cid + "?paid";
    }

    // ── Транзакции по собствените сметки (теглене + превод) ─────────────────

    @GetMapping("/accounts/{id}/transactions")
    public String transactions(@PathVariable long id, Authentication authentication, Model model) {
        AccountDTO account = ownAccountOrThrow(id, authentication.getName());
        model.addAttribute("account", account);
        model.addAttribute("transactions", transactionService.getByAccount(id));
        return "my/transactions";
    }

    @PostMapping("/accounts/{id}/withdraw")
    public String withdraw(@PathVariable long id, @RequestParam BigDecimal amount,
                           Authentication authentication) {
        ownAccountOrThrow(id, authentication.getName());
        transactionService.withdraw(id, amount);
        return "redirect:/my/accounts/" + id + "/transactions?withdrawn";
    }

    @PostMapping("/accounts/{id}/transfer")
    public String transfer(@PathVariable long id, @RequestParam String toIban,
                           @RequestParam BigDecimal amount, Authentication authentication) {
        ownAccountOrThrow(id, authentication.getName());
        transactionService.transfer(id, toIban, amount);
        return "redirect:/my/accounts/" + id + "/transactions?transferred";
    }

    private AccountDTO ownAccountOrThrow(long id, String username) {
        return accountService.getAccountsByUsername(username).stream()
                .filter(a -> a.getId() == id)
                .findFirst()
                .orElseThrow(() -> new AccountNotFoundException("Сметка с id=" + id + " не е намерена!"));
    }
}

