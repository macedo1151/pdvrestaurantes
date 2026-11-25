# PDV Restaurante

[![CI](https://github.com/macedo1151/pdvrestaurantes/actions/workflows/ci.yml/badge.svg)](https://github.com/macedo1151/pdvrestaurantes/actions/workflows/ci.yml)

API de ponto de venda para restaurante feita em Java 21 e Spring Boot 4. Em vez de tentar cobrir tudo que um PDV
faz, o projeto ataca quatro problemas que aparecem todo dia em salão e cozinha e que costumam ser resolvidos
no improviso:

1. **Fechar conta de mesa grande.** Dividir por pessoa na calculadora e errar um centavo, ou cobrar o serviço de
   quem só tomou uma água.
2. **Estoque que nunca bate.** O insumo só é baixado quando alguém lembra de dar baixa, então o sistema vende
   hambúrguer sem ter pão.
3. **Pedido que não chega na cozinha certa.** Bebida e prato saem da mesma comanda, mas são feitos em lugares
   diferentes.
4. **Caixa que fecha com diferença e ninguém sabe de onde veio.**

Este README explica o que cada parte faz e por que foi feita daquele jeito.

## O que tem

| Área | O que resolve |
|---|---|
| Comanda por mesa | Abre, lança itens, cancela item, calcula subtotal, serviço e total. |
| Divisão de conta | Em partes iguais (sem perder centavo) ou por item consumido, com serviço proporcional. |
| Pagamento parcial | Vários pagamentos na mesma conta, em formas diferentes, com troco no dinheiro. |
| Ficha técnica | Cada produto declara quanto de cada insumo usa. Lançar o item baixa o estoque na hora. |
| Estoque | Entrada de mercadoria, alerta de estoque mínimo, histórico de movimentos. |
| KDS | Fila por praça (cozinha, bar, sobremesa) com tempo de espera. Há uma tela pronta em `/kds.html`. |
| Caixa | Abertura, fechamento e conferência do dinheiro da gaveta contra o esperado. |
| Relatório | Faturamento, custo da mercadoria, margem, ticket médio e produtos mais vendidos. |

## Como rodar

Precisa de Java 21 e Maven 3.9+.

```bash
mvn spring-boot:run
```

Sobe na porta 8080 com um banco H2 em arquivo (`./data/pdv`) e já carrega um cardápio de hamburgueria, estoque e
10 mesas, para dar para testar sem cadastrar nada.

Abra http://localhost:8080 no navegador. Essa é a interface do restaurante (salão, cozinha, caixa, estoque,
cardápio e relatórios), explicada na próxima seção. O navegador pede usuário e senha na primeira chamada (veja "Perfis e permissões" abaixo; para testar tudo, entre como `gerente` / `gerente123`). Links úteis:

- Documentação interativa da API: http://localhost:8080/swagger
- Tela cheia da fila de produção, para deixar numa TV na cozinha: http://localhost:8080/kds.html

Com PostgreSQL e Docker:

```bash
docker compose up --build
```

Testes:

```bash
mvn test
```

## Como o restaurante usa

A interface roda no navegador, sem instalar nada além do servidor. Funciona em computador, tablet e celular
(o garçom pode lançar pedido pelo próprio telefone). Cada aba atende uma função:

| Aba | Quem usa | O que faz |
|---|---|---|
| Salão | Garçom | Mapa de mesas. Mesa verde está livre, laranja está ocupada e mostra o total. Aparece um balão "N prontos" quando a cozinha terminou algo para levar. Tocar na mesa abre a comanda. |
| Comanda | Garçom e caixa | Toque nos produtos para lançar (com observação, ex.: "sem cebola"). Cancela item, marca como entregue, recebe pagamento, divide a conta e fecha. |
| Cozinha e bar | Cozinha, bar | Fila por praça com o tempo de espera de cada pedido; fica vermelha depois de 15 minutos. Um botão inicia o preparo e outro marca como pronto. |
| Caixa | Caixa | Abre com o troco inicial e fecha contando a gaveta; mostra a diferença. |
| Estoque | Gerente | Saldos, alerta de reposição, entrada de mercadoria e cadastro de insumo. |
| Cardápio | Gerente | Cadastro de produtos com preço, praça e ficha técnica; mostra custo e margem. |
| Relatórios | Dono | Faturamento, custo, margem, ticket médio e ranking de produtos por período. |

O primeiro passo do dia é abrir o caixa. Sem caixa aberto o sistema não aceita pagamento (ver as decisões abaixo).

## Perfis e permissões

A API usa HTTP Basic, sem sessão, e cada usuário tem um perfil. A tela abre sem login; as chamadas à API é que
são autenticadas. Sem credenciais a resposta é `401`; com credenciais de um perfil sem permissão, `403` (ambos
no formato RFC 7807).

| Perfil | Usuário de demonstração | Pode |
|---|---|---|
| `GARCOM` | `garcom` / `garcom123` | Abrir comanda, lançar e cancelar item, ver a fila, marcar entrega, calcular divisão de conta |
| `COZINHA` | `cozinha` / `cozinha123` | Ver a fila e avançar os itens (iniciar, pronto), consultar o estoque |
| `CAIXA` | `caixa` / `caixa123` | Abrir e fechar o caixa, receber pagamento, fechar comanda, calcular divisão de conta |
| `GERENTE` | `gerente` / `gerente123` | Tudo, incluindo cardápio, estoque, relatórios e console do H2 |

Os usuários ficam em `pdv.seguranca.usuarios` (`application.yml`) e as senhas vêm das variáveis
`PDV_SENHA_GARCOM`, `PDV_SENHA_COZINHA`, `PDV_SENHA_CAIXA` e `PDV_SENHA_GERENTE`. **As senhas padrão são só de
demonstração: troque-as antes de colocar o sistema em um servidor.** As regras estão em um só lugar, a classe
`SegurancaConfig`, e o que não estiver listado lá é negado.

## O mesmo atendimento direto pela API

Quem for integrar com outro sistema pode usar a API sem a interface. Os ids abaixo assumem os dados de exemplo (mesa 1 = id 1, X-Burger = id 1, cerveja = id 3).

```bash
# todas as chamadas usam HTTP Basic (aqui, o gerente, que pode tudo)
# 1. abrir o caixa
curl -u gerente:gerente123 -X POST localhost:8080/api/caixa/abrir -H 'Content-Type: application/json' \
  -d '{"valorAbertura": 100.00, "operador": "Ana"}'

# 2. abrir a comanda da mesa 1
curl -u gerente:gerente123 -X POST localhost:8080/api/comandas -H 'Content-Type: application/json' -d '{"mesaId": 1}'

# 3. lançar itens (baixa o estoque e vai para a tela da cozinha / bar)
curl -u gerente:gerente123 -X POST localhost:8080/api/comandas/1/itens -H 'Content-Type: application/json' \
  -d '{"produtoId": 1, "quantidade": 2, "observacao": "sem cebola"}'
curl -u gerente:gerente123 -X POST localhost:8080/api/comandas/1/itens -H 'Content-Type: application/json' \
  -d '{"produtoId": 3, "quantidade": 2}'

# 4. cozinha avança o item (PENDENTE -> EM_PREPARO -> PRONTO -> ENTREGUE); repita para os dois itens
curl -u gerente:gerente123 -X PATCH localhost:8080/api/itens/1/status -H 'Content-Type: application/json' -d '{"status": "EM_PREPARO"}'

# 5. a conta deu 88,00 + 10% = 96,80; o grupo quer dividir em 3 (32,27 / 32,27 / 32,26)
curl -u gerente:gerente123 'localhost:8080/api/comandas/1/divisao?pessoas=3'

# 6. cada um paga a sua parte, na forma que quiser (aqui a primeira, em dinheiro, com troco)
curl -u gerente:gerente123 -X POST localhost:8080/api/comandas/1/pagamentos -H 'Content-Type: application/json' \
  -d '{"forma": "DINHEIRO", "valor": 32.27, "valorRecebido": 50.00, "pagador": "Carla"}'
curl -u gerente:gerente123 -X POST localhost:8080/api/comandas/1/pagamentos -H 'Content-Type: application/json' \
  -d '{"forma": "PIX", "valor": 64.53, "pagador": "Beto e Dani"}'

# 7. fechar a comanda (só passa com tudo pago e todos os itens entregues) e depois o caixa
curl -u gerente:gerente123 -X POST localhost:8080/api/comandas/1/fechar
curl -u gerente:gerente123 -X POST localhost:8080/api/caixa/fechar -H 'Content-Type: application/json' -d '{"valorContado": 132.27}'
```

Todos os endpoints estão no Swagger, inclusive o cadastro de produtos, insumos e o relatório
(`GET /api/relatorios/vendas?de=2026-11-01&ate=2026-11-30`).

## Por que foi feito assim

### Dinheiro é `BigDecimal` com duas casas, nunca `double`

`double` não representa 0,10 exatamente, e erro de arredondamento em conta de restaurante vira diferença de caixa.
Todas as contas passam pela classe `Money`, que concentra o arredondamento (`HALF_UP`, o padrão comercial) num
lugar só.

### O rateio distribui os centavos que sobram

R$ 100,00 divididos por 3 não dá 33,33 vezes 3. Dá 99,99, e o centavo perdido alguém paga do bolso. O
`Money.ratear` trabalha em centavos inteiros e entrega as sobras às primeiras parcelas: `33,34 / 33,33 / 33,33`.
A soma é sempre exatamente o total e nenhuma parcela difere da outra em mais de um centavo. Há um teste com 500
casos aleatórios garantindo isso.

### Divisão "por item" cobra o serviço de cada um pelo que consumiu

Dividir igualmente é o caso comum, mas quem tomou só uma água reclama de pagar parte do prato do vizinho. Na
divisão por itens cada pessoa paga o que pediu mais a sua parcela do serviço. A API recusa o mesmo item
atribuído a duas pessoas e avisa quanto ficou sem dono (`subtotalNaoAtribuido`), para ninguém fechar a conta
achando que cobriu tudo.

### O item guarda o preço e o custo do momento do pedido

Se o gerente muda o preço do cardápio às 20h, as comandas abertas não podem mudar de valor. Pelo mesmo motivo o
custo da ficha também é congelado no item: sem isso, o custo de mercadoria vendida do mês mudaria sozinho toda vez
que o preço da carne mudasse.

### Ficha técnica e baixa automática de estoque

É o ponto central do projeto. O produto lista os insumos que consome (X-Burger: 1 pão, 1 carne, 2 fatias de
queijo) e lançar o item na comanda baixa tudo isso na mesma transação. Consequências úteis:

- O sistema **recusa** o pedido se faltar qualquer insumo, com mensagem dizendo qual. O garçom descobre na hora, e
  não quando a cozinha avisa.
- Se faltar o terceiro insumo, a baixa dos dois primeiros é desfeita junto (rollback). Nunca fica estoque
  descontado de pedido que não existiu.
- O custo do produto e a margem saem da ficha sem cadastro à parte.

### Travas para dois garçons não venderem o mesmo último pão

Dois pedidos simultâneos podem ler "sobrou 1 pão" e os dois passarem. O saldo do insumo é lido com
`SELECT ... FOR UPDATE` (`PESSIMISTIC_WRITE`), então o segundo pedido espera o primeiro terminar e já enxerga o
saldo atualizado. Os insumos de um produto são travados sempre em ordem de id, para dois pedidos com os mesmos
ingredientes não ficarem esperando um ao outro (deadlock). O teste `pedidosSimultaneosNuncaVendemMaisQueOEstoque`
dispara 20 pedidos ao mesmo tempo para 10 pães e confere que exatamente 10 passam.

Além do lock, `Insumo` tem `@Version`. É uma segunda linha de defesa: se algum dia um código novo esquecer de
travar, a gravação conflitante falha com 409 em vez de sobrescrever o saldo em silêncio.

A comanda também é travada ao mexer em itens, pagamento e fechamento. Sem isso, dois pagamentos simultâneos
poderiam somar mais que a conta.

### Cancelar item: estorno ou perda, dependendo de onde ele está

- Item `PENDENTE` (ninguém começou): o insumo volta ao estoque.
- Item `EM_PREPARO`: a comida já foi feita, então o insumo **não** volta. Vira um movimento de `PERDA`, que é o que
  de fato aconteceu e é o dado que o dono precisa para controlar desperdício.
- Item `PRONTO` ou `ENTREGUE`: a API recusa. Cancelar nesse ponto é decisão de gerente, e esse fluxo não existe
  aqui (ver limitações).

### Todo movimento de estoque fica registrado

Só guardar o saldo atual não permite descobrir por que ele está errado. A tabela `movimento_estoque` registra
entradas, saídas por venda, estornos e perdas, com a referência (qual comanda). É o que se consulta quando o
inventário não bate.

### Fechar a comanda é um passo explícito

Poderia fechar sozinha ao receber o último pagamento, mas aí um cliente que paga adiantado e pede mais uma
sobremesa teria a comanda fechada no meio. Por isso `POST /fechar` é separado e exige: tudo pago, nenhum item
ainda em produção e pelo menos um item. Só então a mesa é liberada.

### Pagamento exige caixa aberto

Todo pagamento fica ligado a um caixa. Sem caixa aberto não há como saber em qual turno o dinheiro entrou, então a
API recusa. Pagamento maior que o restante também é recusado (excesso em dinheiro é troco, e isso é tratado
separadamente via `valorRecebido`).

### O troco não entra na conta da gaveta

No fechamento, o dinheiro esperado é `abertura + valor pago em dinheiro`. O que o cliente entregou a mais já
voltou como troco, então contar `valorRecebido` faria a gaveta sempre "faltar" dinheiro. A diferença
(`contado - esperado`) é o número que o operador precisa ver.

### KDS por praça

Cada produto tem uma `praca` (cozinha, bar, sobremesa) e cada tela consulta só a sua fila, ordenada por hora do
pedido, com os minutos de espera calculados no servidor. A sequência de status só anda um passo por vez
(`PENDENTE -> EM_PREPARO -> PRONTO -> ENTREGUE`), para ninguém marcar "pronto" sem ter iniciado e distorcer o
tempo de preparo.

A fila anda em tempo real por **Server-Sent Events**: a tela abre `GET /api/kds/{praca}/eventos` e, quando um item
é lançado, avança ou é cancelado, o servidor manda o evento `fila` e a tela busca a fila de novo na API. O evento
não carrega dados, só avisa que mudou, então a API continua sendo a única fonte e uma tela que perdeu um aviso se
corrige no próximo. Três cuidados:

- **O aviso sai depois do commit** (`@TransactionalEventListener(AFTER_COMMIT)`). Um pedido recusado por falta de
  estoque desfaz a transação e não avisa ninguém; e quem busca a fila logo após o aviso já enxerga o dado gravado.
- **Cada tela só ouve a sua praça.** Um item do bar não acorda a tela da cozinha.
- **SSE em vez de WebSocket** porque o fluxo é só servidor para tela: o `EventSource` do navegador reconecta
  sozinho, passa por HTTP comum (inclusive a autenticação) e não pede protocolo novo. Um batimento a cada 25 s
  mantém a conexão viva atrás de proxies. A tela ainda atualiza a cada 30 s só para os minutos de espera.

### Interface em HTML, CSS e JavaScript puros, sem framework

A tela é servida pelo próprio Spring (pasta `static`). Foi uma escolha de propósito: não há Node, build nem
segunda aplicação para subir. Quem baixa o projeto roda um comando e tem o sistema completo, e o restaurante
pequeno que não tem quem cuide de infraestrutura mantém um serviço só. O custo é código de tela mais manual; se o
projeto crescer muito, vale migrar para React ou Vue.

Alguns detalhes da interface, pensados para o fluxo de salão:

- **Cada toque num produto vira um item.** Os toques entram numa fila e chegam ao servidor na ordem. Ignorar toques
  enquanto o servidor responde parece seguro, mas o garçom que toca X-Burger e logo em seguida a cerveja perderia
  o segundo pedido sem perceber.
- **A comanda aberta se atualiza sozinha a cada 6 segundos**, para o "pronto" aparecer sem recarregar. A atualização
  é suspensa enquanto há toques na fila, para uma resposta atrasada não apagar um item recém-lançado.
- **Resposta de uma aba antiga não sobrescreve a atual.** Se o usuário troca de aba antes de a anterior terminar de
  carregar, o resultado atrasado é descartado.
- **A divisão de conta deixa pagar parte por parte.** Cada pessoa recebe na forma que quiser, a parte paga fica
  marcada, e o restante da comanda se atualiza. É o fluxo real de mesa grande.
- **O troco aparece enquanto o valor entregue é digitado**, para o caixa não precisar calcular de cabeça.
- **Cancelar item em preparo avisa que haverá perda de insumo** antes de confirmar.
- **Todo texto vindo do banco é escapado** antes de entrar no HTML, para um nome de produto com `<` ou aspas não
  quebrar a tela nem executar script.

### Erros no formato RFC 7807

Todos os erros saem como `ProblemDetail` (`type`, `title`, `status`, `detail`), formato que o próprio Spring já
suporta. Os códigos seguem uma regra única:

- `400`: o pedido está malformado ou com campo inválido (a resposta lista os campos).
- `404`: recurso não existe.
- `409`: pedido válido, mas o estado atual não permite (sem estoque, mesa ocupada, caixa fechado, conflito de
  concorrência).

### Detalhes menores, mas propositais

- **DTOs como `record`**: as entidades JPA não saem pela API. Assim, mudar uma tabela não muda o contrato, e
  coleções preguiçosas não explodem na serialização.
- **`open-in-view: false`**: o acesso ao banco acontece só dentro dos serviços, e não escondido na camada web.
- **Serviço de 10% configurável por comanda** (`pdv.servico-padrao` e campo na abertura): muda de casa para casa
  e o cliente pode pedir para retirar.
- **H2 por padrão, PostgreSQL por perfil**: quem baixa o projeto roda em um comando; produção usa
  `SPRING_PROFILES_ACTIVE=postgres`.
- **Dados de exemplo só com o banco vazio** e desligáveis com `pdv.seed=false`.
- **Produto "deletado" é só desativado**: ele aparece em comandas antigas e nos relatórios, então apagar quebraria
  o histórico.

## Testes

- `MoneyTest`: rateio de centavos, incluindo 500 casos aleatórios, e arredondamento.
- `StatusItemTest`: transições válidas do KDS.
- `FluxoPdvTest`: sobe a aplicação com H2 em memória e percorre os fluxos reais: baixa de estoque, rollback por falta
  de insumo, estorno e perda, pedidos concorrentes, pagamento parcial, troco, fechamento de comanda e de caixa,
  divisões e KDS.
- `ApiErrosTest`: formato e código HTTP das respostas de erro.

## Estrutura

```
src/main/java/br/com/pdv
  domain/      entidades e regras que não dependem de Spring (Comanda calcula total, Money faz o rateio)
  repository/  Spring Data JPA, incluindo as consultas com lock
  service/     casos de uso e transações
  dto/         contratos de entrada e saída da API
  web/         controllers finos e tratamento de erros
  config/      dados de exemplo
src/main/resources/static
  index.html, app.css, app.js   interface do restaurante
  kds.html                      fila de produção em tela cheia
```

## Limitações conhecidas

Este é um ponto de partida, não um sistema pronto para colocar em produção amanhã. O que falta:

- **Sem autenticação nem perfis.** Qualquer pessoa com acesso à rede abre qualquer aba e faz qualquer coisa
  (inclusive cancelar item ou ver o relatório). Para uso real é preciso login e separar garçom, cozinha, caixa e
  gerente (Spring Security). Até lá, rode o sistema só numa rede interna do restaurante.
- **A interface não tem testes automatizados.** O back-end tem; a tela foi verificada à mão no navegador.
- **Sem emissão fiscal** (NFC-e/SAT) nem integração com maquininha ou impressora.
- **O esquema do banco é versionado com Flyway** (`src/main/resources/db/migration`) e o Hibernate só valida
  (`ddl-auto: validate`). Mudou uma entidade? Crie a próxima migração `V2__...sql`. Bancos H2 em arquivo criados
  antes do Flyway (pasta `./data`) precisam ser apagados para recriar o esquema.
- **Usuários vêm da configuração, não de um cadastro.** Serve para um restaurante pequeno; com rotatividade de equipe, o passo seguinte é uma tabela de usuários (e login por token em vez de Basic).
- **Não divide um mesmo item entre pessoas** (a porção de batata que três dividem). Nesse caso use a divisão igual.
- **Cancelar item já pronto ou entregue** exige um fluxo de aprovação do gerente que não existe aqui.
- **Os assinantes do KDS ficam na memória do processo.** Com mais de uma instância do servidor, um evento só chega às telas ligadas na instância que o gerou; aí o caminho é um broker (Redis, PostgreSQL `LISTEN/NOTIFY`).
- **Gorjeta fixa em percentual.** Não há gorjeta em valor nem rateio do serviço entre a equipe.
- **Um caixa aberto por vez.** Restaurante com mais de um caixa precisaria associar o operador ao pagamento.

## Licença

MIT. Veja o arquivo [LICENSE](LICENSE).
