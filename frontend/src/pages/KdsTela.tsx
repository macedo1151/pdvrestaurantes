import { useEffect, useState } from 'react';
import { api } from '../api/client';
import type { KdsItem, Praca } from '../api/types';
import { PRACAS } from '../lib/format';
import { useTentar } from '../ui/Toasts';
import { useKdsFila } from '../ui/useKdsFila';
import { LIMITE_ATRASO_MIN, proximoStatus } from './Cozinha';

/** Tela cheia da fila (para deixar numa TV na cozinha): #kds. Tema escuro, sem menu. */
export function KdsTela() {
  const [praca, setPraca] = useState<Praca>('COZINHA');
  const { itens, recarregar } = useKdsFila(praca);
  const tentar = useTentar();

  useEffect(() => {
    document.body.classList.add('kds-tela');
    return () => document.body.classList.remove('kds-tela');
  }, []);

  const avancar = (item: KdsItem) =>
    tentar(async () => {
      await api(`/itens/${item.itemId}/status`, 'PATCH', { status: proximoStatus(item) });
      await recarregar();
    });

  return (
    <div className="kds-pagina">
      <header>
        <strong>Fila de produção</strong>
        <select aria-label="Praça" value={praca} onChange={e => setPraca(e.target.value as Praca)}>
          {PRACAS.map(p => <option key={p}>{p}</option>)}
        </select>
        <a href="#salao">Voltar ao sistema</a>
      </header>
      <div className="kds-fila">
        {(itens ?? []).map(i => {
          const atrasado = i.minutosEspera >= LIMITE_ATRASO_MIN;
          return (
            <div key={i.itemId} className={`kds-card ${i.status}${atrasado ? ' atrasado' : ''}`}>
              <div className="mesa-info">Mesa {i.mesa} · {i.minutosEspera} min</div>
              <div className="nome">{i.quantidade}x {i.produto}</div>
              {i.observacao && <div className="obs">{i.observacao}</div>}
              <button onClick={() => avancar(i)}>
                {proximoStatus(i) === 'EM_PREPARO' ? 'Iniciar preparo' : 'Marcar como pronto'}
              </button>
            </div>
          );
        })}
      </div>
    </div>
  );
}
