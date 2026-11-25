package br.com.pdv.repository;

import br.com.pdv.domain.ItemComanda;
import br.com.pdv.domain.Praca;
import br.com.pdv.domain.StatusItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ItemComandaRepository extends JpaRepository<ItemComanda, Long> {

    @Query("""
            select i from ItemComanda i
            join fetch i.comanda c
            join fetch c.mesa
            join fetch i.produto
            where i.praca = :praca and i.status in :status
            order by i.criadoEm
            """)
    List<ItemComanda> fila(@Param("praca") Praca praca, @Param("status") Collection<StatusItem> status);
}
