package br.com.pdv.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "produto")
public class Produto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String nome;

    @Column(nullable = false)
    private String categoria;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal preco;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Praca praca;

    private boolean ativo = true;

    @OneToMany(mappedBy = "produto", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ItemFicha> ficha = new ArrayList<>();

    protected Produto() {
    }

    public Produto(String nome, String categoria, BigDecimal preco, Praca praca) {
        this.nome = nome;
        this.categoria = categoria;
        this.preco = preco;
        this.praca = praca;
    }

    public void atualizar(String nome, String categoria, BigDecimal preco, Praca praca) {
        this.nome = nome;
        this.categoria = categoria;
        this.preco = preco;
        this.praca = praca;
    }

    /** Custo de produzir uma unidade, somando insumo x quantidade da ficha. */
    public BigDecimal custoFicha() {
        return ficha.stream()
                .map(f -> f.getQuantidade().multiply(f.getInsumo().getCustoUnitario()))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(4, RoundingMode.HALF_UP);
    }

    public void desativar() {
        this.ativo = false;
    }

    public Long getId() { return id; }
    public String getNome() { return nome; }
    public String getCategoria() { return categoria; }
    public BigDecimal getPreco() { return preco; }
    public Praca getPraca() { return praca; }
    public boolean isAtivo() { return ativo; }
    public List<ItemFicha> getFicha() { return ficha; }
}
