# Procedência

Esta fonte deriva da implementação `src/pt/leituramanga` do repositório `keiyoushi/extensions-source`, incluindo a correção revisada no pull request [#13640](https://github.com/keiyoushi/extensions-source/pull/13640).

O código upstream é distribuído sob Apache License 2.0. A licença do repositório está em `../../LICENSE`.

A fonte usa a API de catálogo e os dados de páginas que o próprio site retorna. Se o site responder HTTP 403, a extensão exibe a orientação prevista pelo upstream para abrir primeiro uma obra ou capítulo na WebView do Mihon; não simula autenticação ou liberação de acesso.
