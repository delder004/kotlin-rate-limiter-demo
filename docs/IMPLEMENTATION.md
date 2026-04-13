# Implementation Plan

## Purpose

This document turns [DESIGN.md](/Users/delos/repos/kotlin-rate-limiter-demo/DESIGN.md) into a concrete build plan.

The target is a Datastar-first, SSE-based dashboard built from scratch, not a migration of the current WebSocket demo.


## Revision Note

The implementation diverged from the earlier UI plan in one important way:
the page shipped as a step-based wizard with persistent run panels, not as distinct `Limiter` and `Traffic` sections.

This document now reflects the implementation that actually exists:

- the Datastar and SSE architecture shipped
- the primary page flow is wizard-based
- scenarios and presets are deferred, not part of the current page contract
- advanced traffic fields remain in the config model, but are not currently exposed in the UI


## Current State Snapshot

Working now:

- Datastar page shell with a step-based wizard
- plain HTTP start, update, and stop actions (Stop evicts the simulation; the next Start always creates a fresh one)
- SSE metrics and log streaming
- transport-agnostic simulation engine and registry
- chart island and automated coverage

Still deferred or unfinished:

- scenario and preset buttons are not mounted in the page
- advanced traffic controls are not exposed in the wizard
- legacy WebSocket and demo files are still present in the repo
- README and CI basics are still missing


## Delivery Strategy

Build the app in thin vertical slices:

1. page shell
2. validation and domain model
3. simulation lifecycle
4. SSE streaming
5. UI refinement
6. cleanup and hardening

Each phase should leave the app runnable and testable.


## Proposed File Layout

### Application Bootstrap

- `src/main/kotlin/com/example/Application.kt`

Responsibilities:

- start Ktor
- install plugins
- register routes

### Web Layer

- `src/main/kotlin/com/example/web/Routes.kt`
- `src/main/kotlin/com/example/web/Page.kt`
- `src/main/kotlin/com/example/web/Fragments.kt`
- `src/main/kotlin/com/example/web/DatastarResponses.kt`

Responsibilities:

- route definitions
- page shell rendering
- reusable HTML fragments
- helpers for Datastar signal and element patch responses

### Simulation Layer

- `src/main/kotlin/com/example/simulation/SimulationRegistry.kt`
- `src/main/kotlin/com/example/simulation/SimulationHandle.kt`
- `src/main/kotlin/com/example/simulation/SimulationEngine.kt`
- `src/main/kotlin/com/example/simulation/SimulationConfig.kt`
- `src/main/kotlin/com/example/simulation/Validation.kt`
- `src/main/kotlin/com/example/simulation/LimiterFactory.kt`
- `src/main/kotlin/com/example/simulation/Events.kt`
- `src/main/kotlin/com/example/simulation/Metrics.kt`

Responsibilities:

- validated simulation config
- lifecycle management
- event emission
- metrics and log buffering
- limiter creation

### Tests

- `src/test/kotlin/com/example/web/PageTest.kt`
- `src/test/kotlin/com/example/web/RoutesTest.kt`
- `src/test/kotlin/com/example/web/SseStreamTest.kt`
- `src/test/kotlin/com/example/simulation/ValidationTest.kt`
- `src/test/kotlin/com/example/simulation/LimiterFactoryTest.kt`
- `src/test/kotlin/com/example/simulation/SimulationEngineTest.kt`
- `src/test/kotlin/com/example/simulation/SimulationRegistryTest.kt`


## Phase 0: Repo Cleanup

### Tasks

- Add `.gitignore` for:
  - `build/`
  - `.gradle/`
  - `.kotlin/`
  - local IDE files as needed
- Remove committed generated artifacts from git
- Decide whether to keep the current demo files during the rewrite or move them under a `legacy/` package

### Output

- clean working tree rules
- new architecture can be added without build noise

### Done When

- generated artifacts are no longer tracked
- new files can be added without unrelated diff churn


## Phase 1: Minimal App Shell

### Goal

Get a clean Ktor app serving a static Datastar page shell.

### Tasks

- Create `Application.kt`
- Move the app entry point away from the current mixed-purpose server file
- Install required Ktor plugins:
  - routing
  - content negotiation if needed
  - SSE
- Create `web/Page.kt`
- Create `web/Routes.kt`
- Add `GET /`
- Render a static page with:
  - title
  - controls layout
  - empty stats
  - empty log area
  - placeholder chart region
  - Datastar script include

### UI Scope

The shell should include all expected controls, but they do not need to work yet.

### Tests

- `PageTest`
  - page contains title
  - page contains controls
  - page contains expected root sections
- `RoutesTest`
  - `GET /` returns `200`
  - content type is HTML

### Done When

- app starts
- `GET /` renders a stable page shell
- tests for the page shell pass


## Phase 2: Domain Model and Validation

### Goal

Define the simulation config and reject bad input before any engine work exists.

### Tasks

- Create `simulation/SimulationConfig.kt`
- Define typed enums or sealed types for:
  - limiter type
  - overflow mode
  - API target
- Create `simulation/Validation.kt`
- Add validation rules for:
  - permits
  - primary period
  - composite child periods
  - warmup
  - request rate
  - service time
  - jitter
  - failure rate
  - worker concurrency
- Create request/response DTOs if needed in `model/`
- Create `web/DatastarResponses.kt` helpers for:
  - signal patch response
  - form error patch response

### Tests

- `ValidationTest`
  - accepts valid bursty config
  - accepts valid smooth config
  - accepts valid composite config
  - rejects zero permits
  - rejects invalid periods
  - rejects missing composite child config
  - rejects out-of-range failure rate
  - rejects invalid enum values

### Done When

- config is typed
- invalid requests can produce structured form errors
- no route needs to parse raw strings directly into runtime behavior


## Phase 3: Start and Stop Lifecycle

### Goal

Introduce an in-memory simulation registry before live streaming.

### Tasks

- Create `simulation/SimulationRegistry.kt`
- Create `simulation/SimulationHandle.kt`
- Add methods:
  - `create(config)`
  - `get(id)`
  - `update(id, config)`
  - `stop(id)`
- Define handle state:
  - ID
  - status
  - latest config
  - latest metrics snapshot
  - bounded log buffer
- Implement basic lifecycle without full engine behavior
- Add routes:
  - `POST /simulations`
  - `DELETE /simulations/:id`
- Start route should:
  - validate config
  - allocate a new simulation ID
  - mark the simulation as running
  - return Datastar patches for `sim.*` and any stream anchor fragment
- Stop route should:
  - stop the handle
  - return idle state patches

### UI Scope

At this stage, the page can start and stop a fake or placeholder simulation.

### Tests

- `SimulationRegistryTest`
  - create returns unique IDs
  - stop transitions handle state
  - stopped handles are not treated as running
- `RoutesTest`
  - `POST /simulations` with valid input returns success
  - `POST /simulations` with invalid input returns form errors
  - `DELETE /simulations/:id` stops an existing simulation
  - delete of missing ID returns `404` or clear structured error

### Done When

- the UI can move between idle and running
- the backend owns simulation lifecycle independent of transport


## Phase 4: Limiter Factory and Core Simulation Engine

### Goal

Build the actual rate-limiter simulation as a transport-agnostic domain service.

### Tasks

- Create `simulation/LimiterFactory.kt`
- Move or adapt existing limiter creation logic
- Create `simulation/Events.kt`
- Define event types:
  - `SimulationStarted`
  - `MetricSample`
  - `ResponseSample`
  - `SimulationWarning`
  - `SimulationStopped`
  - `SimulationFailed`
- Create `simulation/Metrics.kt`
- Create `simulation/SimulationEngine.kt`
- Implement:
  - incoming request generation
  - queue mode
  - reject mode
  - permit acquisition
  - simulated upstream work
  - metric sampling
  - recent log retention
- Add explicit counters:
  - `queued`
  - `admitted`
  - `completed`
  - `denied`
  - `inFlight`
  - `droppedIncoming`
  - `droppedOutgoing`

### Salvage From Existing Demo

Good candidates to reuse conceptually:

- limiter creation logic
- queue mode vs reject mode behavior
- simulated work with service time, jitter, and failure rate
- metric shape ideas

Do not preserve transport-specific assumptions from the old implementation.

### Tests

- `LimiterFactoryTest`
  - bursty limiter config builds correctly
  - smooth limiter config builds correctly
  - composite limiter config builds correctly
- `SimulationEngineTest`
  - queue mode accumulates backlog under overload
  - reject mode increments denials under overload
  - latency includes queue wait where applicable
  - warmup behavior changes pacing
  - composite limiter enforces stricter tier
  - dropped inbound/outbound counters are tracked explicitly

### Done When

- simulation behavior can be exercised with unit tests only
- the engine does not depend on Ktor or Datastar


## Phase 5: SSE Stream

### Goal

Expose live simulation updates over SSE in a Datastar-friendly format.

### Tasks

- Add `GET /simulations/:id/stream`
- Define subscriber attachment on `SimulationHandle`
- Emit:
  - Datastar signal patches for metrics and state
  - Datastar element patches for log rows and status banners
- Add heartbeat messages
- Ensure disconnecting a subscriber does not kill the simulation
- Ensure slow subscribers do not block the engine
- Count dropped outbound updates explicitly

### Datastar Integration Pattern

- add a dedicated hidden stream anchor fragment
- render the anchor only when `sim.running = true`
- the stream anchor should own the SSE request and nothing else

### Tests

- `SseStreamTest`
  - stream attaches to active simulation
  - stream emits metric patches
  - stream emits log patches
  - missing simulation ID returns `404`
  - disconnect does not terminate simulation unexpectedly

### Done When

- live metrics appear over SSE
- stream reconnect is possible with the same simulation ID
- event loss is measurable, not silent


## Phase 6: Datastar Actions and Wizard Controls

### Goal

Make the page interactive using Datastar as the only UI state mechanism outside the chart island, while using a guided wizard as the primary control model.

### Tasks

- Bind visible wizard controls to `config.*` signals
- Implement Start button action
- Implement Stop button action
- Implement debounced update behavior for visible numeric controls while running
- Add status and error fragments
- Add form error rendering tied to `errors.form`
- Add stream error rendering tied to `errors.stream`
- Keep advanced traffic tuning fields in the config and validation model even if they are not exposed in the current page
- Defer scenarios and presets until they fit the wizard model cleanly

### Tests

- `PageTest`
  - required Datastar attributes are present
  - expected wizard steps and signal-bound controls exist
  - stream anchor placeholder region exists
- `RoutesTest`
  - update route applies valid changes
  - update route returns form errors for invalid changes

### Done When

- no custom browser transport client exists
- normal wizard and runtime behavior is driven by Datastar actions and signal patches
- the currently visible controls can update a running simulation without route-specific browser logic

### Scenario and Preset Direction

The repo already contains an unfinished `renderPresetsPanel()` helper.
If scenarios are revived, they should be treated as quick-start recipes that work with the wizard instead of competing with it.

Recommended direction:

- mount scenario buttons above step 1 or in the page header
- let a scenario patch both limiter and traffic signals
- also advance `ui.step` so the page reflects the loaded recipe
- keep `Start` explicit in idle state
- when already running, reuse the existing `PATCH /simulations/:id` flow

Not planned:

- separate limiter-only and traffic-only preset rows
- exposing advanced traffic controls solely to support scenario recipes


## Phase 7: Chart Island

### Goal

Add a minimal time-series visualization without making the chart the center of the architecture.

### Tasks

- Choose one:
  - keep a small Chart.js island
  - render server-side SVG
- If using client-side charting:
  - keep its API narrow
  - feed it only the data it needs
  - avoid building a second client app
- Limit retained points
- Ensure chart updates degrade gracefully under high event rate

### Tests

Testing can remain lightweight here:

- shell contains chart mount point
- no server route depends on chart implementation

### Done When

- the chart works
- chart code is isolated
- the page remains understandable without reading chart internals


## Phase 8: Hardening and Cleanup

### Goal

Tighten correctness, maintainability, and repo quality.

### Tasks

- Remove old WebSocket-only code paths if they are still present
- Remove obsolete demo transport code
- Add `.gitignore` if not already done
- Add a README with:
  - purpose
  - how to run
  - architecture summary
- Add CI basics:
  - test run
  - optional lint or formatting checks
- Review naming and package boundaries
- Ensure shared HTTP clients and coroutine scopes are lifecycle-safe

### Tests

- full `./gradlew test`
- any additional smoke test for startup if useful

### Done When

- the repo has a single clear architecture
- build artifacts are not tracked
- documentation matches implementation


## Route Checklist

These are the routes currently expected in the shipped app:

- `GET /`
- `POST /simulations`
- `PATCH /simulations/:id`
- `DELETE /simulations/:id`
- `GET /simulations/:id/stream`

Optional later:

- `GET /health`
- `GET /simulations/:id`


## Fragment Checklist

These fragments are worth keeping separate:

- page shell
- controls
- status banner
- stats grid
- log list
- single log row
- validation errors
- stream anchor


## Signal Checklist

Minimum signal set for the current implementation:

- `sim.id`
- `sim.running`
- `sim.status`
- `ui.step`
- `config.limiterType`
- `config.permits`
- `config.perSeconds`
- `config.warmupSeconds`
- `config.compositeCount`
- `config.child{n}Type`
- `config.child{n}Permits`
- `config.child{n}PerSeconds`
- `config.child{n}WarmupSeconds`
- `config.requestsPerSecond`
- `config.overflowMode`
- `config.apiTarget`
- `config.serviceTimeMs`
- `config.jitterMs`
- `config.failureRate`
- `config.workerConcurrency`
- `stats.queued`
- `stats.inFlight`
- `stats.completed`
- `stats.denied`
- `stats.droppedIncoming`
- `stats.droppedOutgoing`
- `stats.acceptRate`
- `stats.rejectRate`
- `stats.avgLatencyMs`
- `stats.p50LatencyMs`
- `stats.p95LatencyMs`
- `errors.form`
- `errors.stream`

Not every config signal above is exposed in the page today.
The advanced traffic fields remain part of the model even though the wizard currently exposes only `requestsPerSecond`.


## What To Reuse From The Existing Code

Potentially reusable with adaptation:

- limiter config ideas
- metrics fields
- simulated work function shape
- rate-limiter test scenarios

Probably not worth reusing directly:

- WebSocket route and control message protocol
- inline dashboard JS
- current server file structure
- connection-owned simulation lifecycle


## Suggested Build Order

If implementing sequentially, use this exact order:

1. `.gitignore` and repo cleanup
2. `Application.kt`
3. `web/Page.kt`
4. `web/Routes.kt`
5. `simulation/SimulationConfig.kt`
6. `simulation/Validation.kt`
7. route tests for `GET /` and validation behavior
8. `SimulationRegistry.kt`
9. start and stop routes
10. `LimiterFactory.kt`
11. `SimulationEngine.kt`
12. simulation tests
13. SSE route
14. Datastar action wiring
15. chart island
16. README and CI cleanup


## Current Roadmap

### Planned

- remove legacy WebSocket and demo-only code so the repo has one clear transport story
- add a README with run instructions and architecture notes
- add basic CI for `./gradlew test`
- keep the docs aligned with the shipped wizard

### Potential, Not Planned

- expose `apiTarget`, `serviceTimeMs`, `jitterMs`, `failureRate`, and `workerConcurrency` in the page
- add scenario or preset quick-start recipes above the wizard
- revisit a section-based information architecture if the wizard stops carrying its weight


## Definition of Done

The implementation is complete when:

- the app is driven by Datastar, not a custom JS state machine
- simulation control uses plain HTTP actions
- live updates use SSE
- simulation logic is transport-agnostic
- validation errors are explicit and user-visible
- overload effects are measured explicitly
- routes and simulation behavior are covered by tests
- generated build artifacts are not tracked in git
