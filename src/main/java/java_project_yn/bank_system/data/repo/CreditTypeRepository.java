package java_project_yn.bank_system.data.repo;

import java_project_yn.bank_system.data.entity.CreditType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CreditTypeRepository extends JpaRepository<CreditType, Long> {
    Optional<CreditType> findByName(String name);
    boolean existsByName(String name);
}
