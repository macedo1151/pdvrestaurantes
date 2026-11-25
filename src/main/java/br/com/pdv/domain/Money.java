package br.com.pdv.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/** Operações monetárias em BigDecimal com 2 casas. Nunca usar double para dinheiro. */
public final class Money {

    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    private Money() {
    }

    public static BigDecimal of(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal percentual(BigDecimal base, BigDecimal percentual) {
        return of(base.multiply(percentual).divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP));
    }

    /**
     * Divide um valor em partes iguais sem perder centavos: as partes diferem no máximo em R$ 0,01
     * e a soma é exatamente o total. Ex.: 100,00 / 3 = [33,34; 33,33; 33,33].
     */
    public static List<BigDecimal> ratear(BigDecimal total, int partes) {
        if (partes < 1) {
            throw new IllegalArgumentException("Número de partes deve ser >= 1");
        }
        long centavos = of(total).movePointRight(2).longValueExact();
        long base = centavos / partes;
        long resto = centavos % partes;
        List<BigDecimal> resultado = new ArrayList<>(partes);
        for (int i = 0; i < partes; i++) {
            resultado.add(BigDecimal.valueOf(base + (i < resto ? 1 : 0), 2));
        }
        return resultado;
    }
}
