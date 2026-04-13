# Milestone 4

## Objective

Attach real simulation behavior to the lifecycle created in milestone 3:

- create limiters from typed config
- run the simulation inside `SimulationHandle`
- produce metrics snapshots and recent logs
- keep the engine transport-agnostic

This milestone introduces real runtime behavior, but it still does not expose that behavior over SSE.


## Scope

In scope:

- limiter factory
- transport-agnostic simulation engine
- runtime state attached to `SimulationHandle`
- metrics snapshot model
- bounded recent log buffer
- engine lifecycle hooks through the existing registry/handle model
- engine and factory tests

Out of scope:

- SSE stream route
- Datastar stream anchor behavior
- live browser updates
- `PATCH /simulations/:id`
- chart integration


## Deliverables

- `src/main/kotlin/com/example/simulation/LimiterFactory.kt`
- `src/main/kotlin/com/example/simulation/SimulationEngine.kt`
- `src/main/kotlin/com/example/simulation/Metrics.kt`
- `src/main/kotlin/com/example/simulation/Events.kt`

Likely updates:

- `src/main/kotlin/com/example/simulation/SimulationHandle.kt`
- `src/main/kotlin/com/example/simulation/SimulationRegistry.kt`
- `src/test/kotlin/com/example/simulation/SimulationEngineTest.kt`
- `src/test/kotlin/com/example/simulation/LimiterFactoryTest.kt`
- `src/test/kotlin/com/example/simulation/SimulationRegistryTest.kt`

Optional if needed:

- `src/main/kotlin/com/example/model/Snapshot.kt`


## Milestone Outcome

At the end of milestone 4:

- starting a simulation creates real runtime behavior
- a handle owns current metrics and recent logs
- lifecycle and runtime are connected through a single domain model
- milestone 5 can stream existing state over SSE without redesigning the engine


## Design Constraints

- the engine must not depend on Ktor
- the engine must not depend on Datastar
- the engine must not depend on SSE framing
- `SimulationHandle` remains the lifecycle owner
- the registry remains the lookup and stop boundary


## Runtime Model

### `SimulationHandle`

By the end of this milestone, a handle should own:

- `id`
- `config`
- `status`
- `createdAt`
- `stoppedAt`
- runtime job or supervisor
- latest metrics snapshot
- bounded log buffer
- optional recent warnings/errors

### `SimulationEngine`

The engine should model:

- incoming request generation
- queue mode
- reject mode
- permit acquisition
- simulated work execution
- latency measurement
- metrics sampling
- log retention

The engine should write into handle-owned state through a narrow internal API.


## First-Pass Task List

### 1. Define the Runtime Snapshot Model

- Create `simulation/Metrics.kt`.
- Define the minimum snapshot shape:
  - queued
  - inFlight
  - admitted
  - completed
  - denied
  - droppedIncoming
  - droppedOutgoing
  - acceptRate
  - rejectRate
  - avgLatencyMs
  - p50LatencyMs
  - p95LatencyMs
- Define a log entry shape if it does not fit better in `Events.kt`.

Done when:

- the handle can expose meaningful current state without any web dependency


### 2. Define the Event Model

- Create `simulation/Events.kt`.
- Add typed event concepts such as:
  - `SimulationStarted`
  - `MetricSample`
  - `ResponseSample`
  - `SimulationWarning`
  - `SimulationStopped`
  - `SimulationFailed`

This event model is primarily for internal structure in milestone 4.
Milestone 5 can map it to SSE later.

Done when:

- the engine has a typed seam for runtime output


### 3. Implement the Limiter Factory

- Create `simulation/LimiterFactory.kt`.
- Build rate limiters from typed `SimulationConfig`.
- Support:
  - bursty
  - smooth
  - composite
- Keep configuration logic centralized here instead of inside the engine.

Done when:

- engine construction does not know how to interpret limiter config details


### 4. Implement the Simulation Engine

- Create `simulation/SimulationEngine.kt`.
- Use coroutines to model:
  - incoming request rate
  - overflow mode behavior
  - request processing
  - sample collection
- Reuse the new typed config, not milestone-2 raw parsing shapes.
- Keep the engine deterministic enough to test.
- Prefer injectable collaborators where timing or randomness matter:
  - clock or time source
  - random source if needed

Done when:

- the engine can run without the web layer
- behavior is testable in isolation


### 5. Attach Runtime to `SimulationHandle`

- Update `SimulationHandle.kt` so a running handle can own:
  - runtime job
  - current metrics
  - bounded recent log list
- Ensure stop transitions:
  - cancel or stop runtime work
  - freeze final state
  - preserve `stoppedAt`
- Do not expose coroutine internals directly to the web layer.

Done when:

- the handle is both the lifecycle unit and the runtime state container


### 6. Update `SimulationRegistry` to Start Real Runtime

- Update registry create/stop behavior:
  - `create(config)` should initialize a handle and start engine work
  - `stop(id)` should terminate that work cleanly
- Preserve the existing external contract from milestone 3.
- Avoid breaking route behavior.

Done when:

- routes still only care about create/get/stop
- handles now represent real running simulations


### 7. Decide Buffer Policies Explicitly

- Choose a bounded size for recent logs.
- Choose a policy for overflow:
  - drop oldest
  - drop newest
  - record overflow count
- Define whether inbound request generation can be backpressured or dropped under overload.
- Track any dropped behavior explicitly in counters.

Recommended approach:

- bounded recent log buffer with oldest-drop behavior
- explicit `droppedIncoming` and `droppedOutgoing` counters even if `droppedOutgoing` remains zero until milestone 5

Done when:

- overload semantics are visible instead of hidden


### 8. Add Limiter Factory Tests

- Create `LimiterFactoryTest.kt`.
- Cover:
  - bursty config creates bursty limiter
  - smooth config creates smooth limiter
  - composite config creates composite limiter
- Assert type and basic behavioral expectations rather than implementation trivia.

Done when:

- limiter selection is pinned down independently of the engine


### 9. Add Engine Tests

- Create `SimulationEngineTest.kt`.
- Cover:
  - queue mode builds backlog under overload
  - reject mode increments denials under overload
  - latency includes queue wait where relevant
  - smooth limiter/warmup changes pacing
  - composite limiter obeys stricter tier
  - stopping the handle stops further work
  - bounded log retention works
  - dropped counters are explicit

Done when:

- runtime semantics are locked in before streaming is introduced


### 10. Extend Registry Tests as Needed

- Update `SimulationRegistryTest.kt` if runtime behavior affects:
  - create semantics
  - stop semantics
  - handle visibility
- Keep lifecycle assertions and runtime assertions separate where possible.

Done when:

- registry tests still describe lifecycle ownership clearly


## Suggested Internal Responsibilities

### `LimiterFactory`

Owns:

- limiter construction
- config-to-limiter interpretation

Does not own:

- runtime job management
- metrics
- transport

### `SimulationEngine`

Owns:

- concurrency model
- request scheduling
- processing logic
- metric updates
- log generation

Does not own:

- route behavior
- HTTP parsing
- Datastar responses

### `SimulationHandle`

Owns:

- lifecycle state
- runtime attachment
- latest readable state

Does not own:

- route parsing
- SSE formatting


## Test Checklist

Minimum green test set for this milestone:

- `LimiterFactoryTest`
  - bursty
  - smooth
  - composite
- `SimulationEngineTest`
  - queue overload
  - reject overload
  - latency semantics
  - warmup behavior
  - composite behavior
  - stop semantics
  - bounded logs
- `SimulationRegistryTest`
  - existing lifecycle tests remain green
- `RoutesTest`
  - start and stop routes still pass with real runtime behind them


## Recommended Implementation Order

1. Create `Metrics.kt`
2. Create `Events.kt`
3. Create `LimiterFactory.kt`
4. Create `SimulationEngine.kt`
5. Update `SimulationHandle.kt`
6. Update `SimulationRegistry.kt`
7. Add `LimiterFactoryTest.kt`
8. Add `SimulationEngineTest.kt`
9. Re-run route and registry tests
10. Run the full test suite


## What Not To Do Yet

- do not add SSE routes
- do not stream patches to the browser
- do not add `PATCH /simulations/:id`
- do not wire live chart updates
- do not couple engine internals to web fragment formats


## Acceptance Criteria

Milestone 4 is complete when:

- starting a handle launches real simulation behavior
- stopping a handle stops real simulation behavior
- handles expose current metrics and bounded recent logs
- limiter creation is centralized in a factory
- runtime behavior is covered by dedicated engine tests
- milestone 5 can stream handle state without redesigning lifecycle or runtime ownership


## Handoff to Milestone 5

Milestone 5 should be able to build directly on this by adding:

- `GET /simulations/:id/stream`
- subscriber attachment
- Datastar signal patches for metrics/state
- Datastar element patches for logs and warnings
- heartbeat handling

If milestone 5 needs to redesign runtime ownership or metrics shape, milestone 4 was incomplete.

