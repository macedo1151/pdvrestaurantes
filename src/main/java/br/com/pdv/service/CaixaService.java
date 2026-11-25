package br.com.pdv.service;

import br.com.pdv.domain.*;
import br.com.pdv.dto.Dtos.*;
import br.com.pdv.repository.CaixaRepository;
import br.com.pdv.repository.PagamentoRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;

@Service
public class CaixaService {

    private final CaixaRepository caixas;
    private final PagamentoRepository pagamentos;

    public CaixaService(CaixaRepository caixas, PagamentoRepository pagamentos) {
        this.caixas = caixas;
        this.pagamentos = pagamentos;
    }

    @Transactional
    public CaixaDto abrir(AbrirCaixaRequest r) {
        if (caixas.findFirstByStatus(StatusCaixa.ABERTO).isPresent()) {
            throw new RegraDeNegocioException("Já existe um caixa aberto");
        }
        try {
            // saveAndFlush: a violação da unicidade aparece aqui e não só no commit
            Caixa caixa = caixas.saveAndFlush(new Caixa(Money.of(r.valorAbertura()), r.operador()));
            return resumo(caixa);
        } catch (DataIntegrityViolationException concorrente) {
            throw new RegraDeNegocioException("Já existe um caixa aberto");
        }
    }

    @Transactional(readOnly = true)
    public CaixaDto atual() {
        return resumo(caixas.findFirstByStatus(StatusCaixa.ABERTO)
                .orElseThrow(() -> new RegraDeNegocioException("Não há caixa aberto")));
    }

    /**
     * Fecha o caixa com a linha travada: espera os pagamentos em andamento (que seguram a trava compartilhada, ver
     * {@code ComandaService.pagar}) e impede que novos entrem enquanto o resumo é calculado. Assim nenhum pagamento
     * cai num caixa que acabou de fechar e fica fora do resumo.
     */
    @Transactional
    public CaixaDto fechar(FecharCaixaRequest r) {
        Caixa caixa = caixas.findByStatusForUpdate(StatusCaixa.ABERTO)
                .orElseThrow(() -> new RegraDeNegocioException("Não há caixa aberto"));
        caixa.fechar(Money.of(r.valorContado()));
        return resumo(caixa);
    }

    private CaixaDto resumo(Caixa caixa) {
        Map<FormaPagamento, BigDecimal> porForma = new EnumMap<>(FormaPagamento.class);
        for (FormaPagamento f : FormaPagamento.values()) {
            porForma.put(f, Money.ZERO);
        }
        for (Pagamento p : pagamentos.findByCaixaId(caixa.getId())) {
            porForma.merge(p.getForma(), p.getValor(), BigDecimal::add);
        }
        BigDecimal total = porForma.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        // O troco já saiu da gaveta, então só o valor que abateu a conta entra no esperado.
        BigDecimal dinheiroEsperado = caixa.getValorAbertura().add(porForma.get(FormaPagamento.DINHEIRO));
        BigDecimal diferenca = caixa.getValorContado() == null ? null : caixa.getValorContado().subtract(dinheiroEsperado);
        return new CaixaDto(caixa.getId(), caixa.getStatus(), caixa.getOperador(), caixa.getAbertoEm(),
                caixa.getFechadoEm(), caixa.getValorAbertura(), porForma, total, dinheiroEsperado,
                caixa.getValorContado(), diferenca);
    }
}
