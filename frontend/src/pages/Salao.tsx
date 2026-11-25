import { useState, type FormEvent } from 'react';
import { api } from '../api/client';
import type { Comanda, Mesa } from '../api/types';
import { brl, minutosDesde } from '../lib/format';
import { Modal } from '../ui/Modal';
import { useTentar } from '../ui/Toasts';
import { useLoad } from '../ui/useLoad';
import { ComandaModal } from './Comanda';

type Aberto =
  | { tipo: 'nova-mesa' }
  | { tipo: 'abrir-mesa'; mesa: Mesa }
  | { tipo: 'comanda'; id: number };

export function Salao() {
  const { dados, recarregar } = useLoad(
    async () => {
      const [mesas, comandas] = await Promise.all([api<Mesa[]>('/mesas'), api<Comanda[]>('/comandas?status=ABERTA')]);
      return { mesas, comandas };
    },
    [],
    { intervaloMs: 8000 },
  );
  const [aberto, setAberto] = useState<Aberto | null>(null);

  const fechar = () => {
    setAberto(null);
    void recarregar();
  };

  if (!dados) return null;
  const mesas = [...dados.mesas].sort((a, b) => a.numero - b.numero);
  const porMesa = new Map(dados.comandas.map(c => [c.mesa, c]));
  const proximaMesa = mesas.reduce((mx, m) => Math.max(mx, m.numero), 0) + 1;

  return (
    <>
      <div className="barra">
        <h1>Salão</h1>
        <button className="sec" onClick={() => setAberto({ tipo: 'nova-mesa' })}>+ Mesa</button>
      </div>

      {mesas.length === 0 ? (
        <p className="vazio">Nenhuma mesa cadastrada. Use "+ Mesa".</p>
      ) : (
        <div className="mesas">
          {mesas.map(m => {
            const c = porMesa.get(m.numero);
            if (!c) {
              return (
                <button key={m.id} className="mesa livre" onClick={() => setAberto({ tipo: 'abrir-mesa', mesa: m })}>
                  <b>{m.numero}</b><span>Livre</span>
                </button>
              );
            }
            const prontos = c.itens.filter(i => i.status === 'PRONTO').length;
            return (
              <button key={m.id} className="mesa ocupada" onClick={() => setAberto({ tipo: 'comanda', id: c.id })}>
                <b>{m.numero}</b><span>{brl(c.total)}</span>
                <small>{minutosDesde(c.abertaEm)} min</small>
                {prontos > 0 && <em className="badge">{prontos} pronto{prontos > 1 ? 's' : ''}</em>}
              </button>
            );
          })}
        </div>
      )}

      {aberto?.tipo === 'nova-mesa' && <NovaMesa sugerida={proximaMesa} onClose={fechar} />}
      {aberto?.tipo === 'abrir-mesa' && (
        <AbrirMesa mesa={aberto.mesa} onAberta={id => setAberto({ tipo: 'comanda', id })} onClose={() => setAberto(null)} />
      )}
      {aberto?.tipo === 'comanda' && <ComandaModal id={aberto.id} onClose={fechar} />}
    </>
  );
}

function NovaMesa({ sugerida, onClose }: { sugerida: number; onClose: () => void }) {
  const tentar = useTentar();
  const enviar = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    const numero = Number(new FormData(e.currentTarget).get('numero'));
    if (await tentar(() => api('/mesas', 'POST', { numero })) !== undefined) onClose();
  };
  return (
    <Modal titulo="Nova mesa" onClose={onClose}>
      <form onSubmit={enviar}>
        <label>Número da mesa<input name="numero" type="number" min={1} defaultValue={sugerida} required /></label>
        <div className="rodape"><button>Criar</button></div>
      </form>
    </Modal>
  );
}

function AbrirMesa({ mesa, onAberta, onClose }: { mesa: Mesa; onAberta: (comandaId: number) => void; onClose: () => void }) {
  const tentar = useTentar();
  const enviar = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    const percentualServico = Number(new FormData(e.currentTarget).get('serv'));
    const c = await tentar(() => api<Comanda>('/comandas', 'POST', { mesaId: mesa.id, percentualServico }));
    if (c) onAberta(c.id);
  };
  return (
    <Modal titulo={`Abrir mesa ${mesa.numero}`} onClose={onClose}>
      <form onSubmit={enviar}>
        <label>Serviço (%)<input name="serv" type="number" min={0} max={30} step={0.5} defaultValue={10} /></label>
        <small>Pode ser 0 se o cliente pedir para retirar.</small>
        <div className="rodape"><button>Abrir comanda</button></div>
      </form>
    </Modal>
  );
}
