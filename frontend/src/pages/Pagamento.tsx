import { useState, type FormEvent } from 'react';
import { api } from '../api/client';
import type { Comanda, FormaPagamento } from '../api/types';
import { brl, FORMAS } from '../lib/format';
import { Modal } from '../ui/Modal';
import { useTentar, useToast } from '../ui/Toasts';

interface Props {
  comanda: Comanda;
  /** Valor sugerido (ex.: a parte de uma divisão). Padrão: o que falta pagar. */
  valor?: number;
  pagador?: string;
  onPago: (comanda: Comanda) => void;
  onClose: () => void;
}

export function PagamentoModal({ comanda, valor, pagador = '', onPago, onClose }: Props) {
  const tentar = useTentar();
  const avisar = useToast();
  const [forma, setForma] = useState<FormaPagamento>('DINHEIRO');
  const [aPagar, setAPagar] = useState((valor ?? comanda.restante).toFixed(2));
  const [recebido, setRecebido] = useState('');
  const [quem, setQuem] = useState(pagador);

  const dinheiro = forma === 'DINHEIRO';
  const troco = Number(recebido) - Number(aPagar);
  const mostrarTroco = dinheiro && recebido !== '' && troco >= 0;

  const enviar = async (e: FormEvent) => {
    e.preventDefault();
    const entregue = dinheiro && recebido !== '' ? Number(recebido) : null;
    const nova = await tentar(() =>
      api<Comanda>(`/comandas/${comanda.id}/pagamentos`, 'POST', {
        forma,
        valor: Number(aPagar),
        valorRecebido: entregue,
        pagador: quem.trim() || null,
      }),
    );
    if (!nova) return;
    const devolver = entregue === null ? 0 : entregue - Number(aPagar);
    onPago(nova);
    onClose();
    avisar(devolver > 0 ? `Pagamento registrado. Troco: ${brl(devolver)}` : 'Pagamento registrado.');
  };

  return (
    <Modal titulo="Receber pagamento" onClose={onClose}>
      <form onSubmit={enviar}>
        <label>Forma de pagamento</label>
        <div className="formas">
          {(Object.entries(FORMAS) as [FormaPagamento, string][]).map(([chave, nome]) => (
            <label key={chave} className="forma" style={{ margin: 0 }}>
              <input type="radio" name="forma" value={chave} checked={forma === chave} onChange={() => setForma(chave)} />
              <span>{nome}</span>
            </label>
          ))}
        </div>
        <label>Valor a pagar
          <input type="number" step="0.01" min="0.01" required value={aPagar} onChange={e => setAPagar(e.target.value)} />
        </label>
        {dinheiro && (
          <div>
            <label>Valor entregue pelo cliente
              <input type="number" step="0.01" min="0" value={recebido} onChange={e => setRecebido(e.target.value)} />
            </label>
            <div id="troco">{mostrarTroco ? `Troco: ${brl(troco)}` : ''}</div>
          </div>
        )}
        <label>Quem está pagando (opcional)
          <input maxLength={80} value={quem} onChange={e => setQuem(e.target.value)} />
        </label>
        <div className="rodape"><button>Confirmar</button></div>
      </form>
    </Modal>
  );
}
