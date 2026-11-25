import { useEffect } from 'react';
import { api } from '../api/client';
import type { KdsItem, Praca } from '../api/types';
import { modaisAbertos } from './Modal';
import { useLoad } from './useLoad';

/**
 * Fila de produção de uma praça, em tempo real: o servidor avisa por SSE (`fila`) quando ela muda e a tela
 * busca a lista de novo na API. O EventSource reconecta sozinho; ao reconectar o evento `conectado` recarrega a
 * fila, cobrindo o que mudou enquanto a conexão estava fora. O polling lento só atualiza os minutos de espera.
 */
export function useKdsFila(praca: Praca) {
  const { dados, recarregar } = useLoad(() => api<KdsItem[]>('/kds/' + praca), [praca], { intervaloMs: 30_000 });

  useEffect(() => {
    const fonte = new EventSource(`/api/kds/${praca}/eventos`);
    const atualizar = () => {
      if (modaisAbertos() === 0) void recarregar();
    };
    fonte.addEventListener('fila', atualizar);
    fonte.addEventListener('conectado', atualizar);
    return () => fonte.close();
  }, [praca, recarregar]);

  return { itens: dados, recarregar };
}
