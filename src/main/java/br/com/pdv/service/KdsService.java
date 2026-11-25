package br.com.pdv.service;

import br.com.pdv.domain.ItemComanda;
import br.com.pdv.domain.Praca;
import br.com.pdv.domain.StatusItem;
import br.com.pdv.dto.Dtos.KdsItemDto;
import br.com.pdv.repository.ComandaRepository;
import br.com.pdv.repository.ItemComandaRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;

/** Fila de produção por praça (cozinha, bar, sobremesa). */
@Service
public class KdsService {

    private final ItemComandaRepository itens;
    private final ComandaRepository comandas;

    private final ApplicationEventPublisher eventos;

    public KdsService(ItemComandaRepository itens, ComandaRepository comandas, ApplicationEventPublisher eventos) {
        this.itens = itens;
        this.comandas = comandas;
        this.eventos = eventos;
    }

    @Transactional(readOnly = true)
    public List<KdsItemDto> fila(Praca praca) {
        return itens.fila(praca, EnumSet.of(StatusItem.PENDENTE, StatusItem.EM_PREPARO))
                .stream().map(KdsItemDto::from).toList();
    }

    /**
     * Trava a comanda antes de ler o item, na mesma ordem das operações do garçom (comanda primeiro).
     * Assim o cancelamento e o "iniciar" da cozinha nunca se sobrepõem: quem chega depois enxerga o estado
     * já gravado pelo outro, e um item cancelado não volta para a fila.
     */
    @Transactional
    public KdsItemDto mudarStatus(Long itemId, StatusItem novo) {
        Long comandaId = itens.findComandaId(itemId).orElseThrow(() -> new RecursoNaoEncontradoException("Item", itemId));
        comandas.findByIdForUpdate(comandaId).orElseThrow(() -> new RecursoNaoEncontradoException("Comanda", comandaId));
        // o item só é carregado depois da trava, então traz o estado mais recente
        ItemComanda item = itens.findById(itemId).orElseThrow(() -> new RecursoNaoEncontradoException("Item", itemId));
        if (!item.getStatus().podeAvancarPara(novo)) {
            throw new RegraDeNegocioException("Não é possível ir de %s para %s".formatted(item.getStatus(), novo));
        }
        item.mudarStatus(novo);
        eventos.publishEvent(new FilaMudou(item.getPraca()));
        return KdsItemDto.from(item);
    }
}
