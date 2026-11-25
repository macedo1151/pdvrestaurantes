package br.com.pdv;

import br.com.pdv.domain.Praca;
import br.com.pdv.domain.StatusItem;
import br.com.pdv.dto.Dtos.*;
import br.com.pdv.service.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** O KDS recebe a mudança da fila na hora (SSE), sem esperar o polling. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class KdsEventosTest {

    @LocalServerPort int porta;
    @Autowired EstoqueService estoque;
    @Autowired CatalogoService catalogo;
    @Autowired ComandaService comandas;
    @Autowired KdsService kds;
    @Autowired JdbcTemplate jdbc;

    private final HttpClient http = HttpClient.newHttpClient();
    private final BlockingQueue<String> linhas = new LinkedBlockingQueue<>();
    private Thread leitor;

    @BeforeEach
    void limpar() {
        for (String tabela : List.of("pagamento", "item_consumo", "item_comanda", "comanda", "caixa",
                "movimento_estoque", "item_ficha", "produto", "insumo", "mesa")) {
            jdbc.update("delete from " + tabela);
        }
    }

    @AfterEach
    void fechar() {
        if (leitor != null) {
            leitor.interrupt();
        }
    }

    @Test
    void cozinhaRecebeEventoQuandoUmItemEntraEQuandoMudaDeStatus() throws Exception {
        InsumoDto pao = insumo("Pão", "10");
        ProdutoDto burger = produto("Burger", Praca.COZINHA, pao);
        ComandaDto c = novaComanda(1);
        assinar("cozinha", Praca.COZINHA);

        c = comandas.adicionarItem(c.id(), new NovoItemRequest(burger.id(), 1, null));
        assertThat(esperarEvento("fila")).as("evento ao lançar o item").isTrue();

        kds.mudarStatus(c.itens().get(0).id(), StatusItem.EM_PREPARO);
        assertThat(esperarEvento("fila")).as("evento ao iniciar o preparo").isTrue();

        comandas.cancelarItem(c.id(), c.itens().get(0).id());
        assertThat(esperarEvento("fila")).as("evento ao cancelar").isTrue();
    }

    @Test
    void eventoDeOutraPracaNaoChegaNaAssinatura() throws Exception {
        InsumoDto cerveja = insumo("Cerveja", "10");
        ProdutoDto chopp = produto("Chopp", Praca.BAR, cerveja);
        ComandaDto c = novaComanda(1);
        assinar("cozinha", Praca.COZINHA);

        comandas.adicionarItem(c.id(), new NovoItemRequest(chopp.id(), 1, null));

        assertThat(esperarEvento("fila")).as("a cozinha não é avisada de item do bar").isFalse();
    }

    @Test
    void pedidoRecusadoNaoGeraEvento() throws Exception {
        InsumoDto pao = insumo("Pão", "1");
        ProdutoDto burger = produto("Burger", Praca.COZINHA, pao);
        ComandaDto c = novaComanda(1);
        Long id = c.id();
        assinar("cozinha", Praca.COZINHA);

        // pede 5 com 1 em estoque: a transação desfaz e ninguém deve ser avisado de uma fila que não mudou
        assertThatThrownBy(() -> comandas.adicionarItem(id, new NovoItemRequest(burger.id(), 5, null)))
                .isInstanceOf(EstoqueInsuficienteException.class);

        assertThat(esperarEvento("fila")).isFalse();
    }

    @Test
    void streamExigeLoginEPerfilAdequado() throws Exception {
        HttpResponse<Void> semLogin = http.send(HttpRequest.newBuilder(URI.create(url(Praca.COZINHA))).build(),
                HttpResponse.BodyHandlers.discarding());
        assertThat(semLogin.statusCode()).isEqualTo(401);

        HttpResponse<Void> caixa = http.send(HttpRequest.newBuilder(URI.create(url(Praca.COZINHA)))
                .header("Authorization", basic("caixa")).build(), HttpResponse.BodyHandlers.discarding());
        assertThat(caixa.statusCode()).isEqualTo(403);
    }

    // ---------- helpers ----------

    private String url(Praca praca) {
        return "http://localhost:" + porta + "/api/kds/" + praca + "/eventos";
    }

    private String basic(String usuario) {
        return "Basic " + Base64.getEncoder().encodeToString((usuario + ":" + usuario + "123").getBytes());
    }

    /** Abre o stream e só retorna depois de o servidor confirmar a assinatura (evento "conectado"). */
    private void assinar(String usuario, Praca praca) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url(praca)))
                .header("Authorization", basic(usuario)).header("Accept", "text/event-stream").build();
        HttpResponse<java.util.stream.Stream<String>> resp = http.send(req, HttpResponse.BodyHandlers.ofLines());
        assertThat(resp.statusCode()).isEqualTo(200);
        leitor = new Thread(() -> {
            try {
                resp.body().forEach(linhas::add);
            } catch (RuntimeException encerrado) {
                // o teste terminou e a conexão foi fechada
            }
        });
        leitor.setDaemon(true);
        leitor.start();
        assertThat(esperarEvento("conectado")).as("servidor confirmou a assinatura").isTrue();
    }

    private boolean esperarEvento(String nome) throws InterruptedException {
        long fim = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (System.nanoTime() < fim) {
            String linha = linhas.poll(100, TimeUnit.MILLISECONDS);
            if (linha != null && linha.replace(" ", "").equals("event:" + nome)) {
                return true;
            }
        }
        return false;
    }

    private InsumoDto insumo(String nome, String qtd) {
        return estoque.criar(new InsumoRequest(nome, "un", new BigDecimal(qtd), BigDecimal.ONE, new BigDecimal("2.00")));
    }

    private ProdutoDto produto(String nome, Praca praca, InsumoDto insumo) {
        return catalogo.criar(new ProdutoRequest(nome, "Geral", new BigDecimal("30.00"), praca,
                List.of(new FichaLinhaRequest(insumo.id(), BigDecimal.ONE))));
    }

    private ComandaDto novaComanda(int numeroMesa) {
        MesaDto mesa = catalogo.criarMesa(new MesaRequest(numeroMesa));
        return comandas.abrir(new AbrirComandaRequest(mesa.id(), null));
    }
}
