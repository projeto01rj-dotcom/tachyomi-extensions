# Tachyomi Extensions

Extensões Tachiyomi/Mihon (Kotlin) baseadas no padrão oficial
[keiyoushi/extensions-source](https://github.com/keiyoushi/extensions-source), com
catálogo mantido automaticamente por GitHub Actions.

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
  disponibiliza os artefatos (`apks`). Pode ser executado manualmente em **Actions → Build → Run workflow**.
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