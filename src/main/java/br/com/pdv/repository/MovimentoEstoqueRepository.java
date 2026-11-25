package br.com.pdv.repository;

import br.com.pdv.domain.MovimentoEstoque;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MovimentoEstoqueRepository extends JpaRepository<MovimentoEstoque, Long> {
    List<MovimentoEstoque> findByInsumoIdOrderByIdDesc(Long insumoId);
}
