package br.com.pdv;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Quem pode o quê: cada perfil só alcança as telas e operações do seu trabalho. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SegurancaTest {

    private static final String RELATORIO = "/api/relatorios/vendas?de=2026-01-01&ate=2026-01-31";

    @Autowired MockMvc mvc;

    // ---------- autenticação ----------

    @Test
    void semCredenciaisDevolve401ComDesafioBasic() throws Exception {
        mvc.perform(get("/api/mesas"))
                .andExpect(status().isUnauthorized())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("WWW-Authenticate", org.hamcrest.Matchers.startsWith("Basic")));
    }

    @Test
    void senhaErradaDevolve401() throws Exception {
        mvc.perform(get("/api/mesas").header(HttpHeaders.AUTHORIZATION, "Basic "
                        + java.util.Base64.getEncoder().encodeToString("garcom:errada".getBytes())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void telaEstaticaAbreSemLogin() throws Exception {
        mvc.perform(get("/index.html")).andExpect(status().isOk());
    }

    // ---------- autorização por perfil ----------

    @Test
    void garcomNaoVeRelatorioNemCadastraProduto() throws Exception {
        mvc.perform(como("garcom", get(RELATORIO))).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
        mvc.perform(como("garcom", post("/api/produtos")).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void garcomAbreComandaMasNaoRecebePagamentoNemMexeNoCaixa() throws Exception {
        // 404 (mesa inexistente) prova que passou pela autorização e chegou ao serviço
        mvc.perform(como("garcom", post("/api/comandas")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mesaId\":999999}"))
                .andExpect(status().isNotFound());
        mvc.perform(como("garcom", post("/api/comandas/1/pagamentos")).contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(como("garcom", post("/api/comandas/1/fechar"))).andExpect(status().isForbidden());
        mvc.perform(como("garcom", get("/api/caixa/atual"))).andExpect(status().isForbidden());
    }

    @Test
    void cozinhaVeAFilaMasNaoAbreComandaNemPaga() throws Exception {
        mvc.perform(como("cozinha", get("/api/kds/COZINHA"))).andExpect(status().isOk());
        mvc.perform(como("cozinha", post("/api/comandas")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mesaId\":1}"))
                .andExpect(status().isForbidden());
        mvc.perform(como("cozinha", post("/api/comandas/1/pagamentos")).contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(como("cozinha", get(RELATORIO))).andExpect(status().isForbidden());
    }

    @Test
    void caixaRecebeEFechaMasNaoLancaItemNemCancela() throws Exception {
        mvc.perform(como("caixa", get("/api/caixa/atual"))).andExpect(status().isConflict()); // sem caixa aberto
        mvc.perform(como("caixa", post("/api/comandas/1/itens")).contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(como("caixa", delete("/api/comandas/1/itens/1"))).andExpect(status().isForbidden());
        mvc.perform(como("caixa", get(RELATORIO))).andExpect(status().isForbidden());
    }

    @Test
    void gerenteAlcancaTudo() throws Exception {
        mvc.perform(como("gerente", get(RELATORIO))).andExpect(status().isOk());
        mvc.perform(como("gerente", get("/api/insumos"))).andExpect(status().isOk());
        mvc.perform(como("gerente", get("/api/kds/BAR"))).andExpect(status().isOk());
        mvc.perform(como("gerente", post("/api/mesas")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"numero\":0}"))
                .andExpect(status().isBadRequest()); // passou pela autorização e falhou na validação
    }

    // ---------- helpers ----------

    private MockHttpServletRequestBuilder como(String usuario, MockHttpServletRequestBuilder req) {
        HttpHeaders h = new HttpHeaders();
        h.setBasicAuth(usuario, usuario + "123");
        return req.headers(h);
    }
}
