#!/data/data/com.termux/files/usr/bin/bash
#
# ATUALIZAR — aplica um zip novo do projeto sem deixar arquivo velho para trás.
#
# O problema que isso resolve: quando um arquivo é apagado ou renomeado numa
# versão nova, extrair o zip por cima NÃO remove o antigo. Sobram duas
# versões da mesma classe e o Kotlin acusa "Redeclaration" — foi o que
# gerou 351 erros de uma vez.
#
# USO:
#   bash atualizar.sh ~/storage/downloads/FManager.zip
#
set -e

ZIP="$1"

if [ -z "$ZIP" ] || [ ! -f "$ZIP" ]; then
    echo "Uso: bash atualizar.sh /caminho/para/FManager.zip"
    exit 1
fi

if [ ! -d ".git" ]; then
    echo "Rode isso dentro da pasta do repositório (onde está o .git)."
    exit 1
fi

echo "==> Apagando o código-fonte antigo"
# Só o código. Não toca em .git, nem em assets, nem no gradle wrapper.
rm -rf app/src/main/java

echo "==> Extraindo a versão nova"
TMP=$(mktemp -d)
unzip -q "$ZIP" -d "$TMP"

# O zip tem uma pasta 'fmanager' na raiz; copia o conteúdo dela.
RAIZ="$TMP/fmanager"
if [ ! -d "$RAIZ" ]; then
    RAIZ="$TMP"
fi

cp -r "$RAIZ"/. .
rm -rf "$TMP"

echo "==> Enviando"
# O -A é o que importa: registra também os arquivos APAGADOS.
git add -A
git commit -m "Atualiza projeto" || echo "(nada mudou)"
git push

echo ""
echo "Pronto. Acompanhe o build na aba Actions."
