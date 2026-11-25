package br.com.pdv.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/** Usuários definidos na configuração (pdv.seguranca.usuarios). */
@ConfigurationProperties("pdv.seguranca")
public record SegurancaProperties(List<Usuario> usuarios) {

    public record Usuario(String login, String senha, Perfil perfil) {
    }
}
