package java_project_yn.bank_system.service.impl;

import java_project_yn.bank_system.data.entity.Account;
import java_project_yn.bank_system.data.entity.AccountStatus;
import java_project_yn.bank_system.data.entity.IndividualClient;
import java_project_yn.bank_system.data.repo.AccountRepository;
import java_project_yn.bank_system.data.repo.ClientRepository;
import java_project_yn.bank_system.dto.AccountDTO;
import java_project_yn.bank_system.dto.CreateAccountDTO;
import java_project_yn.bank_system.exception.BusinessRuleException;
import java_project_yn.bank_system.exception.ClientNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AccountServiceImplTest {

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private ClientRepository clientRepository;

    @InjectMocks
    private AccountServiceImpl accountService;

    private IndividualClient client() {
        IndividualClient c = new IndividualClient();
        c.setFirstName("Иван");
        c.setLastName("Петров");
        c.setEgn("8001011234");
        return c;
    }

    @Test
    void openAccount_generatesIbanAndSaves() {
        CreateAccountDTO dto = CreateAccountDTO.builder()
                .ownerId(1L).initialBalance(new BigDecimal("100.00")).build();
        given(clientRepository.findById(1L)).willReturn(Optional.of(client()));
        given(accountRepository.existsByIban(anyString())).willReturn(false);
        given(accountRepository.save(any(Account.class))).willAnswer(inv -> inv.getArgument(0));

        AccountDTO result = accountService.openAccount(dto);

        assertNotNull(result.getIban());
        assertTrue(result.getIban().startsWith("BG"));
        assertEquals("ACTIVE", result.getStatus());
        assertEquals(0, result.getBalance().compareTo(new BigDecimal("100.00")));
        verify(accountRepository).save(any(Account.class));
    }

    @Test
    void openAccount_clientNotFound_throws() {
        CreateAccountDTO dto = CreateAccountDTO.builder().ownerId(99L).build();
        given(clientRepository.findById(99L)).willReturn(Optional.empty());
        assertThrows(ClientNotFoundException.class, () -> accountService.openAccount(dto));
    }

    @Test
    void closeAccount_withBalance_throws() {
        Account account = Account.builder()
                .iban("BG00BANK0000000000000000")
                .balance(new BigDecimal("50.00"))
                .status(AccountStatus.ACTIVE)
                .owner(client())
                .build();
        given(accountRepository.findById(1L)).willReturn(Optional.of(account));

        assertThrows(BusinessRuleException.class, () -> accountService.closeAccount(1L));
    }

    @Test
    void closeAccount_zeroBalance_setsClosed() {
        Account account = Account.builder()
                .iban("BG00BANK0000000000000000")
                .balance(BigDecimal.ZERO)
                .status(AccountStatus.ACTIVE)
                .owner(client())
                .build();
        given(accountRepository.findById(1L)).willReturn(Optional.of(account));
        given(accountRepository.save(any(Account.class))).willAnswer(inv -> inv.getArgument(0));

        AccountDTO result = accountService.closeAccount(1L);

        assertEquals("CLOSED", result.getStatus());
    }
}
