import { describe, expect, it } from 'vitest';
import { brl, nomePraca, percentual, qtd } from './format';

// O toLocaleString usa espaço sem quebra (U+00A0) entre o símbolo e o valor.
const normal = (s: string) => s.replace(/ /g, ' ');

describe('format', () => {
  it('brl formata em reais', () => {
    expect(normal(brl(1234.5))).toBe('R$ 1.234,50');
    expect(normal(brl(0))).toBe('R$ 0,00');
  });

  it('brl trata ausência como zero', () => {
    expect(normal(brl(null))).toBe('R$ 0,00');
    expect(normal(brl(undefined))).toBe('R$ 0,00');
  });

  it('qtd mostra até 3 casas, sem zeros sobrando', () => {
    expect(qtd(0.3)).toBe('0,3');
    expect(qtd(1.2345)).toBe('1,235');
    expect(qtd(60)).toBe('60');
  });

  it('percentual e nome de praça', () => {
    expect(percentual(62.5)).toBe('62,5%');
    expect(nomePraca('SOBREMESA')).toBe('Sobremesa');
  });
});
