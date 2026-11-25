import { useState, type FormEvent } from 'react';
import { api } from '../api/client';
import type { Vendas } from '../api/types';
import { brl, hoje, percentual } from '../lib/format';
import { useLoad } from '../ui/useLoad';

interface Periodo {
  de: string;
  ate: string;
}

export function Relatorios() {
  const [campos, setCampos] = useState<Periodo>(() => ({ de: hoje().slice(0, 8) + '01', ate: hoje() }));
  const [periodo, setPeriodo] = useState<Periodo>(campos);
  const { dados: r } = useLoad(() => api<Vendas>(`/relatorios/vendas?de=${periodo.de}&ate=${periodo.ate}`), [periodo]);

  const gerar = (e: FormEvent) => {
    e.preventDefault();
    setPeriodo(campos);
  };

  return (
    <>
      <div className="barra"><h1>Relatório de vendas</h1></div>
      <form className="filtros" onSubmit={gerar}>
        <label>De<input type="date" required value={campos.de} onChange={e => setCampos({ ...campos, de: e.target.value })} /></label>
        <label>Até<input type="date" required value={campos.ate} onChange={e => setCampos({ ...campos, ate: e.target.value })} /></label>
        <button>Gerar</button>
      </form>
      {r && (
        <>
          <div className="kpis">
            <div className="kpi"><small>Comandas fechadas</small><b>{r.comandas}</b></div>
            <div className="kpi"><small>Faturamento (itens)</small><b>{brl(r.faturamento)}</b></div>
            <div className="kpi"><small>Serviço arrecadado</small><b>{brl(r.servicoArrecadado)}</b></div>
            <div className="kpi"><small>Custo da mercadoria</small><b>{brl(r.custoMercadoria)}</b></div>
            <div className="kpi"><small>Margem bruta</small><b>{percentual(r.margemBruta)}</b></div>
            <div className="kpi"><small>Ticket médio</small><b>{brl(r.ticketMedio)}</b></div>
          </div>
          {r.maisVendidos.length === 0 ? <p className="vazio">Nenhuma venda no período.</p> : (
            <div className="tabela-rolagem">
              <table>
                <thead>
                  <tr><th>Produto</th><th className="num">Qtd</th><th className="num">Receita</th><th className="num">Custo</th></tr>
                </thead>
                <tbody>
                  {r.maisVendidos.map(p => (
                    <tr key={p.produto}>
                      <td>{p.produto}</td><td className="num">{p.quantidade}</td>
                      <td className="num">{brl(p.receita)}</td><td className="num">{brl(p.custo)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </>
      )}
    </>
  );
}
