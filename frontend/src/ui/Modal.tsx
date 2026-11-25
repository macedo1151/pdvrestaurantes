import { useEffect, useRef, type ReactNode } from 'react';

// Pilha de modais abertos: Esc fecha só o do topo, e o polling das telas pausa enquanto há algum aberto.
const pilha: number[] = [];
let sequencia = 0;

export const modaisAbertos = (): number => pilha.length;

interface Props {
  titulo: string;
  larga?: boolean;
  onClose: () => void;
  children: ReactNode;
}

export function Modal({ titulo, larga = false, onClose, children }: Props) {
  const fechar = useRef(onClose);
  fechar.current = onClose;

  useEffect(() => {
    const id = ++sequencia;
    pilha.push(id);
    document.body.classList.add('sem-scroll');
    const aoTeclar = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && pilha[pilha.length - 1] === id) fechar.current();
    };
    document.addEventListener('keydown', aoTeclar);
    return () => {
      document.removeEventListener('keydown', aoTeclar);
      pilha.splice(pilha.indexOf(id), 1);
      if (pilha.length === 0) document.body.classList.remove('sem-scroll');
    };
  }, []);

  return (
    <div className="overlay">
      <div className={'modal' + (larga ? ' larga' : '')} role="dialog" aria-modal="true" aria-label={titulo}>
        <header>
          <h2>{titulo}</h2>
          <button className="x" aria-label="Fechar" onClick={() => onClose()}>×</button>
        </header>
        <div className="corpo">{children}</div>
      </div>
    </div>
  );
}
