package br.com.pdv.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

import java.io.IOException;
import java.util.List;

import static br.com.pdv.config.Perfil.*;

/**
 * Autenticação HTTP Basic e autorização por perfil. A tela (HTML/JS/CSS) abre sem login; é a API que exige
 * credenciais, e o navegador pede usuário e senha na primeira chamada.
 *
 * Sem sessão e sem CSRF: toda requisição leva suas credenciais e a API só aceita JSON, o que um formulário de
 * outro site não consegue enviar.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity // regras que dependem do corpo da requisição, via @PreAuthorize (ver Permissoes)
@EnableConfigurationProperties(SegurancaProperties.class)
public class SegurancaConfig {

    @Bean
    SecurityFilterChain filtros(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(h -> h.frameOptions(f -> f.sameOrigin())) // o console do H2 usa frames
                .httpBasic(b -> b.authenticationEntryPoint((req, res, e) -> {
                    res.setHeader("WWW-Authenticate", "Basic realm=\"PDV Restaurante\"");
                    problema(res, HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized",
                            "Informe usuário e senha válidos");
                }))
                .exceptionHandling(e -> e.accessDeniedHandler((req, res, ex) ->
                        problema(res, HttpServletResponse.SC_FORBIDDEN, "Forbidden",
                                "Seu perfil não tem permissão para esta operação")))
                .authorizeHttpRequests(a -> a
                        // documentação e página de erro
                        .requestMatchers("/swagger", "/swagger-ui/**", "/v3/api-docs/**", "/error").permitAll()
                        .requestMatchers("/h2-console/**").hasRole(GERENTE.name())

                        // cadastros
                        .requestMatchers(HttpMethod.GET, "/api/produtos/**", "/api/mesas").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/insumos/**").hasAnyRole(GERENTE.name(), COZINHA.name())
                        .requestMatchers("/api/produtos/**", "/api/insumos/**", "/api/mesas").hasRole(GERENTE.name())

                        // salão: o garçom lança e cancela; o caixa só consulta e cobra
                        .requestMatchers(HttpMethod.GET, "/api/comandas", "/api/comandas/{id}")
                        .hasAnyRole(GARCOM.name(), CAIXA.name(), GERENTE.name())
                        .requestMatchers(HttpMethod.POST, "/api/comandas", "/api/comandas/*/itens")
                        .hasAnyRole(GARCOM.name(), GERENTE.name())
                        .requestMatchers(HttpMethod.DELETE, "/api/comandas/*/itens/*")
                        .hasAnyRole(GARCOM.name(), GERENTE.name())
                        .requestMatchers(HttpMethod.GET, "/api/comandas/*/divisao")
                        .hasAnyRole(GARCOM.name(), CAIXA.name(), GERENTE.name())
                        .requestMatchers(HttpMethod.POST, "/api/comandas/*/divisao-itens")
                        .hasAnyRole(GARCOM.name(), CAIXA.name(), GERENTE.name())
                        .requestMatchers(HttpMethod.POST, "/api/comandas/*/pagamentos", "/api/comandas/*/fechar")
                        .hasAnyRole(CAIXA.name(), GERENTE.name())

                        // produção: a cozinha avança os itens; o garçom marca a entrega
                        .requestMatchers(HttpMethod.GET, "/api/kds/**")
                        .hasAnyRole(COZINHA.name(), GARCOM.name(), GERENTE.name())
                        .requestMatchers(HttpMethod.PATCH, "/api/itens/*/status")
                        .hasAnyRole(COZINHA.name(), GARCOM.name(), GERENTE.name())

                        // dinheiro e gestão
                        .requestMatchers("/api/caixa/**").hasAnyRole(CAIXA.name(), GERENTE.name())
                        .requestMatchers("/api/relatorios/**").hasRole(GERENTE.name())

                        // o que sobrar na API é negado; fora dela são os arquivos estáticos da tela
                        .requestMatchers("/api/**").denyAll()
                        .anyRequest().permitAll());
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService usuarios(SegurancaProperties props, PasswordEncoder encoder) {
        List<SegurancaProperties.Usuario> lista = props.usuarios();
        if (lista == null || lista.isEmpty()) {
            throw new IllegalStateException("Configure ao menos um usuário em pdv.seguranca.usuarios");
        }
        // Uma variável de ambiente definida como vazia vence o valor padrão do application.yml e viraria senha em
        // branco: melhor recusar na partida do que subir com um usuário sem senha.
        for (SegurancaProperties.Usuario u : lista) {
            if (u.login() == null || u.login().isBlank() || u.senha() == null || u.senha().isBlank() || u.perfil() == null) {
                throw new IllegalStateException("Usuário '" + u.login() + "' sem login, senha ou perfil em pdv.seguranca.usuarios");
            }
        }
        UserDetails[] detalhes = lista.stream()
                .map(u -> User.withUsername(u.login()).password(encoder.encode(u.senha()))
                        .roles(u.perfil().name()).build())
                .toArray(UserDetails[]::new);
        return new InMemoryUserDetailsManager(detalhes);
    }

    /** Mesmo formato RFC 7807 dos demais erros da API. */
    private static void problema(HttpServletResponse res, int status, String titulo, String detalhe) throws IOException {
        res.setStatus(status);
        res.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        res.getWriter().write("{\"type\":\"about:blank\",\"title\":\"" + titulo + "\",\"status\":" + status
                + ",\"detail\":\"" + detalhe + "\"}");
    }
}
