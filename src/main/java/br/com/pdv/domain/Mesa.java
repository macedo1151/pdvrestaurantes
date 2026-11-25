package br.com.pdv.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "mesa")
public class Mesa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private int numero;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusMesa status = StatusMesa.LIVRE;

    protected Mesa() {
    }

    public Mesa(int numero) {
        this.numero = numero;
    }

    public void ocupar() { this.status = StatusMesa.OCUPADA; }
    public void liberar() { this.status = StatusMesa.LIVRE; }

    public Long getId() { return id; }
    public int getNumero() { return numero; }
    public StatusMesa getStatus() { return status; }
}
