package br.com.pdv.dto;

import br.com.pdv.domain.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Contratos da API. Entidades JPA nunca saem pelos controllers. */
public final class Dtos {

    private Dtos() {
    }

    // ---------- Estoque ----------

    public record InsumoRequest(
            @NotBlank String nome,
            @NotBlank @Size(max = 10) String unidade,
            @NotNull @PositiveOrZero BigDecimal estoqueInicial,
            @NotNull @PositiveOrZero BigDecimal estoqueMinimo,
            @NotNull @PositiveOrZero BigDecimal custoUnitario) {
    }

    public record InsumoDto(Long id, String nome, String unidade, BigDecimal estoqueAtual,
                            BigDecimal estoqueMinimo, BigDecimal custoUnitario, boolean abaixoDoMinimo) {
        public static InsumoDto from(Insumo i) {
            return new InsumoDto(i.getId(), i.getNome(), i.getUnidade(), i.getEstoqueAtual(),
                    i.getEstoqueMinimo(), i.getCustoUnitario(), i.abaixoDoMinimo());
        }
    }

    public record EntradaEstoqueRequest(@NotNull @Positive BigDecimal quantidade, String motivo) {
    }

    // ---------- Cardápio ----------

    public record FichaLinhaRequest(@NotNull Long insumoId, @NotNull @Positive BigDecimal quantidade) {
    }

    public record ProdutoRequest(
            @NotBlank String nome,
            @NotBlank String categoria,
            @NotNull @Positive BigDecimal preco,
            @NotNull Praca praca,
            @NotNull List<@Valid FichaLinhaRequest> ficha) {
    }

    public record FichaLinhaDto(Long insumoId, String insumo, String unidade, BigDecimal quantidade) {
    }

    public record ProdutoDto(Long id, String nome, String categoria, BigDecimal preco, Praca praca, boolean ativo,
                             BigDecimal custo, BigDecimal margemPercentual, List<FichaLinhaDto> ficha) {
        public static ProdutoDto from(Produto p) {
            BigDecimal custo = p.custoFicha();
            BigDecimal margem = p.getPreco().signum() == 0 ? BigDecimal.ZERO
                    : p.getPreco().subtract(custo).multiply(BigDecimal.valueOf(100))
                    .divide(p.getPreco(), 1, RoundingMode.HALF_UP);
            return new ProdutoDto(p.getId(), p.getNome(), p.getCategoria(), p.getPreco(), p.getPraca(), p.isAtivo(),
                    Money.of(custo), margem,
                    p.getFicha().stream().map(f -> new FichaLinhaDto(f.getInsumo().getId(), f.getInsumo().getNome(),
                            f.getInsumo().getUnidade(), f.getQuantidade())).toList());
        }
    }

    // ---------- Mesas ----------

    public record MesaRequest(@Positive int numero) {
    }

    public record MesaDto(Long id, int numero, StatusMesa status) {
        public static MesaDto from(Mesa m) {
            return new MesaDto(m.getId(), m.getNumero(), m.getStatus());
        }
    }

    // ---------- Comandas ----------

    public record AbrirComandaRequest(@NotNull Long mesaId,
                                      @DecimalMin("0.0") @DecimalMax("30.0") BigDecimal percentualServico) {
    }

    public record NovoItemRequest(@NotNull Long produtoId, @Min(1) @Max(99) int quantidade,
                                  @Size(max = 200) String observacao) {
    }

    public record ItemDto(Long id, Long produtoId, String produto, int quantidade, BigDecimal precoUnitario,
                          BigDecimal total, String observacao, StatusItem status, Praca praca, Instant criadoEm) {
        public static ItemDto from(ItemComanda i) {
            return new ItemDto(i.getId(), i.getProduto().getId(), i.getProduto().getNome(), i.getQuantidade(),
                    i.getPrecoUnitario(), Money.of(i.total()), i.getObservacao(), i.getStatus(), i.getPraca(),
                    i.getCriadoEm());
        }
    }

    public record PagamentoRequest(@NotNull FormaPagamento forma, @NotNull @Positive BigDecimal valor,
                                   @Positive BigDecimal valorRecebido, @Size(max = 80) String pagador) {
    }

    public record PagamentoDto(Long id, FormaPagamento forma, BigDecimal valor, BigDecimal valorRecebido,
                               BigDecimal troco, String pagador, Instant pagoEm) {
        public static PagamentoDto from(Pagamento p) {
            return new PagamentoDto(p.getId(), p.getForma(), p.getValor(), p.getValorRecebido(), p.troco(),
                    p.getPagador(), p.getPagoEm());
        }
    }

    public record ComandaDto(Long id, int mesa, StatusComanda status, BigDecimal percentualServico,
                             List<ItemDto> itens, BigDecimal subtotal, BigDecimal servico, BigDecimal total,
                             BigDecimal pago, BigDecimal restante, List<PagamentoDto> pagamentos,
                             Instant abertaEm, Instant fechadaEm) {
        public static ComandaDto from(Comanda c) {
            return new ComandaDto(c.getId(), c.getMesa().getNumero(), c.getStatus(), c.getPercentualServico(),
                    c.getItens().stream().map(ItemDto::from).toList(), c.subtotal(), c.servico(), c.total(),
                    c.totalPago(), c.restante(),
                    c.getPagamentos().stream().map(PagamentoDto::from).toList(),
                    c.getAbertaEm(), c.getFechadaEm());
        }
    }

    // ---------- Divisão de conta ----------

    public record DivisaoIgualDto(BigDecimal valorDividido, int pessoas, List<BigDecimal> parcelas) {
    }

    public record GrupoItensRequest(@Size(max = 80) String pagador, @NotEmpty List<@NotNull Long> itemIds) {
    }

    public record DivisaoItensRequest(@NotEmpty List<@Valid GrupoItensRequest> grupos) {
    }

    public record ParteDto(String pagador, BigDecimal subtotal, BigDecimal servico, BigDecimal total) {
    }

    public record DivisaoItensDto(List<ParteDto> partes, BigDecimal subtotalNaoAtribuido) {
    }

    // ---------- KDS ----------

    public record KdsItemDto(Long itemId, Long comandaId, int mesa, String produto, int quantidade,
                             String observacao, StatusItem status, long minutosEspera) {
        public static KdsItemDto from(ItemComanda i) {
            return new KdsItemDto(i.getId(), i.getComanda().getId(), i.getComanda().getMesa().getNumero(),
                    i.getProduto().getNome(), i.getQuantidade(), i.getObservacao(), i.getStatus(),
                    Duration.between(i.getCriadoEm(), Instant.now()).toMinutes());
        }
    }

    public record MudarStatusRequest(@NotNull StatusItem status) {
    }

    // ---------- Caixa ----------

    public record AbrirCaixaRequest(@NotNull @PositiveOrZero BigDecimal valorAbertura, @Size(max = 80) String operador) {
    }

    public record FecharCaixaRequest(@NotNull @PositiveOrZero BigDecimal valorContado) {
    }

    public record CaixaDto(Long id, StatusCaixa status, String operador, Instant abertoEm, Instant fechadoEm,
                           BigDecimal valorAbertura, Map<FormaPagamento, BigDecimal> recebidoPorForma,
                           BigDecimal totalRecebido, BigDecimal dinheiroEsperado, BigDecimal valorContado,
                           BigDecimal diferenca) {
    }

    // ---------- Relatórios ----------

    public record ProdutoVendidoDto(String produto, long quantidade, BigDecimal receita, BigDecimal custo) {
    }

    public record VendasDto(LocalDate de, LocalDate ate, long comandas, BigDecimal faturamento,
                            BigDecimal servicoArrecadado, BigDecimal custoMercadoria, BigDecimal margemBruta,
                            BigDecimal ticketMedio, List<ProdutoVendidoDto> maisVendidos) {
    }
}
