import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { Comanda } from '../api/types';
import { ToastProvider } from '../ui/Toasts';
import { PagamentoModal } from './Pagamento';

vi.mock('../api/client', () => ({ api: vi.fn() }));
import { api } from '../api/client';

const comanda = { id: 5, restante: 66, total: 66 } as Comanda;
const paga = { ...comanda, restante: 0 } as Comanda;

function abrir(props: Partial<Parameters<typeof PagamentoModal>[0]> = {}) {
  const onPago = vi.fn();
  const onClose = vi.fn();
  render(
    <ToastProvider>
      <PagamentoModal comanda={comanda} onPago={onPago} onClose={onClose} {...props} />
    </ToastProvider>,
  );
  return { onPago, onClose };
}

describe('PagamentoModal', () => {
  beforeEach(() => { vi.mocked(api).mockReset(); });

  it('sugere o que falta pagar', () => {
    abrir();
    expect(screen.getByLabelText('Valor a pagar')).toHaveValue(66);
  });

  it('mostra o troco enquanto o valor entregue é digitado (só em dinheiro)', async () => {
    const u = userEvent.setup();
    abrir();

    await u.type(screen.getByLabelText('Valor entregue pelo cliente'), '100');
    expect(screen.getByText(/Troco:/).textContent).toMatch(/34,00/);

    await u.click(screen.getByLabelText('Pix'));
    expect(screen.queryByLabelText('Valor entregue pelo cliente')).not.toBeInTheDocument();
  });

  it('envia o pagamento em dinheiro com o valor recebido e fecha', async () => {
    vi.mocked(api).mockResolvedValue(paga);
    const u = userEvent.setup();
    const { onPago, onClose } = abrir();

    await u.type(screen.getByLabelText('Valor entregue pelo cliente'), '100');
    await u.type(screen.getByLabelText(/Quem está pagando/), 'Ana');
    await u.click(screen.getByRole('button', { name: 'Confirmar' }));

    await waitFor(() => expect(onPago).toHaveBeenCalledWith(paga));
    expect(api).toHaveBeenCalledWith('/comandas/5/pagamentos', 'POST',
      { forma: 'DINHEIRO', valor: 66, valorRecebido: 100, pagador: 'Ana' });
    expect(onClose).toHaveBeenCalled();
  });

  it('Pix não envia valor recebido', async () => {
    vi.mocked(api).mockResolvedValue(paga);
    const u = userEvent.setup();
    abrir();

    await u.click(screen.getByLabelText('Pix'));
    await u.click(screen.getByRole('button', { name: 'Confirmar' }));

    await waitFor(() => expect(api).toHaveBeenCalled());
    expect(api).toHaveBeenCalledWith('/comandas/5/pagamentos', 'POST',
      { forma: 'PIX', valor: 66, valorRecebido: null, pagador: null });
  });

  it('se a API recusar, mostra o erro e mantém o modal aberto', async () => {
    vi.mocked(api).mockRejectedValue(new Error('Abra o caixa antes de receber pagamentos'));
    const u = userEvent.setup();
    const { onPago, onClose } = abrir();

    await u.click(screen.getByRole('button', { name: 'Confirmar' }));

    expect(await screen.findByText('Abra o caixa antes de receber pagamentos')).toBeInTheDocument();
    expect(onPago).not.toHaveBeenCalled();
    expect(onClose).not.toHaveBeenCalled();
  });
});
