package java_project_yn.bank_system.web.api;

import jakarta.validation.Valid;
import java_project_yn.bank_system.dto.AccountDTO;
import java_project_yn.bank_system.dto.CreateAccountDTO;
import java_project_yn.bank_system.service.AccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/accounts")
public class AccountApiController {

    private final AccountService accountService;

    @GetMapping
    public List<AccountDTO> getAll(Authentication authentication) {
        if (isClientOnly(authentication)) {
            return accountService.getAccountsByUsername(authentication.getName());
        }
        return accountService.getAllAccounts();
    }

    @GetMapping("/{id}")
    public AccountDTO getById(@PathVariable long id) {
        return accountService.getAccountById(id);
    }

    @GetMapping("/by-client/{clientId}")
    public List<AccountDTO> getByClient(@PathVariable long clientId) {
        return accountService.getAccountsByClient(clientId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountDTO open(@Valid @RequestBody CreateAccountDTO dto) {
        return accountService.openAccount(dto);
    }

    @PostMapping("/{id}/close")
    public AccountDTO close(@PathVariable long id) {
        return accountService.closeAccount(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        accountService.deleteAccount(id);
    }

    private boolean isClientOnly(Authentication auth) {
        boolean client = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("client"));
        boolean staff = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("admin") || a.getAuthority().equals("employee"));
        return client && !staff;
    }
}
