package br.com.pdv;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiErrosTest {

    @Autowired MockMvc mvc;

    @Test
    void recursoInexistenteDevolve404ComProblemDetail() throws Exception {
        mvc.perform(get("/api/comandas/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Comanda 999999 não encontrado(a)"));
    }

    @Test
    void validacaoDevolve400ComOsCamposInvalidos() throws Exception {
        mvc.perform(post("/api/mesas").contentType(MediaType.APPLICATION_JSON).content("{\"numero\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.numero").exists());
    }

    @Test
    void jsonMalformadoDevolve400() throws Exception {
        mvc.perform(post("/api/mesas").contentType(MediaType.APPLICATION_JSON).content("{nao é json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void relatorioComIntervaloInvertidoDevolve409() throws Exception {
        mvc.perform(get("/api/relatorios/vendas?de=2026-02-10&ate=2026-02-01"))
                .andExpect(status().isConflict());
    }

    @Test
    void relatorioVazioFunciona() throws Exception {
        mvc.perform(get("/api/relatorios/vendas?de=2020-01-01&ate=2020-01-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comandas").value(0));
    }
}
