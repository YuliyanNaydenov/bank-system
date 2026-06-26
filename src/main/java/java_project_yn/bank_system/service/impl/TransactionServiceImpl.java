package java_project_yn.bank_system.service.impl;

import java_project_yn.bank_system.data.entity.Account;
import java_project_yn.bank_system.data.entity.AccountStatus;
import java_project_yn.bank_system.data.entity.Transaction;
import java_project_yn.bank_system.data.entity.TransactionType;
import java_project_yn.bank_system.data.repo.AccountRepository;
import java_project_yn.bank_system.data.repo.TransactionRepository;
import java_project_yn.bank_system.dto.TransactionDTO;
import java_project_yn.bank_system.exception.AccountNotFoundException;
import java_project_yn.bank_system.exception.BusinessRuleException;
import java_project_yn.bank_system.service.AuditService;
import java_project_yn.bank_system.service.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TransactionServiceImpl implements TransactionService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final AuditService auditService;

    @Override
    @Transactional
    @PreAuthorize("hasAnyAuthority('admin', 'employee')")
    public TransactionDTO deposit(long accountId, BigDecimal amount) {
        Account account = getActiveAccount(accountId);
        requirePositive(amount);

        account.setBalance(account.getBalance().add(amount));
        accountRepository.save(account);

        TransactionDTO dto = toDto(record(account, TransactionType.DEPOSIT, amount, null, "Депозит"));
        auditService.log("Депозит", amount + " € по сметка " + account.getIban());
        return dto;
    }

    @Override
    @Transactional
    @PreAuthorize("hasAnyAuthority('admin', 'employee', 'client')")
    public TransactionDTO withdraw(long accountId, BigDecimal amount) {
        Account account = getActiveAccount(accountId);
        ensureCanOperate(account);
        requirePositive(amount);
        requireSufficientFunds(account, amount);

        account.setBalance(account.getBalance().subtract(amount));
        accountRepository.save(account);

        TransactionDTO dto = toDto(record(account, TransactionType.WITHDRAWAL, amount, null, "Теглене"));
        auditService.log("Теглене", amount + " € от сметка " + account.getIban());
        return dto;
    }

    @Override
    @Transactional
    @PreAuthorize("hasAnyAuthority('admin', 'employee', 'client')")
    public void transfer(long fromAccountId, String toIban, BigDecimal amount) {
        Account from = getActiveAccount(fromAccountId);
        ensureCanOperate(from);
        requirePositive(amount);

        Account to = accountRepository.findByIban(toIban.trim())
                .orElseThrow(() -> new AccountNotFoundException("Сметка с IBAN " + toIban + " не е намерена!"));
        if (to.getStatus() != AccountStatus.ACTIVE) {
            throw new BusinessRuleException("Сметката на получателя е закрита!");
        }
        if (from.getId() == to.getId()) {
            throw new BusinessRuleException("Не може да превеждате към същата сметка!");
        }
        requireSufficientFunds(from, amount);

        from.setBalance(from.getBalance().subtract(amount));
        to.setBalance(to.getBalance().add(amount));
        accountRepository.save(from);
        accountRepository.save(to);

        record(from, TransactionType.TRANSFER_OUT, amount, to.getIban(), "Превод към " + to.getIban());
        record(to, TransactionType.TRANSFER_IN, amount, from.getIban(), "Превод от " + from.getIban());
        auditService.log("Превод", amount + " € от " + from.getIban() + " към " + to.getIban());
    }

    @Override
    @PreAuthorize("hasAnyAuthority('admin', 'employee', 'client')")
    public List<TransactionDTO> getByAccount(long accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Сметка с id=" + accountId + " не е намерена!"));
        ensureCanOperate(account);
        return transactionRepository.findByAccount_IdOrderByTimestampDescIdDesc(accountId).stream()
                .map(this::toDto).collect(Collectors.toList());
    }

    @Override
    @PreAuthorize("isAuthenticated()")
    public List<TransactionDTO> getByUsername(String username) {
        return transactionRepository.findByAccount_Owner_UsernameOrderByTimestampDescIdDesc(username).stream()
                .map(this::toDto).collect(Collectors.toList());
    }

    // ── Помощни методи ──────────────────────────────────────────────────────

    /**
     * Ако операцията се извършва от клиент (а не служител), сметката трябва да е негова.
     */
    private void ensureCanOperate(Account account) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            throw new AccessDeniedException("Неоторизиран достъп!");
        }
        boolean staff = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("admin") || a.getAuthority().equals("employee"));
        if (staff) {
            return;
        }
        String username = auth.getName();
        if (account.getOwner() == null || !username.equals(account.getOwner().getUsername())) {
            throw new AccessDeniedException("Нямате достъп до тази сметка!");
        }
    }

    private Account getActiveAccount(long id) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new AccountNotFoundException("Сметка с id=" + id + " не е намерена!"));
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new BusinessRuleException("Операцията не е възможна върху закрита сметка!");
        }
        return account;
    }

    private void requirePositive(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessRuleException("Сумата трябва да е положителна!");
        }
    }

    private void requireSufficientFunds(Account account, BigDecimal amount) {
        if (account.getBalance().compareTo(amount) < 0) {
            throw new BusinessRuleException("Недостатъчна наличност по сметката!");
        }
    }

    private Transaction record(Account account, TransactionType type, BigDecimal amount,
                               String counterpartyIban, String description) {
        Transaction tx = Transaction.builder()
                .account(account)
                .type(type)
                .amount(amount)
                .timestamp(LocalDateTime.now())
                .balanceAfter(account.getBalance())
                .counterpartyIban(counterpartyIban)
                .description(description)
                .build();
        return transactionRepository.save(tx);
    }

    private TransactionDTO toDto(Transaction tx) {
        return TransactionDTO.builder()
                .id(tx.getId())
                .type(tx.getType().name())
                .amount(tx.getAmount())
                .timestamp(tx.getTimestamp())
                .balanceAfter(tx.getBalanceAfter())
                .counterpartyIban(tx.getCounterpartyIban())
                .description(tx.getDescription())
                .accountIban(tx.getAccount().getIban())
                .build();
    }
}
