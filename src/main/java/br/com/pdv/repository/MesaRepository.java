package br.com.pdv.repository;

import br.com.pdv.domain.Mesa;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MesaRepository extends JpaRepository<Mesa, Long> {
    boolean existsByNumero(int numero);
}
