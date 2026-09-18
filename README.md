# Tachyomi Extensions

Extensões Tachiyomi/Mihon (Kotlin) baseadas no padrão oficial
[keiyoushi/extensions-source](https://github.com/keiyoushi/extensions-source), com
catálogo mantido automaticamente por GitHub Actions.

## Como instalar no app (Mihon / Tachiyomi)

A cada build, o CI publica também um **repositório de extensões** na branch `repo` (APKs +
`index.min.json` + `repo.json`). No app:

1. **Mihon**: Settings → Extensions → botão ➕ (adicionar repositório) → cole a URL:

   ```
   https://raw.githubusercontent.com/projeto01rj-dotcom/tachyomi-extensions/repo/index.min.json
   ```

   **Tachiyomi** (versões antigas): Browse → Extensions → ⋮ → Extension repos → adicionar o
   endereço `https://github.com/projeto01rj-dotcom/tachyomi-extensions/raw/repo/index.min.json`.

2. Instale as extensões desejadas normalmente.

Alternativa rápida (instalação manual, sem repo): baixe os APKs do artefato `apks` em
**Actions → Build**, e instale-os como um APK comum.

> **Nota sobre assinatura:** os APKs usam o keystore de **depuração** do Android
> (certificado fixo), e o `repo.json` é gerado com o fingerprint correspondente — por isso o
> app aceita o repositório sem nenhum segredo. Se o projeto for compartilhado, considere
> substituir por um `signingkey.jks` próprio com os secrets `KEY_STORE_PASSWORD`, `ALIAS`,
> `KEY_PASSWORD` (o CI detecta o arquivo automaticamente e recalcula o fingerprint).

> **Conflito com o repo oficial:** a extensão `mangadex` usa o mesmo pacote
> (`eu.kanade.tachiyomi.extension.all.mangadex`) do repositório oficial keiyoushi. Se você tiver
> os dois repositórios adicionados, eles vão disputar a atualização dessa extensão — use um só.

## Fontes incluídas

| Extensão | Site | Como funciona |
| --- | --- | --- |
| `src/all/mangadex` | [mangadex.org](https://mangadex.org) | API oficial `api.mangadex.org` (60 idiomas) |
| `src/pt/saikaiscan` | [housesaikai.net](https://housesaikai.net) | API `api.housesaikai.net/api` |
| `src/pt/mangaonline` | [mangaonline.tv](https://mangaonline.tv) | Tema **Madara** (`lib-multisrc/madara`) |
| `src/pt/noxmangas` | [noxmangas.org](https://noxmangas.org) | API Nix assinada (`/_nix/signer.js` → SHA-256) |

## Estrutura

```
src/            extensões em Kotlin (1 módulo = 1 fonte)
lib/            bibliotecas compartilhadas (i18n)
lib-multisrc/   temas multissite (madara)
core/, compiler/, gradle/   infraestrutura de build do keiyoushi (intocada)
scripts/        sincronização do catálogo em Python
catalog/        dados das fontes atualizados a cada 30 min pelo CI
.github/workflows/  build.yml e sync.yml
```

## GitHub Actions

- **`build.yml`** — compila as 4 extensões em APKs (`assembleRelease`) em todo push e
  disponibiliza os artefatos (`apks`). Na sequência, publica/apodera a branch `repo`
  (APKs + `index.min.json` + `repo.json`) via `.github/scripts/publish_index.py` — é essa
  "loja" que o app consome. Pode ser executado manualmente em **Actions → Build → Run workflow**.
- **`sync.yml`** — roda via cron **a cada 30 minutos** (`*/30 * * * *`):
  1. consulta as 4 fontes;
  2. detecta **mangás novos** e os adiciona ao catálogo;
  3. detecta mangás **removidos pela fonte fornecedora dos dados** e os **marca** no catálogo
     (`status: removed`, `removed_by_source: true`) em vez de apagá-los;
  4. abre uma issue `removed-by-source` para cada remoção (só a primeira vez);
  5. faz commit e push do `catalog/`.

## Compilando localmente (opcional)

Requisitos: JDK 17+, Android SDK (compileSdk 37) e Git.

```bash
./gradlew :src:all:mangadex:assembleRelease \
          :src:pt:mangaonline:assembleRelease \
          :src:pt:noxmangas:assembleRelease \
          :src:pt:saikaiscan:assembleRelease
```

Os APKs ficam em `src/<lang>/<extensão>/build/outputs/apk/release/`. A assinatura usada é de
depuração (automaticamente reverte-se para `debug` quando não há `signingkey.jks`).

## Sincronização local (sem CI)

```bash
pip install -r scripts/requirements.txt
python scripts/sync_catalog.py --repo-dir . --out catalog
```

Exemplos de catálogo em `catalog/` (seed inicial já versionado).

## Licença

O núcleo de build (`gradle/build-logic`, `core`, `compiler`) vem do
[keiyoushi/extensions-source](https://github.com/keiyoushi/extensions-source), licenciado sob
Apache-2.0 conforme `LICENSE`.