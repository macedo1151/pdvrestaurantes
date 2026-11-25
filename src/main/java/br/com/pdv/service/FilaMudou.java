package br.com.pdv.service;

import br.com.pdv.domain.Praca;

/** Publicado quando a fila de uma praça muda (item lançado, avançado ou cancelado). */
public record FilaMudou(Praca praca) {
}
