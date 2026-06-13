package java_project_yn.bank_system.service;

import java_project_yn.bank_system.dto.CreateCreditTypeDTO;
import java_project_yn.bank_system.dto.CreditTypeDTO;

import java.util.List;

public interface CreditTypeService {
    List<CreditTypeDTO> getAllCreditTypes();
    CreditTypeDTO getCreditTypeById(long id);
    CreditTypeDTO createCreditType(CreateCreditTypeDTO dto);
    CreditTypeDTO updateCreditType(long id, CreateCreditTypeDTO dto);
    void deleteCreditType(long id);
}
