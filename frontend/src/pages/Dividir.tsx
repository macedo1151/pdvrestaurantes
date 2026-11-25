import { useState } from 'react';
import { api } from '../api/client';
import type { Comanda, DivisaoIgual, DivisaoItens } from '../api/types';
import { brl } from '../lib/format';
import { Modal } from '../ui/Modal';
import { useTentar } from '../ui/Toasts';
import { PagamentoModal } from './Pagamento';

type Modo = 'igual' | 'itens';

interface Parte {
  nome: string;
  valor: number;
  detalhe?: string;
}

interface Props {
  comanda: Comanda;
  onAtualizar: (comanda: Comanda) => void;
  onClose: () => void;
}

const limitar = (n: number) => Math.min(50, Math.max(2, n || 2));

export function DividirModal({ comanda, onAtualizar, onClose }: Props) {
  const tentar = useTentar();
  const [modo, setModo] = useState<Modo>('igual');
  const [pessoas, setPessoas] = useState('2');
  // item -> número da pessoa que consumiu (começa na pessoa 1)
  const [donos, setDonos] = useState<Record<number, number>>({});
  const [partes, setPartes] = useState<Parte[]>([]);
  const [semDono, setSemDono] = useState(0);
  const [pagas, setPagas] = useState<ReadonlySet<number>>(new Set());
  const [pagando, setPagando] = useState<{ indice: number; parte: Parte } | null>(null);

  const n = limitar(Number(pessoas));
  const ativos = comanda.itens.filter(i => i.status !== 'CANCELADO');
  const donoDe = (itemId: number) => {
    const d = donos[itemId] ?? 1;
    return d > n ? 1 : d;
  };

  const trocarModo = (novo: Modo) => {
    setModo(novo);
    setPartes([]);
    setSemDono(0);
    setPagas(new Set());
  };

  const calcular = () =>
    tentar(async () => {
      setPagas(new Set());
      if (modo === 'igual') {
        const d = await api<DivisaoIgual>(`/comandas/${comanda.id}/divisao?pessoas=${n}`);
        setPartes(d.parcelas.map((v, i) => ({ nome: `Pessoa ${i + 1}`, valor: v })));
        setSemDono(0);
        return;
      }
      const grupos: Record<number, number[]> = {};
      for (const i of ativos) (grupos[donoDe(i.id)] ??= []).push(i.id);
      const d = await api<DivisaoItens>(`/comandas/${comanda.id}/divisao-itens`, 'POST', {
        grupos: Object.entries(grupos).map(([k, ids]) => ({ pagador: `Pessoa ${k}`, itemIds: ids })),
      });
      setPartes(d.partes.map(p => ({
        nome: p.pagador,
        valor: p.total,
        detalhe: `itens ${brl(p.subtotal)} + serviço ${brl(p.servico)}`,
      })));
      setSemDono(d.subtotalNaoAtribuido);
    });

  return (
    <Modal titulo="Dividir conta" onClose={onClose}>
      <div className="seg">
        <button className={modo === 'igual' ? 'ativo' : ''} onClick={() => trocarModo('igual')}>Em partes iguais</button>
        <button className={modo === 'itens' ? 'ativo' : ''} onClick={() => trocarModo('itens')}>Por item consumido</button>
      </div>
      <label>Quantas pessoas
        <input type="number" min={2} max={50} value={pessoas} onChange={e => setPessoas(e.target.value)} />
      </label>

      {modo === 'itens' && (
        <div>
          <small>Itens com mais de uma unidade vão inteiros para uma pessoa; para repartir, use a divisão em partes iguais.</small>
          {ativos.map(i => (
            <div key={i.id} className="linha-item">
              <span>{i.quantidade}× {i.produto} <small>{brl(i.total)}</small></span>
              <select aria-label={`Dono de ${i.produto}`} value={donoDe(i.id)}
                onChange={e => setDonos(d => ({ ...d, [i.id]: Number(e.target.value) }))}>
                {Array.from({ length: n }, (_, k) => <option key={k} value={k + 1}>Pessoa {k + 1}</option>)}
              </select>
            </div>
          ))}
        </div>
      )}

      <div className="rodape"><button onClick={calcular}>Calcular</button></div>

      <div>
        {partes.map((p, i) => (
          <div key={i} className={'parte' + (pagas.has(i) ? ' paga' : '')}>
            <span><b>{p.nome}</b>{p.detalhe && <><br /><small>{p.detalhe}</small></>}</span>
            <span>
              <b>{brl(p.valor)}</b>{' '}
              <button className="mini" disabled={pagas.has(i)} onClick={() => setPagando({ indice: i, parte: p })}>Receber</button>
            </span>
          </div>
        ))}
        {semDono > 0 && <p className="vazio">Itens sem dono: {brl(semDono)}</p>}
      </div>

      {pagando && (
        <PagamentoModal
          comanda={comanda}
          valor={pagando.parte.valor}
          pagador={pagando.parte.nome}
          onPago={nova => {
            onAtualizar(nova);
            setPagas(atual => new Set(atual).add(pagando.indice));
          }}
          onClose={() => setPagando(null)}
        />
      )}
    </Modal>
  );
}
