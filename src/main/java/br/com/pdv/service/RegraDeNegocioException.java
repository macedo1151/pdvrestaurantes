package br.com.pdv.service;

/** Pedido válido na forma, mas que o estado atual do restaurante não permite (vira HTTP 409). */
public class RegraDeNegocioException extends RuntimeException {
    public RegraDeNegocioException(String mensagem) {
        super(mensagem);
    }
}
