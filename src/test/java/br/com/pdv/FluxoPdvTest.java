package br.com.pdv;

import br.com.pdv.domain.FormaPagamento;
import br.com.pdv.domain.Praca;
import br.com.pdv.domain.StatusComanda;
import br.com.pdv.domain.StatusItem;
import br.com.pdv.domain.StatusMesa;
import br.com.pdv.dto.Dtos.*;
import br.com.pdv.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class FluxoPdvTest {

    @Autowired EstoqueService estoque;
    @Autowired CatalogoService catalogo;
    @Autowired ComandaService comandas;
    @Autowired KdsService kds;
    @Autowired CaixaService caixa;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void limpar() {
        for (String tabela : List.of("pagamento", "item_comanda", "comanda", "caixa", "movimento_estoque",
                "item_ficha", "produto", "insumo", "mesa")) {
            jdbc.update("delete from " + tabela);
        }
    }

    // ---------- estoque ----------

    @Test
    void pedidoBaixaOEstoquePelaFichaTecnica() {
        InsumoDto pao = insumo("Pão", "10");
        ProdutoDto burger = produto("Burger", "30.00", Praca.COZINHA, ficha(pao, "2"));
        ComandaDto c = novaComanda(1);

        comandas.adicionarItem(c.id(), new NovoItemRequest(burger.id(), 3, null));

        assertThat(saldo(pao)).isEqualByComparingTo("4");
    }

    @Test
    void pedidoSemEstoqueEhRecusadoEOsInsumosNaoSaem() {
        InsumoDto pao = insumo("Pão", "10");
        InsumoDto carne = insumo("Carne", "1");
        ProdutoDto burger = produto("Burger", "30.00", Praca.COZINHA, ficha(pao, "1"), ficha(carne, "1"));
        ComandaDto c = novaComanda(1);

        assertThatThrownBy(() -> comandas.adicionarItem(c.id(), new NovoItemRequest(burger.id(), 2, null)))
                .isInstanceOf(EstoqueInsuficienteException.class)
                .hasMessageContaining("Carne");

        assertThat(saldo(pao)).isEqualByComparingTo("10");
        assertThat(saldo(carne)).isEqualByComparingTo("1");
        assertThat(comandas.obter(c.id()).itens()).isEmpty();
    }

    @Test
    void cancelarItemPendenteDevolveEstoqueEEmPreparoViraPerda() {
        InsumoDto pao = insumo("Pão", "10");
        ProdutoDto burger = produto("Burger", "30.00", Praca.COZINHA, ficha(pao, "1"));
        ComandaDto c = novaComanda(1);
        c = comandas.adicionarItem(c.id(), new NovoItemRequest(burger.id(), 2, null));
        c = comandas.adicionarItem(c.id(), new NovoItemRequest(burger.id(), 1, null));
        Long pendente = c.itens().get(0).id();
        Long emPreparo = c.itens().get(1).id();
        kds.mudarStatus(emPreparo, StatusItem.EM_PREPARO);
        assertThat(saldo(pao)).isEqualByComparingTo("7");

        comandas.cancelarItem(c.id(), pendente);
        assertThat(saldo(pao)).isEqualByComparingTo("9");

        ComandaDto depois = comandas.cancelarItem(c.id(), emPreparo);
        assertThat(saldo(pao)).isEqualByComparingTo("9"); // já foi usado, não volta
        assertThat(depois.total()).isEqualByComparingTo("0");
    }

    @Test
    void pedidosSimultaneosNuncaVendemMaisQueOEstoque() throws Exception {
        InsumoDto pao = insumo("Pão", "10");
        ProdutoDto burger = produto("Burger", "30.00", Praca.COZINHA, ficha(pao, "1"));
        int tentativas = 20;
        List<Long> comandaIds = new ArrayList<>();
        for (int n = 1; n <= tentativas; n++) {
            comandaIds.add(novaComanda(n).id());
        }

        ExecutorService pool = Executors.newFixedThreadPool(tentativas);
        CountDownLatch largada = new CountDownLatch(1);
        AtomicInteger aceitos = new AtomicInteger();
        List<Future<?>> futuros = new ArrayList<>();
        for (Long id : comandaIds) {
            futuros.add(pool.submit(() -> {
                largada.await();
                try {
                    comandas.adicionarItem(id, new NovoItemRequest(burger.id(), 1, null));
                    aceitos.incrementAndGet();
                } catch (RegraDeNegocioException esperado) {
                    // estoque acabou para este pedido
                }
                return null;
            }));
        }
        largada.countDown();
        for (Future<?> f : futuros) {
            f.get(30, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertThat(aceitos.get()).isEqualTo(10);
        assertThat(saldo(pao)).isEqualByComparingTo("0");
    }

    // ---------- conta e pagamento ----------

    @Test
    void fluxoCompletoDaComandaAteOFechamentoDoCaixa() {
        InsumoDto pao = insumo("Pão", "10");
        ProdutoDto burger = produto("Burger", "30.00", Praca.COZINHA, ficha(pao, "1"));
        caixa.abrir(new AbrirCaixaRequest(new BigDecimal("100.00"), "ana"));
        ComandaDto c = novaComanda(1);
        c = comandas.adicionarItem(c.id(), new NovoItemRequest(burger.id(), 2, null));

        // 60,00 de itens + 10% de serviço
        assertThat(c.subtotal()).isEqualByComparingTo("60.00");
        assertThat(c.servico()).isEqualByComparingTo("6.00");
        assertThat(c.total()).isEqualByComparingTo("66.00");

        Long id = c.id();
        assertThatThrownBy(() -> comandas.pagar(id, pagamento(FormaPagamento.PIX, "70.00", null)))
                .isInstanceOf(RegraDeNegocioException.class).hasMessageContaining("maior que o restante");

        comandas.pagar(id, pagamento(FormaPagamento.PIX, "30.00", null));
        assertThatThrownBy(() -> comandas.fechar(id))
                .isInstanceOf(RegraDeNegocioException.class).hasMessageContaining("falta pagar");

        ComandaDto paga = comandas.pagar(id, pagamento(FormaPagamento.DINHEIRO, "36.00", "50.00"));
        assertThat(paga.restante()).isEqualByComparingTo("0");
        assertThat(paga.pagamentos().get(1).troco()).isEqualByComparingTo("14.00");

        assertThatThrownBy(() -> comandas.fechar(id))
                .isInstanceOf(RegraDeNegocioException.class).hasMessageContaining("em produção");
        for (ItemDto item : paga.itens()) {
            kds.mudarStatus(item.id(), StatusItem.EM_PREPARO);
            kds.mudarStatus(item.id(), StatusItem.PRONTO);
            kds.mudarStatus(item.id(), StatusItem.ENTREGUE);
        }

        assertThat(comandas.fechar(id).status()).isEqualTo(StatusComanda.FECHADA);
        assertThat(catalogo.listarMesas().get(0).status()).isEqualTo(StatusMesa.LIVRE);

        // gaveta: 100 de abertura + 36 em dinheiro (o troco já saiu)
        CaixaDto fechado = caixa.fechar(new FecharCaixaRequest(new BigDecimal("135.00")));
        assertThat(fechado.totalRecebido()).isEqualByComparingTo("66.00");
        assertThat(fechado.dinheiroEsperado()).isEqualByComparingTo("136.00");
        assertThat(fechado.diferenca()).isEqualByComparingTo("-1.00");
    }

    @Test
    void naoRecebePagamentoComCaixaFechado() {
        InsumoDto pao = insumo("Pão", "10");
        ProdutoDto burger = produto("Burger", "30.00", Praca.COZINHA, ficha(pao, "1"));
        ComandaDto c = novaComanda(1);
        comandas.adicionarItem(c.id(), new NovoItemRequest(burger.id(), 1, null));

        assertThatThrownBy(() -> comandas.pagar(c.id(), pagamento(FormaPagamento.PIX, "10.00", null)))
                .isInstanceOf(RegraDeNegocioException.class).hasMessageContaining("caixa");
    }

    @Test
    void mesaOcupadaNaoAbreSegundaComanda() {
        ComandaDto c = novaComanda(1);
        MesaDto mesa = catalogo.listarMesas().get(0);

        assertThatThrownBy(() -> comandas.abrir(new AbrirComandaRequest(mesa.id(), null)))
                .isInstanceOf(RegraDeNegocioException.class);
        assertThat(c.mesa()).isEqualTo(1);
    }

    // ---------- divisão ----------

    @Test
    void divisaoIgualNaoPerdeCentavo() {
        InsumoDto pao = insumo("Pão", "10");
        ProdutoDto burger = produto("Burger", "30.00", Praca.COZINHA, ficha(pao, "1"));
        ComandaDto c = novaComanda(1);
        c = comandas.adicionarItem(c.id(), new NovoItemRequest(burger.id(), 3, null)); // 90 + 9 = 99,00

        DivisaoIgualDto d = comandas.dividirIgualmente(c.id(), 4);

        assertThat(d.parcelas()).hasSize(4);
        assertThat(d.parcelas().stream().reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("99.00");
        assertThat(d.parcelas().get(0)).isEqualByComparingTo("24.75");
    }

    @Test
    void divisaoPorItensCobraServicoProporcional() {
        InsumoDto pao = insumo("Pão", "10");
        ProdutoDto burger = produto("Burger", "30.00", Praca.COZINHA, ficha(pao, "1"));
        ProdutoDto suco = produto("Suco", "10.00", Praca.BAR);
        ComandaDto c = novaComanda(1);
        c = comandas.adicionarItem(c.id(), new NovoItemRequest(burger.id(), 1, null));
        c = comandas.adicionarItem(c.id(), new NovoItemRequest(suco.id(), 1, null));
        Long idBurger = c.itens().get(0).id();
        Long idSuco = c.itens().get(1).id();

        DivisaoItensDto d = comandas.dividirPorItens(c.id(), new DivisaoItensRequest(List.of(
                new GrupoItensRequest("Ana", List.of(idBurger)))));

        assertThat(d.partes().get(0).total()).isEqualByComparingTo("33.00");
        assertThat(d.subtotalNaoAtribuido()).isEqualByComparingTo("10.00");

        Long cid = c.id();
        assertThatThrownBy(() -> comandas.dividirPorItens(cid, new DivisaoItensRequest(List.of(
                new GrupoItensRequest("Ana", List.of(idBurger)),
                new GrupoItensRequest("Beto", List.of(idBurger, idSuco))))))
                .isInstanceOf(RegraDeNegocioException.class).hasMessageContaining("mais de uma pessoa");
    }

    // ---------- KDS ----------

    @Test
    void kdsRoteiaPorPracaEImpedePularEtapa() {
        InsumoDto pao = insumo("Pão", "10");
        InsumoDto cerveja = insumo("Cerveja", "10");
        ProdutoDto burger = produto("Burger", "30.00", Praca.COZINHA, ficha(pao, "1"));
        ProdutoDto chopp = produto("Cerveja", "12.00", Praca.BAR, ficha(cerveja, "1"));
        ComandaDto c = novaComanda(1);
        comandas.adicionarItem(c.id(), new NovoItemRequest(burger.id(), 1, "sem cebola"));
        c = comandas.adicionarItem(c.id(), new NovoItemRequest(chopp.id(), 1, null));

        assertThat(kds.fila(Praca.COZINHA)).extracting(KdsItemDto::produto).containsExactly("Burger");
        assertThat(kds.fila(Praca.BAR)).extracting(KdsItemDto::produto).containsExactly("Cerveja");
        assertThat(kds.fila(Praca.COZINHA).get(0).observacao()).isEqualTo("sem cebola");

        Long itemBar = kds.fila(Praca.BAR).get(0).itemId();
        assertThatThrownBy(() -> kds.mudarStatus(itemBar, StatusItem.PRONTO))
                .isInstanceOf(RegraDeNegocioException.class);

        kds.mudarStatus(itemBar, StatusItem.EM_PREPARO);
        kds.mudarStatus(itemBar, StatusItem.PRONTO);
        assertThat(kds.fila(Praca.BAR)).isEmpty();
    }

    // ---------- helpers ----------

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

    private PagamentoRequest pagamento(FormaPagamento forma, String valor, String recebido) {
        return new PagamentoRequest(forma, new BigDecimal(valor), recebido == null ? null : new BigDecimal(recebido), null);
    }

    private BigDecimal saldo(InsumoDto insumo) {
        return estoque.listar().stream().filter(i -> i.id().equals(insumo.id())).findFirst().orElseThrow().estoqueAtual();
    }
}
