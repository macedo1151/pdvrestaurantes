package br.com.pdv.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SegurancaConfigTest {

    private final SegurancaConfig config = new SegurancaConfig();
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    private UserDetailsService usuarios(SegurancaProperties.Usuario... u) {
        return config.usuarios(new SegurancaProperties(List.of(u)), encoder);
    }

    @Test
    void guardaAsSenhasComHashEComoRole() {
        UserDetailsService s = usuarios(new SegurancaProperties.Usuario("ana", "segredo", Perfil.CAIXA));

        var ana = s.loadUserByUsername("ana");

        assertThat(ana.getPassword()).isNotEqualTo("segredo");
        assertThat(encoder.matches("segredo", ana.getPassword())).isTrue();
        assertThat(ana.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_CAIXA");
    }

    @Test
    void recusaSenhaEmBrancoComoUmaVariavelDeAmbienteVazia() {
        assertThatThrownBy(() -> usuarios(new SegurancaProperties.Usuario("garcom", "", Perfil.GARCOM)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("garcom");
        assertThatThrownBy(() -> usuarios(new SegurancaProperties.Usuario("garcom", "   ", Perfil.GARCOM)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void recusaConfiguracaoSemUsuarios() {
        assertThatThrownBy(() -> config.usuarios(new SegurancaProperties(List.of()), encoder))
                .isInstanceOf(IllegalStateException.class);
    }
}
