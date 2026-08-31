# CalcForge

A local-first, all-in-one calculator: unified expression input, a transparent
Input → Assumptions → Formula → Computation → Result trail for every calculation,
first-class variables and reusable formulas, a multi-card workspace canvas, offline
unit conversion, everyday finance calculators, 2D graphing, and searchable history -
all fully functional with **no internet connection**, with accounts/sync/AI/live
rates layered on top as strictly optional extras.

```
calcforge/
├── backend/    Spring Boot 3 (Java 17) API + MySQL schema (Flyway)
├── frontend/   HTML5 + Bootstrap 5 + vanilla JS (ES modules), zero build step
└── docs/       Calculation trail format, API reference
```

## Why it's built this way

- **Local-first, not "offline as an afterthought."** Every core feature lives under
  `/api/v1/local/**`, requires no authentication, and depends on nothing but this
  server and its MySQL database - no internet, no external API. Accounts, sync, AI
  assistance, live currency, and workspace sharing live entirely under
  `/api/v1/cloud/**` and are off by default; the app is 100% usable with all of them
  permanently disabled. See `SecurityConfig.java` and `CloudFeatureProperties.java`.
- **A real expression engine, not a wrapper around `eval`.** `backend/.../engine/` is a
  hand-written lexer, recursive-descent parser, and evaluator with correct operator
  precedence, implicit multiplication (`2pi`, `3(4+5)`), postfix `%` and `!`,
  arbitrary-precision decimal arithmetic for exact operations, and a documented,
  honest precision boundary for transcendental functions. See
  `docs/CALCULATION_TRAIL.md` for the full precision model.
- **Transparency is structural, not cosmetic.** Every calculation - typed at the
  keypad, saved as a workspace card, evaluated from a formula, converted between
  units - returns the same five-stage trail shape, enforced by the return type of
  `CalculationService.buildTrail()`. There's no code path that returns a number
  without also returning how it was reached.

## Tech stack

- **Frontend**: HTML5, CSS3, Bootstrap 5 (vendored locally in
  `frontend/vendor/bootstrap` - no CDN dependency, so the UI itself never needs
  network access to render), vanilla JavaScript via ES modules. No build step.
- **Backend**: Java 17, Spring Boot 3.3, Spring Data JPA, Spring Security (JWT, cloud
  endpoints only), Flyway, Lombok, Jackson, Bean Validation, Maven.
- **Database**: MySQL 8.x.

## Prerequisites

- JDK 17+
- Maven 3.9+
- MySQL 8.x, running and reachable
- Any static file server for the frontend (Python, Node, `serve`, VS Code Live
  Server, etc.) - **don't** open `index.html` directly via `file://`; browsers block
  ES module imports from `file://` URLs regardless of CORS settings.

## Setup

### 1. Database

Create a database and user (or let the app create the database automatically - see
below):

```sql
CREATE DATABASE calcforge CHARACTER SET utf8mb4;
CREATE USER 'calcforge'@'localhost' IDENTIFIED BY 'calcforge';
GRANT ALL PRIVILEGES ON calcforge.* TO 'calcforge'@'localhost';
```

The default local profile's JDBC URL includes `createDatabaseIfNotExist=true`, so if
your MySQL user has the right privileges you can skip `CREATE DATABASE` and just run
the app - Flyway will create every table and seed the offline unit database plus a
demo workspace on first boot (see `backend/src/main/resources/db/migration`).

Default credentials (override with env vars - see `application-local.yml`):

| Env var | Default |
|---|---|
| `DB_HOST` | `localhost` |
| `DB_PORT` | `3306` |
| `DB_NAME` | `calcforge` |
| `DB_USERNAME` | `calcforge` |
| `DB_PASSWORD` | `calcforge` |

### 2. Backend

```bash
cd backend
mvn spring-boot:run
```

This runs with `spring.profiles.active=local` by default (see `application.yml`),
which means every optional cloud feature starts **disabled**. The server listens on
`http://localhost:8080`.

To build a runnable jar instead:

```bash
mvn clean package
java -jar target/calcforge-backend.jar
```

Run the test suite (the calculation engine's correctness tests, plus finance and unit
conversion tests - all pure unit tests, no database required):

```bash
mvn test
```

### 3. Frontend

From `frontend/`, serve the directory with any static file server, for example:

```bash
cd frontend
python3 -m http.server 5500
# or: npx serve -l 5500
```

Then open `http://localhost:5500`. On first load it checks
`http://localhost:8080/actuator/health`; if the backend isn't reachable it falls back
to offline mode (see below) rather than failing.

If your backend runs somewhere other than `http://localhost:8080`, change it under
**Settings → Backend connection** in the app - it's stored in `localStorage`, no
rebuild needed.

## Running fully offline vs. with cloud features

**Fully offline** (the default, and the required MVP experience): run the backend
locally on the `local` profile against a local MySQL instance with no internet route
at all. The calculator, unit conversion, finance tools, variables, formulas,
workspace canvas, scenarios, graphing, and history all work identically with the
network cable pulled out, because none of them ever leave `/api/v1/local/**`.

**With cloud features**: run with `spring.profiles.active=cloud` (or set
`SPRING_PROFILES_ACTIVE=cloud`) and configure the pieces you want:

```bash
export SPRING_PROFILES_ACTIVE=cloud
export CALCFORGE_JWT_SECRET="a long random string, at least 32 bytes"
export DB_PASSWORD="..."
export AI_ASSIST_ENABLED=true
export ANTHROPIC_API_KEY="sk-ant-..."          # only needed for AI assist
export SHARED_WORKSPACES_ENABLED=true
```

Live currency rates additionally require wiring a real FX provider into
`CurrencyRateService.refreshRates()` (deliberately left as an integration point, not
implemented, since it requires a deployment-specific API key and external network
access - see the comment in that file). Until that's done, currency conversion still
works fully offline via the seeded static rates in the `units` table; the frontend
and `GET /api/v1/cloud/currency/rates` both make clear when rates are static versus
live.

### One important, deliberate frontend fallback

If the backend becomes unreachable while the app is open, the frontend automatically
switches the primary calculator and grapher to a pure client-side JavaScript engine
(`frontend/js/engine/localEngine.js`) - the same grammar as the backend engine, but
using standard double-precision floating point and a smaller function set, clearly
labeled as such in the trail. Workspace/variable/formula/history CRUD still require
the backend (they need MySQL), and the UI says so plainly rather than pretending to
work. This is the "clear offline-capable client-side evaluation for basic operations"
called for in the product spec, without overstating what a browser-only fallback can
reasonably do.

## Seed / demo data

A fresh database gets one local workspace ("Getting Started") with a few sample
variables (`rate`, `principal`, `months`, `radius`, `tax_rate`), three formulas
(`circle_area`, `circle_circumference`, `monthly_loan_payment`), three canvas cards,
two what-if scenarios, and a handful of history entries - see
`backend/src/main/resources/db/migration/V3__seed_demo_data.sql`. All of it is
ordinary data the UI can edit or delete; nothing in the application code treats it
specially.

## Documentation

- `docs/CALCULATION_TRAIL.md` - the exact shape and meaning of the trail returned by
  every calculation endpoint, and the precision model behind it.
- `docs/API_CONTRACTS.md` - endpoint reference.

## Known limitations / honest scope notes

- **Sync is last-write-wins, not a CRDT.** `SyncService` is a deliberately simple
  synchronization model suited to "one account, a couple of devices," not
  concurrent multi-writer editing. Conflicts are detected and reported, not merged
  field-by-field.
- **Transcendental precision is double, not arbitrary.** See
  `docs/CALCULATION_TRAIL.md` - basic arithmetic and integer powers are exact to the
  requested precision (up to 50 significant digits); `sin`, `ln`, `exp`, and friends
  are IEEE-754 double precision, which the trail states explicitly rather than
  implying more precision than was actually computed.
- **AI assistance and live currency need external configuration.** Both are wired up
  end-to-end (real HTTP calls, real config flags) but require a deployment to supply
  its own API key/provider; neither is faked, and both fail closed with a clear
  "not configured" response rather than an error when unset.
- **The offline browser engine is intentionally smaller** than the backend engine
  (common functions only, double precision, no persistence beyond a local queue) -
  it exists so the calculator never fully breaks without a server, not to duplicate
  the backend.
