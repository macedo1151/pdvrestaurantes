package br.com.pdv.domain;

import org.junit.jupiter.api.Test;

import static br.com.pdv.domain.StatusItem.*;
import static org.assertj.core.api.Assertions.assertThat;

class StatusItemTest {

    @Test
    void soAvancaUmPassoPorVez() {
        assertThat(PENDENTE.podeAvancarPara(EM_PREPARO)).isTrue();
        assertThat(PENDENTE.podeAvancarPara(PRONTO)).isFalse();
        assertThat(EM_PREPARO.podeAvancarPara(PRONTO)).isTrue();
        assertThat(PRONTO.podeAvancarPara(ENTREGUE)).isTrue();
    }

    @Test
    void naoVoltaNemSaiDeEstadoFinal() {
        assertThat(PRONTO.podeAvancarPara(PENDENTE)).isFalse();
        assertThat(ENTREGUE.podeAvancarPara(PRONTO)).isFalse();
        assertThat(CANCELADO.podeAvancarPara(EM_PREPARO)).isFalse();
    }
}
