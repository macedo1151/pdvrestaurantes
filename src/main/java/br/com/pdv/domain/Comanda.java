package br.com.pdv.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "comanda")
public class Comanda {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Mesa mesa;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusComanda status = StatusComanda.ABERTA;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal percentualServico;

    @Column(nullable = false)
    private Instant abertaEm = Instant.now();

    private Instant fechadaEm;

    @OneToMany(mappedBy = "comanda", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<ItemComanda> itens = new ArrayList<>();

    @OneToMany(mappedBy = "comanda", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<Pagamento> pagamentos = new ArrayList<>();

    protected Comanda() {
    }

    public Comanda(Mesa mesa, BigDecimal percentualServico) {
        this.mesa = mesa;
        this.percentualServico = percentualServico;
    }

    public List<ItemComanda> itensAtivos() {
        return itens.stream().filter(i -> i.getStatus() != StatusItem.CANCELADO).toList();
    }

    public BigDecimal subtotal() {
        return Money.of(itensAtivos().stream().map(ItemComanda::total).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    public BigDecimal servico() {
        return Money.percentual(subtotal(), percentualServico);
    }

    public BigDecimal total() {
        return subtotal().add(servico());
    }

    public BigDecimal totalPago() {
        return Money.of(pagamentos.stream().map(Pagamento::getValor).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    public BigDecimal restante() {
        return total().subtract(totalPago());
    }

    public boolean temItemEmProducao() {
        return itensAtivos().stream().anyMatch(i -> i.getStatus().emProducao());
    }

    public void fechar() {
        this.status = StatusComanda.FECHADA;
        this.fechadaEm = Instant.now();
    }

    public Long getId() { return id; }
    public Mesa getMesa() { return mesa; }
    public StatusComanda getStatus() { return status; }
    public BigDecimal getPercentualServico() { return percentualServico; }
    public Instant getAbertaEm() { return abertaEm; }
    public Instant getFechadaEm() { return fechadaEm; }
    public List<ItemComanda> getItens() { return itens; }
    public List<Pagamento> getPagamentos() { return pagamentos; }
}
