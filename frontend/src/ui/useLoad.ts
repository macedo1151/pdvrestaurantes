import { useCallback, useEffect, useRef, useState, type Dispatch, type SetStateAction } from 'react';
import { modaisAbertos } from './Modal';
import { useTentar } from './Toasts';

interface Opcoes {
  /** Recarrega a cada N ms (pausado enquanto houver um modal aberto). */
  intervaloMs?: number;
}

/**
 * Carrega dados da API ao montar e quando `deps` mudam, com polling opcional.
 * Uma resposta atrasada nunca sobrescreve uma mais nova (nem a de uma tela que já foi fechada).
 */
export function useLoad<T>(carregar: () => Promise<T>, deps: readonly unknown[], { intervaloMs }: Opcoes = {}) {
  const [dados, setDados] = useState<T | undefined>(undefined);
  const tentar = useTentar();
  const ultimo = useRef(0);
  const funcao = useRef(carregar);
  funcao.current = carregar;

  const recarregar = useCallback(async () => {
    const numero = ++ultimo.current;
    const resultado = await tentar(() => funcao.current());
    if (numero === ultimo.current && resultado !== undefined) setDados(resultado);
  }, [tentar]);

  useEffect(() => {
    void recarregar();
    return () => {
      ultimo.current++; // descarta o que ainda estiver a caminho
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [recarregar, ...deps]);

  useEffect(() => {
    if (!intervaloMs) return;
    const id = setInterval(() => {
      if (modaisAbertos() === 0) void recarregar();
    }, intervaloMs);
    return () => clearInterval(id);
  }, [intervaloMs, recarregar]);

  return { dados, recarregar, setDados: setDados as Dispatch<SetStateAction<T | undefined>> };
}
