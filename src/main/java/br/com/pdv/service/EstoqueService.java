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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    public Map<Long, BigDecimal> consumir(Produto produto, int quantidade, String referencia) {
        Map<Long, BigDecimal> consumido = new LinkedHashMap<>();
        for (ItemFicha linha : linhasOrdenadas(produto)) {
            Insumo insumo = travar(linha.getInsumo().getId());
            BigDecimal necessario = linha.getQuantidade().multiply(BigDecimal.valueOf(quantidade));
            if (insumo.getEstoqueAtual().compareTo(necessario) < 0) {
                throw new EstoqueInsuficienteException(insumo.getNome(), insumo.getUnidade(),
                        insumo.getEstoqueAtual(), necessario);
            }
            insumo.baixar(necessario);
            movimentos.save(new MovimentoEstoque(insumo, TipoMovimento.SAIDA_VENDA, necessario.negate(), referencia));
            consumido.put(insumo.getId(), necessario);
        }
        return consumido;
    }

    /**
     * Item cancelado antes de ir para a produção: o insumo volta para o estoque. Recebe o consumo gravado no
     * item (e não a ficha atual do produto), para devolver exatamente o que saiu.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void estornar(Map<Long, BigDecimal> consumo, String referencia) {
        for (Map.Entry<Long, BigDecimal> e : ordenado(consumo)) {
            Insumo insumo = travar(e.getKey());
            insumo.repor(e.getValue());
            movimentos.save(new MovimentoEstoque(insumo, TipoMovimento.ESTORNO, e.getValue(), referencia));
        }
    }

    /**
     * Item cancelado depois de começar a ser preparado: o insumo já foi usado, vira perda registrada.
     * O saldo não muda (a baixa da venda já saiu), mas o histórico precisa continuar fechando com o saldo: o
     * ESTORNO desfaz a saída da venda e a PERDA registra a saída definitiva, então a soma dos movimentos
     * continua igual ao saldo.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void registrarPerda(Map<Long, BigDecimal> consumo, String referencia) {
        for (Map.Entry<Long, BigDecimal> e : ordenado(consumo)) {
            Insumo insumo = insumos.getReferenceById(e.getKey());
            movimentos.save(new MovimentoEstoque(insumo, TipoMovimento.ESTORNO, e.getValue(), referencia));
            movimentos.save(new MovimentoEstoque(insumo, TipoMovimento.PERDA, e.getValue().negate(), referencia));
        }
    }

    /** Sempre travamos na mesma ordem (por id) para dois pedidos não ficarem esperando um ao outro (deadlock). */
    private List<ItemFicha> linhasOrdenadas(Produto produto) {
        return produto.getFicha().stream()
                .sorted(Comparator.comparing(l -> l.getInsumo().getId()))
                .toList();
    }

    private List<Map.Entry<Long, BigDecimal>> ordenado(Map<Long, BigDecimal> consumo) {
        return consumo.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList();
    }

    private Insumo travar(Long id) {
        return insumos.findByIdForUpdate(id).orElseThrow(() -> new RecursoNaoEncontradoException("Insumo", id));
    }
}
