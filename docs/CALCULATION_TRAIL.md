# The calculation trail

Every calculation CalcForge performs - typed at the keypad, saved as a workspace card,
run from a formula, converted between units - produces a **trail**: a structured,
step-by-step record of exactly how the result was reached. This is the product's core
promise of transparency, and it is not optional or best-effort: if the engine can't
build a trail, it doesn't return a result.

## Shape

A trail is a flat, ordered list of steps, grouped into five stages:

```json
{
  "steps": [
    { "stage": "INPUT",        "title": "Input",              "expression": "2 + 3 * 4", "value": null,   "note": null },
    { "stage": "ASSUMPTIONS",  "title": "Angle mode",          "expression": null,        "value": "DEGREES", "note": "Applies to trigonometric functions" },
    { "stage": "ASSUMPTIONS",  "title": "Precision",           "expression": null,        "value": "20 significant digits", "note": null },
    { "stage": "FORMULA",      "title": "Normalized formula",  "expression": "2 + (3 * 4)", "value": null, "note": "Implicit multiplication and operator precedence made explicit" },
    { "stage": "COMPUTATION",  "title": "Step 1: Multiply",    "expression": "3 * 4",     "value": "12",   "note": null },
    { "stage": "COMPUTATION",  "title": "Step 2: Add",         "expression": "2 + 12",    "value": "14",   "note": null },
    { "stage": "RESULT",       "title": "Result",              "expression": null,        "value": "14",   "note": null }
  ]
}
```

Each step has:

| field        | meaning                                                                 |
|--------------|--------------------------------------------------------------------------|
| `stage`      | one of `INPUT`, `ASSUMPTIONS`, `FORMULA`, `COMPUTATION`, `RESULT`        |
| `title`      | short label, e.g. `"Apply sin()"`, `"Step 3: Multiply"`                 |
| `expression` | the sub-expression or state being described, if applicable              |
| `value`      | the value at this point, as a plain (never scientific-notation) string  |
| `note`       | optional free-text explanation, e.g. why a step exists                  |

## The five stages

1. **Input** - the raw string as typed or submitted, verbatim.
2. **Assumptions** - everything the computation depends on besides the expression
   itself: angle mode, numeric precision, and the concrete value of every variable or
   constant referenced (so a trail is self-contained - you never have to go look up
   what `rate` meant at the time).
3. **Formula** - the parsed expression rendered back out with implicit multiplication
   made explicit and full, unambiguous parenthesization. This is "what the engine
   actually understood you to mean," which can matter for expressions with subtle
   precedence (`2^-2`, `-2^2`, `2pi`, ...).
4. **Computation** - one entry per real operation performed (every binary operator,
   unary negation, factorial, percent, and function call), in true evaluation order
   (innermost/leftmost first). Bare numbers and variable substitutions don't get their
   own computation step - they aren't operations. If the whole expression is a single
   literal (e.g. just `42`), this stage contains one synthetic "Direct value" step so
   the trail is never empty.
5. **Result** - the final value, formatted for display (scientific notation only for
   very large or very small magnitudes; see `NumberFormatter.display`).

## Precision model

The four basic operations, integer powers, `sqrt`, `abs`, `floor`, `ceil`, and `round`
are computed with exact or arbitrary-precision decimal arithmetic (`java.math.BigDecimal`),
honoring the requested precision up to 50 significant digits. Transcendental functions
(trig, logs, `exp`, non-integer powers/roots) are computed in IEEE-754 double precision
(roughly 15-17 significant digits) because arbitrary-precision transcendental math would
require a full Taylor-series or CORDIC implementation. This is a deliberate trade-off,
not an oversight, and the trail never implies more precision than a function actually
delivered - the ASSUMPTIONS stage states the requested precision plainly, and no attempt
is made to fake extra digits from a double-precision result.

The offline client-side engine (`frontend/js/engine/localEngine.js`, used only when the
backend is unreachable) always uses plain JavaScript `number` (double precision) and says
so explicitly in its ASSUMPTIONS stage.

## Where a trail comes from

- **Backend**: `CalculationService.buildTrail()` assembles the trail from the parsed AST
  and the `EvaluationContext` produced by `Evaluator.evaluate()`. It is serialized to the
  `trail_json` JSON column on `calculations` and `history_entries` and returned in every
  calculation response (`CalculationResponse.trail`, `CalculationCardResponse.trail`,
  `HistoryEntryResponse.trail`, `UnitConversionResponse.trail`,
  `ScenarioRunResultDto.trail`).
- **Frontend (offline fallback)**: `evaluateOffline()` in `localEngine.js` builds a
  trail object of the identical shape, so `renderTrail()` in `frontend/js/utils.js` is
  the single rendering path for both online and offline results.
