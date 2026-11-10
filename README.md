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

O índice publicado contém os APKs assinados e os metadados necessários para o Mihon validar as extensões. A última publicação foi validada pelo workflow [Build](https://github.com/projeto01rj-dotcom/tachyomi-extensions/actions/workflows/build.yml).

Como alternativa, baixe os APKs no artefato `apks` em **Actions → Build** e instale-os manualmente no Android.

> **Nota sobre assinatura:** os APKs usam o keystore de **depuração** do Android (certificado fixo), e o `repo.json` é gerado com o fingerprint correspondente. Por isso, o Mihon consegue validar o repositório sem que nenhum segredo seja incluído no código. Se o projeto for compartilhado, considere substituir por um `signingkey.jks` próprio com os secrets `KEY_STORE_PASSWORD`, `ALIAS` e `KEY_PASSWORD`; o CI detecta o arquivo automaticamente e recalcula o fingerprint.

> **Conflito com o repositório oficial:** a extensão `mangadex` usa o mesmo pacote (`eu.kanade.tachiyomi.extension.all.mangadex`) do repositório oficial keiyoushi. Se os dois repositórios estiverem adicionados ao Mihon, eles poderão disputar a atualização dessa extensão. Use apenas um dos repositórios para MangaDex.

## Fontes incluídas

| Extensão | Site | Como funciona |
| --- | --- | --- |
| `src/all/mangadex` | [mangadex.org](https://mangadex.org) | API oficial `api.mangadex.org` (60 idiomas) |
| `src/all/mangaplus` | [MANGA Plus](https://mangaplus.shueisha.co.jp) | API oficial do serviço, com catálogo, capítulos e leitor protobuf |
| `src/all/googledrive` | [Google Drive](https://drive.google.com) | Leitura de pastas compartilhadas, com suporte a CBZ, CBR e PDF |
| `src/pt/saikaiscan` | [housesaikai.net](https://housesaikai.net) | API `api.housesaikai.net/api` |
| `src/pt/mangaonline` | [mangaonline.love](https://mangaonline.love) | Tema **Madara** (`lib-multisrc/madara`) |
| `src/pt/noxmangas` | [noxmangas.org](https://noxmangas.org) | API Nix assinada (`/_nix/signer.js` → SHA-256) |
| `src/pt/loobyt` | [loobyt.org](https://loobyt.org) | API Nix assinada com fallback para HTML público |
| `src/en/reimanga` | [reimanga.net](https://www.reimanga.net) | Catálogo, detalhes, capítulos e páginas por HTML |
| `src/all/megacompartilhamento` | [MEGA](https://mega.nz) | Leitura de pastas compartilhadas via API MEGA; sem login |

## Configuração das fontes compartilhadas

### Google Drive

Depois de instalar a extensão, abra suas configurações no Mihon e informe uma ou mais pastas compartilhadas. Os valores podem ser separados por quebra de linha, vírgula ou espaço. São aceitos links de pasta do Google Drive e IDs de pasta. Para pastas que exigem autenticação, configure também a chave de API nas opções da extensão.

A extensão indexa as pastas e usa cache local para os arquivos baixados. O cache pode ser dimensionado nas configurações e limpo pela opção **Borrar archivos en caché**.

### MEGA compartilhamento

Abra as configurações da extensão e cole o link completo da pasta compartilhada no campo **Link compartilhado do MEGA**. São aceitos os formatos atuais `mega.nz/folder/...#...`, `mega.co.nz/folder/...#...` e o formato legado `#F!...!...`.

O MEGA usa criptografia de conhecimento zero no serviço, mas a chave de uma pasta compartilhada faz parte do próprio link. Portanto, qualquer pessoa que tenha o link completo pode acessar o conteúdo autorizado por ele. Não publique links MEGA em locais públicos se o conteúdo for privado.

## Segurança e rede

As extensões deste repositório fazem apenas conexões de saída HTTPS com os serviços configurados e seus CDNs. A auditoria estática das extensões Google Drive e MEGA não encontrou servidor local, `ServerSocket`, `bind`, abertura intencional de portas, telemetria adicional ou envio de arquivos para destinos fora dos serviços correspondentes. Arquivos do Google Drive podem permanecer no cache local até que a opção de limpeza seja usada.

## Estrutura

```text
src/            extensões em Kotlin (1 módulo = 1 fonte)
lib/            bibliotecas compartilhadas (i18n e leitura de arquivos)
lib-multisrc/   temas multissite (madara)
core/, compiler/, gradle/   infraestrutura de build do keiyoushi (intocada)
scripts/        sincronização do catálogo em Python
catalog/        dados das fontes atualizados a cada 30 minutos pelo CI
.github/workflows/  build.yml e sync.yml
```

## GitHub Actions

- **`build.yml`** — compila as nove extensões em APKs (`assembleRelease`) a cada push e disponibiliza o artefato `apks`. Em seguida, publica a branch `repo` (APKs, `index.min.json` e `repo.json`) via `.github/scripts/publish_index.py`; essa é a fonte que o Mihon consome. O workflow também pode ser executado manualmente em **Actions → Build → Run workflow**.
- **`sync.yml`** — executa a cada 30 minutos (`*/30 * * * *`), consulta as quatro fontes com adaptador de catálogo, atualiza o catálogo, marca obras removidas e publica as alterações na pasta `catalog/`.

O build oficial mais recente confirmou a compilação das nove extensões, a geração dos APKs e a publicação da branch `repo`.

## Compilando localmente (opcional)

Requisitos: JDK 17+, Android SDK (compileSdk 37) e Git.

```bash
./gradlew :src:all:mangadex:assembleRelease \
          :src:all:mangaplus:assembleRelease \
          :src:all:googledrive:assembleRelease \
          :src:all:megacompartilhamento:assembleRelease \
          :src:en:reimanga:assembleRelease \
          :src:pt:mangaonline:assembleRelease \
          :src:pt:noxmangas:assembleRelease \
          :src:pt:loobyt:assembleRelease \
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
