package br.com.pdv.config;

import br.com.pdv.domain.StatusItem;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * Regras de autorização que dependem do conteúdo da requisição e por isso não cabem nos padrões de URL do
 * {@link SegurancaConfig}. Usadas em {@code @PreAuthorize("@permissoes...")}.
 */
@Component("permissoes")
public class Permissoes {

    /**
     * Quem pode levar um item para o status de destino: a cozinha inicia e finaliza o preparo, o garçom entrega.
     * O gerente pode tudo. Os demais destinos (voltar à fila, cancelar) não são transições de produção.
     */
    public boolean podeMudarStatus(Authentication autenticacao, StatusItem destino) {
        if (tem(autenticacao, Perfil.GERENTE)) {
            return true;
        }
        return switch (destino) {
            case EM_PREPARO, PRONTO -> tem(autenticacao, Perfil.COZINHA);
            case ENTREGUE -> tem(autenticacao, Perfil.GARCOM);
            case PENDENTE, CANCELADO -> false;
        };
    }

    private static boolean tem(Authentication autenticacao, Perfil perfil) {
        return autenticacao != null && autenticacao.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_" + perfil.name()));
    }
}
