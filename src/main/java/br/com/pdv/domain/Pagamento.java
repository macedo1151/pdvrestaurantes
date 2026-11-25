package br.com.pdv.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "pagamento")
public class Pagamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Comanda comanda;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Caixa caixa;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FormaPagamento forma;

    /** Quanto abate da conta. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal valor;

    /** Quanto o cliente entregou (só difere de valor em dinheiro). */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal valorRecebido;

    private String pagador;

    @Column(nullable = false)
    private Instant pagoEm = Instant.now();

    protected Pagamento() {
    }

    public Pagamento(Comanda comanda, Caixa caixa, FormaPagamento forma, BigDecimal valor,
                     BigDecimal valorRecebido, String pagador) {
        this.comanda = comanda;
        this.caixa = caixa;
        this.forma = forma;
        this.valor = valor;
        this.valorRecebido = valorRecebido;
        this.pagador = pagador;
    }

    public BigDecimal troco() {
        return valorRecebido.subtract(valor);
    }

    public Long getId() { return id; }
    public Comanda getComanda() { return comanda; }
    public Caixa getCaixa() { return caixa; }
    public FormaPagamento getForma() { return forma; }
    public BigDecimal getValor() { return valor; }
    public BigDecimal getValorRecebido() { return valorRecebido; }
    public String getPagador() { return pagador; }
    public Instant getPagoEm() { return pagoEm; }
}
