import { useState } from 'react';
import { api } from '../api/client';
import type { KdsItem, Praca } from '../api/types';
import { nomePraca, PRACAS } from '../lib/format';
import { useTentar } from '../ui/Toasts';
import { useKdsFila } from '../ui/useKdsFila';

export const LIMITE_ATRASO_MIN = 15;

export function proximoStatus(item: KdsItem): 'EM_PREPARO' | 'PRONTO' {
  return item.status === 'PENDENTE' ? 'EM_PREPARO' : 'PRONTO';
}

export function Cozinha() {
  const [praca, setPraca] = useState<Praca>('COZINHA');
  const { itens, recarregar } = useKdsFila(praca);
  const tentar = useTentar();

  const avancar = (item: KdsItem) =>
    tentar(async () => {
      await api(`/itens/${item.itemId}/status`, 'PATCH', { status: proximoStatus(item) });
      await recarregar();
    });

  return (
    <>
      <div className="barra">
        <h1>Produção</h1>
        <div className="seg" style={{ margin: 0 }}>
          {PRACAS.map(p => (
            <button key={p} className={p === praca ? 'ativo' : ''} onClick={() => setPraca(p)}>{nomePraca(p)}</button>
          ))}
        </div>
      </div>
      {itens === undefined ? null : itens.length === 0 ? (
        <p className="vazio">Fila vazia.</p>
      ) : (
        <div className="fila">
          {itens.map(i => (
            <div key={i.itemId} className={`pedido ${i.status}${i.minutosEspera >= LIMITE_ATRASO_MIN ? ' atrasado' : ''}`}>
              <small>Mesa {i.mesa} · {i.minutosEspera} min</small>
              <div className="nome">{i.quantidade}× {i.produto}</div>
              {i.observacao && <div className="obs">{i.observacao}</div>}
              <button className={i.status === 'PENDENTE' ? '' : 'ok'} onClick={() => avancar(i)}>
                {i.status === 'PENDENTE' ? 'Iniciar preparo' : 'Marcar como pronto'}
              </button>
            </div>
          ))}
        </div>
      )}
    </>
  );
}
