import { useState, type FormEvent } from 'react';
import { api } from '../api/client';
import type { Insumo } from '../api/types';
import { brl, qtd } from '../lib/format';
import { Modal } from '../ui/Modal';
import { useTentar, useToast } from '../ui/Toasts';
import { useLoad } from '../ui/useLoad';

type Aberto = { tipo: 'novo' } | { tipo: 'entrada'; insumo: Insumo } | null;

export function Estoque() {
  const { dados: todos, recarregar } = useLoad(() => api<Insumo[]>('/insumos'), []);
  const [soAlertas, setSoAlertas] = useState(false);
  const [aberto, setAberto] = useState<Aberto>(null);

  if (!todos) return null;
  const alertas = todos.filter(i => i.abaixoDoMinimo).length;
  const lista = [...(soAlertas ? todos.filter(i => i.abaixoDoMinimo) : todos)].sort((a, b) => a.nome.localeCompare(b.nome));
  const fechar = () => { setAberto(null); void recarregar(); };

  return (
    <>
      <div className="barra">
        <h1>Estoque</h1>
        <div style={{ display: 'flex', gap: 8 }}>
          <button className="sec" onClick={() => setSoAlertas(v => !v)}>{soAlertas ? 'Ver todos' : `Só alertas (${alertas})`}</button>
          <button onClick={() => setAberto({ tipo: 'novo' })}>+ Insumo</button>
        </div>
      </div>

      {lista.length === 0 ? <p className="vazio">Nada por aqui.</p> : (
        <div className="tabela-rolagem">
          <table>
            <thead>
              <tr><th>Insumo</th><th className="num">Saldo</th><th className="num">Mínimo</th><th className="num">Custo unit.</th><th></th></tr>
            </thead>
            <tbody>
              {lista.map(i => (
                <tr key={i.id} className={i.abaixoDoMinimo ? 'alerta' : ''}>
                  <td>{i.nome}{i.abaixoDoMinimo && <small style={{ color: 'var(--vermelho)' }}> repor</small>}</td>
                  <td className="num">{qtd(i.estoqueAtual)} {i.unidade}</td>
                  <td className="num">{qtd(i.estoqueMinimo)}</td>
                  <td className="num">{brl(i.custoUnitario)}</td>
                  <td className="num"><button className="mini" onClick={() => setAberto({ tipo: 'entrada', insumo: i })}>Entrada</button></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {aberto?.tipo === 'novo' && <NovoInsumo onClose={fechar} />}
      {aberto?.tipo === 'entrada' && <Entrada insumo={aberto.insumo} onClose={fechar} />}
    </>
  );
}

function NovoInsumo({ onClose }: { onClose: () => void }) {
  const tentar = useTentar();
  const enviar = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    const f = new FormData(e.currentTarget);
    const ok = await tentar(() => api('/insumos', 'POST', {
      nome: String(f.get('nome')).trim(),
      unidade: String(f.get('un')).trim(),
      estoqueInicial: Number(f.get('ini')),
      estoqueMinimo: Number(f.get('min')),
      custoUnitario: Number(f.get('custo')),
    }));
    if (ok !== undefined) onClose();
  };
  return (
    <Modal titulo="Novo insumo" onClose={onClose}>
      <form onSubmit={enviar}>
        <label>Nome<input name="nome" required /></label>
        <label>Unidade (kg, un, l...)<input name="un" maxLength={10} required /></label>
        <label>Estoque inicial<input name="ini" type="number" step="0.001" min="0" defaultValue="0" required /></label>
        <label>Estoque mínimo (gera alerta)<input name="min" type="number" step="0.001" min="0" defaultValue="0" required /></label>
        <label>Custo por unidade (R$)<input name="custo" type="number" step="0.01" min="0" defaultValue="0" required /></label>
        <div className="rodape"><button>Salvar</button></div>
      </form>
    </Modal>
  );
}

function Entrada({ insumo, onClose }: { insumo: Insumo; onClose: () => void }) {
  const tentar = useTentar();
  const avisar = useToast();
  const enviar = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    const f = new FormData(e.currentTarget);
    const motivo = String(f.get('mot') ?? '').trim();
    const ok = await tentar(() => api(`/insumos/${insumo.id}/entrada`, 'POST', {
      quantidade: Number(f.get('q')), motivo: motivo || null,
    }));
    if (ok !== undefined) { avisar('Entrada registrada.'); onClose(); }
  };
  return (
    <Modal titulo={`Entrada: ${insumo.nome}`} onClose={onClose}>
      <form onSubmit={enviar}>
        <label>Quantidade recebida<input name="q" type="number" step="0.001" min="0.001" required autoFocus /></label>
        <label>Motivo (opcional)<input name="mot" placeholder="Ex.: compra fornecedor X" /></label>
        <div className="rodape"><button>Registrar entrada</button></div>
      </form>
    </Modal>
  );
}
