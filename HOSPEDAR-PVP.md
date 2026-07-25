# Hospedar o servidor do modo online

**Você não escreve nenhuma linha de código de servidor.**

O modo online usa [PocketBase](https://pocketbase.io): um binário único de
~15 MB com SQLite embutido, API REST automática, autenticação e painel de
administração. Você sobe o binário, cria três coleções pelo painel, e
acabou.

## Por que não um servidor próprio

Os projetos de referência resolvem isso com Spring Boot + MySQL + MongoDB +
Redis + Nginx + Prometheus via docker-compose. Isso significa escrever **e
manter** um servidor: atualizações de segurança, migrações de banco,
monitoramento, backup.

Com PocketBase o servidor é um processo. Se ele cair, você reinicia. O
backup é copiar um arquivo `.db`.

**Não existe servidor oficial deste jogo.** Cada pessoa aponta o app para a
instância que quiser — a sua, a de um amigo, uma comunitária. Não há ponto
único de falha nem custo caindo em cima de ninguém.

---

## Passo 1 — Subir o PocketBase

### Opção A: VPS (recomendado)

Qualquer VPS de 4 a 6 dólares por mês aguenta centenas de jogadores.

```bash
# Baixe o binário para Linux x86_64
wget https://github.com/pocketbase/pocketbase/releases/latest/download/pocketbase_linux_amd64.zip
unzip pocketbase_linux_amd64.zip

# Suba
./pocketbase serve --http=0.0.0.0:8090
```

Para deixar rodando depois de fechar o terminal, use systemd:

```ini
# /etc/systemd/system/pocketbase.service
[Unit]
Description=PocketBase
After=network.target

[Service]
Type=simple
User=pocketbase
WorkingDirectory=/opt/pocketbase
ExecStart=/opt/pocketbase/pocketbase serve --http=127.0.0.1:8090
Restart=always

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl enable --now pocketbase
```

### Opção B: Docker

```yaml
# docker-compose.yml
services:
  pocketbase:
    image: ghcr.io/muchobien/pocketbase:latest
    restart: unless-stopped
    ports:
      - "8090:8090"
    volumes:
      - ./pb_data:/pb_data
```

```bash
docker compose up -d
```

### HTTPS

Ponha o Caddy na frente. Ele resolve certificado automaticamente:

```
# Caddyfile
meu-servidor.com {
    reverse_proxy 127.0.0.1:8090
}
```

O app **exige `https://`** para conexões fora da rede local, então isso não
é opcional se você for expor na internet.

---

## Passo 2 — Criar as coleções

Abra `http://SEU_IP:8090/_/`, crie a conta de administrador, e monte três
coleções.

### 1. `tecnicos` — coleção do tipo **Auth**

Marque como **Auth collection** ao criar. Depois adicione os campos:

| Campo | Tipo | Padrão | Observação |
|---|---|---|---|
| `apelido` | Text | — | obrigatório, máx. 18 |
| `pontos` | Number | 1400 | rating Elo |
| `vitorias` | Number | 0 | |
| `empates` | Number | 0 | |
| `derrotas` | Number | 0 | |

**Regras de API** (aba *API Rules*):

- List/View: `@request.auth.id != ""`
- Create: deixe **vazio** (qualquer um pode se registrar)
- Update: `id = @request.auth.id`
- Delete: `id = @request.auth.id`

### 2. `desafios` — coleção **Base**

| Campo | Tipo | Observação |
|---|---|---|
| `versao` | Number | |
| `dono` | Relation → tecnicos | obrigatório |
| `dono_apelido` | Text | |
| `dono_pontos` | Number | |
| `estado` | Text | ABERTO / ACEITO / CONFIRMADO / EM_DISPUTA |
| `semente` | Number | |
| `elenco_dono` | JSON | |
| `custo_dono` | Number | |
| `adversario` | Relation → tecnicos | |
| `adversario_apelido` | Text | |
| `elenco_adversario` | JSON | |
| `custo_adversario` | Number | |
| `resultado_dono` | Text | assinatura do resultado |
| `resultado_adversario` | Text | |
| `gols_dono` | Number | |
| `gols_adversario` | Number | |

**Regras de API:**

- List/View: `@request.auth.id != ""`
- Create: `@request.auth.id = dono`
- Update: `@request.auth.id = dono || @request.auth.id = adversario`
- Delete: `@request.auth.id = dono`

A regra de Update é a que importa: só os dois envolvidos mexem no desafio.

---

## Passo 3 — Apontar o app

No app, aba **Online**, coloque o endereço (`https://meu-servidor.com`),
toque em **Testar conexão**, e crie a conta.

---

## Como o anti-trapaça funciona sem servidor de jogo

O PocketBase **não simula nada**. Ele é um banco com API.

O árbitro é o **determinismo do motor**: os dois aparelhos rodam a mesma
partida com a mesma semente e enviam uma assinatura do resultado. Quando as
duas assinaturas batem, o desafio é confirmado e o rating se move. Quando
não batem, vai para `EM_DISPUTA` e **ninguém pontua**.

Consequência: um cliente modificado consegue no máximo **anular** a
partida. Nunca fabricar uma vitória, porque a vitória só conta com a
concordância do adversário.

**Limite honesto:** isso não impede duas pessoas combinadas de rodarem
clientes modificados idênticos para inflar rating entre si. Impedir isso
exigiria o servidor simular a partida — e aí você estaria mantendo um
servidor de jogo, que é exatamente o que este desenho evita.

---

## Backup

```bash
# Todo o estado do servidor está numa pasta
tar czf backup-$(date +%F).tar.gz pb_data/
```

Um cron semanal resolve.

## Quanto aguenta

Um desafio pesa uns 40 KB (dois elencos completos em JSON). Uma VPS de 1 GB
de RAM com PocketBase serve tranquilamente algumas centenas de jogadores
ativos. Se crescer além disso, o gargalo será banda, não CPU — e a solução é
guardar menos atributos por jogador no pacote do elenco.
