import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { api } from '../api/client';
import type { Comanda, ItemComanda, Produto } from '../api/types';
import { brl, FORMAS, STATUS } from '../lib/format';
import { Modal } from '../ui/Modal';
import { useTentar, useToast } from '../ui/Toasts';
import { DividirModal } from './Dividir';
import { PagamentoModal } from './Pagamento';

const ATUALIZACAO_MS = 6000;

export function ComandaModal({ id, onClose }: { id: number; onClose: () => void }) {
  const tentar = useTentar();
  const avisar = useToast();
  const [c, setC] = useState<Comanda | null>(null);
  const [produtos, setProdutos] = useState<Produto[]>([]);
  const [categoria, setCategoria] = useState('Todos');
  const [busca, setBusca] = useState('');
  const [obs, setObs] = useState('');
  const [subtela, setSubtela] = useState<'pagar' | 'dividir' | null>(null);

  // Cada toque num produto vira um item; as chamadas entram numa fila para chegar ao servidor na ordem do toque.
  const fila = useRef<Promise<void>>(Promise.resolve());
  const pendentes = useRef(0);

  useEffect(() => {
    void tentar(async () => {
      const [lista, atual] = await Promise.all([api<Produto[]>('/produtos'), api<Comanda>('/comandas/' + id)]);
      setProdutos(lista.filter(p => p.ativo));
      setC(atual);
    });
  }, [id, tentar]);

  // A comanda se atualiza sozinha (para o "pronto" aparecer). Fica suspensa enquanto há toques na fila,
  // para uma resposta atrasada não apagar um item recém-lançado.
  useEffect(() => {
    const iv = setInterval(async () => {
      if (pendentes.current > 0) return;
      try {
        const nova = await api<Comanda>('/comandas/' + id);
        if (pendentes.current === 0) setC(nova);
      } catch {
        /* tenta de novo no próximo ciclo */
      }
    }, ATUALIZACAO_MS);
    return () => clearInterval(iv);
  }, [id]);

  const categorias = useMemo(() => ['Todos', ...new Set(produtos.map(p => p.categoria))], [produtos]);
  const visiveis = produtos.filter(p =>
    (categoria === 'Todos' || p.categoria === categoria) && p.nome.toLowerCase().includes(busca.toLowerCase()));

  const lancar = useCallback((produtoId: number, observacao: string) => {
    pendentes.current++;
    fila.current = fila.current
      .then(async () => {
        await tentar(async () => setC(await api<Comanda>(`/comandas/${id}/itens`, 'POST', {
          produtoId, quantidade: 1, observacao: observacao || null,
        })));
      })
      .finally(() => { pendentes.current--; });
  }, [id, tentar]);

  if (!c) return <Modal titulo="Comanda" larga onClose={onClose}><p className="vazio">Carregando…</p></Modal>;

  const ativos = c.itens.filter(i => i.status !== 'CANCELADO');
  const falta = c.restante > 0;
  const quitada = c.restante === 0 && ativos.length > 0;

  const entregar = (i: ItemComanda) => tentar(async () => {
    await api(`/itens/${i.id}/status`, 'PATCH', { status: 'ENTREGUE' });
    setC(await api<Comanda>('/comandas/' + id));
  });

  const cancelar = (i: ItemComanda) => {
    const aviso = i.status === 'EM_PREPARO'
      ? 'Este item já está em preparo. Cancelar registra a perda dos insumos. Continuar?'
      : 'Cancelar este item?';
    if (confirm(aviso)) void tentar(async () => setC(await api<Comanda>(`/comandas/${id}/itens/${i.id}`, 'DELETE')));
  };

  const fechar = async () => {
    const ok = await tentar(() => api(`/comandas/${id}/fechar`, 'POST'));
    if (ok !== undefined) {
      avisar('Comanda fechada. Mesa liberada.');
      onClose();
    }
  };

  return (
    <Modal titulo={`Mesa ${c.mesa}`} larga onClose={onClose}>
      <div className="comanda">
        <section>
          <div>
            {c.itens.length === 0 && <p className="vazio">Nenhum item ainda. Toque em um produto para lançar.</p>}
            {c.itens.map(i => (
              <div key={i.id} className={`item ${i.status.toLowerCase()}`}>
                <div className="info">
                  <b>{i.quantidade}× {i.produto}</b>
                  {i.observacao && <small>{i.observacao}</small>}
                  <span className={`chip ${i.status}`}>{STATUS[i.status]}</span>
                </div>
                <div className="val">{i.status === 'CANCELADO' ? '' : brl(i.total)}</div>
                <div className="bt">
                  {i.status === 'PRONTO' && <button className="ok mini" onClick={() => entregar(i)}>Marcar entregue</button>}
                  {(i.status === 'PENDENTE' || i.status === 'EM_PREPARO') &&
                    <button className="perigo mini" onClick={() => cancelar(i)}>Cancelar</button>}
                </div>
              </div>
            ))}
          </div>
          <div className="pags">
            {c.pagamentos.map(p => (
              <div key={p.id}>Pago: {FORMAS[p.forma]} {brl(p.valor)}{p.pagador ? ` (${p.pagador})` : ''}</div>
            ))}
          </div>
          <div className="totais">
            <div><span>Itens</span><span>{brl(c.subtotal)}</span></div>
            <div><span>Serviço ({Number(c.percentualServico)}%)</span><span>{brl(c.servico)}</span></div>
            <div><span>Total</span><b>{brl(c.total)}</b></div>
            <div><span>Já pago</span><span>{brl(c.pago)}</span></div>
            <div className="restante"><span>Falta pagar</span><span>{brl(c.restante)}</span></div>
          </div>
          <div className="acoes">
            <button disabled={!falta} onClick={() => setSubtela('pagar')}>Receber pagamento</button>
            <button className="sec" disabled={!falta} onClick={() => setSubtela('dividir')}>Dividir conta</button>
            <button className={quitada ? 'ok' : 'sec'} disabled={!quitada} onClick={fechar}>Fechar comanda</button>
          </div>
        </section>

        <section>
          <input placeholder="Buscar produto" autoComplete="off" value={busca} onChange={e => setBusca(e.target.value)} />
          <div className="chips">
            {categorias.map(k => (
              <button key={k} className={k === categoria ? 'ativo' : ''} onClick={() => setCategoria(k)}>{k}</button>
            ))}
          </div>
          <input placeholder="Observação do próximo item (ex.: sem cebola)" maxLength={200}
            value={obs} onChange={e => setObs(e.target.value)} />
          <div className="tiles">
            {visiveis.map(p => (
              <button key={p.id} className="tile" onClick={() => { lancar(p.id, obs.trim()); setObs(''); }}>
                {p.nome}<span>{brl(p.preco)}</span>
              </button>
            ))}
            {visiveis.length === 0 && <p className="vazio">Nenhum produto encontrado.</p>}
          </div>
        </section>
      </div>

      {subtela === 'pagar' && <PagamentoModal comanda={c} onPago={setC} onClose={() => setSubtela(null)} />}
      {subtela === 'dividir' && <DividirModal comanda={c} onAtualizar={setC} onClose={() => setSubtela(null)} />}
    </Modal>
  );
}
