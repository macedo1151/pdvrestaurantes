package br.com.pdv.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "item_comanda")
public class ItemComanda {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Comanda comanda;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Produto produto;

    @Column(nullable = false)
    private int quantidade;

    /** Preço no momento do pedido: mudar o cardápio depois não altera contas já abertas. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal precoUnitario;

    /** Custo da ficha no momento do pedido, para o CMV do relatório não variar com o preço do insumo. */
    @Column(nullable = false, precision = 14, scale = 4)
    private BigDecimal custoUnitario;

    private String observacao;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusItem status = StatusItem.PENDENTE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Praca praca;

    @Column(nullable = false)
    private Instant criadoEm = Instant.now();

    private Instant atualizadoEm = Instant.now();

    protected ItemComanda() {
    }

    public ItemComanda(Comanda comanda, Produto produto, int quantidade, String observacao) {
        this.comanda = comanda;
        this.produto = produto;
        this.quantidade = quantidade;
        this.precoUnitario = produto.getPreco();
        this.custoUnitario = produto.custoFicha();
        this.observacao = observacao;
        this.praca = produto.getPraca();
    }

    public BigDecimal total() {
        return precoUnitario.multiply(BigDecimal.valueOf(quantidade));
    }

    public BigDecimal custoTotal() {
        return custoUnitario.multiply(BigDecimal.valueOf(quantidade));
    }

    public void mudarStatus(StatusItem novo) {
        this.status = novo;
        this.atualizadoEm = Instant.now();
    }

    public Long getId() { return id; }
    public Comanda getComanda() { return comanda; }
    public Produto getProduto() { return produto; }
    public int getQuantidade() { return quantidade; }
    public BigDecimal getPrecoUnitario() { return precoUnitario; }
    public BigDecimal getCustoUnitario() { return custoUnitario; }
    public String getObservacao() { return observacao; }
    public StatusItem getStatus() { return status; }
    public Praca getPraca() { return praca; }
    public Instant getCriadoEm() { return criadoEm; }
}
