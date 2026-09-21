# Extensões para Mihon

Extensões em Kotlin para o **Mihon**, baseadas no padrão oficial do projeto [keiyoushi/extensions-source](https://github.com/keiyoushi/extensions-source), com catálogo mantido automaticamente por GitHub Actions.

## Como instalar no Mihon

A cada build, o CI publica um **repositório de extensões** na branch `repo` (APKs, `index.min.json` e `repo.json`). No Mihon:

1. Abra **Configurações → Extensões**.
2. Toque no botão **➕** para adicionar um repositório.
3. Cole a URL abaixo:

   ```text
   https://raw.githubusercontent.com/projeto01rj-dotcom/tachyomi-extensions/repo/index.min.json
   ```

4. Atualize a lista de repositórios e instale as extensões desejadas.

Como alternativa, baixe os APKs no artefato `apks` em **Actions → Build** e instale-os manualmente no Android.

> **Nota sobre assinatura:** os APKs usam o keystore de **depuração** do Android (certificado fixo), e o `repo.json` é gerado com o fingerprint correspondente. Por isso, o Mihon consegue validar o repositório sem que nenhum segredo seja incluído no código. Se o projeto for compartilhado, considere substituir por um `signingkey.jks` próprio com os secrets `KEY_STORE_PASSWORD`, `ALIAS` e `KEY_PASSWORD`; o CI detecta o arquivo automaticamente e recalcula o fingerprint.

> **Conflito com o repositório oficial:** a extensão `mangadex` usa o mesmo pacote (`eu.kanade.tachiyomi.extension.all.mangadex`) do repositório oficial keiyoushi. Se os dois repositórios estiverem adicionados ao Mihon, eles poderão disputar a atualização dessa extensão. Use apenas um dos repositórios para MangaDex.

## Fontes incluídas

| Extensão | Site | Como funciona |
| --- | --- | --- |
| `src/all/mangadex` | [mangadex.org](https://mangadex.org) | API oficial `api.mangadex.org` (60 idiomas) |
| `src/pt/saikaiscan` | [housesaikai.net](https://housesaikai.net) | API `api.housesaikai.net/api` |
| `src/pt/mangaonline` | [mangaonline.love](https://mangaonline.love) | Tema **Madara** (`lib-multisrc/madara`) |
| `src/pt/noxmangas` | [noxmangas.org](https://noxmangas.org) | API Nix assinada (`/_nix/signer.js` → SHA-256) |

## Estrutura

```text
src/            extensões em Kotlin (1 módulo = 1 fonte)
lib/            bibliotecas compartilhadas (i18n)
lib-multisrc/   temas multissite (madara)
core/, compiler/, gradle/   infraestrutura de build do keiyoushi (intocada)
scripts/        sincronização do catálogo em Python
catalog/        dados das fontes atualizados a cada 30 minutos pelo CI
.github/workflows/  build.yml e sync.yml
```

## GitHub Actions

- **`build.yml`** — compila as quatro extensões em APKs (`assembleRelease`) a cada push e disponibiliza o artefato `apks`. Em seguida, publica a branch `repo` (APKs, `index.min.json` e `repo.json`) via `.github/scripts/publish_index.py`; essa é a fonte que o Mihon consome. O workflow também pode ser executado manualmente em **Actions → Build → Run workflow**.
- **`sync.yml`** — executa a cada 30 minutos (`*/30 * * * *`), consulta as quatro fontes, atualiza o catálogo, marca obras removidas e publica as alterações na pasta `catalog/`.

## Compilando localmente (opcional)

Requisitos: JDK 17+, Android SDK (compileSdk 37) e Git.

```bash
./gradlew :src:all:mangadex:assembleRelease \
          :src:pt:mangaonline:assembleRelease \
          :src:pt:noxmangas:assembleRelease \
          :src:pt:saikaiscan:assembleRelease
```

Os APKs ficam em `src/<lang>/<extensão>/build/outputs/apk/release/`. A assinatura usada é de depuração e é ativada automaticamente quando não existe `signingkey.jks`.

## Sincronização local (sem CI)

```bash
pip install -r scripts/requirements.txt
python scripts/sync_catalog.py --repo-dir . --out catalog
```

Exemplos de catálogo ficam em `catalog/`.

## Licença

O núcleo de build (`gradle/build-logic`, `core` e `compiler`) vem do [keiyoushi/extensions-source](https://github.com/keiyoushi/extensions-source) e é licenciado sob Apache-2.0 conforme o arquivo `LICENSE`.
