/** Erro da API já com a mensagem pronta para mostrar ao usuário. */
export class ApiError extends Error {
  readonly status: number;

  constructor(message: string, status: number) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
  }
}

type Metodo = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';

interface Problema {
  detail?: string;
  campos?: Record<string, string>;
}

/**
 * Chama a API (/api + caminho). Os erros chegam no formato RFC 7807 (`detail`, e `campos` na validação);
 * viram um ApiError com texto legível. O navegador cuida do login (HTTP Basic) por conta própria.
 */
export async function api<T>(caminho: string, metodo: Metodo = 'GET', corpo?: unknown): Promise<T> {
  let resposta: Response;
  try {
    resposta = await fetch('/api' + caminho, {
      method: metodo,
      headers: { 'Content-Type': 'application/json' },
      body: corpo === undefined ? undefined : JSON.stringify(corpo),
    });
  } catch {
    throw new ApiError('Sem conexão com o servidor', 0);
  }
  if (resposta.status === 204) return null as T;

  let dados: unknown = null;
  try {
    dados = await resposta.json();
  } catch {
    /* resposta sem corpo */
  }
  if (!resposta.ok) {
    const p = (dados ?? {}) as Problema;
    let mensagem = p.detail || `Erro ${resposta.status}`;
    if (p.campos) {
      mensagem += ': ' + Object.entries(p.campos).map(([k, v]) => `${k} ${v}`).join('; ');
    }
    throw new ApiError(mensagem, resposta.status);
  }
  return dados as T;
}
