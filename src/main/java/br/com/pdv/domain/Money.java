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
     * Reparte {@code total} proporcionalmente aos {@code pesos}, em centavos, pelo método dos maiores restos:
     * a soma das partes é exatamente o total. Se todos os pesos forem zero, todas as partes são zero.
     */
    public static List<BigDecimal> ratearProporcional(BigDecimal total, List<BigDecimal> pesos) {
        long totalCentavos = of(total).movePointRight(2).longValueExact();
        long[] w = pesos.stream().mapToLong(p -> of(p).movePointRight(2).longValueExact()).toArray();
        long somaPesos = 0;
        for (long p : w) {
            somaPesos += p;
        }
        long[] parte = new long[w.length];
        long[] resto = new long[w.length];
        long distribuido = 0;
        if (somaPesos > 0) {
            for (int i = 0; i < w.length; i++) {
                long produto = Math.multiplyExact(totalCentavos, w[i]);
                parte[i] = produto / somaPesos;
                resto[i] = produto % somaPesos;
                distribuido += parte[i];
            }
        }
        long sobra = somaPesos > 0 ? totalCentavos - distribuido : 0;
        // quem tem o maior resto recebe o centavo que sobrou (empate: o primeiro da lista)
        Integer[] ordem = new Integer[w.length];
        for (int i = 0; i < ordem.length; i++) {
            ordem[i] = i;
        }
        java.util.Arrays.sort(ordem, (a, b) -> Long.compare(resto[b], resto[a]));
        for (int k = 0; k < sobra; k++) {
            parte[ordem[k]]++;
        }
        List<BigDecimal> resultado = new ArrayList<>(w.length);
        for (long p : parte) {
            resultado.add(BigDecimal.valueOf(p, 2));
        }
        return resultado;
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
