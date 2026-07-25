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












## Tela deitada em todo o app

Antes só a tela de partida forçava paisagem, o que causava um giro visível
ao entrar e sair dela. Agora o manifesto declara `sensorLandscape` para a
Activity inteira e a tela de partida não mexe mais em orientação.

**A navegação virou trilho lateral.** Em tela deitada a ALTURA é o recurso
escasso — gastar 80dp dela com uma barra embaixo é desperdício. O trilho na
lateral também é a orientação natural do gesto quando o aparelho está de
lado.

E telas que antes eram rolagem infinita ganharam **duas colunas**: o modo
online mostra seu time à esquerda e desafios à direita, lado a lado.

## PVP online — "Ultimate Team de técnico"

### A decisão de arquitetura

O pedido foi: multijogador fora da rede local, sem construir um servidor,
mas hospedável pelo dono do app.

O `lollito/fm` que você mandou resolve isso com Spring Boot + MySQL +
MongoDB + Redis + Nginx + Prometheus via docker-compose. Isso é escrever
**e manter** um servidor: atualizações de segurança, migrações, backup,
monitoramento.

Escolhi **PocketBase**: um binário único de ~15 MB com SQLite embutido,
REST automático, autenticação e painel de administração. O dono roda
`./pocketbase serve`, cria duas coleções pelo painel, e não escreve uma
linha de código de servidor. O backup é copiar uma pasta.

**O endereço não é fixo no código.** Cada pessoa aponta para a instância que
quiser. Não existe servidor oficial nem ponto único de falha.

Instruções completas em `HOSPEDAR-PVP.md`.

### Três decisões de desenho

**1. Orçamento igual para todos.** Ninguém joga com o Real Madrid porque
escolheu o Real Madrid. Cada técnico recebe 700 moedas e monta os 11 do
acervo mundial de 16 mil jogadores.

O preço é **exponencial** (`(overall/60)^6.2`), não linear. Com curva linear
o melhor elenco seria sempre "onze jogadores de 84" e não haveria decisão.
Exponencial força a escolha real do Ultimate Team: dois craques e nove
medianos, ou onze bons e nenhum craque?

O montador automático otimiza **rendimento por moeda**, não rendimento —
que é exatamente a conta que o formato obriga a fazer.

**2. Assíncrono.** Os dois jamais precisam estar online juntos. Você publica
um desafio, alguém aceita quando quiser, cada lado simula quando abrir o
app. É o que faz um jogo de celular funcionar de verdade.

**3. Simulação dupla com conferência.** O servidor **não simula nada** — ele
é um banco com API. Os dois clientes rodam a mesma partida com a mesma
semente (gravada no desafio) e enviam uma assinatura do resultado.
Assinaturas iguais → confirmado e o Elo se move. Diferentes →
`EM_DISPUTA` e ninguém pontua.

Isso é o que permite não escrever servidor: **o árbitro é o determinismo do
motor**, que eu já tinha construído para o modo LAN.

### O que isso garante e o que não garante

**Garante:** um cliente modificado consegue no máximo *anular* a partida.
Nunca fabricar uma vitória, porque vitória só conta com a concordância do
adversário.

**Não garante:** duas pessoas combinadas rodando clientes modificados
idênticos podem inflar rating entre si. Impedir isso exigiria o servidor
simular a partida — e aí você estaria mantendo um servidor de jogo, que é
exatamente o que este desenho evita.

Preferi ser explícito sobre o limite a fingir que criptografia resolve tudo.

### Ladder

Elo com K=28, começando em 1400. Divisões de 4 a Elite. O emparelhamento
ordena os desafios abertos por **proximidade de rating** — enfrentar alguém
600 pontos acima não é competição.

## Gestão humana

Comparando a lista de features do **OpenFootManager** com a minha, apareceu
uma lacuna inteira: tudo que eu havia construído era tático ou estatístico
— formação, xG, garimpo, olheiro. Nada sobre lidar com gente, que é metade
do trabalho de um treinador.

Duas telas do OpenFootManager que eu não tinha nada parecido: **playertalk**
e **presstalk**.

### O erro que isso expôs

O campo `moral` existe em `Contrato` desde o começo do projeto e **nunca foi
lido por nada**. Criei e esqueci. Um número guardado que não afeta nada é
pior que não ter o campo: dá a impressão de sistema onde não há.

Agora a moral faz três coisas concretas:

1. **multiplica o rendimento em campo** (0,92 a 1,06)
2. **decide se o jogador aceita renovar** — revoltado não renova por dinheiro
3. **gera insatisfação** que aparece no painel e na caixa de entrada

A faixa do multiplicador é estreita de propósito. Moral importa, mas um
craque revoltado ainda é melhor que um reserva animado — sistemas que deixam
a moral dominar premiam micro-gestão em vez de decisão tática.

### Como a moral se move

O peso maior é **minutos jogados**. Jogador que não joga fica insatisfeito
independente de quanto o time ganha — é a reclamação número um do futebol
real. Craque no banco perde 2,5 a mais que um reserva comum, e veterano sem
jogar aceita menos que um jovem.

E tudo tende devagar para o meio: ninguém fica eufórico para sempre.

### Clima do vestiário: média ponderada

A média é ponderada pelo overall **ao cubo**. O titular insatisfeito
contamina muito mais que o décimo reserva, e é isso que faz a gestão do
craque importar de verdade.

### Conversa com jogador

O princípio: **nenhuma opção é sempre certa.**

- Elogiar quem vai bem: +8. Elogiar quem vai mal: **−4** — soa falso, e ele
  sabe que não está jogando bem
- Cobrar um jovem: +4. Cobrar o melhor do elenco: **−9** — ele se considera
  acima disso
- Pedir paciência a um jovem: +4. A um trintão: **−6** — na idade dele não
  sobra tempo
- **Prometer titularidade: +14** — e a promessa fica registrada

Os assuntos disponíveis dependem do contexto. Oferecer "explicar a falta de
minutos" a um titular absoluto seria ruído, e o jogo ficaria com cara de
menu em vez de conversa.

### Coletiva de imprensa

As perguntas nascem do que acabou de acontecer: sequência ruim, posição
abaixo da meta, eliminação na copa, rumores de vestiário. Coletiva com
perguntas genéricas seria decoração.

Cada tom mexe em **três medidores ao mesmo tempo**, e nenhum agrada os três:

| Tom | Vestiário | Diretoria | Torcida |
|---|---|---|---|
| Confiante (time bem) | +5 | +3 | +6 |
| Confiante (time mal) | +4 | **−6** | −2 |
| Cauteloso | +1 | +2 | 0 |
| Defensivo (time mal) | **−5** | +3 | −3 |
| Provocador (time bem) | +8 | −4 | **+12** |
| Provocador (time mal) | −6 | **−9** | −8 |

Provocar ganhando eletriza a torcida e vira manchete — mas agora você tem
que sustentar em campo. Provocar perdendo é o pior resultado possível.

## Comissão técnica

Outra lacuna: o treino funcionava sozinho, como se o clube não tivesse
ninguém aplicando. Um clube pequeno e um gigante evoluíam jogadores na mesma
velocidade, o que apagava boa parte da diferença entre eles.

Seis cargos — auxiliar, preparador físico, treinador de goleiros, analista,
olheiro-chefe, fisioterapeuta. Sem auxiliar o elenco treina a **82%** do que
treinaria com uma comissão boa, e ao longo de uma temporada isso é muito.

**A qualidade dos candidatos depende da reputação do clube.** Clube pequeno
não atrai referência mundial — é isso que faz subir de clube significar algo
além de orçamento maior. E o salário sobe muito mais rápido que a
competência, então um preparador físico bom compete por orçamento com o
quarto zagueiro.

## Renovação de contrato

Faltava por completo: eu tinha `terminaEmTemporada` e nenhuma forma de
renovar. Agora a moral decide:

- moral ≥ 70 → aceita manter o salário
- moral ≥ 50 → quer +15%
- moral ≥ 30 → quer +40%
- abaixo de 30 → **não renova por dinheiro nenhum**

## Nota técnica: enum no Room

`Cargo` é um enum, e **Room não persiste enum sem conversor**. Isso quebraria
a compilação, não em tempo de execução.

O conversor guarda pelo **nome**, não pelo ordinal. Se eu reordenar o enum
um dia, os dados salvos continuam válidos — ordinal quebraria em silêncio,
que é o pior tipo de bug.

## Torneios customizados

Trazido do padrão das ferramentas de torneio de eFootball. O formato que
faltava é o mais reconhecível de todos: **fase de grupos seguida de
eliminatória**. É o desenho da Champions, da Copa do Mundo e de
praticamente todo torneio que alguém organiza entre amigos.

Eu tinha liga de pontos corridos e copa de eliminatória pura, mas não a
combinação.

### Sorteio com potes

O que dá sabor ao torneio, e o que um sorteio puramente aleatório erra: os
clubes são ordenados por reputação, cortados em tantos potes quantos
grupos existem, e distribuídos um de cada pote por grupo.

Sem isso sai um grupo com quatro gigantes e outro com quatro fracos. Com
isso a força fica equilibrada entre grupos, mas **o adversário exato ainda
é surpresa** — cada pote é embaralhado por dentro.

### Chaveamento cruzado

Os classificados não entram na eliminatória em ordem. Primeiro de grupo
encara segundo de **outro** grupo, e os líderes ficam nas pontas opostas
do chaveamento — só se encontram na final. É o que evita que os dois
melhores caiam nas quartas.

### Montar a competição

Você escolhe os clubes de **qualquer liga**, filtrando por competição.
Quer uma Libertadores com times europeus? Um torneio só de rivais? Uma
Champions de 32 com oito grupos? Tudo cabe.

O app avisa quando a divisão não fecha exata (`23 clubes em 4 grupos = 5
por grupo, 3 de fora`) em vez de montar um grupo desigual em silêncio.

### Faixas de rodada

As rodadas usam faixas próprias para não colidir na tabela de partidas:
1000+ copa nacional, 2000+ fase de grupos, 3000+ eliminatória de torneio.
E cada torneio tem um `ligaIdVirtual` de 9100+.

## Palmarés

Torneio ganho fica registrado. Sem isso vencer não significa nada na
temporada seguinte — e a sala de trofeus é metade do sentido de um modo
carreira.

Guarda o mínimo: o que, quando e o tipo. Migração aditiva 4 → 5.

### Nota sobre a fonte

O repositório `islam80012/Efootball-Tournament-Api` é um Spring Boot REST
API com backend e UI, **sem README** — a descrição de uma linha é toda a
documentação existente:

> *automated match scheduling, team registrations, and real-time player
> statistics*

Agendamento automático de partidas eu já tinha. Os outros dois eram
lacunas reais, e estão nas seções abaixo.

## Inscrição de elenco por torneio

Vinha de *"team registrations"*. Antes o clube entrava no torneio com o
elenco inteiro. Na prática existe uma **lista fechada**: você inscreve 23
jogadores para aquela competição, e quem ficou de fora não joga — nem por
lesão de outro.

Isso muda decisões de verdade. Contratar no meio do torneio não resolve
nada se a lista já está cheia, e deixar o garoto fora para inscrever o
veterano passa a ser uma escolha com consequência.

A inscrição vale **só para o clube do usuário**. Obrigar a IA a fechar
lista criaria times incompletos sem nenhum ganho de jogabilidade.

Mínimo de 14 para a lista ser válida, e `montarTime()` filtra o elenco por
ela quando a partida é daquele torneio.

## Estatística de jogador em tempo real

Vinha de *"real-time player statistics"*, e era a lacuna mais incômoda:
durante a partida eu só mostrava números do **time** — posse, chutes,
faltas. Nada por jogador.

Então não havia como perceber, no minuto 60, que o seu camisa 10 tinha
errado catorze passes e a nota dele estava em 5,2. Você descobria no
resumo, quando já não dava para fazer nada.

Agora cada jogador acumula os próprios números lance a lance: passes e
precisão, finalizações, dribles tentados e certos, desarmes, faltas
cometidas e sofridas, bolas perdidas, cartões.

### A nota ao vivo

Parte de 6,0 e move conforme o que ele fez, com os pesos de uma nota de
imprensa — gol vale 1,35, assistência 0,85, bola perdida custa 0,045 cada.

Um detalhe que evita ruído: a **precisão de passe só entra depois de 8
passes**. Antes disso a amostra é pequena demais e a nota ficaria pulando
a cada toque.

Na lateral da tela de partida há um seletor **Lances / Notas**. A aba de
notas lista o elenco ordenado por nota, com barra de gás ao lado — porque
gás é o motivo mais comum de tirar alguém, e assim os dois números que
importam para a decisão ficam na mesma linha.

## Bola: modelo de voo

A primeira versão da física ainda deixava a bola saltando, e havia **dois
bugs** por trás disso.

**Bug 1 — velocidade em tempo de jogo.** A bola andava por `velocidade ×
dt de jogo`. Medindo:

| ritmo | tempo real para cruzar 30% do campo |
|---|---|
| 1x | 226 ms |
| 4x | 56 ms |
| 8x | **28 ms** |
| 20x | **11 ms** |

Abaixo de ~8 frames o olho não lê como movimento, lê como teletransporte.
A 8x eram 1,7 frames.

**Bug 2, o pior — o alvo era o slot.** `alvoDaBola` devolvia a posição do
*slot* do portador, mas a peça desenhada usava a posição *física*. A bola
perseguia um ponto onde ninguém estava, enquanto o jogador caminhava para
lá separadamente.

### O conserto

A bola agora tem um **voo**: origem, destino e duração com **piso em tempo
real**. E é ancorada na posição física do portador, não no slot.

A duração encurta com o ritmo pela **raiz**, não linearmente:

| ritmo | voo de um passe médio |
|---|---|
| 1x | 520 ms (31 frames) |
| 4x | 260 ms (16 frames) |
| 8x | 184 ms (11 frames) |
| 20x | 170 ms (10 frames) |

Todos acima de 10 frames. A raiz importa: linear voltaria a teletransportar
nos ritmos altos, e um piso fixo faria a bola parecer descolada dos
jogadores, que aceleram linearmente.

### Três coisas que fazem o olho ler movimento

**Rastro.** Doze posições recentes da bola desenhadas com transparência
decrescente. Mostra de onde ela veio.

**Sombra separada.** A sombra fica no chão e se afasta quando a bola sobe.
É o que comunica altura numa vista de cima.

**Parábola.** O tamanho da bola varia ao longo do voo, e a altura vem de
`4t(1−t)` — sobe e desce. Lançamento longo sobe; toque curto vai rasteiro.

Jogadores correndo também deixam rastro, o que dá a direção do movimento.

### Condução da bola

Sem voo em curso, a bola fica no pé do portador com uma **oscilação de
condução** — pequena, mas é o que diferencia "jogador com a bola" de
"jogador com um círculo grudado".

## Plano de jogo no vocabulário do EA FC

Os sete controles contínuos dão precisão, mas ninguém pensa em "risco no
passe 62". Adicionei a camada nomeada do EA FC:

**Construção** — Equilibrada · Toque curto · Bola longa · Saída rápida
**Criação de chances** — Equilibrada · Infiltrações · Passe direto · Posse
**Postura defensiva** — Equilibrada · Pressão após perder · Pressão
constante · Recuar

Cada escolha mexe em **vários números de uma vez** e liga instruções de
equipe correspondentes — escolher "bola longa" ativa ligação direta e bola
no homem-alvo, não só sobe um slider.

É uma **camada** sobre o que existia: escolher um plano preenche os
sliders, e você continua livre para afinar cada um depois.

## Partida: física contínua e tela deitada

A versão anterior parecia travada por um motivo estrutural: cada jogador
tinha **exatamente três posições possíveis** — a do slot em cada fase — e
saltava entre elas. Não havia movimento, havia teletransporte entre três
pontos.

O conserto foi separar a simulação **tática** (discreta, por lances) da
**física** (contínua, a 60fps).

### Física do campo (`Fisica.kt`)

Cada jogador tem posição contínua e caminha para um alvo que muda a cada
instante. O alvo **não é o slot** — é o slot deslocado por três forças:

**Atração pela bola.** Todo mundo desliza para o lado onde a bola está, e
quem está mais perto se move mais. É isso que faz o bloco compactar de
verdade em vez de ficar esticado como um desenho técnico.

**Linha defensiva como bloco.** Os defensores compartilham uma altura
comum que sobe e desce com a bola. Sem isso não existe linha, existem
quatro jogadores parados em pontos fixos.

**Marcação.** Quem está mais perto do portador vai atrás dele — e vai para
**entre ele e o próprio gol**, não para cima dele.

A velocidade de cada jogador sai dos atributos: um rápido cruza o campo em
~11 segundos, um lento em ~16. O **esforço** (0 a 1) vem da distância até
o alvo, então perto do lugar ele caminha e longe ele corre — é o que dá a
leitura de gente andando no campo.

### Linha de impedimento em tempo real

Regra real: a linha fica no penúltimo defensor. Como o goleiro é quase
sempre o último, na prática é o defensor de campo mais recuado.

É calculada da **posição real**, não do slot — então ela se move de verdade
quando a defesa sobe, e você vê a armadilha acontecendo. Quem fica além
dela **e à frente da bola** aparece em laranja. Atrás da bola não existe
impedimento, e o cálculo respeita isso.

### Duelos visíveis

Desarme, drible perdido e roubo de bola registram um duelo, e os dois
envolvidos ganham um anel branco por ~0,9 segundo. O duelo é **consumido**
ao ser lido — sem isso a tela reregistraria o mesmo lance 60 vezes por
segundo e o anel nunca apagaria.

### Relógio contínuo: o conserto do "muita coisa de uma vez"

Antes o ritmo era "um lance a cada X milissegundos". O relógio de jogo
disparava e os eventos se amontoavam.

Agora o **relógio de jogo** avança suave, e o motor só é chamado quando o
relógio alcança o ponto em que ele parou:

```
relógio += dt × multiplicador
while (motor.relogioDeJogo < relógio) motor.passo()
```

Os lances se espalham no tempo sozinhos, porque cada um já custa segundos
de jogo diferentes: um toque 2s, uma falta cobrada 30s, um gol 55s.

Ritmos: **1x** (90 min de verdade), **4x** (22 min), **8x** (11 min),
**20x** (4 min).

### Tela deitada

Um campo tem 105 por 68 metros. Em pé ele fica estreito e as peças se
empilham. A tela de partida agora força orientação **paisagem**, o campo
ocupa quase tudo e o mandante ataca da esquerda para a direita.

O `configChanges` no manifesto é obrigatório aqui: sem ele a Activity é
recriada ao girar e a partida reiniciaria do zero.

## Calibração do motor: o conserto dos gols

O placar estava saindo alto e a causa era matemática, com **dois erros que
se multiplicavam**.

**Erro 1 — conversão de 41% por finalização.** O real é 10-11%. O modelo
antigo calculava a chance de gol como "qualidade do atacante contra
qualidade do goleiro":

```
probGol = qualidadeChute / (qualidadeChute + qualidadeGoleiro × 1,55)
```

Para um atacante bom contra um goleiro bom isso dava 0,41. Quatro vezes o
real.

**Erro 2 — 48% dos lances em zona adiantada viravam chute.** O razoável é
15-20%. Time nenhum finaliza a cada dois toques no campo de ataque.

Juntos: cerca de dez vezes mais gols do que deveria.

### O conserto: modelo de xG

O problema era conceitual. **No futebol real quem manda é a posição, não o
atacante.** Um chute da entrada da área vale ~4% para qualquer um —
Haaland ou um zagueiro improvisado. Um chute de dentro da pequena área vale
~30% para qualquer um. O finalizador move esse número em ±30%, não em 400%.

Então a estrutura correta ancora no xG e só tempera:

```
probabilidade = xG_da_posição
              × fatorFinalizador   (0,55 a 1,55)
              × fatorGoleiro       (0,70 a 1,35)
              × fatorPressão       (0,50 a 1,00)
              × fatorMinuto
```

O xG sai de patamares de distância reais — fora da área, entrada, dentro,
pequena área — multiplicados pelo ângulo. Chute da linha de fundo é quase
nada mesmo colado no gol.

### Como o 0,70 foi encontrado

O peso de chutar não foi chutado. Fiz uma varredura contra os números reais:

| peso | finalizações | gols | conversão |
|---|---|---|---|
| 0,62 | 9,0 | 0,8 | 11,1% |
| **0,70** | **13,7** | **1,1** | **8,1%** |
| 0,80 | 15,8 | 1,2 | 8,2% |
| 0,92 | 16,6 | 2,0 | 13,0% |

0,70 acerta as finalizações. Os gols aparecem baixos porque a varredura não
conta pênaltis, faltas e escanteios — que no motor somam ~0,3 por time.
Fecha em ~1,4.

### Aba "Motor": verifique você mesmo

Em **Análise → Motor** há um botão que roda 40 partidas em segundos e
compara suas médias com o futebol real, linha por linha. Existe porque
"está saindo muito gol" é impossível de verificar jogando — seriam dezenas
de partidas para formar média.

Se algum número sair fora, é ali que você vê qual, e os pontos de ajuste
estão todos em `Finalizacao.kt`.

## Sistema tático: mentalidade e instruções

**Mentalidade** é o controle mestre, como no FM. De 0 (muito retraída) a
100 (muito ofensiva). Ela não substitui os sete controles contínuos: ela os
**desloca**. `Tatica.efetiva()` aplica o deslocamento, e o motor lê sempre
a efetiva — é isso que faz a mentalidade valer de verdade em vez de ser um
número decorativo.

**Oito instruções de equipe**, cada uma com custo real:

| Instrução | Ganho | Custo |
|---|---|---|
| Linha de impedimento | rouba muitas bolas | expõe as costas da zaga (−8% defesa) |
| Pressão após perda | +10% no meio | cansa muito |
| Explorar os lados | +6% ataque | redireciona a bola pelos corredores |
| Jogar pelo meio | +8% ataque | mais bola perdida no miolo |
| Bola no homem-alvo | atacante recebe 80% mais | previsível |
| Fazer cera | +8% defesa | a torcida detesta |
| Sair jogando curto | mais posse | fatal quando falha |
| Ligação direta | evita risco na saída | perde posse |

## Passes mais fluidos

Três mudanças:

**Passe curto ficou muito mais provável.** O peso de proximidade passou de
`(1 − dist)` para `(1 − dist × 1,35)`, o que gera sequência de toques em
vez de bola pra frente.

**O relógio anda menos no toque curto** (2s contra 6s do lançamento). É o
que faz um time de posse dar 500 passes e um de ligação direta dar 300.

**A bola viaja conforme a distância.** Antes a interpolação era fixa e todo
passe parecia igual. Agora o fator é
`k × 2,4 / (0,35 + distância × 2,2)` — toque de três metros chega quase
instantâneo, lançamento cruza o campo visivelmente.

## Segundo lote de ideias

### moneyball-mentality (kemogu) — duas ideias, uma delas a melhor do lote

**Desenvolvimento do elenco.** Aquele projeto compara dois retratos do
elenco em temporadas diferentes e mostra quem evoluiu, atributo por
atributo. Eu não tinha visto isso em nenhum outro projeto e é a melhor
ideia deste lote.

Aqui virou automático: ao carregar uma temporada o jogo guarda um retrato
de cada jogador (`retratos`). Depois dá para responder perguntas que antes
eram invisíveis — o treino focado em ritmo funcionou? aquele garoto de 18
cresceu ou estagnou? quem começou a cair?

É a diferença entre saber o overall de hoje e entender a **trajetória**.

**Moneyball.** A pergunta deixa de ser "quem é o melhor?" e passa a ser
"quem entrega mais por euro?". O cálculo usa **preço por ponto acima de uma
base**, não preço por overall — porque a curva de preço no futebol é
exponencial e cada ponto acima de 75 custa desproporcionalmente mais.

**Arquétipos.** Seis categorias em vez de doze posições: goleiro, defensor,
pressionador, ala, criador, finalizador. Na hora de olhar o mercado você
normalmente quer "um criador", não especificamente "um MEI".

### FIFA-Player-Recomendation (inboxpraveen)

**Busca por semelhança.** Similaridade de cosseno sobre 20 eixos de
atributo, de 0 a 100.

Cosseno em vez de distância euclidiana **de propósito**: ele compara o
PERFIL, não o nível. Um camisa 9 de overall 68 pode ser 94% parecido com um
de 85 — mesmo tipo de jogador, qualidade diferente. É exatamente isso que
interessa quando seu titular saiu e você precisa de um substituto que caiba
no orçamento.

### FplDataCard (nishantbahri)

**Radar de atributos.** Aquele projeto monta cartões visuais em vez de
tabelas. A diferença prática é grande: uma tabela de 29 atributos obriga a
ler tudo para formar uma impressão; um radar entrega o perfil de relance.

Usei os **seis arquétipos como eixos**, não os 29 atributos. Radar com
muitos eixos vira uma bolha ilegível; com seis, cada ponta significa algo e
a forma inteira é reconhecível.

### O que não entrou, e por quê

**faces-download-fm** (manuelinfosec) e **fm-facepack-downloader**
(ashenarx) são a mesma ideia — baixar pacotes de rosto para o FM. A API do
projeto já entrega a imagem da carta de cada jogador, então isso está
coberto.

**BestPositionsFM** (gyane14) faz o que já foi implementado na rodada
anterior a partir do PyScoutFM: nota do jogador para cada posição.
Confirma a decisão, não adiciona nada novo.

**gafferOSv2** (asraym) — não abri este. Fui honesto sobre o critério: com
sete repositórios, priorizei os que tinham ideia distinta de mecânica sobre
os que pareciam ferramenta de importação. Se ele tem algo específico que
você quer, aponta e eu implemento.

## Ideias trazidas de outros projetos

### PyScoutFM (olimorris) — a fonte mais rica

**1. Nota para todas as posições, lado a lado.** O PyScoutFM gera um
relatório com a nota do jogador em cada posição, ordenável. É assim que se
acha o lateral que na verdade é um ala, ou o volante que dá um zagueiro
melhor do que os que você tem. Está em `TelaOlheiro`.

**2. Pesos editáveis.** Lá os pesos por posição ficam num JSON que o
usuário troca. Aqui `PesosPorPosicao` aceita pesos personalizados em vez
de esconder números fixos no código.

**3. DNA do clube.** A melhor ideia do conjunto. O autor agrupa atributos
com pesos próprios para produzir um "rating de DNA" — quanto o jogador
combina com a *identidade* do clube, não com a posição.

É diferente da adequação que já existia. Adequação responde "ele joga bem
de lateral?". DNA responde "ele joga do jeito que ESTE clube joga?". Um
lateral tecnicamente ótimo pode ter DNA baixo num time que vive de
intensidade física. Quatro identidades: Intensidade, Técnica,
Verticalidade, Solidez — e o clube já começa com a que mais combina com o
elenco herdado.

**4. Mascaramento de atributo** (`Observacao.kt`). No PyScoutFM, jogador
não observado aparece com faixa (`7-11`) e a ferramenta usa o pior valor.
Isso expôs uma lacuna grande aqui: você via os 29 atributos exatos de 16
mil jogadores do mundo, de graça — não existia trabalho de olheiro nenhum.

Agora jogador desconhecido mostra **faixa, não número**. Observar custa
por semana e vai estreitando a faixa em cinco níveis até o valor exato.
Potencial é o dado mais difícil: no nível 1 só diz "pode crescer".

A faixa é **determinística**, derivada de um hash do id e do atributo. Sem
isso o número dançaria a cada abertura de tela, o que quebraria a
confiança na informação.

### Football-Simulator (AllenThomasDev)

**5. Probabilidade por minuto.** Aquele projeto tabelou a chance de cada
evento minuto a minuto a partir de dados reais, em vez de taxa fixa. Fazia
falta: no futebol sai mais gol no fim (defesa cansada, time perdendo se
expõe) e mais cartão depois do intervalo. Meu motor usava taxa achatada
nos 90 minutos. Agora tem `fatorGol`, `fatorCartao` e `fatorFalta` — o
minuto 85 tem 30% mais chance de gol que a média, e o cartão quase triplica
do minuto 20 ao 70.

**6. Escanteios.** Estavam simplesmente ausentes do meu motor. Agora um
chute bloqueado tem 50% de sair pela linha de fundo. Quem cobra é o melhor
de cruzamento; quem cabeceia é o melhor de cabeceio e impulsão em campo,
contra a melhor defesa aérea adversária — e por isso passa a valer escalar
um zagueiro forte no alto mesmo que não seja o melhor no chão.

### Football-Manager-Game (ErenElagz)

**7. Popularidade separada de força.** O projeto mantém as duas como
eixos distintos. A reputação aqui já fazia esse papel; o que absorvi foi
usá-la de forma mais consistente na expectativa da diretoria, que compara
sua posição com a **força relativa** do clube na liga em vez de uma meta
fixa.

### Crypto Football Game (ChainInsighter)

Já documentado na seção do multijogador: cadeia de assinaturas, chave de
sessão por partida e verificação de integridade — que num P2P sem servidor
deixam de ser exercício educativo e passam a ser a única defesa existente.

## Multijogador local (sem servidor)

Dois aparelhos na mesma rede Wi-Fi jogam um contra o outro. Não existe
servidor em ponto nenhum do caminho.

### Por que isso é possível: o motor é determinístico

`PartidaAoVivo` recebe um `Random(semente)` e nada mais é aleatório. Dois
aparelhos com a mesma entrada produzem, gol a gol, **exatamente a mesma
partida**. Isso permite a arquitetura de **passo trancado**:

1. **OLA** — troca de chaves públicas e nonces
2. **IMPRESSÃO** — cada lado manda a assinatura da própria base de
   jogadores. Se não bate, a partida **nem começa**
3. **ESQUADRÃO** — escalação completa, assinada
4. **PRONTO** — semente combinada dos dois nonces
5. Os dois **simulam localmente**
6. **CHECKSUM** a cada 100 lances, comparado
7. **FIM** — resultado, também comparado

**Ninguém manda o resultado para o outro.** É isso que impede trapaça sem
servidor: um cliente modificado não consegue declarar que ganhou. Se a
simulação dele divergir, o checksum quebra e a partida é anulada. O que
ele consegue é apenas invalidar o jogo, nunca fabricar um placar.

### Criptografia (ideia trazida do Crypto Football Game)

O repositório de referência é um manager em Python com foco em segurança —
cadeia de certificados, chave de sessão por partida, dados cifrados. Aqui
essas ideias não são educativas, são a única defesa que existe:

| Peça | O que resolve |
|---|---|
| ECDH (secp256r1) | os dois derivam a mesma chave sem transmiti-la |
| AES-GCM | ninguém na mesma rede lê nem altera as mensagens |
| ECDSA | nenhum lado forja um comando "do outro jogador" |
| Impressão da base | os dois provam ter o mesmo dataset |
| Código de 6 dígitos | os dois confirmam a olho que não há intermediário |

O **código de verificação** merece nota: um atacante no meio do caminho
pode trocar as chaves públicas, mas não consegue fazer os dois códigos
baterem. Se os números diferem na tela dos dois, alguém está no meio. A
tela expõe isso de propósito, em fonte grande.

Tudo com `java.security` e `javax.crypto` — zero dependência nova.

### Comandos com atraso

Trocar tática não vale no lance atual: vale 12 lances à frente, nos dois
aparelhos. Sem esse atraso, quem mudou aplicaria antes de o outro receber
o aviso, e a partir dali as duas simulações seguiriam caminhos diferentes.

### Limites honestos

**Só funciona na mesma rede local.** Jogar pela internet sem servidor é
impossível: atravessar NAT exige, no mínimo, um servidor de sinalização.
Não existe truque que contorne isso — quem diz que contorna está usando
um servidor e chamando de outra coisa.

**A criptografia eleva a barra, não elimina a trapaça.** Quem controla o
próprio aparelho pode modificar o cliente. O que o desenho garante é que
uma modificação seja *detectada* (checksum divergente) em vez de aceita.
Sem servidor de confiança, detectar é o máximo que se consegue.

**O determinismo é frágil.** Qualquer mudança no motor quebra a
compatibilidade entre versões. Por isso existe o `VERSAO_MOTOR`, checado
no aperto de mão: melhor recusar a partida do que dessincronizar no
minuto 60.

## Design

A direção é uma **prancheta de técnico à noite**: fundo quase preto com um
verde-piscina frio, e o número em destaque sempre pesado. App de esporte
vive de número grande.

Decisões que sustentam isso, em `Design.kt`:

- **Hierarquia por peso, não por cor.** Placar em `FontWeight.Black` com
  espaçamento negativo de -2sp; rótulos de seção em caixa alta 10sp com
  espaçamento +1.4sp. O contraste vem dessa distância, não de encher a
  tela de cores.
- **Sem fonte customizada de propósito.** Arquivo de fonte pesa no APK, e
  o contraste de peso já dá a personalidade.
- **Identidade do clube derivada do nome.** O dataset não traz cor, então
  um hash estável do nome escolhe uma cor numa paleta curada. O mesmo
  clube sempre recebe a mesma cor, e nenhuma sai feia porque nenhuma é
  sorteada livremente.
- **Faixa da carta pelo overall** — bronze abaixo de 65, prata até 74,
  ouro até 83, elite acima. Quando o jogador está fora da posição, a
  moldura vira laranja ou vermelha: a informação mais importante da tela
  de escalação virou parte do desenho, não um texto ao lado.

`Componentes.kt` tem as peças reutilizáveis: carta de jogador, barra de
atributo, barra comparativa entre times, sequência de forma (V/E/D),
selo, cartão de número e linha de elenco.

## Painel da carreira

A home deixou de ser lista de links e virou painel. De cara aparecem:

- cabeçalho com gradiente na cor do clube, posição atual contra a meta
- **confiança da diretoria** com barra e situação (seguro → ameaçado)
- forma dos últimos 5 jogos
- próximo compromisso, com assistir ou simular
- mensagens urgentes da caixa de entrada
- artilheiros do elenco em carrossel

Navegação principal na **barra de baixo** (Painel, Time, Táticas, Mercado,
Caixa), com contador de urgentes na caixa de entrada.

## Diretoria

O que a diretoria espera sai da reputação do clube **em relação à liga**.
Assumir o lanterna e terminar no meio da tabela é sucesso; assumir o
favorito e terminar em quinto é fracasso. É essa relação que dá sentido a
começar pequeno.

A confiança pesa a diferença entre onde você está e onde deveria estar, e
**amadurece conforme a temporada avança** — errar na terceira rodada custa
menos que errar na trigésima.

## Caixa de entrada

Mensagens de diretoria, olheiro, departamento médico, contratos, imprensa
e mercado.

As notícias **não são armazenadas**: são derivadas do estado do jogo cada
vez que a tela abre. Evita mais uma tabela e garante que nada fique
obsoleto — se o jogador se recuperou, a mensagem sobre a lesão deixa de
existir sozinha.

## Artilharia

Tabela nova no banco (`estatisticas`), com gols, assistências, jogos,
cartões e média de nota por temporada.

Entrou por **migração explícita 1 → 2**, não por recriação. Adicionar
tabela é operação segura; recriar o banco apagaria os 16 mil jogadores
importados e obrigaria a esperar a importação inteira de novo. A migração
está em `AppDatabase.MIGRACAO_1_2`.


## Comportamento de função (substituiu os presets de fase)

Correção de um erro de design meu. Antes as fases ofensivas eram
desenhadas à mão em dois presets — e um deles era o exemplo pessoal de um
usuário, promovido a padrão do jogo. Isso estava errado.

Agora funciona como no Football Manager: cada jogador tem um
**comportamento**, e a posição dele com a bola é **calculada**:

```
COM_POSSE = base defensiva + deslocamento cheio do comportamento
TRANSICAO = base defensiva + metade do deslocamento
```

São 18 comportamentos, filtrados pela função — um lateral pode ser
*Fica na linha de quatro*, *Sobe como ala* ou *Entra no meio*; um volante
pode ser *Pivô*, *Cai entre os zagueiros* ou *Chega na área*.

O deslocamento lateral é **espelhado**: "para o centro" é direita para
quem joga na esquerda. Sem isso, um lateral esquerdo invertido sairia
para fora do campo.

Arrastar na aba **Sem a bola** move as três fases juntas, mantendo a
coerência do movimento. Arrastar nas outras sobrescreve só aquela — a
liberdade total continua lá, mas o padrão sai da função, não do gosto de
ninguém.

## Biblioteca de formações

16 formações com base e comportamentos sugeridos, agrupadas por família:

- **Linha de 4** — 4-4-2, 4-2-3-1, 4-3-3 com volante, 4-3-3 ofensiva,
  4-1-4-1, 4-4-2 diamante, 4-4-1-1, 4-1-2-1-2 estreito, 4-2-2-2, 4-5-1
- **Linha de 3** — 3-5-2, 3-4-3, 3-1-4-2
- **Linha de 5** — 5-3-2, 5-4-1, 5-2-3

Nenhuma tem fase ofensiva escrita à mão. Tudo é calculado.

## Treinador adversário

Antes todo adversário jogava a mesma 4-3-3 com o mesmo estilo
"equilibrado". A liga inteira não tinha cara, e a sua escolha tática
valia pouco porque não havia nada para responder.

Agora cada clube da IA tem três coisas:

1. **Formação escolhida pelo elenco.** A lógica é a de um técnico olhando
   o plantel: tenho pontas? tenho dois centroavantes? sobra zagueiro?
   Time fraco encolhe e prioriza não tomar gol.
2. **Estilo derivado dos atributos médios**, com uma variação pequena por
   clube para dois times de perfil parecido não ficarem idênticos.
3. **Adaptação a cada 10 minutos de jogo.** Perdendo no fim, sobe a linha
   e arrisca no passe. Ganhando fora de casa, recua e fecha. Ganhando por
   três, administra e poupa. Empatando em casa no fim, aprieta.

O sorteio usa o **id do clube como semente**, então o mesmo adversário
joga sempre igual ao longo da temporada. Ele tem identidade, não é
aleatório a cada partida.

## Ritmo da partida

Quatro velocidades, calibradas para ~1200 lances por jogo:

| Ritmo | Duração real |
|---|---|
| Tempo real | ~14 min |
| Pausado (padrão) | ~8 min |
| Normal | ~4 min |
| Rápido | ~1 min |

O fator de suavização da animação **acompanha o ritmo**. Isso importa:
antes, num ritmo lento as peças ficavam paradas e depois pulavam de uma
vez, porque a interpolação era fixa. E a bola agora **viaja** entre
passador e receptor em vez de teleportar.

## Motor de partida — cadeia de posse

**A bola sempre pertence a um jogador.** Nada acontece por conta própria:
todo lance é a decisão de quem está com ela — passar, conduzir, driblar
ou chutar. O adversário mais próximo tenta interceptar ou desarmar, e aí
pode sair falta.

Isso resolveu três problemas de uma vez:

- os passes deixaram de ser estranhos, porque **existem de verdade**
- a narração ganhou autor em cada linha
- faltas, pênaltis e impedimentos passaram a ter onde acontecer

O relógio anda **por lance**, não em passos fixos: um toque leva 3s, uma
falta cobrada leva 30s, um gol leva quase um minuto com a comemoração.
Uma partida dá entre 800 e 1200 lances, o que bate com o futebol real.

A decisão do portador sai dos atributos dele, de onde está no campo e das
instruções que você deu. Um ponta com drible alto e liberdade criativa
alta encara; um volante com passe alto procura o lançamento.

### Faltas, cartões e pênaltis

Desarme que falha pode virar falta, com chance vinda da agressividade do
defensor. Onde a falta aconteceu decide o que vem depois:

- dentro da área e no miolo → **pênalti**, batido por quem tem o melhor
  atributo de pênaltis
- fora da área mas adiantada → chance de **cobrança direta**, com curva e
  precisão de falta contando, e 28% de bater na barreira
- resto do campo → reposição simples

Segundo amarelo expulsa, e o time joga com um menos de verdade — a peça
sai do campo e das contas de força.

### Simulação instantânea usa o mesmo motor

`MotorPartida.simular()` é literalmente `PartidaAoVivo().pularParaOFim()`.
Uma partida assistida e uma simulada seguem exatamente as mesmas regras,
então nunca dá aquela sensação de que simular "dá outro resultado".

## Partida ao vivo

Quem tem a bola aparece com um anel, e cada passe desenha uma linha da
origem ao destino — dá para acompanhar a construção da jogada.

Dois laços rodam em paralelo na tela da partida:

- o **laço de simulação** chama `partida.passo()` no ritmo escolhido
- o **laço de animação** desliza as peças para o alvo a 60fps

As posições das peças **não são inventadas** — vêm direto do editor de
fases. Quando seu time tem a bola, as peças caminham para as coordenadas
de `COM_POSSE`; quando defende, para `SEM_POSSE`. Você vê a 4-2-3-1
virar 3-2-5 em tempo real, porque é literalmente a mesma estrutura de
dados que você desenhou.

Ritmos de 1x a 20x, com pausa a qualquer momento. O botão **Táticas** abre os controles no meio do jogo.
O botão **Substituir** pausa a partida e mostra quem está em campo com o
gás de cada um, mais o banco — 5 substituições, e o reserva herda o slot,
as instruções e o estilo de quem saiu.

A narração tem filtro: "só destaques" esconde os toques de rotina e deixa
gols, faltas, dribles, cartões e chutes.

**Performance:** as posições animadas ficam num `HashMap` comum, fora do
sistema de estado do Compose. O redesenho é disparado por um único
contador lido dentro do `Canvas`, então a cada frame roda só a fase de
desenho, não a de composição. Sem isso, 22 peças a 60fps recomporiam a
tela inteira 60 vezes por segundo.

## Escolha de clube

Carreira nova começa pela escolha: navega liga → clube, com força do
elenco, caixa e teto salarial à vista.

**Você não pode começar num clube de elite.** O teto de reputação sobe
por temporada (66 → 72 → 78 → 84 → livre), então você prova o trabalho
num clube pequeno e os grandes vão ficando ao alcance. Tem também o
botão **"Sortear um clube pra mim"**, no espírito do Soccer Champs.

O teto sai de `reputacaoMaximaPara()` em `Tatica.kt` — se quiser começar
solto, é só devolver 100 ali.

### Tática herdada

O clube que você assume **já vem com um estilo**, e ele não é sorteado:
sai dos atributos do próprio elenco, em `TaticaDoClube.derivarDe()`.

- elenco de bons passadores → **Posse de bola**
- elenco rápido → **Contra-ataque**
- elenco físico e resistente → **Pressão alta**
- elenco fraco (geral < 62) → **Retranca**

Aparece na tela inicial como "Estilo herdado do clube". A partir daí é
seu para mexer.

## Escalação com as cartas

O campo mostra os 11 com a imagem da carta vinda da API. Tocar num slot
abre o elenco **ordenado pelo rendimento naquela função específica**, com
a familiaridade de cada um. Se você escolher alguém que já está escalado,
os dois trocam de lugar.

A moldura do número conta a história: verde se ele joga ali, laranja se é
adaptação, vermelho se é gambiarra.

## Copa nacional

Eliminatória em jogo único rodando em paralelo à liga. Chaveamento com
cabeças de chave (1º x último), fases geradas conforme os vencedores
aparecem. Empate é decidido pelo mando de campo — aproximação simples
para a disputa de pênaltis.

As rodadas de copa são numeradas a partir de 1000 para não colidir com
as da liga na mesma tabela.

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

As fases ofensivas são calculadas pelo comportamento de cada função —
veja a seção "Comportamento de função" acima.

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

- Competições continentais (a estrutura de copa já suporta, falta popular)
- Salvar a formação no banco — hoje ela vive só na memória
- Renovação de contrato e jogadores livres
- Passagem de temporada (promoção, rebaixamento, envelhecimento do elenco)
- Balanceamento: os números do motor são um ponto de partida, não estão afinados

## Ajustando o motor

Se os placares saírem altos ou baixos demais, mexa em `MotorPartida.kt`:

- `probabilidadeDeChance()` — o `0.30f` no final controla quantas chegadas viram finalização
- `probGol` — o `coerceIn(0.02f, 0.55f)` limita a chance de cada chute virar gol
- `MOMENTOS` — mais momentos, mais eventos por jogo

## Sobre os dados

Os datasets do EA FC são levantamentos de comunidade. Servem para projeto pessoal, mas nomes de jogadores, escudos de clubes e a marca EA/FIFA são licenciados. Publicar na Play Store com esses dados é problema legal.
