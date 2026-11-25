package br.com.pdv.repository;

import br.com.pdv.domain.Insumo;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface InsumoRepository extends JpaRepository<Insumo, Long> {

    boolean existsByNomeIgnoreCase(String nome);

    /** SELECT ... FOR UPDATE: dois pedidos simultâneos do mesmo insumo são atendidos um de cada vez. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Insumo i where i.id = :id")
    Optional<Insumo> findByIdForUpdate(@Param("id") Long id);

    @Query("select i from Insumo i where i.estoqueAtual <= i.estoqueMinimo order by i.nome")
    List<Insumo> findAbaixoDoMinimo();
}
