import { useCallback, useEffect, useState } from 'react';
import { api } from './api/client';
import type { Caixa as CaixaDto } from './api/types';
import { Caixa } from './pages/Caixa';
import { Cardapio } from './pages/Cardapio';
import { Cozinha } from './pages/Cozinha';
import { Estoque } from './pages/Estoque';
import { KdsTela } from './pages/KdsTela';
import { Relatorios } from './pages/Relatorios';
import { Salao } from './pages/Salao';

interface Aba {
  id: string;
  titulo: string;
}

const ABAS: Aba[] = [
  { id: 'salao', titulo: 'Salão' },
  { id: 'cozinha', titulo: 'Cozinha e bar' },
  { id: 'caixa', titulo: 'Caixa' },
  { id: 'estoque', titulo: 'Estoque' },
  { id: 'cardapio', titulo: 'Cardápio' },
  { id: 'relatorios', titulo: 'Relatórios' },
];

const lerHash = (): string => location.hash.replace(/^#\/?/, '') || 'salao';

function useAba(): [string, (aba: string) => void] {
  const [aba, setAba] = useState(lerHash);
  useEffect(() => {
    const aoMudar = () => setAba(lerHash());
    window.addEventListener('hashchange', aoMudar);
    return () => window.removeEventListener('hashchange', aoMudar);
  }, []);
  return [aba, (nova: string) => { location.hash = nova; }];
}

/** Mostra se há caixa aberto e quem o abriu; atualiza a cada 15 s e quando o caixa muda. */
function usePillCaixa() {
  const [caixa, setCaixa] = useState<CaixaDto | null | undefined>(undefined);
  const atualizar = useCallback(async () => {
    try {
      setCaixa(await api<CaixaDto>('/caixa/atual'));
    } catch {
      setCaixa(null);
    }
  }, []);
  useEffect(() => {
    void atualizar();
    const id = setInterval(() => void atualizar(), 15_000);
    return () => clearInterval(id);
  }, [atualizar]);
  return { caixa, atualizar };
}

/**
 * A tela de cada aba. Fica FORA do App de propósito: um componente declarado dentro de outro ganha uma
 * identidade nova a cada render, e o React desmontaria e recriaria a tela inteira (perdendo o estado).
 */
function Pagina({ aba, onCaixaMudou }: { aba: string; onCaixaMudou: () => void }) {
  switch (aba) {
    case 'cozinha': return <Cozinha />;
    case 'caixa': return <Caixa onMudou={onCaixaMudou} />;
    case 'estoque': return <Estoque />;
    case 'cardapio': return <Cardapio />;
    case 'relatorios': return <Relatorios />;
    default: return <Salao />;
  }
}

export function App() {
  const [aba, ir] = useAba();
  const { caixa, atualizar } = usePillCaixa();

  if (aba === 'kds') return <KdsTela />;

  const ativa = ABAS.some(a => a.id === aba) ? aba : 'salao';

  return (
    <>
      <header className="topo">
        <strong className="marca">PDV Restaurante</strong>
        <nav id="menu">
          {ABAS.map(a => (
            <button key={a.id} className={a.id === ativa ? 'ativo' : ''} onClick={() => ir(a.id)}>{a.titulo}</button>
          ))}
        </nav>
        <span className={'pill' + (caixa ? ' aberto' : '')}>
          {caixa === undefined ? '...' : caixa ? `Caixa aberto (${caixa.operador || 'sem operador'})` : 'Caixa fechado'}
        </span>
      </header>

      <main>
        {/* a chave recria a tela ao trocar de aba, descartando qualquer carga que ficou pendente */}
        <Pagina aba={ativa} onCaixaMudou={atualizar} key={ativa} />
      </main>

      <footer className="rodape-site">
        <a href="/swagger">Documentação da API</a>
        <a href="#kds">Tela cheia da cozinha</a>
      </footer>
    </>
  );
}
