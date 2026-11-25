package br.com.pdv.service;

import br.com.pdv.domain.ItemComanda;
import br.com.pdv.domain.Praca;
import br.com.pdv.domain.StatusItem;
import br.com.pdv.dto.Dtos.KdsItemDto;
import br.com.pdv.repository.ItemComandaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;

/** Fila de produção por praça (cozinha, bar, sobremesa). */
@Service
public class KdsService {

    private final ItemComandaRepository itens;

    public KdsService(ItemComandaRepository itens) {
        this.itens = itens;
    }

    @Transactional(readOnly = true)
    public List<KdsItemDto> fila(Praca praca) {
        return itens.fila(praca, EnumSet.of(StatusItem.PENDENTE, StatusItem.EM_PREPARO))
                .stream().map(KdsItemDto::from).toList();
    }

    @Transactional
    public KdsItemDto mudarStatus(Long itemId, StatusItem novo) {
        ItemComanda item = itens.findById(itemId).orElseThrow(() -> new RecursoNaoEncontradoException("Item", itemId));
        if (!item.getStatus().podeAvancarPara(novo)) {
            throw new RegraDeNegocioException("Não é possível ir de %s para %s".formatted(item.getStatus(), novo));
        }
        item.mudarStatus(novo);
        return KdsItemDto.from(item);
    }
}
