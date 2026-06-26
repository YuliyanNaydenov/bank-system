package java_project_yn.bank_system.data.repo;

import java_project_yn.bank_system.data.entity.Client;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ClientRepository extends JpaRepository<Client, Long> {
    Optional<Client> findByUsername(String username);

    @Query("select count(c) > 0 from IndividualClient c where c.egn = :egn")
    boolean existsByEgn(@Param("egn") String egn);

    @Query("select count(c) > 0 from CompanyClient c where c.eik = :eik")
    boolean existsByEik(@Param("eik") String eik);
}
