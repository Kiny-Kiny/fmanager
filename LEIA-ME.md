# FManager

Jogo de gerenciamento de futebol para Android. Kotlin + Jetpack Compose + Room.

## Como rodar

**Sem PC?** Veja `COMPILAR-SEM-PC.md` — o fluxo é Termux para editar e
enviar, GitHub Actions para compilar de graça e devolver o APK pronto.
O workflow já vem configurado em `.github/workflows/build.yml`.

**Com PC:**

1. **Abra no Android Studio** (versão Ladybug ou mais nova). Use `Open`, não `New Project`, e aponte para a pasta `FManager`.

2. **Rode** (botão ▶). Só isso. Na primeira abertura o app importa os
   jogadores da API `api.msmc.cc/api/eafc` — leva alguns minutos, com a
   barra mostrando liga por liga. Da segunda em diante abre instantâneo,
   sem tocar em rede.

3. **Reserva opcional:** se quiser garantir que funciona mesmo com a API
   fora do ar, baixe um CSV do EA FC no Kaggle, renomeie para
   `jogadores.csv` e coloque em `app/src/main/assets/`. O app só usa isso
   se a API falhar.

5. **Gerar o APK:** menu `Build` → `Build Bundle(s)/APK(s)` → `Build APK(s)`. Sai em `app/build/outputs/apk/debug/app-debug.apk`.

## Arquitetura

```
dados/
  Jogador.kt        atributos + função de adequação ao papel
  Entidades.kt      clube, liga, contrato, partida, carreira
  AppDatabase.kt    Room + todos os DAOs
  ImportadorCsv.kt  semeia o banco na primeira execução

formacao/
  FormationEditor.kt  editor com arrastar, posição automática por zona
  Tatica.kt           estilo de jogo do time

motor/
  MotorPartida.kt   simulação por eventos discretos

sistemas/
  Treino.kt         evolução de atributos
  Transferencias.kt precificação e negociação
  Temporada.kt      calendário e classificação

ui/                 telas
JogoViewModel.kt    amarra tudo
```



## De onde vêm os dados

Fonte principal: **api.msmc.cc/api/eafc**, uma API pública com o banco do
EA FC 25 e 26. Ela entrega, por jogador:

- os 29 atributos detalhados (mesmos nomes que já usávamos)
- **PlayStyles da EA** — os traços inatos (Finesse Shot, Trivela, Rapid...)
- **a imagem da carta** (`card`), carregada com Coil
- posições alternativas, que alimentam o sistema de familiaridade

### Por que ela não é dependência de gameplay

É projeto de hobby: sem autenticação, sem SLA, sem paginação documentada,
suporte por e-mail. Ótima como fonte, péssima como dependência em tempo
de execução. Por isso ela roda **uma vez**, popula o SQLite e sai de cena.
Se a API cair amanhã, o jogo continua funcionando igual.

Duas decisões vêm disso:

1. **Importa liga por liga.** Pedir os 16 mil jogadores de uma vez estoura
   a requisição — foi o que aconteceu no teste.
2. **Falha parcial não derruba tudo.** Se uma liga der erro, as outras
   seguem. Importação incompleta é melhor que nenhuma.

O que a API **não** traz: potencial e valor de mercado. Ambos são
estimados a partir de overall e idade em `ImportadorApi.kt`.

## PlayStyles vs Estilo de jogador

São coisas diferentes e trabalham juntas — o eFootball tem a mesma
separação:

- **PlayStyle** (`PlayStyle.kt`) — traço inato, vem pronto da API.
  Finesse Shot, Trivela, Relentless. O sufixo `+` marca a versão de elite,
  que pesa quase o dobro.
- **EstiloJogador** (`EstiloJogador.kt`) — a função que **você** atribui.
  Ponta Invertido, Falso 9, Cão de Guarda.

A função `afinidadeCom()` cruza os dois: um jogador com Finesse Shot e
Trivela tem afinidade alta com Ponta Invertido. Nasceu pra isso.

Os PlayStyles entram no motor por categoria — finalização, criação,
defesa, resistência, drible — com teto de +32% para ninguém virar
sobre-humano acumulando traços.

## Formação por fase (o sistema principal)

Cada jogador guarda **uma posição por fase de jogo**, não uma posição só:

- **Sem a bola** — a organização defensiva
- **Transição** — os segundos após ganhar ou perder a bola
- **Com a bola** — a estrutura de ataque

As abas no topo do editor trocam a fase. Arrastar o volante para trás na
aba "Com a bola" não mexe na forma defensiva. O rastro tracejado mostra
onde o jogador fica nas outras fases.

O desenho tático de cada fase é **deduzido das coordenadas**, nunca
cadastrado. Se sair 6-4-0 ou 5-5-0, é porque foi isso que você desenhou.

Já vem pronta a pré-definida **4-2-3-1 → 3-2-5**: sem a bola é uma
4-2-3-1 normal; com a bola o primeiro volante cai entre os zagueiros,
os laterais sobem para alas, o camisa 10 desce para a dupla de volantes
e os pontas estreitam para perto do centroavante.

## As quatro camadas do rendimento

Todo cálculo do motor multiplica quatro coisas:

1. **Atributos** (`adequacao`) — ele tem as qualidades que a função pede?
2. **Familiaridade** (`Familiaridade.kt`) — ele joga ali, ou é gambiarra?
   Natural 100%, alternativa 94%, adaptável 85%, improviso 72%,
   fora de função 55%. Sai da coluna de posições alternativas do CSV.
3. **Entrosamento** (`Entrosamento.kt`) — vizinhos no campo se ligam por
   clube, liga e seleção. Como a formação é livre, os vizinhos são
   calculados por distância: quem você põe perto, se liga.
4. **Estilo** (`EstiloJogador.kt`) — 20 estilos que mudam o comportamento
   na simulação. O jogador só destrava o estilo se tiver os atributos.

## Estilos de jogador

Alguns exemplos e o que eles fazem de fato no motor:

- **Finalizador de área** — some da construção, dobra o peso de finalizar
- **Falso 9** — recua na fase com a bola, vira o principal criador
- **Ponta invertido** — estreita 14% rumo ao centro, finaliza mais
- **Lateral invertido** — com a posse entra no meio-campo, não na linha
- **Ala** — faz o corredor inteiro, mas gasta 50% mais gás
- **Cão de guarda** — quase não finaliza, contribuição defensiva +55%

Estilo exigente cansa mais. Um Ala numa formação de muito deslocamento
entre fases chega ao fim do jogo sem pernas — e o risco de lesão sobe.

## Decisões de performance

**O jogo nunca toca em rede durante o gameplay.** Todos os dados vêm do SQLite local. A permissão de internet no manifesto existe só para baixar fotos dos jogadores no futuro.

**A simulação roda por eventos discretos**, não frame a frame. Uma partida sai em menos de um milissegundo, então dá para simular uma rodada inteira sem o usuário perceber espera. Roda em `Dispatchers.Default`, fora da thread de UI.

**No editor de formação**, a posição do jogador é lida dentro do lambda de `offset { }`, que roda na fase de layout. Arrastar um token não recompõe a tela.

**As buscas do mercado usam índices** na tabela de jogadores. Filtrar 18 mil jogadores por posição, idade e valor responde instantâneo.

## O que já funciona

- Formação por fase: estrutura diferente com e sem a bola
- Detecção automática do desenho tático em cada fase
- Rastro visual do deslocamento entre fases
- Familiaridade posicional com perda de overall fora da função
- 20 estilos de jogador com requisitos de atributo
- PlayStyles reais da EA por jogador, vindos da API
- Imagens das cartas dos jogadores
- Importação automática pela API, com CSV de reserva
- Entrosamento por clube, liga e seleção, calculado por proximidade
- Posição redefinida pela zona do campo, com opção de travar
- Instruções individuais por jogador (movimentação, apoio, marcação, pressão, amplitude)
- Estilo de jogo do time com 7 parâmetros + 5 presets
- Simulação de partida com gols, assistências, cartões, lesões e notas
- Simulação do resto da rodada para a tabela andar junto
- Classificação com todos os critérios de desempate
- Calendário de pontos corridos, turno e returno
- Contratações com precificação real e negociação
- Treino semanal com evolução por idade, potencial e intensidade
- Escalação automática pelo melhor jogador para cada papel

## O que falta

- Salvar a formação no banco — hoje ela vive só na memória
- Renovação de contrato e jogadores livres
- Passagem de temporada (promoção, rebaixamento, envelhecimento do elenco)
- Substituições durante a partida
- Balanceamento: os números do motor são um ponto de partida, não estão afinados

## Ajustando o motor

Se os placares saírem altos ou baixos demais, mexa em `MotorPartida.kt`:

- `probabilidadeDeChance()` — o `0.30f` no final controla quantas chegadas viram finalização
- `probGol` — o `coerceIn(0.02f, 0.55f)` limita a chance de cada chute virar gol
- `MOMENTOS` — mais momentos, mais eventos por jogo

## Sobre os dados

Os datasets do EA FC são levantamentos de comunidade. Servem para projeto pessoal, mas nomes de jogadores, escudos de clubes e a marca EA/FIFA são licenciados. Publicar na Play Store com esses dados é problema legal.
