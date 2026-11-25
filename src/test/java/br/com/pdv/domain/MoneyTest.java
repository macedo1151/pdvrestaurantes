package br.com.pdv.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    @Test
    void ratearDistribuiOsCentavosQueSobram() {
        List<BigDecimal> partes = Money.ratear(new BigDecimal("100.00"), 3);

        assertThat(partes).containsExactly(
                BigDecimal.valueOf(3334, 2), BigDecimal.valueOf(3333, 2), BigDecimal.valueOf(3333, 2));
    }

    @Test
    void ratearSempreSomaExatamenteOTotal() {
        Random random = new Random(42);
        for (int i = 0; i < 500; i++) {
            BigDecimal total = BigDecimal.valueOf(random.nextInt(1_000_000), 2);
            int pessoas = 1 + random.nextInt(20);

            List<BigDecimal> partes = Money.ratear(total, pessoas);

            assertThat(partes.stream().reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo(total);
            BigDecimal maior = partes.stream().max(BigDecimal::compareTo).orElseThrow();
            BigDecimal menor = partes.stream().min(BigDecimal::compareTo).orElseThrow();
            assertThat(maior.subtract(menor)).isLessThanOrEqualTo(new BigDecimal("0.01"));
        }
    }

    @Test
    void ratearRecusaZeroPartes() {
        assertThatThrownBy(() -> Money.ratear(BigDecimal.TEN, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void percentualArredondaParaCentavo() {
        assertThat(Money.percentual(new BigDecimal("33.33"), new BigDecimal("10"))).isEqualByComparingTo("3.33");
        assertThat(Money.percentual(new BigDecimal("33.35"), new BigDecimal("10"))).isEqualByComparingTo("3.34");
    }
}
