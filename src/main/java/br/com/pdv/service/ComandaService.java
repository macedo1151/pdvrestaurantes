package br.com.pdv.service;

import br.com.pdv.domain.*;
import br.com.pdv.dto.Dtos.*;
import br.com.pdv.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Service
public class ComandaService {

    private final ComandaRepository comandas;
    private final MesaRepository mesas;
    private final ProdutoRepository produtos;
    private final CaixaRepository caixas;
    private final EstoqueService estoque;
    private final BigDecimal servicoPadrao;

    public ComandaService(ComandaRepository comandas, MesaRepository mesas, ProdutoRepository produtos,
                          CaixaRepository caixas, EstoqueService estoque,
                          @Value("${pdv.servico-padrao:10}") BigDecimal servicoPadrao) {
        this.comandas = comandas;
        this.mesas = mesas;
        this.produtos = produtos;
        this.caixas = caixas;
        this.estoque = estoque;
        this.servicoPadrao = servicoPadrao;
    }

    @Transactional
    public ComandaDto abrir(AbrirComandaRequest r) {
        Mesa mesa = mesas.findById(r.mesaId()).orElseThrow(() -> new RecursoNaoEncontradoException("Mesa", r.mesaId()));
        if (mesa.getStatus() == StatusMesa.OCUPADA) {
            throw new RegraDeNegocioException("A mesa " + mesa.getNumero() + " já tem uma comanda aberta");
        }
        mesa.ocupar();
        BigDecimal servico = r.percentualServico() != null ? r.percentualServico() : servicoPadrao;
        return ComandaDto.from(comandas.save(new Comanda(mesa, servico)));
    }

    @Transactional(readOnly = true)
    public ComandaDto obter(Long id) {
        return ComandaDto.from(comandas.findById(id).orElseThrow(() -> new RecursoNaoEncontradoException("Comanda", id)));
    }

    @Transactional(readOnly = true)
    public List<ComandaDto> listar(StatusComanda status) {
        return comandas.findByStatusOrderByAbertaEmAsc(status).stream().map(ComandaDto::from).toList();
    }

    @Transactional
    public ComandaDto adicionarItem(Long comandaId, NovoItemRequest r) {
        Comanda c = abertaTravada(comandaId);
        Produto p = produtos.findById(r.produtoId()).orElseThrow(() -> new RecursoNaoEncontradoException("Produto", r.produtoId()));
        if (!p.isAtivo()) {
            throw new RegraDeNegocioException("O produto " + p.getNome() + " está desativado");
        }
        estoque.consumir(p, r.quantidade(), "comanda " + c.getId());
        c.getItens().add(new ItemComanda(c, p, r.quantidade(), r.observacao()));
        comandas.flush(); // gera o id do item antes de montar a resposta
        return ComandaDto.from(c);
    }

    @Transactional
    public ComandaDto cancelarItem(Long comandaId, Long itemId) {
        Comanda c = abertaTravada(comandaId);
        ItemComanda item = c.getItens().stream().filter(i -> i.getId().equals(itemId)).findFirst()
                .orElseThrow(() -> new RecursoNaoEncontradoException("Item", itemId));
        switch (item.getStatus()) {
            case CANCELADO -> throw new RegraDeNegocioException("Item já cancelado");
            case PRONTO, ENTREGUE -> throw new RegraDeNegocioException(
                    "Item já preparado; o cancelamento exige ajuste do gerente");
            case PENDENTE -> estoque.estornar(item.getProduto(), item.getQuantidade(), "cancelamento comanda " + c.getId());
            case EM_PREPARO -> estoque.registrarPerda(item.getProduto(), item.getQuantidade(), "cancelamento comanda " + c.getId());
        }
        item.mudarStatus(StatusItem.CANCELADO);
        if (c.restante().signum() < 0) {
            throw new RegraDeNegocioException("O cancelamento deixaria a comanda com mais pagamentos que o total");
        }
        return ComandaDto.from(c);
    }

    @Transactional
    public ComandaDto pagar(Long comandaId, PagamentoRequest r) {
        Comanda c = abertaTravada(comandaId);
        Caixa caixa = caixas.findFirstByStatus(StatusCaixa.ABERTO)
                .orElseThrow(() -> new RegraDeNegocioException("Abra o caixa antes de receber pagamentos"));
        if (c.itensAtivos().isEmpty()) {
            throw new RegraDeNegocioException("A comanda não tem itens");
        }
        BigDecimal valor = Money.of(r.valor());
        if (valor.compareTo(c.restante()) > 0) {
            throw new RegraDeNegocioException("Valor maior que o restante da conta (" + c.restante() + ")");
        }
        BigDecimal recebido = valor;
        if (r.forma() == FormaPagamento.DINHEIRO && r.valorRecebido() != null) {
            recebido = Money.of(r.valorRecebido());
            if (recebido.compareTo(valor) < 0) {
                throw new RegraDeNegocioException("Valor recebido é menor que o valor a pagar");
            }
        }
        c.getPagamentos().add(new Pagamento(c, caixa, r.forma(), valor, recebido, r.pagador()));
        comandas.flush();
        return ComandaDto.from(c);
    }

    @Transactional
    public ComandaDto fechar(Long comandaId) {
        Comanda c = abertaTravada(comandaId);
        if (c.itensAtivos().isEmpty()) {
            throw new RegraDeNegocioException("Comanda sem itens: não há o que fechar");
        }
        if (c.restante().signum() != 0) {
            throw new RegraDeNegocioException("Ainda falta pagar " + c.restante());
        }
        if (c.temItemEmProducao()) {
            throw new RegraDeNegocioException("Há itens ainda em produção");
        }
        c.fechar();
        c.getMesa().liberar();
        return ComandaDto.from(c);
    }

    /** Divide o que falta pagar em partes iguais, distribuindo os centavos que sobram. */
    @Transactional(readOnly = true)
    public DivisaoIgualDto dividirIgualmente(Long comandaId, int pessoas) {
        Comanda c = comandas.findById(comandaId).orElseThrow(() -> new RecursoNaoEncontradoException("Comanda", comandaId));
        if (pessoas < 2 || pessoas > 50) {
            throw new RegraDeNegocioException("Informe entre 2 e 50 pessoas");
        }
        BigDecimal restante = c.restante();
        return new DivisaoIgualDto(restante, pessoas, Money.ratear(restante, pessoas));
    }

    /** Cada pessoa paga os itens que consumiu, mais a sua parte proporcional do serviço. */
    @Transactional(readOnly = true)
    public DivisaoItensDto dividirPorItens(Long comandaId, DivisaoItensRequest r) {
        Comanda c = comandas.findById(comandaId).orElseThrow(() -> new RecursoNaoEncontradoException("Comanda", comandaId));
        Map<Long, ItemComanda> ativos = new HashMap<>();
        c.itensAtivos().forEach(i -> ativos.put(i.getId(), i));
        Set<Long> usados = new HashSet<>();
        List<ParteDto> partes = new ArrayList<>();
        for (GrupoItensRequest g : r.grupos()) {
            BigDecimal subtotal = BigDecimal.ZERO;
            for (Long itemId : g.itemIds()) {
                ItemComanda item = ativos.get(itemId);
                if (item == null) {
                    throw new RegraDeNegocioException("Item " + itemId + " não pertence a esta comanda ou foi cancelado");
                }
                if (!usados.add(itemId)) {
                    throw new RegraDeNegocioException("Item " + itemId + " foi atribuído a mais de uma pessoa");
                }
                subtotal = subtotal.add(item.total());
            }
            subtotal = Money.of(subtotal);
            BigDecimal servico = Money.percentual(subtotal, c.getPercentualServico());
            partes.add(new ParteDto(g.pagador(), subtotal, servico, subtotal.add(servico)));
        }
        BigDecimal naoAtribuido = ativos.entrySet().stream().filter(e -> !usados.contains(e.getKey()))
                .map(e -> e.getValue().total()).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new DivisaoItensDto(partes, Money.of(naoAtribuido));
    }

    private Comanda abertaTravada(Long id) {
        Comanda c = comandas.findByIdForUpdate(id).orElseThrow(() -> new RecursoNaoEncontradoException("Comanda", id));
        if (c.getStatus() != StatusComanda.ABERTA) {
            throw new RegraDeNegocioException("A comanda " + id + " já está fechada");
        }
        return c;
    }
}
