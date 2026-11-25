import { useState, type FormEvent } from 'react';
import { api } from '../api/client';
import type { Insumo, Praca, Produto } from '../api/types';
import { brl, nomePraca, percentual, PRACAS, qtd } from '../lib/format';
import { Modal } from '../ui/Modal';
import { useTentar, useToast } from '../ui/Toasts';
import { useLoad } from '../ui/useLoad';

type Aberto = { tipo: 'form'; produto: Produto | null } | null;

export function Cardapio() {
  const tentar = useTentar();
  const { dados, recarregar } = useLoad(async () => {
    const [produtos, insumos] = await Promise.all([api<Produto[]>('/produtos'), api<Insumo[]>('/insumos')]);
    return { produtos, insumos };
  }, []);
  const [aberto, setAberto] = useState<Aberto>(null);

  if (!dados) return null;
  const ativos = dados.produtos.filter(p => p.ativo)
    .sort((a, b) => a.categoria.localeCompare(b.categoria) || a.nome.localeCompare(b.nome));
  const fechar = () => { setAberto(null); void recarregar(); };

  const tirar = async (p: Produto) => {
    if (!confirm(`Tirar "${p.nome}" do cardápio? O histórico de vendas é mantido.`)) return;
    await tentar(() => api('/produtos/' + p.id, 'DELETE'));
    void recarregar();
  };

  return (
    <>
      <div className="barra">
        <h1>Cardápio</h1>
        <button onClick={() => setAberto({ tipo: 'form', produto: null })}>+ Produto</button>
      </div>
      {ativos.length === 0 ? <p className="vazio">Cardápio vazio.</p> : (
        <div className="tabela-rolagem">
          <table>
            <thead>
              <tr><th>Produto</th><th>Categoria</th><th>Praça</th><th className="num">Preço</th>
                <th className="num">Custo</th><th className="num">Margem</th><th></th></tr>
            </thead>
            <tbody>
              {ativos.map(p => (
                <tr key={p.id}>
                  <td>{p.nome}<br />
                    <small>{p.ficha.map(f => `${qtd(f.quantidade)} ${f.unidade} ${f.insumo}`).join(', ') || 'sem ficha técnica'}</small>
                  </td>
                  <td>{p.categoria}</td>
                  <td>{nomePraca(p.praca)}</td>
                  <td className="num">{brl(p.preco)}</td>
                  <td className="num">{brl(p.custo)}</td>
                  <td className="num">{percentual(p.margemPercentual)}</td>
                  <td className="num">
                    <button className="mini sec" onClick={() => setAberto({ tipo: 'form', produto: p })}>Editar</button>{' '}
                    <button className="mini perigo" onClick={() => tirar(p)}>Tirar</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {aberto && (
        <FormProduto produto={aberto.produto} produtos={dados.produtos} insumos={dados.insumos} onClose={fechar} />
      )}
    </>
  );
}

interface LinhaFicha {
  chave: number;
  insumoId: string;
  quantidade: string;
}

interface Props {
  produto: Produto | null;
  produtos: Produto[];
  insumos: Insumo[];
  onClose: () => void;
}

function FormProduto({ produto, produtos, insumos, onClose }: Props) {
  const tentar = useTentar();
  const avisar = useToast();
  const [proxima, setProxima] = useState(1000);
  const [ficha, setFicha] = useState<LinhaFicha[]>(() =>
    (produto?.ficha ?? []).map((f, i) => ({ chave: i, insumoId: String(f.insumoId), quantidade: String(f.quantidade) })));

  const alterar = (chave: number, campo: 'insumoId' | 'quantidade', valor: string) =>
    setFicha(l => l.map(x => (x.chave === chave ? { ...x, [campo]: valor } : x)));

  const enviar = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    const f = new FormData(e.currentTarget);
    const corpo = {
      nome: String(f.get('nome')).trim(),
      categoria: String(f.get('cat')).trim(),
      preco: Number(f.get('preco')),
      praca: String(f.get('praca')) as Praca,
      ficha: ficha
        .map(l => ({ insumoId: Number(l.insumoId), quantidade: Number(l.quantidade) }))
        .filter(l => l.insumoId && l.quantidade > 0),
    };
    const ok = await tentar(() => (produto
      ? api('/produtos/' + produto.id, 'PUT', corpo)
      : api('/produtos', 'POST', corpo)));
    if (ok !== undefined) { avisar('Produto salvo.'); onClose(); }
  };

  return (
    <Modal titulo={produto ? 'Editar produto' : 'Novo produto'} onClose={onClose}>
      <form onSubmit={enviar}>
        <label>Nome<input name="nome" required defaultValue={produto?.nome} /></label>
        <label>Categoria<input name="cat" required list="categorias" defaultValue={produto?.categoria} /></label>
        <datalist id="categorias">
          {[...new Set(produtos.map(p => p.categoria))].map(c => <option key={c} value={c} />)}
        </datalist>
        <label>Preço de venda (R$)<input name="preco" type="number" step="0.01" min="0.01" required defaultValue={produto?.preco} /></label>
        <label>Praça (para onde o pedido vai)
          <select name="praca" defaultValue={produto?.praca ?? 'COZINHA'}>
            {PRACAS.map(p => <option key={p} value={p}>{nomePraca(p)}</option>)}
          </select>
        </label>
        <label>Ficha técnica (insumos gastos por unidade vendida)</label>
        <div>
          {ficha.map(l => (
            <div key={l.chave} className="ficha-linha">
              <select aria-label="Insumo" value={l.insumoId} onChange={e => alterar(l.chave, 'insumoId', e.target.value)}>
                <option value="">Insumo...</option>
                {insumos.map(i => <option key={i.id} value={i.id}>{i.nome} ({i.unidade})</option>)}
              </select>
              <input aria-label="Quantidade" type="number" step="0.001" min="0.001" placeholder="Qtd"
                value={l.quantidade} onChange={e => alterar(l.chave, 'quantidade', e.target.value)} />
              <button type="button" className="perigo mini" aria-label="Remover ingrediente"
                onClick={() => setFicha(x => x.filter(y => y.chave !== l.chave))}>×</button>
            </div>
          ))}
        </div>
        <button type="button" className="sec mini"
          onClick={() => { setFicha(l => [...l, { chave: proxima, insumoId: '', quantidade: '' }]); setProxima(n => n + 1); }}>
          + ingrediente
        </button>
        <div className="rodape"><button>Salvar</button></div>
      </form>
    </Modal>
  );
}
