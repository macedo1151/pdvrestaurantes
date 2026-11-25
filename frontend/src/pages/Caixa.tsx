import { useEffect, useState, type FormEvent } from 'react';
import { api, ApiError } from '../api/client';
import type { Caixa as CaixaDto, FormaPagamento } from '../api/types';
import { brl, FORMAS } from '../lib/format';
import { useTentar, useToast } from '../ui/Toasts';

interface Resultado {
  classe: 'ok' | 'falta' | 'sobra';
  esperado: number;
  contado: number;
  texto: string;
}

export function descreverDiferenca(d: number): Pick<Resultado, 'classe' | 'texto'> {
  if (d === 0) return { classe: 'ok', texto: 'Caixa conferido, sem diferença.' };
  if (d < 0) return { classe: 'falta', texto: `Faltam ${brl(-d)} na gaveta.` };
  return { classe: 'sobra', texto: `Sobram ${brl(d)} na gaveta.` };
}

export function Caixa({ onMudou }: { onMudou: () => void }) {
  const tentar = useTentar();
  const avisar = useToast();
  // undefined = carregando; null = não há caixa aberto
  const [caixa, setCaixa] = useState<CaixaDto | null | undefined>(undefined);
  const [resultado, setResultado] = useState<Resultado | null>(null);

  const carregar = async () => {
    try {
      setCaixa(await api<CaixaDto>('/caixa/atual'));
    } catch (e) {
      // 409 = "não há caixa aberto"; qualquer outro erro é um problema de verdade
      if (e instanceof ApiError && e.status === 409) setCaixa(null);
      else { avisar(e instanceof Error ? e.message : String(e), true); setCaixa(null); }
    }
    onMudou();
  };

  useEffect(() => { void carregar(); /* eslint-disable-next-line react-hooks/exhaustive-deps */ }, []);

  if (caixa === undefined) return null;

  if (caixa === null) {
    return (
      <>
        <div className="barra"><h1>Caixa</h1></div>
        {resultado && (
          <div className={`resultado ${resultado.classe}`}>
            <b>Caixa fechado.</b> Esperado {brl(resultado.esperado)}, contado {brl(resultado.contado)}. {resultado.texto}
          </div>
        )}
        <AbrirCaixa onAberto={async () => { setResultado(null); avisar('Caixa aberto.'); await carregar(); }} />
      </>
    );
  }

  const fechar = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    const valorContado = Number(new FormData(e.currentTarget).get('v'));
    if (!confirm('Fechar o caixa agora? Novos pagamentos só serão aceitos após abrir outro.')) return;
    const r = await tentar(() => api<CaixaDto>('/caixa/fechar', 'POST', { valorContado }));
    if (!r) return;
    setResultado({ ...descreverDiferenca(Number(r.diferenca)), esperado: r.dinheiroEsperado, contado: Number(r.valorContado) });
    await carregar();
  };

  return (
    <>
      <div className="barra">
        <h1>Caixa</h1>
        <small>Aberto em {new Date(caixa.abertoEm).toLocaleString('pt-BR')}</small>
      </div>
      <div className="kpis">
        <div className="kpi"><small>Total recebido</small><b>{brl(caixa.totalRecebido)}</b></div>
        {(Object.entries(FORMAS) as [FormaPagamento, string][]).map(([k, nome]) => (
          <div key={k} className="kpi"><small>{nome}</small><b>{brl(caixa.recebidoPorForma[k])}</b></div>
        ))}
        <div className="kpi"><small>Dinheiro esperado na gaveta</small><b>{brl(caixa.dinheiroEsperado)}</b></div>
      </div>
      <div className="card" style={{ maxWidth: 420 }}>
        <h2>Fechar caixa</h2>
        <small>Conte o dinheiro da gaveta e informe abaixo. O sistema compara com o esperado (abertura + dinheiro recebido, já sem o troco).</small>
        <form onSubmit={fechar}>
          <label>Dinheiro contado<input name="v" type="number" step="0.01" min="0" required /></label>
          <div className="rodape"><button>Fechar caixa</button></div>
        </form>
      </div>
    </>
  );
}

function AbrirCaixa({ onAberto }: { onAberto: () => void }) {
  const tentar = useTentar();
  const enviar = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    const dados = new FormData(e.currentTarget);
    const operador = String(dados.get('op') ?? '').trim();
    const ok = await tentar(() => api('/caixa/abrir', 'POST', {
      valorAbertura: Number(dados.get('v')), operador: operador || null,
    }));
    if (ok !== undefined) onAberto();
  };
  return (
    <div className="card" style={{ maxWidth: 420 }}>
      <h2>Abrir caixa</h2>
      <form onSubmit={enviar}>
        <label>Dinheiro na gaveta (troco inicial)<input name="v" type="number" step="0.01" min="0" defaultValue="100.00" required /></label>
        <label>Operador<input name="op" maxLength={80} placeholder="Seu nome" /></label>
        <div className="rodape"><button>Abrir caixa</button></div>
      </form>
    </div>
  );
}
