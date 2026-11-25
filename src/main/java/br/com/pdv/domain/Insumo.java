package br.com.pdv.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "insumo")
public class Insumo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String nome;

    @Column(nullable = false, length = 10)
    private String unidade;

    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal estoqueAtual = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal estoqueMinimo = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 4)
    private BigDecimal custoUnitario = BigDecimal.ZERO;

    /** Rede de segurança: se dois caixas gravarem o mesmo saldo ao mesmo tempo, um deles falha em vez de sobrescrever. */
    @Version
    private Long versao;

    protected Insumo() {
    }

    public Insumo(String nome, String unidade, BigDecimal estoqueAtual, BigDecimal estoqueMinimo, BigDecimal custoUnitario) {
        this.nome = nome;
        this.unidade = unidade;
        this.estoqueAtual = estoqueAtual;
        this.estoqueMinimo = estoqueMinimo;
        this.custoUnitario = custoUnitario;
    }

    public void baixar(BigDecimal quantidade) {
        this.estoqueAtual = estoqueAtual.subtract(quantidade);
    }

    public void repor(BigDecimal quantidade) {
        this.estoqueAtual = estoqueAtual.add(quantidade);
    }

    public boolean abaixoDoMinimo() {
        return estoqueAtual.compareTo(estoqueMinimo) <= 0;
    }

    public Long getId() { return id; }
    public String getNome() { return nome; }
    public String getUnidade() { return unidade; }
    public BigDecimal getEstoqueAtual() { return estoqueAtual; }
    public BigDecimal getEstoqueMinimo() { return estoqueMinimo; }
    public BigDecimal getCustoUnitario() { return custoUnitario; }
    public void setCustoUnitario(BigDecimal custoUnitario) { this.custoUnitario = custoUnitario; }
    public void setEstoqueMinimo(BigDecimal estoqueMinimo) { this.estoqueMinimo = estoqueMinimo; }
}
