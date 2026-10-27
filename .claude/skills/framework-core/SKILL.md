---
name: framework-core
description: The specifics of this Gatling (Java DSL) load-test template — the layout (core/ + one package per service with actions, dto, feeders, scenarios, simulations), the example MCP service and its mock, HOCON config per environment, the five load profiles with a verdict each, the token renewed beside the run, feeders of real data loaded in before(), weights, the FailureGuard, the run's own counting and live metrics. The code style itself is qa-java:perf-code-style. Use whenever writing or changing a simulation, a scenario, an action, a DTO, a feeder, auth, config or thresholds here.
---

# Framework core — this template's specifics

The workflow (which profile answers which question, the load model, reading a run, onboarding a service, laying this core over a
company runner) comes from the `qa-core` + `qa-java` plugins enabled in `.claude/settings.json` — `qa-core:which-skill` routes.
**The code style — the layers, the one action layout, DTO bodies, `Checks`, the protocol-level counting, `RunStats.stop()` in
`after()` — follows `qa-java:perf-code-style`**; this file holds only what is particular to this framework. On a conflict this file
wins, and the conflict is a finding for `qa-core:self-analysis`.

## Layout

```
src/gatling/java/com/frunoyman/perf/
  core/                 shared by every service, no service names in it
    config/             Environment (application.conf blocks), Profile (the five), LoadProfile (-D parameters, Defaults, Thresholds),
                        Weights (a scenario's mix, -Dweights)
    checks/             Checks — status(…), failed(…): the only checks a request uses
    stats/              RunStats (counted once in the protocol), RunSummaryLog, EmptyAnswers
    metrics/            LiveMetrics — Graphite lines for a dashboard, off without graphite.host
    simulation/         Sessions (closed / open + FailureGuard), Injections (the profile + the plan), Limits (assertions + verdict),
                        FailureGuard
    grpc/               GrpcDsl → grpc(name).unary / serverStream / bidiStream(descriptor), the actions, GrpcCall, GrpcChannel
    protocols/          Protocols.restProtocol — JSON, User-Agent, the run's counting
    utils/              Json — the one Jackson mapper
  mcp/                  the example service (ONBOARD: a package per service under load)
    actions/            McpSessionActions, CompanyToolActions — a method per request
    auth/               TokenProvider, ClientCredentialsTokenProvider, AccessToken, TokenStore
    config/             ServiceSettings, DataSourceSettings, MockServerSettings — each load()s its block
    dto/                tool arguments (SearchRequest, CompanyDetailsRequest), JSON-RPC envelope, the data source's records
    feeders/            CompanyFeeder (+ DataSourceClient) — real records, load() in before()
    protocol/           McpProtocol (the REST base + SSE accept + the bearer token), McpChecks, McpHeaders
    scenarios/          McpScenarios — smoke (every tool once), load (weighted), the MCP session around them
    simulations/        McpSimulation — the setUp on one screen
    mock/               MockMcpServer — the example service; delete when the real one is wired
  grpcdemo/             the gRPC example: actions/ (CompanySearchActions), config/, feeders/, scenarios/, simulations/
                        (GrpcSimulation), mock/ (MockCompanySearchServer) — delete if no service speaks gRPC
src/gatling/proto/      the example's .proto (ONBOARD: the service's own, or its client artifact as protobuf("g:a:v"))
src/gatling/resources/  application.conf (a block per environment), logback-test.xml
```

A new service = a package next to `mcp/` with the same sub-packages; `core/` does not change.

## Configuration

- `application.conf` (HOCON): one block per environment, `-Denv=<block>` (default `local`); secrets only as `${?ENV_NAME}`; any
  value overridable with `-D<env>.<path>=…`. Each settings record has `load()` reading its block through `Environment`, which fails
  with the path when a secret is unset. Settings records hide secrets in `toString()`.
- Locally `gatlingRun` passes `.env/local.env` (git-ignored; names in `.env.example`) as environment variables — as a pod gets its
  secrets — and forwards the run parameters (`build.gradle.kts` → `runParameters`) and `-Dlocal.*` / `-Ddev.*` overrides.
- `-Dsimulation=McpSimulation` finds the class under `**/simulations`.

## Profiles, defaults and the verdict

| `-Dprofile` | Model | Shape | Verdict (`Limits`) |
|---|---|---|---|
| `smoke` | closed | `users` looping sessions, each calling every tool | 0 failed requests + p95 per request |
| `load` | open | ramp to `rate` sessions/s + plateau — the regression gate | failed % ≤ limit + p95 per request |
| `stress` | open | stairs `rate` + `step` × `steps` | none — the result is the level it broke at |
| `spike` | open | base → ×`spikeFactor` for `spike` → base | failed % ≤ limit |
| `soak` | open | constant `rate` for hours | failed % ≤ limit + p95 per request |

- A simulation states its defaults (`LoadProfile.Defaults`: rate, calls per session, pause, the call p95, protocol requests per
  session); `-D` overrides each. An open rate is **sessions per second**; requests/s ≈ rate × (4 + `callsPerSession`) for MCP.
- p95 per kind of request: `initialize` 1.5 s, `notifications/initialized` / `tools/list` 0.5 s, every other request (the tool
  calls) 3 s — `Limits.of(profile, perRequest, everyRequest)`. Never assert `close session` (`maxDuration` may cut it).
- **The mix**: `McpScenarios.WEIGHTS` (search 100, getCompany 0 — the stand-in for a tool that costs something), a run overrides
  with `-Dweights=search:80,getCompany:20`; the shares add up to 100.
- **FailureGuard**: the failed share of the last 100 sessions above `-DabortFailedPercent` (30) stops the load; `-DabortOn=requests`
  counts the last 500 requests instead — a session with any failed request is a failed session, so rare refusals in long sessions
  trip the session basis long before the request share matters. Prove it on the
  mock: `-Dlocal.mock-server.failure-percent=50`.
- The run's own numbers (`RunStats`) match Gatling's report request by request (checked on the mock 2026-10-22: smoke 170 = 170,
  load with 20 % tool errors 325 / 31 KO = 325 / 31 KO); the summary table is logged after the run, empty answers too.

## Auth

- A `TokenProvider` per kind of login (client credentials here) on the JDK `HttpClient` — no REST Assured in a perf framework.
- `TokenStore.start()` in `before()`; a daemon thread renews at ¾ of the token's life; the token is never a Gatling request.
- `Authorization` is set once, on the protocol (`McpProtocol`), read from the `TokenStore` on every request.

## Data — feeders

- Real records from a live source, a random sample per run, loaded **once in `before()`**; fewer than the minimum fails before any
  load. Feeders are `Supplier<Iterator<Map>>` (Gatling's `listFeeder` needs its records in the constructor). The session carries
  plain values (`#{companyName}`, `#{companyId}`); the action builds the request DTO from them.

## Live metrics

`LiveMetrics` sends what Gatling's Graphite writer sent before 3.12 (per-second counts and percentiles per request, `allRequests`,
users) to `graphite.host`:2003. Unset → off. To fill an existing dashboard, point it at the Graphite listener the old framework's
`gatling.conf` used (a telegraf / InfluxDB in front of the board).

## gRPC — without Gatling Enterprise

Gatling's gRPC plugin is Gatling Enterprise; here a gRPC call is a custom action over protoc's generated **method descriptor** and
the **async** `ClientCalls` (`core/grpc/`), written in Gatling's own DSL shape, recorded in Gatling's report under the RPC's name and
in the run's own counting (`RunStats.sent / answered`, the live metrics), the session going on on its own event loop. An actions
class extends `GrpcDsl` (it holds the channel):

```java
return exec(
        grpc("CompanySearch/Find")
            .unary(CompanySearchGrpc.getFindMethod())
            .send(session -> FindRequest.newBuilder()
                    .setName(session.getString("companyName"))
                    .build())
            .check(response(FindResponse::getCompaniesCount).gt(0))
);
```

- No stub: `getXxxMethod()` carries the marshallers, the name and the shape. `.unary` / `.serverStream` / `.bidiStream` must match
  the descriptor's `MethodType` — checked when the scenario is built, before the first user (`GrpcShape`).
- Checks: `response(getter).is(v)` / `.is(session -> v)` / `.isEL("#{…}")` / `.gt(v)`, `messageCount().is(n)` for a stream;
  `.check(a, b)` runs them in order. A lambda `(response, session) -> reasonOrNull` for anything else. A KO names its reason:
  the gRPC status code + description, or the check's text with both values.
- The deadline (`deadline-ms`) is set per call by `GrpcChannel.callOptions()` — never a `CallOptions` field: a deadline is absolute,
  shared options expire for the whole run at once. A per-user token: `.callOptions(o -> o.withCallCredentials(…))`.
- A stream is one action, timed as a whole; a bidi / client stream sends under flow control. Never a blocking call (it holds
  Gatling's engine thread: half the planned load applied, a healthy p95).
- One `GrpcChannel` per target: `start()` in `before()`, `stop()` in `after()` after `RunStats.stop()`; no protocol in `setUp`.
- It uses Gatling's internal Scala API (`Action`, `StatsEngine.logResponse`) — after a Gatling upgrade, compile and run the gRPC
  smoke. Proven on the mock (2026-10-27, Gatling 3.16.0, grpc-java 1.68.0): smoke 942 / 0 KO; with 20 % mock errors and a check
  made wrong on purpose our counts = the report (201 / 117 KO: `UNAVAILABLE: mock failure`, `response is '1', expected > '100'`).
  Streams proven in [gatling-grpc-connector-demo](https://github.com/FruNoman/gatling-grpc-connector-demo) against grpcbin.
- Descriptors: the protobuf Gradle plugin generates them from `src/gatling/proto` into the `gatling` source set (checkstyle skips
  them).

## Gotchas

- Set a `User-Agent` — a web firewall may answer 403 to a client without one.
- MCP answers come as JSON or as an event stream (`data: {…}`); `McpChecks` parses both, a tool's refusal is HTTP 200 with
  `isError: true` and fails `toolSucceeded`.
- A latency assertion turns red: time the same request once with `curl` before blaming the code.
- Short runs on a shared environment unless the team agreed on more; a charged tool only behind `-Dweights`.

## Run

```bash
./gradlew gatlingRun -Dsimulation=McpSimulation -Dprofile=smoke -Dusers=3 -DsteadyState=30
./gradlew gatlingRun -Dsimulation=McpSimulation -Dprofile=load -Drate=2 -DsteadyState=60 -Dweights=search:80,getCompany:20
./gradlew build
```
Report: `build/reports/gatling/<simulation>-<timestamp>/index.html`; the exit code is the verdict.
