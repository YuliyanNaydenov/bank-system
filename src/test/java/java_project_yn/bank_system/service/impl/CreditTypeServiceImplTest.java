package java_project_yn.bank_system.service.impl;

import java_project_yn.bank_system.data.entity.CreditType;
import java_project_yn.bank_system.data.repo.CreditRepository;
import java_project_yn.bank_system.data.repo.CreditTypeRepository;
import java_project_yn.bank_system.dto.CreateCreditTypeDTO;
import java_project_yn.bank_system.dto.CreditTypeDTO;
import java_project_yn.bank_system.exception.CreditTypeNotFoundException;
import java_project_yn.bank_system.exception.DuplicateEntityException;
import java_project_yn.bank_system.service.AuditService;
import java_project_yn.bank_system.util.MapperUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class CreditTypeServiceImplTest {

    @Mock
    private CreditTypeRepository creditTypeRepository;

    private CreditTypeServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CreditTypeServiceImpl(creditTypeRepository, mock(CreditRepository.class),
                new MapperUtil(), mock(AuditService.class));
    }

    @Test
    void create_savesNewType() {
        CreateCreditTypeDTO dto = CreateCreditTypeDTO.builder()
                .name("Потребителски").annualInterestRate(new BigDecimal("10.5"))
                .maxAmount(new BigDecimal("50000")).maxTermMonths(84).build();
        given(creditTypeRepository.existsByName("Потребителски")).willReturn(false);
        given(creditTypeRepository.save(any(CreditType.class))).willAnswer(inv -> inv.getArgument(0));

        CreditTypeDTO result = service.createCreditType(dto);

        assertEquals("Потребителски", result.getName());
        assertEquals(84, result.getMaxTermMonths());
    }

    @Test
    void create_duplicateName_throws() {
        CreateCreditTypeDTO dto = CreateCreditTypeDTO.builder()
                .name("Ипотечен").annualInterestRate(new BigDecimal("3.2"))
                .maxAmount(new BigDecimal("500000")).maxTermMonths(360).build();
        given(creditTypeRepository.existsByName("Ипотечен")).willReturn(true);

        assertThrows(DuplicateEntityException.class, () -> service.createCreditType(dto));
    }

    @Test
    void update_success_savesChanges() {
        CreditType existing = CreditType.builder()
                .name("Потребителски").annualInterestRate(new BigDecimal("10.5"))
                .maxAmount(new BigDecimal("50000")).maxTermMonths(84).build();
        existing.setId(5L);
        given(creditTypeRepository.findById(5L)).willReturn(Optional.of(existing));
        given(creditTypeRepository.findByName("Потребителски нов")).willReturn(Optional.empty());
        given(creditTypeRepository.save(any(CreditType.class))).willAnswer(inv -> inv.getArgument(0));

        CreateCreditTypeDTO dto = CreateCreditTypeDTO.builder()
                .name("Потребителски нов").annualInterestRate(new BigDecimal("9.0"))
                .maxAmount(new BigDecimal("60000")).maxTermMonths(90).build();

        CreditTypeDTO result = service.updateCreditType(5L, dto);

        assertEquals("Потребителски нов", result.getName());
        assertEquals(90, result.getMaxTermMonths());
    }

    @Test
    void update_duplicateName_throws() {
        CreditType existing = CreditType.builder().name("Потребителски")
                .annualInterestRate(new BigDecimal("10.5")).maxAmount(new BigDecimal("50000"))
                .maxTermMonths(84).build();
        existing.setId(5L);
        CreditType other = CreditType.builder().name("Ипотечен")
                .annualInterestRate(new BigDecimal("3.2")).maxAmount(new BigDecimal("500000"))
                .maxTermMonths(360).build();
        other.setId(9L);
        given(creditTypeRepository.findById(5L)).willReturn(Optional.of(existing));
        given(creditTypeRepository.findByName("Ипотечен")).willReturn(Optional.of(other));

        CreateCreditTypeDTO dto = CreateCreditTypeDTO.builder()
                .name("Ипотечен").annualInterestRate(new BigDecimal("3.2"))
                .maxAmount(new BigDecimal("500000")).maxTermMonths(360).build();

        assertThrows(DuplicateEntityException.class, () -> service.updateCreditType(5L, dto));
    }

    @Test
    void delete_notFound_throws() {
        given(creditTypeRepository.existsById(99L)).willReturn(false);
        assertThrows(CreditTypeNotFoundException.class, () -> service.deleteCreditType(99L));
    }

    @Test
    void getById_found_returnsDto() {
        CreditType type = CreditType.builder()
                .name("Ипотечен").annualInterestRate(new BigDecimal("3.2"))
                .maxAmount(new BigDecimal("500000")).maxTermMonths(360).build();
        type.setId(5L);
        given(creditTypeRepository.findById(5L)).willReturn(Optional.of(type));

        CreditTypeDTO result = service.getCreditTypeById(5L);

        assertEquals("Ипотечен", result.getName());
        assertEquals(5L, result.getId());
    }
}
