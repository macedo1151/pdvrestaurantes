package br.com.pdv.repository;

import br.com.pdv.domain.Caixa;
import br.com.pdv.domain.StatusCaixa;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CaixaRepository extends JpaRepository<Caixa, Long> {
    Optional<Caixa> findFirstByStatus(StatusCaixa status);
}
