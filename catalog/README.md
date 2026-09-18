# Catálogo gerado por CI

Este diretório é gerado automaticamente pelo workflow [`.github/workflows/sync.yml`](../.github/workflows/sync.yml),
que roda **a cada 30 minutos** e atualiza os dados das fontes.

## Arquivos

- `<fonte>.json` — catálogo completo da fonte (`mangadex`, `saikai`, `mangaonline`, `noxmangas`).
- `diffs/` — registros por execução com as obras **adicionadas** e **removidas** naquele passo.
- `last_run.json` — resumo da última sincronização (contagens por fonte).
- `removal_issues.json` — mapeamento de obras removidas → issues já abertas (evita duplicatas).

## Formato do catálogo

Cada obra tem:

```json
{
  "title": "Título",
  "url": "https://...",
  "thumb": "https://...",
  "status": "active",
  "first_seen": "2026-09-18T00:00:00Z",
  "removed_at": null
}
```

## Mangás removidos não são apagados

Quando uma obra deixa de aparecer na fonte, ela **não sai** do catálogo: o status vira
`removed` com `"removed_by_source": true` e `removed_at` preenchido, e uma issue
`removed-by-source` é aberta informando que ela foi retirada pela **fonte fornecedora dos dados**.