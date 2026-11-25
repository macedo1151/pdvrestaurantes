package br.com.pdv.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

/** Histórico de tudo que mexeu no saldo de um insumo. Saldo sem histórico não dá para auditar. */
@Entity
@Table(name = "movimento_estoque")
public class MovimentoEstoque {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Insumo insumo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoMovimento tipo;

    /** Positivo entra, negativo sai. */
    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal quantidade;

    private String referencia;

    @Column(nullable = false)
    private Instant criadoEm = Instant.now();

    protected MovimentoEstoque() {
    }

    public MovimentoEstoque(Insumo insumo, TipoMovimento tipo, BigDecimal quantidade, String referencia) {
        this.insumo = insumo;
        this.tipo = tipo;
        this.quantidade = quantidade;
        this.referencia = referencia;
    }

    public Long getId() { return id; }
    public Insumo getInsumo() { return insumo; }
    public TipoMovimento getTipo() { return tipo; }
    public BigDecimal getQuantidade() { return quantidade; }
    public String getReferencia() { return referencia; }
    public Instant getCriadoEm() { return criadoEm; }
}
