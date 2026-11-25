'use strict';

/* ---------- utilidades ---------- */
const $ = (s, r = document) => r.querySelector(s);
const $$ = (s, r = document) => [...r.querySelectorAll(s)];
const brl = v => Number(v ?? 0).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
const qtd = v => Number(v).toLocaleString('pt-BR', { maximumFractionDigits: 3 });
const ESC = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' };
const esc = s => String(s ?? '').replace(/[&<>"']/g, c => ESC[c]);
const STATUS = { PENDENTE: 'Na fila', EM_PREPARO: 'Preparando', PRONTO: 'Pronto', ENTREGUE: 'Entregue', CANCELADO: 'Cancelado' };
const FORMAS = { DINHEIRO: 'Dinheiro', PIX: 'Pix', CREDITO: 'Crédito', DEBITO: 'Débito' };
const hoje = () => new Date().toLocaleDateString('sv-SE');

async function api(path, method = 'GET', body) {
  let r;
  try {
    r = await fetch('/api' + path, {
      method,
      headers: { 'Content-Type': 'application/json' },
      body: body === undefined ? undefined : JSON.stringify(body)
    });
  } catch {
    throw new Error('Sem conexão com o servidor');
  }
  if (r.status === 204) return null;
  let data = null;
  try { data = await r.json(); } catch { /* resposta sem corpo */ }
  if (!r.ok) {
    let msg = data?.detail || ('Erro ' + r.status);
    if (data?.campos) msg += ': ' + Object.entries(data.campos).map(([k, v]) => `${k} ${v}`).join('; ');
    throw new Error(msg);
  }
  return data;
}

function toast(msg, erro = false) {
  const t = document.createElement('div');
  t.className = 'toast' + (erro ? ' erro' : '');
  t.textContent = msg;
  $('#toasts').append(t);
  setTimeout(() => t.remove(), erro ? 6000 : 3000);
}

async function tentar(fn) {
  try { return await fn(); } catch (e) { toast(e.message, true); }
}

function modal(titulo, corpo, { larga = false, aoFechar } = {}) {
  const ov = document.createElement('div');
  ov.className = 'overlay';
  ov.innerHTML = `<div class="modal${larga ? ' larga' : ''}"><header><h2>${esc(titulo)}</h2>
    <button class="x" aria-label="Fechar">×</button></header><div class="corpo">${corpo}</div></div>`;
  const close = () => {
    if (!ov.isConnected) return;
    ov.remove();
    document.body.classList.toggle('sem-scroll', !!$('.overlay'));
    aoFechar?.();
  };
  $('.x', ov).onclick = close;
  document.body.append(ov);
  document.body.classList.add('sem-scroll');
  return { el: $('.corpo', ov), ov, close };
}

document.addEventListener('keydown', e => {
  if (e.key !== 'Escape') return;
  const ovs = $$('.overlay');
  if (ovs.length) $('.x', ovs[ovs.length - 1]).click();
});

/* ---------- navegação ---------- */
const state = { tab: 'salao', praca: 'COZINHA' };
const TABS = {
  salao: [renderSalao, 8000],
  cozinha: [renderCozinha, 30000],
  caixa: [renderCaixa, 0],
  estoque: [renderEstoque, 0],
  cardapio: [renderCardapio, 0],
  relatorios: [renderRelatorios, 0]
};
let timer;
// Evita que uma tela que demorou a carregar sobrescreva a aba para a qual o usuário já trocou.
const viva = tab => state.tab === tab;

function go(tab) {
  if (!TABS[tab]) tab = 'salao';
  state.tab = tab;
  history.replaceState(null, '', '#' + tab);
  $$('#menu button').forEach(b => b.classList.toggle('ativo', b.dataset.tab === tab));
  clearInterval(timer);
  assinarKds(tab === 'cozinha');
  const [fn, ms] = TABS[tab];
  tentar(fn);
  if (ms) timer = setInterval(() => { if (!$('.overlay')) tentar(fn); }, ms);
}

/* Fila da cozinha em tempo real (SSE): o servidor avisa quando a fila da praça muda.
   O polling da aba fica só para atualizar os minutos de espera. */
let fonteKds;
let pracaKds;
function assinarKds(ativo) {
  if (!ativo || pracaKds !== state.praca) {
    fonteKds?.close();
    fonteKds = null;
    pracaKds = null;
  }
  if (!ativo || fonteKds) return;
  pracaKds = state.praca;
  fonteKds = new EventSource('/api/kds/' + pracaKds + '/eventos');
  const atualizar = () => { if (viva('cozinha') && !$('.overlay')) tentar(renderCozinha); };
  fonteKds.addEventListener('fila', atualizar);
}

async function atualizarPill() {
  const pill = $('#pillCaixa');
  try {
    const cx = await api('/caixa/atual');
    pill.textContent = `Caixa aberto (${cx.operador || 'sem operador'})`;
    pill.className = 'pill aberto';
  } catch {
    pill.textContent = 'Caixa fechado';
    pill.className = 'pill';
  }
}

$('#menu').addEventListener('click', e => {
  const b = e.target.closest('button[data-tab]');
  if (b) go(b.dataset.tab);
});

/* ---------- salão ---------- */
async function renderSalao() {
  const [mesas, comandas] = await Promise.all([api('/mesas'), api('/comandas?status=ABERTA')]);
  if (!viva('salao')) return;
  const porMesa = new Map(comandas.map(c => [c.mesa, c]));
  mesas.sort((a, b) => a.numero - b.numero);
  const tiles = mesas.map(m => {
    const c = porMesa.get(m.numero);
    if (!c) return `<button class="mesa livre" data-mesa="${m.id}" data-numero="${m.numero}"><b>${m.numero}</b><span>Livre</span></button>`;
    const prontos = c.itens.filter(i => i.status === 'PRONTO').length;
    const min = Math.floor((Date.now() - new Date(c.abertaEm)) / 60000);
    return `<button class="mesa ocupada" data-comanda="${c.id}"><b>${m.numero}</b><span>${brl(c.total)}</span>
      <small>${min} min</small>${prontos ? `<em class="badge">${prontos} pronto${prontos > 1 ? 's' : ''}</em>` : ''}</button>`;
  }).join('');
  $('#view').innerHTML = `<div class="barra"><h1>Salão</h1><button class="sec" id="novaMesa">+ Mesa</button></div>
    ${mesas.length ? `<div class="mesas">${tiles}</div>` : '<p class="vazio">Nenhuma mesa cadastrada. Use "+ Mesa".</p>'}`;

  $('#novaMesa').onclick = () => {
    const proxima = mesas.reduce((mx, m) => Math.max(mx, m.numero), 0) + 1;
    const m = modal('Nova mesa', `<form><label>Número da mesa<input name="numero" type="number" min="1" value="${proxima}" required></label>
      <div class="rodape"><button>Criar</button></div></form>`);
    $('form', m.el).onsubmit = ev => {
      ev.preventDefault();
      tentar(async () => {
        await api('/mesas', 'POST', { numero: +ev.target.numero.value });
        m.close();
        renderSalao();
      });
    };
  };
  $$('.mesa.livre').forEach(b => b.onclick = () => abrirMesa(+b.dataset.mesa, b.dataset.numero));
  $$('.mesa.ocupada').forEach(b => b.onclick = () => abrirComanda(+b.dataset.comanda));
}

function abrirMesa(mesaId, numero) {
  const m = modal(`Abrir mesa ${numero}`, `<form><label>Serviço (%)<input name="serv" type="number" min="0" max="30" step="0.5" value="10"></label>
    <small>Pode ser 0 se o cliente pedir para retirar.</small>
    <div class="rodape"><button>Abrir comanda</button></div></form>`);
  $('form', m.el).onsubmit = ev => {
    ev.preventDefault();
    tentar(async () => {
      const c = await api('/comandas', 'POST', { mesaId, percentualServico: +ev.target.serv.value });
      m.close();
      abrirComanda(c.id);
    });
  };
}

/* ---------- comanda ---------- */
async function abrirComanda(id) {
  const produtos = (await api('/produtos')).filter(p => p.ativo);
  let c = await api('/comandas/' + id);
  let categoria = 'Todos', busca = '', fila = Promise.resolve(), pendentes = 0;

  const m = modal(`Mesa ${c.mesa}`, `<div class="comanda">
    <section>
      <div id="itens"></div><div class="pags" id="pags"></div><div class="totais" id="totais"></div><div class="acoes" id="acoes"></div>
    </section>
    <section>
      <input id="busca" placeholder="Buscar produto" autocomplete="off">
      <div id="cats" class="chips"></div>
      <input id="obs" placeholder="Observação do próximo item (ex.: sem cebola)" maxlength="200">
      <div id="tiles" class="tiles"></div>
    </section></div>`, { larga: true, aoFechar: () => { clearInterval(iv); if (state.tab === 'salao') tentar(renderSalao); } });

  const setC = nova => { c = nova; drawItens(); };

  function drawItens() {
    const ativos = c.itens.filter(i => i.status !== 'CANCELADO');
    $('#itens', m.el).innerHTML = c.itens.length ? c.itens.map(i => `<div class="item ${i.status.toLowerCase()}">
      <div class="info"><b>${i.quantidade}× ${esc(i.produto)}</b>${i.observacao ? `<small>${esc(i.observacao)}</small>` : ''}
      <span class="chip ${i.status}">${STATUS[i.status]}</span></div>
      <div class="val">${i.status === 'CANCELADO' ? '' : brl(i.total)}</div>
      <div class="bt">${i.status === 'PRONTO' ? `<button class="ok mini" data-entregar="${i.id}">Marcar entregue</button>` : ''}
      ${['PENDENTE', 'EM_PREPARO'].includes(i.status) ? `<button class="perigo mini" data-cancelar="${i.id}" data-status="${i.status}">Cancelar</button>` : ''}</div>
      </div>`).join('') : '<p class="vazio">Nenhum item ainda. Toque em um produto para lançar.</p>';
    $('#pags', m.el).innerHTML = c.pagamentos.map(p =>
      `Pago: ${FORMAS[p.forma]} ${brl(p.valor)}${p.pagador ? ' (' + esc(p.pagador) + ')' : ''}`).join('<br>');
    $('#totais', m.el).innerHTML = `<div><span>Itens</span><span>${brl(c.subtotal)}</span></div>
      <div><span>Serviço (${Number(c.percentualServico)}%)</span><span>${brl(c.servico)}</span></div>
      <div><span>Total</span><b>${brl(c.total)}</b></div>
      <div><span>Já pago</span><span>${brl(c.pago)}</span></div>
      <div class="restante"><span>Falta pagar</span><span>${brl(c.restante)}</span></div>`;
    const quitada = Number(c.restante) === 0 && ativos.length > 0;
    $('#acoes', m.el).innerHTML = `<button id="bPagar" ${Number(c.restante) > 0 ? '' : 'disabled'}>Receber pagamento</button>
      <button id="bDividir" class="sec" ${Number(c.restante) > 0 ? '' : 'disabled'}>Dividir conta</button>
      <button id="bFechar" class="${quitada ? 'ok' : 'sec'}" ${quitada ? '' : 'disabled'}>Fechar comanda</button>`;
    $('#bPagar', m.el).onclick = () => pagar(c, {}, setC);
    $('#bDividir', m.el).onclick = () => dividir(c, setC);
    $('#bFechar', m.el).onclick = () => tentar(async () => {
      await api(`/comandas/${id}/fechar`, 'POST');
      toast('Comanda fechada. Mesa liberada.');
      m.close();
    });
  }

  function drawMenu() {
    const cats = ['Todos', ...new Set(produtos.map(p => p.categoria))];
    $('#cats', m.el).innerHTML = cats.map(k => `<button class="${k === categoria ? 'ativo' : ''}" data-cat="${esc(k)}">${esc(k)}</button>`).join('');
    const lista = produtos.filter(p => (categoria === 'Todos' || p.categoria === categoria) &&
      p.nome.toLowerCase().includes(busca.toLowerCase()));
    $('#tiles', m.el).innerHTML = lista.map(p => `<button class="tile" data-produto="${p.id}">${esc(p.nome)}<span>${brl(p.preco)}</span></button>`).join('')
      || '<p class="vazio">Nenhum produto encontrado.</p>';
  }

  m.el.addEventListener('click', async e => {
    const cat = e.target.closest('[data-cat]');
    if (cat) { categoria = cat.dataset.cat; drawMenu(); return; }
    const tile = e.target.closest('[data-produto]');
    if (tile) {
      // Cada toque vira um item; as chamadas entram numa fila para chegar ao servidor na ordem do toque.
      const obs = $('#obs', m.el).value.trim();
      $('#obs', m.el).value = '';
      const produtoId = +tile.dataset.produto;
      pendentes++;
      fila = fila.then(() => tentar(async () =>
        setC(await api(`/comandas/${id}/itens`, 'POST', { produtoId, quantidade: 1, observacao: obs || null })))
        .finally(() => pendentes--));
      return;
    }
    const ent = e.target.closest('[data-entregar]');
    if (ent) {
      tentar(async () => { await api(`/itens/${ent.dataset.entregar}/status`, 'PATCH', { status: 'ENTREGUE' }); setC(await api('/comandas/' + id)); });
      return;
    }
    const can = e.target.closest('[data-cancelar]');
    if (can) {
      const aviso = can.dataset.status === 'EM_PREPARO'
        ? 'Este item já está em preparo. Cancelar registra a perda dos insumos. Continuar?' : 'Cancelar este item?';
      if (confirm(aviso)) tentar(async () => setC(await api(`/comandas/${id}/itens/${can.dataset.cancelar}`, 'DELETE')));
    }
  });
  $('#busca', m.el).oninput = e => { busca = e.target.value; drawMenu(); };

  const iv = setInterval(async () => {
    if (!m.ov.isConnected || pendentes > 0) return;
    try { const nova = await api('/comandas/' + id); if (pendentes === 0) setC(nova); } catch { /* tenta de novo no próximo ciclo */ }
  }, 6000);

  drawItens();
  drawMenu();
}

/* ---------- pagamento e divisão ---------- */
function pagar(c, { valor, pagador } = {}, aoPagar) {
  const sugerido = valor ?? Number(c.restante);
  const m = modal('Receber pagamento', `<form>
    <label>Forma de pagamento</label>
    <div class="formas">${Object.entries(FORMAS).map(([k, v], i) =>
      `<label class="forma" style="margin:0"><input type="radio" name="forma" value="${k}" ${i === 0 ? 'checked' : ''}><span>${v}</span></label>`).join('')}</div>
    <label>Valor a pagar<input name="valor" type="number" step="0.01" min="0.01" value="${sugerido.toFixed(2)}" required></label>
    <div id="boxRecebido"><label>Valor entregue pelo cliente<input name="recebido" type="number" step="0.01" min="0"></label>
      <div id="troco"></div></div>
    <label>Quem está pagando (opcional)<input name="pagador" maxlength="80" value="${esc(pagador || '')}"></label>
    <div class="rodape"><button>Confirmar</button></div></form>`);
  const f = $('form', m.el);
  const forma = () => f.forma.value;
  const atualizarTroco = () => {
    $('#boxRecebido', m.el).style.display = forma() === 'DINHEIRO' ? '' : 'none';
    const t = (+f.recebido.value || 0) - (+f.valor.value || 0);
    $('#troco', m.el).textContent = forma() === 'DINHEIRO' && f.recebido.value && t >= 0 ? `Troco: ${brl(t)}` : '';
  };
  f.addEventListener('input', atualizarTroco);
  atualizarTroco();
  f.onsubmit = ev => {
    ev.preventDefault();
    tentar(async () => {
      const dinheiro = forma() === 'DINHEIRO' && f.recebido.value;
      const troco = dinheiro ? +f.recebido.value - +f.valor.value : 0;
      const nova = await api(`/comandas/${c.id}/pagamentos`, 'POST', {
        forma: forma(), valor: +f.valor.value,
        valorRecebido: dinheiro ? +f.recebido.value : null, pagador: f.pagador.value.trim() || null
      });
      m.close();
      aoPagar(nova);
      toast(troco > 0 ? `Pagamento registrado. Troco: ${brl(troco)}` : 'Pagamento registrado.');
    });
  };
}

function dividir(c, setC) {
  let modo = 'igual';
  const m = modal('Dividir conta', `<div class="seg"><button data-modo="igual" class="ativo">Em partes iguais</button>
    <button data-modo="itens">Por item consumido</button></div>
    <label>Quantas pessoas<input id="n" type="number" min="2" max="50" value="2"></label>
    <div id="lista"></div><div class="rodape"><button id="calc">Calcular</button></div><div id="res"></div>`);
  const nPessoas = () => Math.min(50, Math.max(2, +$('#n', m.el).value || 2));

  // O botão "Calcular" é criado uma vez só: se fosse recriado ao mudar o número de pessoas,
  // o clique que tirou o foco do campo cairia num botão já removido da tela.
  function desenharLista() {
    if (modo === 'igual') { $('#lista', m.el).innerHTML = ''; return; }
    const n = nPessoas();
    const ativos = c.itens.filter(i => i.status !== 'CANCELADO');
    const antes = Object.fromEntries($$('select[data-item]', m.el).map(s => [s.dataset.item, s.value]));
    $('#lista', m.el).innerHTML = `<small>Itens com mais de uma unidade vão inteiros para uma pessoa; para repartir, use a divisão em partes iguais.</small>
      ${ativos.map(i => `<div class="linha-item"><span>${i.quantidade}× ${esc(i.produto)} <small>${brl(i.total)}</small></span>
      <select data-item="${i.id}">${Array.from({ length: n }, (_, k) => `<option value="${k + 1}" ${antes[i.id] == k + 1 ? 'selected' : ''}>Pessoa ${k + 1}</option>`).join('')}</select></div>`).join('')}`;
  }

  function desenhar() {
    $$('.seg button', m.el).forEach(b => b.classList.toggle('ativo', b.dataset.modo === modo));
    $('#res', m.el).innerHTML = '';
    desenharLista();
  }

  function mostrar(partes, extra) {
    $('#res', m.el).innerHTML = partes.map((p, i) => `<div class="parte" data-i="${i}">
      <span><b>${esc(p.nome)}</b>${p.detalhe ? `<br><small>${p.detalhe}</small>` : ''}</span>
      <span><b>${brl(p.valor)}</b> <button class="mini" data-receber="${i}">Receber</button></span></div>`).join('') + (extra || '');
    $$('[data-receber]', m.el).forEach(b => b.onclick = () => {
      const p = partes[+b.dataset.receber];
      pagar(c, { valor: p.valor, pagador: p.nome }, nova => {
        c = nova; setC(nova);
        b.closest('.parte').classList.add('paga');
        b.disabled = true;
      });
    });
  }

  function calcular() {
    tentar(async () => {
      const n = nPessoas();
      if (modo === 'igual') {
        const d = await api(`/comandas/${c.id}/divisao?pessoas=${n}`);
        mostrar(d.parcelas.map((v, i) => ({ nome: `Pessoa ${i + 1}`, valor: v })));
      } else {
        const grupos = {};
        $$('select[data-item]', m.el).forEach(s => (grupos[s.value] ??= []).push(+s.dataset.item));
        const d = await api(`/comandas/${c.id}/divisao-itens`, 'POST', {
          grupos: Object.entries(grupos).map(([k, ids]) => ({ pagador: `Pessoa ${k}`, itemIds: ids }))
        });
        mostrar(d.partes.map(p => ({ nome: p.pagador, valor: p.total, detalhe: `itens ${brl(p.subtotal)} + serviço ${brl(p.servico)}` })),
          Number(d.subtotalNaoAtribuido) > 0 ? `<p class="vazio">Itens sem dono: ${brl(d.subtotalNaoAtribuido)}</p>` : '');
      }
    });
  }

  m.el.addEventListener('click', e => {
    const b = e.target.closest('[data-modo]');
    if (b) { modo = b.dataset.modo; desenhar(); }
  });
  $('#n', m.el).onchange = desenharLista;
  $('#calc', m.el).onclick = calcular;
  desenhar();
}

/* ---------- cozinha e bar ---------- */
async function renderCozinha() {
  const itens = await api('/kds/' + state.praca);
  if (!viva('cozinha')) return;
  $('#view').innerHTML = `<div class="barra"><h1>Produção</h1>
    <div class="seg" style="margin:0">${['COZINHA', 'BAR', 'SOBREMESA'].map(p =>
      `<button data-praca="${p}" class="${p === state.praca ? 'ativo' : ''}">${p[0] + p.slice(1).toLowerCase()}</button>`).join('')}</div></div>
    ${itens.length ? `<div class="fila">${itens.map(i => `<div class="pedido ${i.status}${i.minutosEspera >= 15 ? ' atrasado' : ''}">
      <small>Mesa ${i.mesa} · ${i.minutosEspera} min</small>
      <div class="nome">${i.quantidade}× ${esc(i.produto)}</div>
      ${i.observacao ? `<div class="obs">${esc(i.observacao)}</div>` : ''}
      <button data-item="${i.itemId}" data-prox="${i.status === 'PENDENTE' ? 'EM_PREPARO' : 'PRONTO'}" class="${i.status === 'PENDENTE' ? '' : 'ok'}">
        ${i.status === 'PENDENTE' ? 'Iniciar preparo' : 'Marcar como pronto'}</button></div>`).join('')}</div>`
      : '<p class="vazio">Fila vazia.</p>'}`;
  $$('[data-praca]').forEach(b => b.onclick = () => { state.praca = b.dataset.praca; assinarKds(true); tentar(renderCozinha); });
  $$('[data-item]').forEach(b => b.onclick = () => tentar(async () => {
    await api(`/itens/${b.dataset.item}/status`, 'PATCH', { status: b.dataset.prox });
    renderCozinha();
  }));
}

/* ---------- caixa ---------- */
async function renderCaixa(resultado) {
  let cx = null;
  try { cx = await api('/caixa/atual'); } catch { /* sem caixa aberto */ }
  atualizarPill();
  if (!viva('caixa')) return;
  if (!cx) {
    $('#view').innerHTML = `<div class="barra"><h1>Caixa</h1></div>${resultado || ''}
      <div class="card" style="max-width:420px"><h2>Abrir caixa</h2>
      <form id="fAbrir"><label>Dinheiro na gaveta (troco inicial)<input name="v" type="number" step="0.01" min="0" value="100.00" required></label>
      <label>Operador<input name="op" maxlength="80" placeholder="Seu nome"></label>
      <div class="rodape"><button>Abrir caixa</button></div></form></div>`;
    $('#fAbrir').onsubmit = ev => {
      ev.preventDefault();
      tentar(async () => {
        await api('/caixa/abrir', 'POST', { valorAbertura: +ev.target.v.value, operador: ev.target.op.value.trim() || null });
        toast('Caixa aberto.');
        renderCaixa();
      });
    };
    return;
  }
  $('#view').innerHTML = `<div class="barra"><h1>Caixa</h1><small>Aberto em ${new Date(cx.abertoEm).toLocaleString('pt-BR')}</small></div>
    <div class="kpis">
      <div class="kpi"><small>Total recebido</small><b>${brl(cx.totalRecebido)}</b></div>
      ${Object.entries(FORMAS).map(([k, v]) => `<div class="kpi"><small>${v}</small><b>${brl(cx.recebidoPorForma[k])}</b></div>`).join('')}
      <div class="kpi"><small>Dinheiro esperado na gaveta</small><b>${brl(cx.dinheiroEsperado)}</b></div></div>
    <div class="card" style="max-width:420px"><h2>Fechar caixa</h2>
    <small>Conte o dinheiro da gaveta e informe abaixo. O sistema compara com o esperado (abertura + dinheiro recebido, já sem o troco).</small>
    <form id="fFechar"><label>Dinheiro contado<input name="v" type="number" step="0.01" min="0" required></label>
    <div class="rodape"><button>Fechar caixa</button></div></form></div>`;
  $('#fFechar').onsubmit = ev => {
    ev.preventDefault();
    if (!confirm('Fechar o caixa agora? Novos pagamentos só serão aceitos após abrir outro.')) return;
    tentar(async () => {
      const r = await api('/caixa/fechar', 'POST', { valorContado: +ev.target.v.value });
      const d = Number(r.diferenca);
      const cls = d === 0 ? 'ok' : d < 0 ? 'falta' : 'sobra';
      const txt = d === 0 ? 'Caixa conferido, sem diferença.' : d < 0 ? `Faltam ${brl(-d)} na gaveta.` : `Sobram ${brl(d)} na gaveta.`;
      renderCaixa(`<div class="resultado ${cls}"><b>Caixa fechado.</b> Esperado ${brl(r.dinheiroEsperado)}, contado ${brl(r.valorContado)}. ${txt}</div>`);
    });
  };
}

/* ---------- estoque ---------- */
async function renderEstoque(soAlertas = false) {
  const todos = await api('/insumos');
  if (!viva('estoque')) return;
  const lista = (soAlertas ? todos.filter(i => i.abaixoDoMinimo) : todos).sort((a, b) => a.nome.localeCompare(b.nome));
  const alertas = todos.filter(i => i.abaixoDoMinimo).length;
  $('#view').innerHTML = `<div class="barra"><h1>Estoque</h1><div style="display:flex;gap:8px">
    <button class="sec" id="fAlerta">${soAlertas ? 'Ver todos' : `Só alertas (${alertas})`}</button><button id="novo">+ Insumo</button></div></div>
    ${lista.length ? `<div class="tabela-rolagem"><table><tr><th>Insumo</th><th class="num">Saldo</th><th class="num">Mínimo</th><th class="num">Custo unit.</th><th></th></tr>
    ${lista.map(i => `<tr class="${i.abaixoDoMinimo ? 'alerta' : ''}"><td>${esc(i.nome)}${i.abaixoDoMinimo ? ' <small style="color:var(--vermelho)">repor</small>' : ''}</td>
      <td class="num">${qtd(i.estoqueAtual)} ${esc(i.unidade)}</td><td class="num">${qtd(i.estoqueMinimo)}</td>
      <td class="num">${brl(i.custoUnitario)}</td><td class="num"><button class="mini" data-entrada="${i.id}" data-nome="${esc(i.nome)}">Entrada</button></td></tr>`).join('')}</table></div>`
      : '<p class="vazio">Nada por aqui.</p>'}`;
  $('#fAlerta').onclick = () => tentar(() => renderEstoque(!soAlertas));
  $('#novo').onclick = () => {
    const m = modal('Novo insumo', `<form><label>Nome<input name="nome" required></label>
      <label>Unidade (kg, un, l...)<input name="un" maxlength="10" required></label>
      <label>Estoque inicial<input name="ini" type="number" step="0.001" min="0" value="0" required></label>
      <label>Estoque mínimo (gera alerta)<input name="min" type="number" step="0.001" min="0" value="0" required></label>
      <label>Custo por unidade (R$)<input name="custo" type="number" step="0.01" min="0" value="0" required></label>
      <div class="rodape"><button>Salvar</button></div></form>`);
    $('form', m.el).onsubmit = ev => {
      ev.preventDefault();
      const f = ev.target;
      tentar(async () => {
        await api('/insumos', 'POST', { nome: f.nome.value.trim(), unidade: f.un.value.trim(), estoqueInicial: +f.ini.value, estoqueMinimo: +f.min.value, custoUnitario: +f.custo.value });
        m.close(); renderEstoque(soAlertas);
      });
    };
  };
  $$('[data-entrada]').forEach(b => b.onclick = () => {
    const m = modal(`Entrada: ${b.dataset.nome}`, `<form><label>Quantidade recebida<input name="q" type="number" step="0.001" min="0.001" required autofocus></label>
      <label>Motivo (opcional)<input name="mot" placeholder="Ex.: compra fornecedor X"></label>
      <div class="rodape"><button>Registrar entrada</button></div></form>`);
    $('form', m.el).onsubmit = ev => {
      ev.preventDefault();
      tentar(async () => {
        await api(`/insumos/${b.dataset.entrada}/entrada`, 'POST', { quantidade: +ev.target.q.value, motivo: ev.target.mot.value.trim() || null });
        m.close(); toast('Entrada registrada.'); renderEstoque(soAlertas);
      });
    };
  });
}

/* ---------- cardápio ---------- */
async function renderCardapio() {
  const [produtos, insumos] = await Promise.all([api('/produtos'), api('/insumos')]);
  if (!viva('cardapio')) return;
  const ativos = produtos.filter(p => p.ativo).sort((a, b) => a.categoria.localeCompare(b.categoria) || a.nome.localeCompare(b.nome));
  $('#view').innerHTML = `<div class="barra"><h1>Cardápio</h1><button id="novo">+ Produto</button></div>
    ${ativos.length ? `<div class="tabela-rolagem"><table><tr><th>Produto</th><th>Categoria</th><th>Praça</th><th class="num">Preço</th><th class="num">Custo</th><th class="num">Margem</th><th></th></tr>
    ${ativos.map(p => `<tr><td>${esc(p.nome)}<br><small>${p.ficha.map(f => `${qtd(f.quantidade)} ${esc(f.unidade)} ${esc(f.insumo)}`).join(', ') || 'sem ficha técnica'}</small></td>
      <td>${esc(p.categoria)}</td><td>${p.praca[0] + p.praca.slice(1).toLowerCase()}</td><td class="num">${brl(p.preco)}</td>
      <td class="num">${brl(p.custo)}</td><td class="num">${Number(p.margemPercentual).toLocaleString('pt-BR')}%</td>
      <td class="num"><button class="mini sec" data-editar="${p.id}">Editar</button> <button class="mini perigo" data-desativar="${p.id}" data-nome="${esc(p.nome)}">Tirar</button></td></tr>`).join('')}</table></div>`
      : '<p class="vazio">Cardápio vazio.</p>'}`;

  const form = produto => {
    const m = modal(produto ? 'Editar produto' : 'Novo produto', `<form>
      <label>Nome<input name="nome" required value="${esc(produto?.nome)}"></label>
      <label>Categoria<input name="cat" required list="cats" value="${esc(produto?.categoria)}"></label>
      <datalist id="cats">${[...new Set(produtos.map(p => p.categoria))].map(c => `<option value="${esc(c)}">`).join('')}</datalist>
      <label>Preço de venda (R$)<input name="preco" type="number" step="0.01" min="0.01" required value="${produto?.preco ?? ''}"></label>
      <label>Praça (para onde o pedido vai)<select name="praca">${['COZINHA', 'BAR', 'SOBREMESA'].map(p =>
        `<option value="${p}" ${produto?.praca === p ? 'selected' : ''}>${p[0] + p.slice(1).toLowerCase()}</option>`).join('')}</select></label>
      <label>Ficha técnica (insumos gastos por unidade vendida)</label><div id="ficha"></div>
      <button type="button" class="sec mini" id="addIng">+ ingrediente</button>
      <div class="rodape"><button>Salvar</button></div></form>`);
    const linha = (insumoId = '', q = '') => {
      const d = document.createElement('div');
      d.className = 'ficha-linha';
      d.innerHTML = `<select><option value="">Insumo...</option>${insumos.map(i => `<option value="${i.id}" ${i.id == insumoId ? 'selected' : ''}>${esc(i.nome)} (${esc(i.unidade)})</option>`).join('')}</select>
        <input type="number" step="0.001" min="0.001" placeholder="Qtd" value="${q}"><button type="button" class="perigo mini">×</button>`;
      $('button', d).onclick = () => d.remove();
      $('#ficha', m.el).append(d);
    };
    (produto?.ficha || []).forEach(f => linha(f.insumoId, f.quantidade));
    $('#addIng', m.el).onclick = () => linha();
    $('form', m.el).onsubmit = ev => {
      ev.preventDefault();
      const f = ev.target;
      const ficha = $$('.ficha-linha', m.el).map(d => ({ insumoId: +$('select', d).value, quantidade: +$('input', d).value })).filter(l => l.insumoId && l.quantidade > 0);
      tentar(async () => {
        const corpo = { nome: f.nome.value.trim(), categoria: f.cat.value.trim(), preco: +f.preco.value, praca: f.praca.value, ficha };
        await (produto ? api('/produtos/' + produto.id, 'PUT', corpo) : api('/produtos', 'POST', corpo));
        m.close(); toast('Produto salvo.'); renderCardapio();
      });
    };
  };
  $('#novo').onclick = () => form(null);
  $$('[data-editar]').forEach(b => b.onclick = () => form(produtos.find(p => p.id == b.dataset.editar)));
  $$('[data-desativar]').forEach(b => b.onclick = () => {
    if (confirm(`Tirar "${b.dataset.nome}" do cardápio? O histórico de vendas é mantido.`))
      tentar(async () => { await api('/produtos/' + b.dataset.desativar, 'DELETE'); renderCardapio(); });
  });
}

/* ---------- relatórios ---------- */
function renderRelatorios() {
  const inicio = hoje().slice(0, 8) + '01';
  $('#view').innerHTML = `<div class="barra"><h1>Relatório de vendas</h1></div>
    <form class="filtros" id="fRel"><label>De<input type="date" name="de" value="${inicio}" required></label>
    <label>Até<input type="date" name="ate" value="${hoje()}" required></label><button>Gerar</button></form><div id="rel"></div>`;
  const gerar = () => tentar(async () => {
    const f = $('#fRel');
    const r = await api(`/relatorios/vendas?de=${f.de.value}&ate=${f.ate.value}`);
    if (!viva('relatorios') || !$('#rel')) return;
    $('#rel').innerHTML = `<div class="kpis">
      <div class="kpi"><small>Comandas fechadas</small><b>${r.comandas}</b></div>
      <div class="kpi"><small>Faturamento (itens)</small><b>${brl(r.faturamento)}</b></div>
      <div class="kpi"><small>Serviço arrecadado</small><b>${brl(r.servicoArrecadado)}</b></div>
      <div class="kpi"><small>Custo da mercadoria</small><b>${brl(r.custoMercadoria)}</b></div>
      <div class="kpi"><small>Margem bruta</small><b>${Number(r.margemBruta).toLocaleString('pt-BR')}%</b></div>
      <div class="kpi"><small>Ticket médio</small><b>${brl(r.ticketMedio)}</b></div></div>
      ${r.maisVendidos.length ? `<div class="tabela-rolagem"><table><tr><th>Produto</th><th class="num">Qtd</th><th class="num">Receita</th><th class="num">Custo</th></tr>
      ${r.maisVendidos.map(p => `<tr><td>${esc(p.produto)}</td><td class="num">${p.quantidade}</td><td class="num">${brl(p.receita)}</td><td class="num">${brl(p.custo)}</td></tr>`).join('')}</table></div>`
        : '<p class="vazio">Nenhuma venda no período.</p>'}`;
  });
  $('#fRel').onsubmit = ev => { ev.preventDefault(); gerar(); };
  gerar();
}

/* ---------- início ---------- */
atualizarPill();
setInterval(atualizarPill, 15000);
go(location.hash.slice(1) || 'salao');
