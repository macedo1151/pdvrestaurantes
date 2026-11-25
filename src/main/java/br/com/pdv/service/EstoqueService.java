package br.com.pdv.service;

import br.com.pdv.domain.*;
import br.com.pdv.dto.Dtos.*;
import br.com.pdv.repository.InsumoRepository;
import br.com.pdv.repository.MovimentoEstoqueRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

@Service
public class EstoqueService {

    private final InsumoRepository insumos;
    private final MovimentoEstoqueRepository movimentos;

    public EstoqueService(InsumoRepository insumos, MovimentoEstoqueRepository movimentos) {
        this.insumos = insumos;
        this.movimentos = movimentos;
    }

    @Transactional
    public InsumoDto criar(InsumoRequest r) {
        if (insumos.existsByNomeIgnoreCase(r.nome())) {
            throw new RegraDeNegocioException("Já existe um insumo chamado " + r.nome());
        }
        Insumo insumo = insumos.save(new Insumo(r.nome(), r.unidade(), BigDecimal.ZERO, r.estoqueMinimo(), r.custoUnitario()));
        if (r.estoqueInicial().signum() > 0) {
            insumo.repor(r.estoqueInicial());
            movimentos.save(new MovimentoEstoque(insumo, TipoMovimento.ENTRADA, r.estoqueInicial(), "estoque inicial"));
        }
        return InsumoDto.from(insumo);
    }

    @Transactional(readOnly = true)
    public List<InsumoDto> listar() {
        return insumos.findAll().stream().map(InsumoDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<InsumoDto> alertas() {
        return insumos.findAbaixoDoMinimo().stream().map(InsumoDto::from).toList();
    }

    @Transactional
    public InsumoDto entrada(Long id, EntradaEstoqueRequest r) {
        Insumo insumo = insumos.findByIdForUpdate(id).orElseThrow(() -> new RecursoNaoEncontradoException("Insumo", id));
        insumo.repor(r.quantidade());
        movimentos.save(new MovimentoEstoque(insumo, TipoMovimento.ENTRADA, r.quantidade(),
                r.motivo() == null ? "entrada manual" : r.motivo()));
        return InsumoDto.from(insumo);
    }

    /**
     * Baixa os insumos da ficha técnica. Roda dentro da transação de quem chamou: se qualquer insumo
     * faltar, a exceção desfaz também as baixas anteriores e o pedido inteiro é recusado.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void consumir(Produto produto, int quantidade, String referencia) {
        for (ItemFicha linha : linhasOrdenadas(produto)) {
            Insumo insumo = travar(linha);
            BigDecimal necessario = linha.getQuantidade().multiply(BigDecimal.valueOf(quantidade));
            if (insumo.getEstoqueAtual().compareTo(necessario) < 0) {
                throw new EstoqueInsuficienteException(insumo.getNome(), insumo.getUnidade(),
                        insumo.getEstoqueAtual(), necessario);
            }
            insumo.baixar(necessario);
            movimentos.save(new MovimentoEstoque(insumo, TipoMovimento.SAIDA_VENDA, necessario.negate(), referencia));
        }
    }

    /** Item cancelado antes de ir para a produção: o insumo volta para o estoque. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void estornar(Produto produto, int quantidade, String referencia) {
        for (ItemFicha linha : linhasOrdenadas(produto)) {
            Insumo insumo = travar(linha);
            BigDecimal qtd = linha.getQuantidade().multiply(BigDecimal.valueOf(quantidade));
            insumo.repor(qtd);
            movimentos.save(new MovimentoEstoque(insumo, TipoMovimento.ESTORNO, qtd, referencia));
        }
    }

    /** Item cancelado depois de começar a ser preparado: o insumo já foi usado, vira perda registrada. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void registrarPerda(Produto produto, int quantidade, String referencia) {
        for (ItemFicha linha : linhasOrdenadas(produto)) {
            BigDecimal qtd = linha.getQuantidade().multiply(BigDecimal.valueOf(quantidade));
            movimentos.save(new MovimentoEstoque(linha.getInsumo(), TipoMovimento.PERDA, qtd.negate(), referencia));
        }
    }

    /** Sempre travamos na mesma ordem (por id) para dois pedidos não ficarem esperando um ao outro (deadlock). */
    private List<ItemFicha> linhasOrdenadas(Produto produto) {
        return produto.getFicha().stream()
                .sorted(Comparator.comparing(l -> l.getInsumo().getId()))
                .toList();
    }

    private Insumo travar(ItemFicha linha) {
        Long id = linha.getInsumo().getId();
        return insumos.findByIdForUpdate(id).orElseThrow(() -> new RecursoNaoEncontradoException("Insumo", id));
    }
}
