package java_project_yn.bank_system.web.view;

import java_project_yn.bank_system.service.AccountService;
import java_project_yn.bank_system.service.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@Controller
@RequestMapping("/accounts")
@RequiredArgsConstructor
public class TransactionViewController {

    private final AccountService accountService;
    private final TransactionService transactionService;

    @GetMapping("/{id}/transactions")
    public String history(@PathVariable long id, Model model) {
        model.addAttribute("account", accountService.getAccountById(id));
        model.addAttribute("transactions", transactionService.getByAccount(id));
        model.addAttribute("allAccounts", accountService.getAllAccounts());
        return "accounts/transactions";
    }

    @PostMapping("/{id}/deposit")
    public String deposit(@PathVariable long id, @RequestParam BigDecimal amount) {
        transactionService.deposit(id, amount);
        return "redirect:/accounts/" + id + "/transactions?deposited";
    }

    @PostMapping("/{id}/withdraw")
    public String withdraw(@PathVariable long id, @RequestParam BigDecimal amount) {
        transactionService.withdraw(id, amount);
        return "redirect:/accounts/" + id + "/transactions?withdrawn";
    }

    @PostMapping("/{id}/transfer")
    public String transfer(@PathVariable long id,
                           @RequestParam String toIban,
                           @RequestParam BigDecimal amount) {
        transactionService.transfer(id, toIban, amount);
        return "redirect:/accounts/" + id + "/transactions?transferred";
    }
}
