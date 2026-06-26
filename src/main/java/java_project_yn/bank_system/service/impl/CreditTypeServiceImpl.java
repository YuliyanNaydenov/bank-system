package java_project_yn.bank_system.service.impl;

import java_project_yn.bank_system.data.entity.CreditType;
import java_project_yn.bank_system.data.repo.CreditRepository;
import java_project_yn.bank_system.data.repo.CreditTypeRepository;
import java_project_yn.bank_system.dto.CreateCreditTypeDTO;
import java_project_yn.bank_system.dto.CreditTypeDTO;
import java_project_yn.bank_system.exception.BusinessRuleException;
import java_project_yn.bank_system.exception.CreditTypeNotFoundException;
import java_project_yn.bank_system.exception.DuplicateEntityException;
import java_project_yn.bank_system.service.AuditService;
import java_project_yn.bank_system.service.CreditTypeService;
import java_project_yn.bank_system.util.MapperUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Управление на видовете кредит. Параметрите (лихва, макс. сума, макс. срок)
 * се конфигурират от администратор.
 */
@Service
@RequiredArgsConstructor
public class CreditTypeServiceImpl implements CreditTypeService {

    private final CreditTypeRepository creditTypeRepository;
    private final CreditRepository creditRepository;
    private final MapperUtil mapperUtil;
    private final AuditService auditService;

    @Override
    @PreAuthorize("isAuthenticated()")
    public List<CreditTypeDTO> getAllCreditTypes() {
        return mapperUtil.mapList(creditTypeRepository.findAll(), CreditTypeDTO.class);
    }

    @Override
    @PreAuthorize("isAuthenticated()")
    public CreditTypeDTO getCreditTypeById(long id) {
        return mapperUtil.getModelMapper().map(getEntity(id), CreditTypeDTO.class);
    }

    @Override
    @PreAuthorize("hasAuthority('admin')")
    public CreditTypeDTO createCreditType(CreateCreditTypeDTO dto) {
        if (creditTypeRepository.existsByName(dto.getName())) {
            throw new DuplicateEntityException("Кредитен вид '" + dto.getName() + "' вече съществува!");
        }
        CreditType type = CreditType.builder()
                .name(dto.getName())
                .annualInterestRate(dto.getAnnualInterestRate())
                .maxAmount(dto.getMaxAmount())
                .maxTermMonths(dto.getMaxTermMonths())
                .build();
        CreditTypeDTO saved = mapperUtil.getModelMapper().map(creditTypeRepository.save(type), CreditTypeDTO.class);
        auditService.log("Нов кредитен вид", saved.getName());
        return saved;
    }

    @Override
    @PreAuthorize("hasAuthority('admin')")
    public CreditTypeDTO updateCreditType(long id, CreateCreditTypeDTO dto) {
        CreditType type = getEntity(id);
        creditTypeRepository.findByName(dto.getName()).ifPresent(existing -> {
            if (existing.getId() != id) {
                throw new DuplicateEntityException("Кредитен вид '" + dto.getName() + "' вече съществува!");
            }
        });
        type.setName(dto.getName());
        type.setAnnualInterestRate(dto.getAnnualInterestRate());
        type.setMaxAmount(dto.getMaxAmount());
        type.setMaxTermMonths(dto.getMaxTermMonths());
        CreditTypeDTO saved = mapperUtil.getModelMapper().map(creditTypeRepository.save(type), CreditTypeDTO.class);
        auditService.log("Редактиран кредитен вид", saved.getName());
        return saved;
    }

    @Override
    @PreAuthorize("hasAuthority('admin')")
    public void deleteCreditType(long id) {
        if (!creditTypeRepository.existsById(id)) {
            throw new CreditTypeNotFoundException("Кредитен вид с id=" + id + " не е намерен!");
        }
        if (creditRepository.existsByCreditType_Id(id)) {
            throw new BusinessRuleException("Кредитен вид с отпуснати кредити не може да бъде изтрит!");
        }
        creditTypeRepository.deleteById(id);
        auditService.log("Изтрит кредитен вид", "id=" + id);
    }

    private CreditType getEntity(long id) {
        return creditTypeRepository.findById(id)
                .orElseThrow(() -> new CreditTypeNotFoundException("Кредитен вид с id=" + id + " не е намерен!"));
    }
}
