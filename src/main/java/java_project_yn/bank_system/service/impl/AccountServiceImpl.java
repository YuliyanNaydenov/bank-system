package java_project_yn.bank_system.service.impl;

import java_project_yn.bank_system.data.entity.Account;
import java_project_yn.bank_system.data.entity.AccountStatus;
import java_project_yn.bank_system.data.entity.Client;
import java_project_yn.bank_system.data.repo.AccountRepository;
import java_project_yn.bank_system.data.repo.ClientRepository;
import java_project_yn.bank_system.dto.AccountDTO;
import java_project_yn.bank_system.dto.CreateAccountDTO;
import java_project_yn.bank_system.exception.AccountNotFoundException;
import java_project_yn.bank_system.exception.BusinessRuleException;
import java_project_yn.bank_system.exception.ClientNotFoundException;
import java_project_yn.bank_system.service.AccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AccountServiceImpl implements AccountService {

    private final AccountRepository accountRepository;
    private final ClientRepository clientRepository;

    @Override
    @PreAuthorize("hasAnyAuthority('admin', 'employee')")
    public List<AccountDTO> getAllAccounts() {
        return accountRepository.findAll().stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    @PreAuthorize("isAuthenticated()")
    public AccountDTO getAccountById(long id) {
        return toDto(getEntity(id));
    }

    @Override
    @PreAuthorize("hasAnyAuthority('admin', 'employee')")
    public List<AccountDTO> getAccountsByClient(long clientId) {
        return accountRepository.findByOwner_Id(clientId).stream()
                .map(this::toDto).collect(Collectors.toList());
    }

    @Override
    @PreAuthorize("isAuthenticated()")
    public List<AccountDTO> getAccountsByUsername(String username) {
        return accountRepository.findByOwner_Username(username).stream()
                .map(this::toDto).collect(Collectors.toList());
    }

    @Override
    @PreAuthorize("hasAnyAuthority('admin', 'employee')")
    public AccountDTO openAccount(CreateAccountDTO dto) {
        Client owner = clientRepository.findById(dto.getOwnerId())
                .orElseThrow(() -> new ClientNotFoundException(
                        "Клиент с id=" + dto.getOwnerId() + " не е намерен!"));

        BigDecimal initial = dto.getInitialBalance() == null ? BigDecimal.ZERO : dto.getInitialBalance();
        if (initial.signum() < 0) {
            throw new BusinessRuleException("Началната наличност не може да е отрицателна!");
        }

        Account account = Account.builder()
                .iban(generateUniqueIban())
                .balance(initial)
                .status(AccountStatus.ACTIVE)
                .owner(owner)
                .build();
        return toDto(accountRepository.save(account));
    }

    @Override
    @PreAuthorize("hasAnyAuthority('admin', 'employee')")
    public AccountDTO closeAccount(long id) {
        Account account = getEntity(id);
        if (account.getStatus() == AccountStatus.CLOSED) {
            throw new BusinessRuleException("Сметката вече е закрита!");
        }
        if (account.getBalance().signum() != 0) {
            throw new BusinessRuleException("Сметка с ненулева наличност не може да бъде закрита!");
        }
        account.setStatus(AccountStatus.CLOSED);
        return toDto(accountRepository.save(account));
    }

    @Override
    @PreAuthorize("hasAuthority('admin')")
    public void deleteAccount(long id) {
        if (!accountRepository.existsById(id)) {
            throw new AccountNotFoundException("Сметка с id=" + id + " не е намерена!");
        }
        accountRepository.deleteById(id);
    }

    // ── Помощни методи ──────────────────────────────────────────────────────

    private Account getEntity(long id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new AccountNotFoundException("Сметка с id=" + id + " не е намерена!"));
    }

    /**
     * Генерира псевдо български IBAN (BG + 2 контролни + BANK + 14 цифри).
     * За учебни цели стойностите са произволни, но уникални в рамките на БД.
     */
    private String generateUniqueIban() {
        String iban;
        do {
            StringBuilder digits = new StringBuilder();
            for (int i = 0; i < 16; i++) {
                digits.append(ThreadLocalRandom.current().nextInt(10));
            }
            iban = "BG" + digits.substring(0, 2) + "BANK" + digits.substring(2);
        } while (accountRepository.existsByIban(iban));
        return iban;
    }

    private AccountDTO toDto(Account account) {
        return AccountDTO.builder()
                .id(account.getId())
                .iban(account.getIban())
                .balance(account.getBalance())
                .status(account.getStatus().name())
                .ownerId(account.getOwner().getId())
                .ownerName(account.getOwner().getDisplayName())
                .build();
    }
}
