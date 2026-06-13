package java_project_yn.bank_system.web.api;

import jakarta.validation.Valid;
import java_project_yn.bank_system.dto.CreateCreditTypeDTO;
import java_project_yn.bank_system.dto.CreditTypeDTO;
import java_project_yn.bank_system.service.CreditTypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/credit-types")
public class CreditTypeApiController {

    private final CreditTypeService creditTypeService;

    @GetMapping
    public List<CreditTypeDTO> getAll() {
        return creditTypeService.getAllCreditTypes();
    }

    @GetMapping("/{id}")
    public CreditTypeDTO getById(@PathVariable long id) {
        return creditTypeService.getCreditTypeById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreditTypeDTO create(@Valid @RequestBody CreateCreditTypeDTO dto) {
        return creditTypeService.createCreditType(dto);
    }

    @PutMapping("/{id}")
    public CreditTypeDTO update(@PathVariable long id, @Valid @RequestBody CreateCreditTypeDTO dto) {
        return creditTypeService.updateCreditType(id, dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        creditTypeService.deleteCreditType(id);
    }
}
