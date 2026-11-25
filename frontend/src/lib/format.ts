import type { FormaPagamento, Praca, StatusItem } from '../api/types';

export const brl = (v: number | null | undefined): string =>
  Number(v ?? 0).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });

export const qtd = (v: number): string => Number(v).toLocaleString('pt-BR', { maximumFractionDigits: 3 });

export const percentual = (v: number): string => `${Number(v).toLocaleString('pt-BR')}%`;

export const STATUS: Record<StatusItem, string> = {
  PENDENTE: 'Na fila',
  EM_PREPARO: 'Preparando',
  PRONTO: 'Pronto',
  ENTREGUE: 'Entregue',
  CANCELADO: 'Cancelado',
};

export const FORMAS: Record<FormaPagamento, string> = {
  DINHEIRO: 'Dinheiro',
  PIX: 'Pix',
  CREDITO: 'Crédito',
  DEBITO: 'Débito',
};

export const PRACAS: Praca[] = ['COZINHA', 'BAR', 'SOBREMESA'];

/** COZINHA -> Cozinha */
export const nomePraca = (p: string): string => p.charAt(0) + p.slice(1).toLowerCase();

/** Data de hoje no formato yyyy-mm-dd, no fuso do navegador. */
export const hoje = (): string => new Date().toLocaleDateString('sv-SE');

export const minutosDesde = (iso: string): number => Math.floor((Date.now() - new Date(iso).getTime()) / 60000);
