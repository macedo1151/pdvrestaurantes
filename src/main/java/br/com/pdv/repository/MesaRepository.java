package br.com.pdv.repository;

import br.com.pdv.domain.Mesa;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface MesaRepository extends JpaRepository<Mesa, Long> {
    boolean existsByNumero(int numero);

    /** Trava a mesa ao abrir comanda: duas requisições simultâneas não passam juntas pela checagem de status. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Mesa m where m.id = :id")
    Optional<Mesa> findByIdForUpdate(@Param("id") Long id);
}
