package br.com.pdv.service;

import br.com.pdv.domain.Comanda;
import br.com.pdv.domain.ItemComanda;
import br.com.pdv.domain.Money;
import br.com.pdv.domain.StatusComanda;
import br.com.pdv.dto.Dtos.ProdutoVendidoDto;
import br.com.pdv.dto.Dtos.VendasDto;
import br.com.pdv.repository.ComandaRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class RelatorioService {

    private final ComandaRepository comandas;

    private final ZoneId zona;

    /** O fuso do restaurante vem da configuração: no contêiner o fuso do sistema costuma ser UTC. */
    public RelatorioService(ComandaRepository comandas, @Value("${pdv.fuso:America/Sao_Paulo}") ZoneId zona) {
        this.comandas = comandas;
        this.zona = zona;
    }

    /** Vendas das comandas fechadas entre as datas (ambas inclusive). */
    @Transactional(readOnly = true)
    public VendasDto vendas(LocalDate de, LocalDate ate) {
        if (ate.isBefore(de)) {
            throw new RegraDeNegocioException("A data final é anterior à inicial");
        }
        List<Comanda> fechadas = comandas.findFechadasNoPeriodo(StatusComanda.FECHADA,
                de.atStartOfDay(zona).toInstant(), ate.plusDays(1).atStartOfDay(zona).toInstant());

        BigDecimal faturamento = BigDecimal.ZERO;
        BigDecimal servico = BigDecimal.ZERO;
        BigDecimal custo = BigDecimal.ZERO;
        Map<String, long[]> qtdPorProduto = new LinkedHashMap<>();
        Map<String, BigDecimal[]> valoresPorProduto = new LinkedHashMap<>();

        for (Comanda c : fechadas) {
            faturamento = faturamento.add(c.subtotal());
            servico = servico.add(c.servico());
            for (ItemComanda i : c.itensAtivos()) {
                custo = custo.add(i.custoTotal());
                String nome = i.getProduto().getNome();
                qtdPorProduto.computeIfAbsent(nome, k -> new long[1])[0] += i.getQuantidade();
                BigDecimal[] v = valoresPorProduto.computeIfAbsent(nome, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
                v[0] = v[0].add(i.total());
                v[1] = v[1].add(i.custoTotal());
            }
        }

        List<ProdutoVendidoDto> ranking = qtdPorProduto.entrySet().stream()
                .map(e -> new ProdutoVendidoDto(e.getKey(), e.getValue()[0],
                        Money.of(valoresPorProduto.get(e.getKey())[0]), Money.of(valoresPorProduto.get(e.getKey())[1])))
                .sorted(Comparator.comparingLong(ProdutoVendidoDto::quantidade).reversed())
                .toList();

        BigDecimal ticket = fechadas.isEmpty() ? Money.ZERO
                : Money.of(faturamento.divide(BigDecimal.valueOf(fechadas.size()), 4, RoundingMode.HALF_UP));
        BigDecimal margem = faturamento.signum() == 0 ? BigDecimal.ZERO
                : faturamento.subtract(custo).multiply(BigDecimal.valueOf(100))
                .divide(faturamento, 1, RoundingMode.HALF_UP);

        return new VendasDto(de, ate, fechadas.size(), Money.of(faturamento), Money.of(servico), Money.of(custo),
                margem, ticket, ranking);
    }
}
