// Contratos da API (espelham br.com.pdv.dto.Dtos). Valores em BigDecimal chegam como number no JSON.

export type Praca = 'COZINHA' | 'BAR' | 'SOBREMESA';
export type StatusItem = 'PENDENTE' | 'EM_PREPARO' | 'PRONTO' | 'ENTREGUE' | 'CANCELADO';
export type StatusMesa = 'LIVRE' | 'OCUPADA';
export type FormaPagamento = 'DINHEIRO' | 'PIX' | 'CREDITO' | 'DEBITO';

export interface Mesa {
  id: number;
  numero: number;
  status: StatusMesa;
}

export interface Insumo {
  id: number;
  nome: string;
  unidade: string;
  estoqueAtual: number;
  estoqueMinimo: number;
  custoUnitario: number;
  abaixoDoMinimo: boolean;
}

export interface InsumoRequest {
  nome: string;
  unidade: string;
  estoqueInicial: number;
  estoqueMinimo: number;
  custoUnitario: number;
}

export interface FichaLinha {
  insumoId: number;
  insumo: string;
  unidade: string;
  quantidade: number;
}

export interface Produto {
  id: number;
  nome: string;
  categoria: string;
  preco: number;
  praca: Praca;
  ativo: boolean;
  custo: number;
  margemPercentual: number;
  ficha: FichaLinha[];
}

export interface ProdutoRequest {
  nome: string;
  categoria: string;
  preco: number;
  praca: Praca;
  ficha: { insumoId: number; quantidade: number }[];
}

export interface ItemComanda {
  id: number;
  produtoId: number;
  produto: string;
  quantidade: number;
  precoUnitario: number;
  total: number;
  observacao: string | null;
  status: StatusItem;
  praca: Praca;
  criadoEm: string;
}

export interface Pagamento {
  id: number;
  forma: FormaPagamento;
  valor: number;
  valorRecebido: number;
  troco: number;
  pagador: string | null;
  pagoEm: string;
}

export interface Comanda {
  id: number;
  mesa: number;
  status: 'ABERTA' | 'FECHADA';
  percentualServico: number;
  itens: ItemComanda[];
  subtotal: number;
  servico: number;
  total: number;
  pago: number;
  restante: number;
  pagamentos: Pagamento[];
  abertaEm: string;
  fechadaEm: string | null;
}

export interface PagamentoRequest {
  forma: FormaPagamento;
  valor: number;
  valorRecebido: number | null;
  pagador: string | null;
}

export interface DivisaoIgual {
  valorDividido: number;
  pessoas: number;
  parcelas: number[];
}

export interface ParteDivisao {
  pagador: string;
  subtotal: number;
  servico: number;
  total: number;
}

export interface DivisaoItens {
  partes: ParteDivisao[];
  subtotalNaoAtribuido: number;
}

export interface KdsItem {
  itemId: number;
  comandaId: number;
  mesa: number;
  produto: string;
  quantidade: number;
  observacao: string | null;
  status: StatusItem;
  minutosEspera: number;
}

export interface Caixa {
  id: number;
  status: 'ABERTO' | 'FECHADO';
  operador: string | null;
  abertoEm: string;
  fechadoEm: string | null;
  valorAbertura: number;
  recebidoPorForma: Record<FormaPagamento, number>;
  totalRecebido: number;
  dinheiroEsperado: number;
  valorContado: number | null;
  diferenca: number | null;
}

export interface ProdutoVendido {
  produto: string;
  quantidade: number;
  receita: number;
  custo: number;
}

export interface Vendas {
  de: string;
  ate: string;
  comandas: number;
  faturamento: number;
  servicoArrecadado: number;
  custoMercadoria: number;
  margemBruta: number;
  ticketMedio: number;
  maisVendidos: ProdutoVendido[];
}
