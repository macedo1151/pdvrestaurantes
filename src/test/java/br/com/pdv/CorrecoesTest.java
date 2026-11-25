package br.com.pdv;

import br.com.pdv.domain.Comanda;
import br.com.pdv.domain.FormaPagamento;
import br.com.pdv.domain.Praca;
import br.com.pdv.domain.StatusItem;
import br.com.pdv.dto.Dtos.*;
import br.com.pdv.repository.ComandaRepository;
import br.com.pdv.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Cada teste reproduz um defeito apontado na revisão; ele falha sem a correção correspondente. */
@SpringBootTest
@ActiveProfiles("test")
class CorrecoesTest {

    @Autowired EstoqueService estoque;
    @Autowired CatalogoService catalogo;
    @Autowired ComandaService comandas;
    @Autowired KdsService kds;
    @Autowired CaixaService caixa;
    @Autowired RelatorioService relatorio;
    @Autowired JdbcTemplate jdbc;
    @Autowired ComandaRepository comandaRepo;
    @Autowired PlatformTransactionManager tm;

    @BeforeEach
    void limpar() {
        for (String tabela : List.of("pagamento", "item_consumo", "item_comanda", "comanda", "caixa",
                "movimento_estoque", "item_ficha", "produto", "insumo", "mesa")) {
            jdbc.update("delete from " + tabela);
        }
    }

    // ---------- KDS x cancelamento ----------

    @Test
    void kdsEsperaATravaDaComandaEItemCanceladoNaoVoltaParaAFila() throws Exception {
        InsumoDto pao = insumo("Pão", "10");
        ProdutoDto burger = produto("Burger", "30.00", Praca.COZINHA, ficha(pao, "1"));
        ComandaDto c = novaComanda(1);
        c = comandas.adicionarItem(c.id(), new NovoItemRequest(burger.id(), 1, null));
        Long comandaId = c.id();
        Long itemId = c.itens().get(0).id();

        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            // o "garçom" segura a comanda travada enquanto cancela o item
            Future<Object> iniciar = new TransactionTemplate(tm).execute(status -> {
                Comanda travada = comandaRepo.findByIdForUpdate(comandaId).orElseThrow();
                Future<Object> f = pool.submit(() -> {
                    try {
                        kds.mudarStatus(itemId, StatusItem.EM_PREPARO);
                        return "iniciou";
                    } catch (RegraDeNegocioException e) {
                        return "recusado";
                    }
                });
                // com a comanda travada, a cozinha precisa esperar; sem trava ela passa direto
                assertThatThrownBy(() -> f.get(700, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
                travada.getItens().get(0).mudarStatus(StatusItem.CANCELADO);
                return f;
            });
            assertThat(iniciar.get(10, TimeUnit.SECONDS)).isEqualTo("recusado");
        } finally {
            pool.shutdownNow();
        }

        assertThat(comandas.obter(comandaId).itens().get(0).status()).isEqualTo(StatusItem.CANCELADO);
        assertThat(kds.fila(Praca.COZINHA)).isEmpty();
    }

    // ---------- trava de mesa e de caixa ----------

    @Test
    void duasRequisicoesSimultaneasNaoAbremDuasComandasNaMesma() throws Exception {
        MesaDto mesa = catalogo.criarMesa(new MesaRequest(1));

        int sucessos = disparar(12, () -> comandas.abrir(new AbrirComandaRequest(mesa.id(), null)));

        assertThat(sucessos).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from comanda where status = 'ABERTA'", Integer.class)).isEqualTo(1);
    }

    @Test
    void duasRequisicoesSimultaneasNaoAbremDoisCaixas() throws Exception {
        int sucessos = disparar(12, () -> caixa.abrir(new AbrirCaixaRequest(new BigDecimal("100.00"), "ana")));

        assertThat(sucessos).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from caixa where status = 'ABERTO'", Integer.class)).isEqualTo(1);
    }

    // ---------- histórico de estoque ----------

    @Test
    void perdaNaoContaEmDobroNoHistorico() {
        InsumoDto pao = insumo("Pão", "10");
        ProdutoDto burger = produto("Burger", "30.00", Praca.COZINHA, ficha(pao, "1"));
        ComandaDto c = novaComanda(1);
        c = comandas.adicionarItem(c.id(), new NovoItemRequest(burger.id(), 2, null));
        Long item = c.itens().get(0).id();
        kds.mudarStatus(item, StatusItem.EM_PREPARO);

        comandas.cancelarItem(c.id(), item);

        assertThat(saldo(pao)).isEqualByComparingTo("8"); // o insumo já foi usado
        assertThat(somaMovimentos(pao)).as("soma do histórico deve bater com o saldo").isEqualByComparingTo("8");
        assertThat(jdbc.queryForObject("select count(*) from movimento_estoque where tipo = 'PERDA'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void estornoUsaOConsumoDoMomentoDoPedidoENaoAFichaAtual() {
        InsumoDto pao = insumo("Pão", "10");
        ProdutoDto burger = produto("Burger", "30.00", Praca.COZINHA, ficha(pao, "1"));
        ComandaDto c = novaComanda(1);
        c = comandas.adicionarItem(c.id(), new NovoItemRequest(burger.id(), 2, null));
        assertThat(saldo(pao)).isEqualByComparingTo("8");

        // a ficha muda depois do pedido
        catalogo.atualizar(burger.id(), new ProdutoRequest("Burger", "Geral", new BigDecimal("30.00"),
                Praca.COZINHA, List.of(ficha(pao, "3"))));
        comandas.cancelarItem(c.id(), c.itens().get(0).id());

        assertThat(saldo(pao)).isEqualByComparingTo("10"); // volta exatamente o que saiu
        assertThat(somaMovimentos(pao)).isEqualByComparingTo("10");
    }

    // ---------- divisão por itens ----------

    @Test
    void divisaoPorItensSomaExatamenteOTotalDaConta() {
        ProdutoDto prato = produto("Prato", "10.05", Praca.COZINHA);
        ComandaDto c = novaComanda(1);
        c = comandas.adicionarItem(c.id(), new NovoItemRequest(prato.id(), 1, null));
        c = comandas.adicionarItem(c.id(), new NovoItemRequest(prato.id(), 1, null));
        assertThat(c.total()).isEqualByComparingTo("22.11");

        DivisaoItensDto d = comandas.dividirPorItens(c.id(), new DivisaoItensRequest(List.of(
                new GrupoItensRequest("Ana", List.of(c.itens().get(0).id())),
                new GrupoItensRequest("Beto", List.of(c.itens().get(1).id())))));

        BigDecimal soma = d.partes().stream().map(ParteDto::total).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(soma).isEqualByComparingTo("22.11");

        // e todas as partes são aceitas no caixa, inclusive a última
        caixa.abrir(new AbrirCaixaRequest(new BigDecimal("50.00"), "ana"));
        Long id = c.id();
        for (ParteDto p : d.partes()) {
            comandas.pagar(id, new PagamentoRequest(FormaPagamento.PIX, p.total(), null, p.pagador()));
        }
        assertThat(comandas.obter(id).restante()).isEqualByComparingTo("0");
    }

    // ---------- relatório x fuso ----------

    @Test
    void relatorioAgrupaPorDiaDeSaoPauloMesmoComJvmEmUtc() {
        TimeZone original = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        try {
            ProdutoDto prato = produto("Prato", "20.00", Praca.BAR);
            caixa.abrir(new AbrirCaixaRequest(new BigDecimal("50.00"), "ana"));
            ComandaDto c = novaComanda(1);
            c = comandas.adicionarItem(c.id(), new NovoItemRequest(prato.id(), 1, null));
            Long item = c.itens().get(0).id();
            kds.mudarStatus(item, StatusItem.EM_PREPARO);
            kds.mudarStatus(item, StatusItem.PRONTO);
            kds.mudarStatus(item, StatusItem.ENTREGUE);
            comandas.pagar(c.id(), new PagamentoRequest(FormaPagamento.PIX, c.total(), null, null));
            comandas.fechar(c.id());

            // 22h30 do dia 9 em São Paulo = 01h30 do dia 10 em UTC
            jdbc.update("update comanda set fechada_em = ? where id = ?",
                    Timestamp.from(Instant.parse("2026-03-10T01:30:00Z")), c.id());

            assertThat(relatorio.vendas(LocalDate.of(2026, 3, 9), LocalDate.of(2026, 3, 9)).comandas()).isEqualTo(1);
            assertThat(relatorio.vendas(LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 10)).comandas()).isZero();
        } finally {
            TimeZone.setDefault(original);
        }
    }

    // ---------- helpers ----------

    /** Dispara N chamadas ao mesmo tempo e devolve quantas terminaram sem erro. */
    private int disparar(int vezes, Runnable acao) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(vezes);
        CountDownLatch largada = new CountDownLatch(1);
        AtomicInteger sucessos = new AtomicInteger();
        List<Future<?>> futuros = new ArrayList<>();
        for (int i = 0; i < vezes; i++) {
            futuros.add(pool.submit(() -> {
                largada.await();
                try {
                    acao.run();
                    sucessos.incrementAndGet();
                } catch (RuntimeException esperado) {
                    // recusada: a outra chegou primeiro
                }
                return null;
            }));
        }
        largada.countDown();
        for (Future<?> f : futuros) {
            f.get(30, TimeUnit.SECONDS);
        }
        pool.shutdown();
        return sucessos.get();
    }

    private InsumoDto insumo(String nome, String qtd) {
        return estoque.criar(new InsumoRequest(nome, "un", new BigDecimal(qtd), BigDecimal.ONE, new BigDecimal("2.00")));
    }

    private FichaLinhaRequest ficha(InsumoDto insumo, String qtd) {
        return new FichaLinhaRequest(insumo.id(), new BigDecimal(qtd));
    }

    private ProdutoDto produto(String nome, String preco, Praca praca, FichaLinhaRequest... ficha) {
        return catalogo.criar(new ProdutoRequest(nome, "Geral", new BigDecimal(preco), praca, List.of(ficha)));
    }

    private ComandaDto novaComanda(int numeroMesa) {
        MesaDto mesa = catalogo.criarMesa(new MesaRequest(numeroMesa));
        return comandas.abrir(new AbrirComandaRequest(mesa.id(), null));
    }

    private BigDecimal saldo(InsumoDto insumo) {
        return estoque.listar().stream().filter(i -> i.id().equals(insumo.id())).findFirst().orElseThrow().estoqueAtual();
    }

    private BigDecimal somaMovimentos(InsumoDto insumo) {
        return jdbc.queryForObject("select coalesce(sum(quantidade), 0) from movimento_estoque where insumo_id = ?",
                BigDecimal.class, insumo.id());
    }
}
