import { afterEach, describe, expect, it, vi } from 'vitest';
import { api, ApiError } from './client';

function resposta(status: number, corpo?: unknown): Response {
  return new Response(corpo === undefined ? null : JSON.stringify(corpo), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

afterEach(() => vi.unstubAllGlobals());

describe('api', () => {
  it('devolve o JSON da resposta e prefixa /api', async () => {
    const fetchMock = vi.fn().mockResolvedValue(resposta(200, [{ id: 1 }]));
    vi.stubGlobal('fetch', fetchMock);

    await expect(api('/mesas')).resolves.toEqual([{ id: 1 }]);
    expect(fetchMock).toHaveBeenCalledWith('/api/mesas', expect.objectContaining({ method: 'GET' }));
  });

  it('serializa o corpo em JSON', async () => {
    const fetchMock = vi.fn().mockResolvedValue(resposta(201, { id: 7 }));
    vi.stubGlobal('fetch', fetchMock);

    await api('/mesas', 'POST', { numero: 3 });

    expect(fetchMock.mock.calls[0]?.[1]).toMatchObject({ method: 'POST', body: '{"numero":3}' });
  });

  it('204 devolve null', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(resposta(204)));
    await expect(api('/produtos/1', 'DELETE')).resolves.toBeNull();
  });

  it('usa o detail do ProblemDetail como mensagem de erro', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(resposta(409, { detail: 'A mesa 1 já tem uma comanda aberta' })));

    const erro = await api('/comandas', 'POST', {}).catch((e: unknown) => e);

    expect(erro).toBeInstanceOf(ApiError);
    expect((erro as ApiError).message).toBe('A mesa 1 já tem uma comanda aberta');
    expect((erro as ApiError).status).toBe(409);
  });

  it('lista os campos inválidos na validação (400)', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(
      resposta(400, { detail: 'Dados inválidos', campos: { numero: 'deve ser maior que 0' } })));

    await expect(api('/mesas', 'POST', { numero: 0 })).rejects.toThrow('Dados inválidos: numero deve ser maior que 0');
  });

  it('cai numa mensagem genérica quando a resposta de erro não tem corpo', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('boom', { status: 500 })));
    await expect(api('/mesas')).rejects.toThrow('Erro 500');
  });

  it('avisa quando o servidor não responde', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')));

    const erro = await api('/mesas').catch((e: unknown) => e);

    expect(erro).toBeInstanceOf(ApiError);
    expect((erro as ApiError).message).toBe('Sem conexão com o servidor');
  });
});
