# gatling-framework-template

Starting point for a load-test project: **native Gatling 3.16 (Java DSL)** on Gradle, Java 21 — one parameterized simulation per
service, five load profiles with a verdict each, explicit actions → scenarios → simulations, DTO request bodies, real data loaded
before the run, the token renewed beside the load, the run's own counting (matching Gatling's report) and live metrics for a
dashboard, and a mock MCP service so it runs on a fresh clone with nothing installed. The Claude Code setup
that drives the work — the `qa-core` + `qa-java` plugins from
[qa-automation-toolkit](https://github.com/FruNoman/qa-automation-toolkit) — is enabled in `.claude/settings.json`; this repo keeps
only `framework-core` (this framework's specifics); the code style is the plugin's `qa-java:perf-code-style`.

## Run it

```bash
./gradlew gatlingRun -Dsimulation=McpSimulation -Dprofile=smoke -Dusers=3 -DsteadyState=30
```

The simulation starts the mock service (`mcp/mock/`), takes a token, loads 200 random records for the feeders, logs the plan and
runs; after it, a summary table per request and the empty answers. Report:
`build/reports/gatling/mcpsimulation-<timestamp>/index.html`; **the exit code is the verdict** (assertions).

## Profiles — one simulation, a question per run

| `-Dprofile` | Answers | Shape | Verdict |
|---|---|---|---|
| `smoke` | does the chain work under concurrency? (every deploy) | closed: `users` looping sessions | 0 failed + p95 per request |
| `load` | does the release hold the expected peak? (the regression gate) | open: ramp to `rate` sessions/s, plateau | failed % + p95 per request |
| `stress` | where does it break? | open: stairs `rate` + `step` × `steps` | none — the level it broke at |
| `spike` | does it survive a ×N burst and recover? | open: base → ×`spikeFactor` → base | failed % |
| `soak` | does it degrade over hours? (leaks, pools, renewals) | open: constant `rate` | failed % + p95 per request |

```bash
./gradlew gatlingRun -Dsimulation=McpSimulation -Dprofile=load   -Drate=2 -DrampUp=10 -DsteadyState=60
./gradlew gatlingRun -Dsimulation=McpSimulation -Dprofile=stress -Drate=1 -Dstep=1 -Dsteps=4 -DsteadyState=40
./gradlew gatlingRun -Dsimulation=McpSimulation -Dprofile=spike  -Drate=1 -DspikeFactor=4 -Dspike=15 -DsteadyState=20
./gradlew gatlingRun -Dsimulation=McpSimulation -Dprofile=soak   -Drate=1 -DsteadyState=3600
#  -Denv=local|dev   -Dweights=search:80,getCompany:20 (the real traffic's mix)   -DcallsPerSession=5 -DpauseMs=1000
#  thresholds: -DmaxFailedPercent=1 -DmaxCallP95Ms=3000 -DmaxInitializeP95Ms=1500 -DmaxProtocolP95Ms=500
#  stop at a breaking point: -DabortFailedPercent=30 (of the last 100 sessions; -DabortOn=requests: of the last 500 requests)
```

An open rate is **sessions per second**; one session = initialize, initialized, tools/list, `callsPerSession` tool calls, close —
so requests per second ≈ rate × (4 + `callsPerSession`). The plan in the log gives every level with its wall-clock time.

See it fail: `-Dlocal.mock-server.failure-percent=50` on `stress` — the FailureGuard stops the load
("Load stopped: 97 % of the last 100 sessions failed"); `=5` on `load` — the run ends red (exit 1), the error table says
`tool error: Simulated failure`.

## Layout

| Package | What |
|---|---|
| `core/` | shared by every service: config (`Environment`, `Profile`, `LoadProfile`, `Weights`), `Checks`, stats (`RunStats` — counted once in the protocol, `RunSummaryLog`, `EmptyAnswers`), `LiveMetrics`, simulation (`Sessions`, `Injections`, `Limits`, `FailureGuard`), `Protocols`, `Json` |
| `mcp/actions/` | a method per request, written out: name, path, DTO body, checks |
| `mcp/dto/` | tool arguments, the JSON-RPC envelope, the data source's records |
| `mcp/feeders/` | `CompanyFeeder` — real records loaded in `before()`, plain values into the session |
| `mcp/scenarios/` | smoke (every tool once), load (weighted by `-Dweights`), the MCP session around them |
| `mcp/simulations/` | `McpSimulation` — the setUp on one screen |
| `mcp/auth/`, `mcp/config/`, `mcp/protocol/` | the token store, the settings records, the MCP protocol + checks + headers |
| `mcp/mock/` | the example service on the JDK `HttpServer` — tokens that expire, sessions, an SSE answer, tool errors, a data endpoint |
| `core/grpc/`, `grpcdemo/`, `src/gatling/proto/` | gRPC without Gatling Enterprise: a unary call over the async stub as a Gatling action, an example service with its mock (below) |

A new service = a package next to `mcp/` with the same sub-packages.

**gRPC**: `./gradlew gatlingRun -Dsimulation=GrpcSimulation -Dprofile=smoke` — a mock gRPC server on port 8090 (latency
`-Dlocal.grpc-demo.mock.latency-ms=50`, errors `-Dlocal.grpc-demo.mock.failure-percent=20`); the actions are
`exec(grpc("Service/Method", this::stub, Stub::method).request(…).check(…))` over the generated **async** stub — Gatling's own gRPC
plugin is Gatling Enterprise, a blocking stub stalls the load generator. Details in `framework-core`.

**Live metrics**: `graphite.host` in `application.conf` (or `GRAPHITE_HOST`) — `LiveMetrics` sends what Gatling's removed Graphite
writer sent, every second, to a Graphite listener in front of a dashboard; unset → off.

## Start a new project

1. Create a repo from this template, open it in Claude Code (accept the `qa-core` and `qa-java` plugins it offers).
2. Ask: *"onboard this project"* / *"set up load tests for <service>"* — `qa-core:engage` asks functional / performance, the
   language, and leads: the load model (`qa-core:design-load-model`), the service's scenario (`qa-java:new-perf-simulation`), the
   first green smoke on the real environment, then the reading of every run (`qa-core:analyze-perf-run`).
3. Every place the template expects a change is marked `ONBOARD:` — the environments in `application.conf`, the actions and their
   DTOs, the data source, the token provider; `mcp/mock/` goes once the real service answers.

A company scaffold that runs Gatling from its own app (a scheduled job with Slack / dashboards / AI analysis) keeps that shell and
takes this core inside it — `qa-java:adopt-perf-runner`.

## Configuration and secrets

`src/gatling/resources/application.conf` — a block per environment (`-Denv`, default `local`), secrets only as `${?ENV_NAME}`.
Locally `gatlingRun` passes `.env/local.env` (git-ignored; the names in `.env.example`) as environment variables, as a pod gets
its secrets. `./gradlew checkstyleGatling` — no star or unused imports, lines ≤ 150.
