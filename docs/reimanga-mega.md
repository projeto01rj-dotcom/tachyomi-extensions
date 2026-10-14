# ReiManga + MEGA compartilhamento

Esta alteração adiciona duas fontes instaláveis para o Mihon:

- **ReiManga**: catálogo, busca, detalhes, capítulos e imagens diretamente de `reimanga.net`.
- **MEGA compartilhamento (PT-BR)**: leitura de uma pasta pública da MEGA organizada como `mangá/capítulo/imagens`, com descriptografia local das chaves públicas e sem login.

## Organização da pasta MEGA

```text
pasta compartilhada/
└── Nome do mangá/
    ├── capa.jpg                 # opcional
    ├── capítulo 181/
    │   ├── 001.png
    │   └── 002.png
    └── capítulo 182/
        └── 001.png
```

A fonte MEGA usa a pasta pública configurada nas preferências. O padrão é o link compartilhado fornecido para este projeto. A chave do link fica no fragmento da URL e é usada somente no aparelho; não há e-mail, senha ou token de conta no código.

## O que fica fora do APK

Uma extensão Mihon não é um agente de automação persistente e não deve guardar credenciais de upload. Por isso, o APK **não** baixa capítulos do Reimanga para traduzir, não faz OCR/inpainting e não envia arquivos à MEGA. A fonte apenas lê o resultado já publicado na pasta pública.

O pipeline externo deve:

1. detectar o mangá/capítulo selecionado no Reimanga;
2. baixar temporariamente os dez primeiros capítulos ainda não processados;
3. traduzir as imagens para português brasileiro conforme a habilidade do projeto, mantendo a arte e as onomatopeias;
4. criar `Nome do mangá/capítulo N/` e enviar imagens individuais, sem CBZ/ZIP;
5. atualizar um estado por mangá (`último capítulo traduzido`, `próximo lote`);
6. ao concluir a leitura do lote, processar os dez seguintes até o lançamento atual;
7. publicar uma mensagem de indisponibilidade quando a tradução falhar, em vez de expor credenciais ou servir um capítulo incompleto.

Esse agente externo deve receber a conta MEGA por variável de ambiente ou cofre de segredos. Senhas não devem ser colocadas em Kotlin, no GitHub, no README ou em URLs públicas.

## Limitações e comportamento esperado

- Mangás e capítulos que já estejam em português no Reimanga permanecem sem alteração.
- Capítulos traduzidos aparecem na fonte **MEGA compartilhamento (PT-BR)** depois que a pasta pública for atualizada e a fonte for recarregada no Mihon.
- A integração não altera arquivos das fontes existentes do repositório.
- O build local desta sessão não pôde ser concluído porque o sandbox não possui Android SDK instalado; o JDK foi instalado e o Gradle parou antes da compilação ao procurar o SDK. A validação final deve ser executada em CI ou em um ambiente Android configurado.

## Privacidade e segurança

O link público permite leitura por qualquer pessoa que o possua. A fonte não implementa login de conta MEGA, upload, exclusão, renomeação ou compartilhamento. Essa separação evita que uma senha de upload seja distribuída dentro do APK.
