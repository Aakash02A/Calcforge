# API contracts

Base path: `/api/v1`. Two clearly separated surfaces:

- **`/api/v1/local/**`** - no authentication, ever. Fully deterministic, works with no
  internet connection as long as this server and its MySQL database are reachable.
- **`/api/v1/cloud/**`** - optional. Everything except `/cloud/auth/**`,
  `/cloud/feature-flags`, and `/cloud/shared/**` requires `Authorization: Bearer <JWT>`.

All error responses share one shape (`ApiError`):

```json
{
  "timestamp": "2026-01-01T12:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "errorCode": "DIVISION_BY_ZERO",
  "message": "Division by zero (5 / 0)",
  "path": "/api/v1/local/calculate",
  "fieldErrors": null
}
```

`errorCode` is one of the engine's `ExpressionException.ErrorCode` values
(`SYNTAX_ERROR`, `UNBALANCED_PARENTHESES`, `UNKNOWN_VARIABLE`, `UNKNOWN_FUNCTION`,
`WRONG_ARGUMENT_COUNT`, `DIVISION_BY_ZERO`, `DOMAIN_ERROR`, `OVERFLOW`,
`LIMIT_EXCEEDED`, `EMPTY_EXPRESSION`) for calculation errors, or a general code
(`VALIDATION_ERROR`, `NOT_FOUND`, `DUPLICATE_RESOURCE`, `INVALID_CREDENTIALS`,
`ACCESS_DENIED`, `INTERNAL_ERROR`) otherwise.

## Local - calculation

| Method | Path | Body | Notes |
|---|---|---|---|
| POST | `/local/calculate` | `CalculateRequest` | Evaluate an expression; returns `CalculationResponse` with the full trail. Saves to history by default (`saveToHistory: false` to skip). |
| POST | `/local/calculate/validate` | `{ "expression": "..." }` | Syntax check only - no evaluation, no persistence. |

## Local - workspaces & canvas

| Method | Path | Notes |
|---|---|---|
| GET/POST | `/local/workspaces` | List / create local workspaces. |
| GET/PUT/DELETE | `/local/workspaces/{id}` | |
| GET/POST | `/local/workspaces/{id}/calculations` | List / create canvas cards. |
| GET/PUT/DELETE | `/local/workspaces/{id}/calculations/{cardId}` | |
| POST | `/local/workspaces/{id}/calculations/reorder` | Body `{ "cardIds": [3,1,2] }`. |

## Local - variables, formulas, scenarios

| Method | Path | Notes |
|---|---|---|
| GET/POST | `/local/workspaces/{id}/variables` | |
| GET/PUT/DELETE | `/local/workspaces/{id}/variables/{variableId}` | |
| GET/POST | `/local/workspaces/{id}/formulas` | |
| GET/PUT/DELETE | `/local/workspaces/{id}/formulas/{formulaId}` | |
| POST | `/local/workspaces/{id}/formulas/{formulaId}/evaluate` | Body `FormulaEvaluateRequest`. |
| GET/POST | `/local/workspaces/{id}/scenarios` | |
| DELETE | `/local/workspaces/{id}/scenarios/{scenarioId}` | |
| POST | `/local/workspaces/{id}/scenarios/run` | Evaluates one expression under a baseline plus each scenario's overrides. |

## Local - history

| Method | Path | Notes |
|---|---|---|
| GET | `/local/history?q=&tag=&page=&pageSize=` | Paginated search. |
| GET/PATCH/DELETE | `/local/history/{id}` | PATCH body `HistoryUpdateRequest` (tags/favorite). |

## Local - units, finance, graph

| Method | Path | Notes |
|---|---|---|
| GET | `/local/units/categories` | All categories and their units. |
| POST | `/local/units/convert` | `UnitConversionRequest` -> `UnitConversionResponse` (includes a trail). |
| POST | `/local/finance/loan` | Amortization schedule. |
| POST | `/local/finance/compound-interest` | |
| POST | `/local/finance/npv` | |
| POST | `/local/finance/tip-split` | |
| POST | `/local/graph` | `GraphRequest` -> sampled `(x, y)` points; `y: null` where undefined. |

## Cloud - auth (unauthenticated)

| Method | Path | Notes |
|---|---|---|
| POST | `/cloud/auth/register` | -> `AuthResponse` (access + refresh token). |
| POST | `/cloud/auth/login` | |
| POST | `/cloud/auth/refresh` | Rotates the refresh token. |
| POST | `/cloud/auth/logout` | Revokes the given refresh token. |

## Cloud - authenticated

| Method | Path | Notes |
|---|---|---|
| POST | `/cloud/sync/push` | Push local changes (last-write-wins). |
| POST | `/cloud/sync/pull` | Pull everything changed since a timestamp. |
| GET | `/cloud/backup/export` | Full account export. |
| POST | `/cloud/backup/restore` | Re-applies an exported bundle. |
| POST | `/cloud/ai/ask` | Natural-language assist (needs `calcforge.cloud.ai-assist-enabled` + an Anthropic API key). |
| GET | `/cloud/currency/rates` | Reports whether currency rates are live or static-seeded. |
| POST | `/cloud/workspaces/{id}/share` | Owner-only; body `{ "shared": true }`. |

## Cloud - public

| Method | Path | Notes |
|---|---|---|
| GET | `/cloud/feature-flags` | Which optional features this deployment has enabled. |
| GET | `/cloud/shared/workspaces/{id}` | Read-only view of a workspace its owner shared. |

See `docs/CALCULATION_TRAIL.md` for the shape of every `trail` field returned above.
