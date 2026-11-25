import { act, render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { Caixa } from './api/types';
import { App } from './App';
import { ToastProvider } from './ui/Toasts';

vi.mock('./api/client', () => ({ api: vi.fn() }));
import { api } from './api/client';

const caixaAberto: Caixa = {
  id: 1, status: 'ABERTO', operador: 'Ana', abertoEm: '2026-03-10T12:00:00Z', fechadoEm: null,
  valorAbertura: 100, recebidoPorForma: { DINHEIRO: 36, PIX: 30, CREDITO: 0, DEBITO: 0 },
  totalRecebido: 66, dinheiroEsperado: 136, valorContado: null, diferenca: null,
};

const chamadasA = (caminho: string) => vi.mocked(api).mock.calls.filter(c => c[0] === caminho).length;

function abrirApp(hash: string) {
  location.hash = hash;
  render(<ToastProvider><App /></ToastProvider>);
}

describe('App', () => {
  beforeEach(() => {
    vi.mocked(api).mockReset();
    vi.mocked(api).mockImplementation(async (caminho: string) => {
      if (caminho === '/caixa/atual') return caixaAberto;
      if (caminho.startsWith('/mesas') || caminho.startsWith('/comandas') || caminho.startsWith('/kds')) return [];
      return null;
    });
  });

  it('a aba do caixa permanece na tela depois de o indicador do topo atualizar (sem remontar em loop)', async () => {
    abrirApp('#caixa');

    expect(await screen.findByText('Fechar caixa', { selector: 'h2' })).toBeInTheDocument();
    // o indicador do topo também reflete o caixa aberto
    expect(await screen.findByText('Caixa aberto (Ana)')).toBeInTheDocument();

    // dá tempo de uma remontagem em loop aparecer como uma enxurrada de chamadas
    await new Promise(r => setTimeout(r, 300));
    expect(screen.getByText('Fechar caixa', { selector: 'h2' })).toBeInTheDocument();
    // esperado: a tela do caixa, o indicador do topo e a atualização do indicador depois de o caixa carregar
    expect(chamadasA('/caixa/atual')).toBeLessThanOrEqual(3);
  });

  it('trocar de aba mostra a nova tela e marca o botão do menu', async () => {
    abrirApp('#salao');
    expect(await screen.findByRole('heading', { name: 'Salão' })).toBeInTheDocument();

    await act(async () => {
      location.hash = '#cozinha';
      window.dispatchEvent(new HashChangeEvent('hashchange'));
    });

    expect(await screen.findByRole('heading', { name: 'Produção' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Cozinha e bar' })).toHaveClass('ativo');
  });
});
