package br.com.pdv.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "caixa")
public class Caixa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusCaixa status = StatusCaixa.ABERTO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal valorAbertura;

    private String operador;

    @Column(nullable = false)
    private Instant abertoEm = Instant.now();

    private Instant fechadoEm;

    /** Dinheiro contado na gaveta no fechamento. */
    @Column(precision = 12, scale = 2)
    private BigDecimal valorContado;

    /**
     * TRUE enquanto o caixa está aberto e NULL depois de fechado. A restrição de unicidade do banco aceita vários
     * NULL, mas só um TRUE: é ela que impede dois caixas abertos quando duas aberturas chegam juntas.
     */
    @Column(unique = true)
    private Boolean abertoUnico = Boolean.TRUE;

    protected Caixa() {
    }

    public Caixa(BigDecimal valorAbertura, String operador) {
        this.valorAbertura = valorAbertura;
        this.operador = operador;
    }

    public void fechar(BigDecimal valorContado) {
        this.status = StatusCaixa.FECHADO;
        this.abertoUnico = null;
        this.valorContado = valorContado;
        this.fechadoEm = Instant.now();
    }

    public Long getId() { return id; }
    public StatusCaixa getStatus() { return status; }
    public BigDecimal getValorAbertura() { return valorAbertura; }
    public String getOperador() { return operador; }
    public Instant getAbertoEm() { return abertoEm; }
    public Instant getFechadoEm() { return fechadoEm; }
    public BigDecimal getValorContado() { return valorContado; }
}
