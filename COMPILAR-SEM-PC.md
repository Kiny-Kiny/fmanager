# Compilar sem PC — só com o celular

O caminho é: **Termux para editar e enviar o código, GitHub para compilar.**

Você não compila nada no celular. O GitHub tem máquinas Linux x86_64 de
graça, com o SDK oficial do Android. Elas compilam e devolvem o APK
pronto para você baixar.

## Por que não compilar direto no Termux

O Google distribui as build-tools do Android (`aapt2`, `d8`, `apksigner`)
apenas como binários **x86_64**. Seu celular é **ARM**. Não são
compatíveis.

O AndroidIDE existia justamente para resolver isso: ele empacotava
versões ARM dessas ferramentas. Mas o projeto foi **arquivado** pelo
criador, então não dá mais para contar com ele a longo prazo.

Sobram as alternativas ruins: emular x86_64 (lento a ponto de inviabilizar)
ou caçar binários ARM de terceiros (frágil e arriscado). Por isso o
caminho abaixo evita o problema em vez de brigar com ele.

---

## Passo 1 — Preparar o Termux

Instale o Termux pelo **F-Droid**, não pela Play Store (a versão da Play
Store está desatualizada e quebrada). Depois:

```bash
pkg update && pkg upgrade
pkg install git openssh
```

Configure seu nome e e-mail do GitHub:

```bash
git config --global user.name "Seu Nome"
git config --global user.email "seu@email.com"
```

## Passo 2 — Criar o repositório

1. Abra `github.com` no navegador do celular e crie uma conta, se não tiver
2. Toque em **New repository**
3. Nome: `fmanager`. Deixe **público** (Actions é ilimitado em repositório
   público; em privado você tem cota mensal)
4. **Não** marque nenhuma opção de inicialização
5. Crie

## Passo 3 — Gerar um token de acesso

O GitHub não aceita mais senha no `git push`. Você precisa de um token:

1. No GitHub: **Settings** → **Developer settings** → **Personal access
   tokens** → **Tokens (classic)**
2. **Generate new token (classic)**
3. Marque a permissão **repo** e também **workflow**
4. Gere e **copie o token** — ele só aparece uma vez

Guarde num lugar seguro. Ele funciona como senha no `git push`.

## Passo 4 — Enviar o projeto

Descompacte o `FManager.zip` numa pasta acessível (a pasta Download
serve). No Termux:

```bash
# Dá acesso ao armazenamento do celular
termux-setup-storage

cd ~/storage/downloads/fmanager

git init
git add .
git commit -m "Primeira versão"
git branch -M main
git remote add origin https://github.com/SEU_USUARIO/fmanager.git
git push -u origin main
```

Quando pedir senha, cole o **token**, não a senha da conta.

## Passo 5 — Pegar o APK

O envio já dispara a compilação. Depois de alguns minutos:

1. Abra seu repositório no navegador
2. Aba **Actions**
3. Toque na execução mais recente
4. Role até **Artifacts** e baixe **FManager-debug**

Vem como `.zip`. Qualquer gerenciador de arquivos do Android abre. Dentro
tem o `app-debug.apk`.

**Para baixar sem descompactar:** na aba Actions, use **Run workflow**
(botão manual). Isso também publica o APK em **Releases**, que baixa
direto em um toque.

## Passo 6 — Instalar

Toque no APK. O Android vai pedir para permitir instalação de fontes
desconhecidas — autorize para o gerenciador de arquivos que você usou.

---

## O ciclo do dia a dia

Depois de montado, mexer no código é assim:

```bash
cd ~/storage/downloads/fmanager
nano app/src/main/java/com/exemplo/fmanager/motor/MotorPartida.kt
# edita, Ctrl+O pra salvar, Ctrl+X pra sair

git add .
git commit -m "Ajustei o balanceamento do motor"
git push
```

E em poucos minutos tem APK novo na aba Actions.

Se o `nano` te incomodar, instale o **micro**, que é bem mais amigável:

```bash
pkg install micro
micro arquivo.kt
```

## Quando der erro de compilação

O log fica na aba Actions, dentro da execução, no passo **Compilar o APK
de debug**. Erro de Kotlin aparece com arquivo e linha. Copie a mensagem
e ajuste o arquivo pelo Termux.

Esse é o ponto chato desse fluxo: o retorno demora minutos em vez de
segundos. Vale editar com calma e revisar antes de enviar.

## Alternativa para editar com conforto

Se quiser um editor de verdade com autocompletar em vez do `nano`,
existe o **Android IDE - PHONE AS** (`com.m4coding.ide`) na Play Store.
Ele é ativo, suporta projetos Gradle e Kotlin, e tem preview de Compose.

Use ele para **editar**, e continue compilando pelo GitHub. Mesmo que a
compilação local dele funcione, o build na nuvem é mais confiável e não
esquenta nem gasta a bateria do celular.
