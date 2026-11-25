package br.com.pdv.service;

import java.math.BigDecimal;

public class EstoqueInsuficienteException extends RegraDeNegocioException {
    public EstoqueInsuficienteException(String insumo, String unidade, BigDecimal disponivel, BigDecimal necessario) {
        super("Estoque insuficiente de %s: disponível %s %s, necessário %s %s"
                .formatted(insumo, disponivel.stripTrailingZeros().toPlainString(), unidade,
                        necessario.stripTrailingZeros().toPlainString(), unidade));
    }
}
