package br.com.pdv.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;

/** Uma linha da ficha técnica: quanto de um insumo entra em uma unidade do produto. */
@Entity
@Table(name = "item_ficha")
public class ItemFicha {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Produto produto;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Insumo insumo;

    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal quantidade;

    protected ItemFicha() {
    }

    public ItemFicha(Produto produto, Insumo insumo, BigDecimal quantidade) {
        this.produto = produto;
        this.insumo = insumo;
        this.quantidade = quantidade;
    }

    public Long getId() { return id; }
    public Produto getProduto() { return produto; }
    public Insumo getInsumo() { return insumo; }
    public BigDecimal getQuantidade() { return quantidade; }
}
