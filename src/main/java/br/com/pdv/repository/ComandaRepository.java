package br.com.pdv.repository;

import br.com.pdv.domain.Comanda;
import br.com.pdv.domain.StatusComanda;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ComandaRepository extends JpaRepository<Comanda, Long> {

    List<Comanda> findByStatusOrderByAbertaEmAsc(StatusComanda status);

    /** Trava a comanda para operações que mexem em valores (item, pagamento, fechamento). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Comanda c where c.id = :id")
    Optional<Comanda> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            select distinct c from Comanda c
            join fetch c.itens i
            join fetch i.produto
            where c.status = :status and c.fechadaEm >= :de and c.fechadaEm < :ate
            """)
    List<Comanda> findFechadasNoPeriodo(@Param("status") StatusComanda status,
                                        @Param("de") Instant de, @Param("ate") Instant ate);
}
