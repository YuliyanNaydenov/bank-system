package java_project_yn.bank_system.web.api;

import jakarta.validation.Valid;
import java_project_yn.bank_system.dto.AmountDTO;
import java_project_yn.bank_system.dto.TransactionDTO;
import java_project_yn.bank_system.dto.TransferDTO;
import java_project_yn.bank_system.service.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/accounts")
public class TransactionApiController {

    private final TransactionService transactionService;

    @GetMapping("/{id}/transactions")
    public List<TransactionDTO> history(@PathVariable long id) {
        return transactionService.getByAccount(id);
    }

    @PostMapping("/{id}/deposit")
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionDTO deposit(@PathVariable long id, @Valid @RequestBody AmountDTO dto) {
        return transactionService.deposit(id, dto.getAmount());
    }

    @PostMapping("/{id}/withdraw")
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionDTO withdraw(@PathVariable long id, @Valid @RequestBody AmountDTO dto) {
        return transactionService.withdraw(id, dto.getAmount());
    }

    @PostMapping("/{id}/transfer")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void transfer(@PathVariable long id, @Valid @RequestBody TransferDTO dto) {
        transactionService.transfer(id, dto.getToIban(), dto.getAmount());
    }
}
