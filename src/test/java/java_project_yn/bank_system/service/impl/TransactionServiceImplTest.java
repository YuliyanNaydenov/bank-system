package java_project_yn.bank_system.service.impl;

import java_project_yn.bank_system.data.entity.Account;
import java_project_yn.bank_system.data.entity.AccountStatus;
import java_project_yn.bank_system.data.entity.IndividualClient;
import java_project_yn.bank_system.data.repo.AccountRepository;
import java_project_yn.bank_system.data.repo.TransactionRepository;
import java_project_yn.bank_system.dto.TransactionDTO;
import java_project_yn.bank_system.exception.AccountNotFoundException;
import java_project_yn.bank_system.exception.BusinessRuleException;
import java_project_yn.bank_system.service.AuditService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TransactionServiceImplTest {

    @Mock private AccountRepository accountRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private AuditService auditService;

    @InjectMocks
    private TransactionServiceImpl transactionService;

    @BeforeEach
    void setUp() {
        // По подразбиране операциите се извършват от служител
        authenticateAs("employee", "employee");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(String username, String authority) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, "x",
                        List.of(new SimpleGrantedAuthority(authority))));
    }

    private Account account(String iban, String ownerUsername, String balance) {
        IndividualClient owner = new IndividualClient();
        owner.setFirstName("Иван");
        owner.setLastName("Петров");
        owner.setEgn("8001011234");
        owner.setUsername(ownerUsername);
        Account a = Account.builder()
                .iban(iban)
                .balance(new BigDecimal(balance))
                .status(AccountStatus.ACTIVE)
                .owner(owner)
                .build();
        a.setId(1L);
        return a;
    }

    @Test
    void deposit_increasesBalance() {
        Account acc = account("BG1", "client1", "100.00");
        given(accountRepository.findById(1L)).willReturn(Optional.of(acc));
        given(transactionRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        TransactionDTO dto = transactionService.deposit(1L, new BigDecimal("50.00"));

        assertEquals(0, acc.getBalance().compareTo(new BigDecimal("150.00")));
        assertEquals("DEPOSIT", dto.getType());
        assertEquals(0, dto.getBalanceAfter().compareTo(new BigDecimal("150.00")));
        verify(accountRepository).save(acc);
    }

    @Test
    void withdraw_insufficientFunds_throws() {
        Account acc = account("BG1", "client1", "30.00");
        given(accountRepository.findById(1L)).willReturn(Optional.of(acc));

        assertThrows(BusinessRuleException.class,
                () -> transactionService.withdraw(1L, new BigDecimal("100.00")));
    }

    @Test
    void withdraw_onClosedAccount_throws() {
        Account acc = account("BG1", "client1", "100.00");
        acc.setStatus(AccountStatus.CLOSED);
        given(accountRepository.findById(1L)).willReturn(Optional.of(acc));

        assertThrows(BusinessRuleException.class,
                () -> transactionService.withdraw(1L, new BigDecimal("10.00")));
    }

    @Test
    void transfer_movesFunds() {
        Account from = account("BG1", "client1", "100.00");
        IndividualClient owner2 = new IndividualClient();
        owner2.setUsername("client2");
        Account to = Account.builder().iban("BG2").balance(new BigDecimal("0.00"))
                .status(AccountStatus.ACTIVE).owner(owner2).build();
        to.setId(2L);

        given(accountRepository.findById(1L)).willReturn(Optional.of(from));
        given(accountRepository.findByIban("BG2")).willReturn(Optional.of(to));
        given(transactionRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        transactionService.transfer(1L, "BG2", new BigDecimal("40.00"));

        assertEquals(0, from.getBalance().compareTo(new BigDecimal("60.00")));
        assertEquals(0, to.getBalance().compareTo(new BigDecimal("40.00")));
    }

    @Test
    void transfer_toMissingIban_throws() {
        Account from = account("BG1", "client1", "100.00");
        given(accountRepository.findById(1L)).willReturn(Optional.of(from));
        given(accountRepository.findByIban("NOPE")).willReturn(Optional.empty());

        assertThrows(AccountNotFoundException.class,
                () -> transactionService.transfer(1L, "NOPE", new BigDecimal("10.00")));
    }

    @Test
    void transfer_insufficientFunds_throws() {
        Account from = account("BG1", "client1", "20.00");
        Account to = Account.builder().iban("BG2").balance(BigDecimal.ZERO)
                .status(AccountStatus.ACTIVE).owner(new IndividualClient()).build();
        to.setId(2L);
        given(accountRepository.findById(1L)).willReturn(Optional.of(from));
        given(accountRepository.findByIban("BG2")).willReturn(Optional.of(to));

        assertThrows(BusinessRuleException.class,
                () -> transactionService.transfer(1L, "BG2", new BigDecimal("50.00")));
    }

    @Test
    void clientCannotOperateOnForeignAccount() {
        // Клиент, който НЕ е собственик на сметката
        authenticateAs("intruder", "client");
        Account acc = account("BG1", "client1", "100.00");
        given(accountRepository.findById(1L)).willReturn(Optional.of(acc));

        assertThrows(AccessDeniedException.class,
                () -> transactionService.withdraw(1L, new BigDecimal("10.00")));
    }

    @Test
    void clientCanOperateOnOwnAccount() {
        authenticateAs("client1", "client");
        Account acc = account("BG1", "client1", "100.00");
        given(accountRepository.findById(1L)).willReturn(Optional.of(acc));
        given(transactionRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        TransactionDTO dto = transactionService.withdraw(1L, new BigDecimal("40.00"));

        assertEquals(0, acc.getBalance().compareTo(new BigDecimal("60.00")));
        assertEquals("WITHDRAWAL", dto.getType());
    }
}
