package br.com.pdv.domain;

/** Ciclo de vida de um item pedido. Cancelamento é tratado à parte (não é uma transição de produção). */
public enum StatusItem {
    PENDENTE, EM_PREPARO, PRONTO, ENTREGUE, CANCELADO;

    public boolean podeAvancarPara(StatusItem destino) {
        return switch (this) {
            case PENDENTE -> destino == EM_PREPARO;
            case EM_PREPARO -> destino == PRONTO;
            case PRONTO -> destino == ENTREGUE;
            case ENTREGUE, CANCELADO -> false;
        };
    }

    /** Item que ainda está na fila/produção (aparece no KDS). */
    public boolean emProducao() {
        return this == PENDENTE || this == EM_PREPARO;
    }
}
