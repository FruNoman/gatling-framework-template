---
name: framework-core
description: The rules of this Gatling (Java DSL) load-test framework — layers dto → actions → scenario → simulations, one parameterized simulation per service, load profiles (smoke / load / stress / spike / soak) with a verdict each, HOCON config per environment, the token on the protocol and renewed beside the run, feeders of real data loaded in before(), checks that name the reason, the FailureGuard, the mock service. Use whenever writing or changing a simulation, a scenario, an action, a DTO, a feeder, auth, config or thresholds here.
---

# Framework core — the rules

The workflow (which profile answers which question, the load model, reading a run, onboarding a service, laying this core over a
company runner) comes from the `qa-core` + `qa-java` plugins enabled in `.claude/settings.json` — `qa-core:which-skill` routes. This
file holds the framework's **rules**; on a conflict it wins, and the conflict is a finding for `qa-core:self-analysis`.

## Layout

```
src/gatling/java/com/frunoyman/perf/
  config/       Environment (application.conf → records), ServiceSettings, DataSourceSettings, MockServerSettings,
                Profile (the five profiles), LoadProfile (-D load parameters + Thresholds), Tool (switch key, tool name, request name)
  protocols/    Protocols — base URL = host, JSON | SSE, User-Agent, Authorization from the TokenStore
  auth/         TokenProvider, ClientCredentialsTokenProvider (JDK HttpClient), AccessToken, TokenStore (renews at ¾ of the life)
  clients/      DataSourceClient — the feeders' source, JDK HttpClient
  dto/          jsonrpc/ (JsonRpcRequest factories, JsonRpcResponse), tools/ (one record per tool's arguments), data/ (source records)
  feeders/      CompanyFeeder — load() in before(), Supplier feeders of request DTOs
  mcp/          McpChecks (the reason in the error table), McpHeaders (session headers)
  actions/      McpSessionActions, tools/<Tool>Actions — one class per resource, one method per request, path in the constructor
  scenario/     McpScenarios (composition only), FailureGuard
  simulations/  McpSimulation (setUp, before/after, assertions per profile), Injections (the profile's injection + the plan)
  mock/         MockMcpServer — the example service; delete when the real one is wired
src/gatling/resources/  application.conf (a block per environment), logback-test.xml
```

## Layers — each knows only the one below

- **dto/** — Lombok records `@Builder(toBuilder = true)`, `@JsonProperty` for snake_case, `@JsonInclude(NON_NULL)` on requests,
  `@JsonIgnoreProperties(ignoreUnknown = true)` on answers (an answer grows; a load test must not break on it). No JSON by hand, no
  `StringBody("{\"jsonrpc\"…")`.
- **actions/** — one class per resource or tool; the endpoint's path comes **in the constructor** (`new SearchActions(mcpPath)`) and
  every request names it: `.post(mcpPath)`. Never `.post("")` on a base URL that is the full endpoint, never a path or a request name
  from the session (`http("#{toolLabel}")`), no `Endpoints` class of constants. The body is the DTO the feeder put into the session,
  serialized by `utils/Json`. Request names are fixed strings (`Tool.requestName()`) — the assertions name the same ones.
- **scenario/** — composition only: `exec`, `repeat`, `exitBlockOnFail`, `feed`, `uniformRandomSwitch`. No HTTP, no JSON.
- **simulations/** — the load model and the verdict: `setUp(Injections.of(...)).protocols(...).maxDuration(...).assertions(...)`.
  **One simulation per service, parameterized by `-D`**, not a subclass per variant (entry point, tenant, product = a `-D` switch).

## Configuration

- `application.conf` (HOCON — Typesafe Config is on Gatling's classpath): one block per environment, `-Denv=<block>` (default
  `local`); secrets only as `${?ENV_NAME}`; any value overridable with `-D<env>.<path>=…`. `Environment` reads a block into typed
  records and fails with the path when a secret is unset. No hand-written `.env` parser, no static map of string keys.
- Locally `gatlingRun` passes `.env/local.env` (git-ignored; names in `.env.example`) as environment variables — as a pod gets its
  secrets. Never print a secret; settings records hide them in `toString()`.
- Load parameters and thresholds: `LoadProfile.fromSystemProperties()` (`-Dprofile -Drate -Dusers -DsteadyState …`). A runner
  (CI, a scheduled job, a company shell) only sets properties and reads the exit code.

## Profiles and the verdict

| `-Dprofile` | Model | Shape | Verdict (assertions) |
|---|---|---|---|
| `smoke` | closed | `users` looping sessions, ramp + plateau | 0 failed requests + p95 per request |
| `load` | open | ramp to `rate` sessions/s + plateau — the regression gate | failed % ≤ limit + p95 per request |
| `stress` | open | `incrementUsersPerSec(step).times(steps)` stairs from `rate` | none — the result is the level it broke at |
| `spike` | open | base → ×`spikeFactor` for `spike` → base | failed % ≤ limit (latency rises in the burst by design) |
| `soak` | open | constant `rate` for hours | failed % ≤ limit + p95 per request |

- An open rate is **sessions per second**; requests per second ≈ rate × `requestsPerSession()` (4 + `callsPerSession`). The plan
  logged at the start gives each level / phase with its wall-clock time — read the report and the APM against it.
- **The mix**: `-Dtools=search:8,company_details:1` — each call picks a tool by its weight (`randomSwitch` with shares that add
  up to exactly 100 %; a gap would be a call never made). Weights come from the real traffic (logs, APM); an expensive tool is
  never in the default.
- Assert **per request**: a tool call (hundreds of ms) and a protocol request (tens of ms) in one global p95 hide each other.
  `initialize` has its own threshold (session creation, cold TLS). Never assert a teardown request (`close session`): `maxDuration`
  may cut it, and an assertion on a request that never ran fails the run ("Could not find stats matching assertion path").
- Open profiles end with a 30 s drain in `maxDuration`, so the last sessions finish.
- **FailureGuard**: the failed share of the last 100 sessions above `-DabortFailedPercent` (30) stops the load generator
  (`stopLoadGeneratorIf`) and logs one WARN with the reason — a stress run stops at its breaking point instead of hammering a
  shared environment. Prove it on the mock: `-Dlocal.mock-server.failure-percent=50`.

## Auth

- A `TokenProvider` per kind of login (client credentials here; a user's SSO / PKCE login is another provider) on the **JDK
  `HttpClient` directly** — no REST Assured, no shared HTTP wrapper in a perf framework.
- `TokenStore.start()` in `before()`; a daemon thread renews at ¾ of the token's life (a refresh token when the provider has one).
  The token is never a Gatling request — not in the stats, no virtual user waits for a login.
- `Authorization` is set **once, on the protocol**: `.header("Authorization", session -> "Bearer " + tokenStore.token())`. Not per
  request, not `#{token}` from the session.

## Data — feeders

- Real records from a live source (a search index, a DB, the service's list endpoint), a random sample per run, loaded **once in
  `before()`**; fewer than the minimum fails before any load. A handful of fixed values measures the service's cache.
- Data loaded in `before()` → `feed(Supplier<Iterator<Map>>)` (called when Gatling builds the run, after `before()`). Gatling's
  `listFeeder(list)` needs its records when the scenario is built (the constructor) — with `before()` data it fails "Feeder must
  not be empty". Static data ready in the constructor → `listFeeder(...).random()` / `jsonFile(...)`. No CSV.
- The session carries the request DTO (`Map.of(SEARCH_REQUEST, searchRequest)`), not loose fields.

## Checks

- MCP answers come as JSON or as an event stream (`data: {…}`); `McpChecks` parses both.
- A tool's refusal is **HTTP 200 with `isError: true`** — `toolSucceeded()` fails it, or a wrong argument passes as OK.
- A failed check names the reason through `bodyString().validate(name, (body, session) -> …)` throwing with the reason —
  `transform(...).is(...)` on a body reports only "failed" (Gatling hides a body check's actual value).
- An MCP session is `exitBlockOnFail { initialize … calls }` and the old session id is removed before `initialize` — otherwise one
  failed initialize turns every later request of that user into an error on the missing id.

## Gotchas

- Set a `User-Agent` — a web firewall may answer 403 to a client without one.
- An identity provider may answer a redirect `Location` with raw spaces (`scope=openid profile`) — `URI.create` fails; encode
  spaces before parsing.
- A latency assertion turns red: time the same request once with `curl` before blaming the code — a shared environment's latency
  moves by the hour.
- Short runs on a shared environment (≈ 20 users / a few sessions per second, under 3 min) unless the team agreed on more; charged
  tools only behind `-Dtools`, never a default.

## Run

```bash
./gradlew gatlingRun --simulation com.frunoyman.perf.simulations.McpSimulation -Dprofile=smoke -Dusers=5 -DsteadyState=40
./gradlew gatlingRun --simulation com.frunoyman.perf.simulations.McpSimulation -Dprofile=load -Drate=2 -DsteadyState=60
./gradlew checkstyleGatling
```
Report: `build/reports/gatling/<simulation>-<timestamp>/index.html`; the exit code is the verdict.
