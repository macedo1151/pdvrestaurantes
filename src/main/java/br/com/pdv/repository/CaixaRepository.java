package br.com.pdv.repository;

import br.com.pdv.domain.Caixa;
import br.com.pdv.domain.StatusCaixa;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CaixaRepository extends JpaRepository<Caixa, Long> {
    Optional<Caixa> findFirstByStatus(StatusCaixa status);

    /**
     * Trava exclusiva do caixa, para fechá-lo: espera os pagamentos em andamento terminarem e impede que novos
     * entrem enquanto o resumo é calculado.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Caixa c where c.status = :status")
    Optional<Caixa> findByStatusForUpdate(@Param("status") StatusCaixa status);

    /**
     * Trava compartilhada, para receber pagamento: vários pagamentos seguem juntos (onde o banco suporta), mas
     * nenhum entra enquanto o fechamento do caixa está em curso.
     */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select c from Caixa c where c.status = :status")
    Optional<Caixa> findByStatusForShare(@Param("status") StatusCaixa status);
}
