import { createContext, useCallback, useContext, useRef, useState, type ReactNode } from 'react';

type Notificar = (mensagem: string, erro?: boolean) => void;

interface Aviso {
  id: number;
  mensagem: string;
  erro: boolean;
}

const Contexto = createContext<Notificar>(() => {});

export function ToastProvider({ children }: { children: ReactNode }) {
  const [avisos, setAvisos] = useState<Aviso[]>([]);
  const proximo = useRef(0);

  const notificar = useCallback<Notificar>((mensagem, erro = false) => {
    const id = ++proximo.current;
    setAvisos(a => [...a, { id, mensagem, erro }]);
    setTimeout(() => setAvisos(a => a.filter(x => x.id !== id)), erro ? 6000 : 3000);
  }, []);

  return (
    <Contexto.Provider value={notificar}>
      {children}
      <div id="toasts" role="status" aria-live="polite">
        {avisos.map(a => (
          <div key={a.id} className={'toast' + (a.erro ? ' erro' : '')}>{a.mensagem}</div>
        ))}
      </div>
    </Contexto.Provider>
  );
}

export const useToast = (): Notificar => useContext(Contexto);

/** Executa uma ação e mostra o erro (se houver) como aviso, em vez de deixar a promessa rejeitada. */
export function useTentar() {
  const notificar = useToast();
  return useCallback(
    async <T,>(acao: () => Promise<T>): Promise<T | undefined> => {
      try {
        return await acao();
      } catch (e) {
        notificar(e instanceof Error ? e.message : String(e), true);
        return undefined;
      }
    },
    [notificar],
  );
}
