# Milestone 3

## Objective

Add simulation lifecycle management to the new app:

- create and track in-memory simulations
- start and stop simulations through HTTP routes
- move the page cleanly between idle and running states
- keep the implementation engine-free for now

This milestone introduces lifecycle and route behavior, but it still does not include the real simulation engine or SSE streaming.


## Scope

In scope:

- in-memory simulation registry
- simulation handle state
- start route
- stop route
- route-level use of milestone 2 validation
- Datastar responses for idle/running state transitions
- route tests and registry tests

Out of scope:

- real request generation
- real limiter execution
- metrics streaming
- SSE
- chart updates
- config updates on running simulations


## Deliverables

- `src/main/kotlin/com/example/simulation/SimulationRegistry.kt`
- `src/main/kotlin/com/example/simulation/SimulationHandle.kt`
- `src/test/kotlin/com/example/simulation/SimulationRegistryTest.kt`

Likely updates:

- `src/main/kotlin/com/example/web/Routes.kt`
- `src/main/kotlin/com/example/web/Fragments.kt`
- `src/main/kotlin/com/example/web/DatastarResponses.kt`
- `src/test/kotlin/com/example/web/RoutesTest.kt`
- `src/test/kotlin/com/example/web/PageTest.kt`

Optional if needed:

- `src/main/kotlin/com/example/model/Responses.kt`


## Milestone Outcome

At the end of milestone 3:

- the server can create and stop named simulation instances
- the page can enter a running state through `POST /simulations`
- the page can return to idle through `DELETE /simulations/:id`
- lifecycle state exists independently of transport
- milestone 4 can plug the real engine into an existing lifecycle shell


## Design Constraints

- the simulation registry owns lifecycle, not the browser connection
- handles are lightweight in this milestone and may use placeholder runtime state
- start/stop must work entirely through Datastar-friendly HTTP responses
- lifecycle state must not depend on the legacy WebSocket code


## First-Pass Task List

### 1. Define `SimulationHandle`

- Create `simulation/SimulationHandle.kt`.
- Include the minimum state needed for lifecycle:
  - `id`
  - `config`
  - `status`
  - `createdAt`
  - `stoppedAt` or equivalent
- Use explicit status values such as:
  - `idle`
  - `running`
  - `stopped`
  - optional `failed`
- Keep room for future fields:
  - latest metrics snapshot
  - log buffer
  - engine job

Done when:

- the handle is the single lifecycle unit
- future engine work can attach to the handle without redesign


### 2. Define `SimulationRegistry`

- Create `simulation/SimulationRegistry.kt`.
- Support:
  - `create(config)`
  - `get(id)`
  - `stop(id)`
  - optional `list()` for diagnostics if useful
- Generate unique IDs suitable for embedding in routes and Datastar signals.
- Keep storage in memory only.
- Ensure `stop` is idempotent or clearly defined if called multiple times.

Done when:

- the backend has a transport-independent lifecycle owner
- simulations can be found and stopped by ID


### 3. Decide Registry Lifetime in the App

- Decide where the registry is constructed.
- Recommended approach:
  - create a single app-scoped registry during app startup
  - inject or pass it into route registration
- Do not hide it in globals if avoidable.

Done when:

- routes can access the same registry instance consistently


### 4. Add the Start Route

- Add `POST /simulations` to `web/Routes.kt`.
- Parse incoming config using milestone 2 parsing and validation.
- On invalid input:
  - return Datastar form error patches
  - do not create a handle
- On valid input:
  - create a `SimulationHandle`
  - return Datastar patches for:
    - `sim.id`
    - `sim.running = true`
    - `sim.status = "running"`
    - cleared form errors
  - optionally patch start/stop control state

At this milestone, start does not need to launch real simulation work.

Done when:

- the page can transition from idle to running using a real server-owned simulation ID


### 5. Add the Stop Route

- Add `DELETE /simulations/:id`.
- If the handle exists:
  - stop it
  - return Datastar patches for:
    - `sim.running = false`
    - `sim.status = "idle"` or `"stopped"` depending on chosen UX
    - any cleared transient errors
- If the handle does not exist:
  - return a clear error response or a safe no-op policy

Recommended policy:

- return a structured not-found response for direct route callers
- keep the page behavior resilient if a stale simulation ID is submitted

Done when:

- the page can leave the running state cleanly through the server


### 6. Add Lifecycle Fragments

- Extend `web/Fragments.kt` if needed for:
  - running status badge
  - idle status badge
  - start/stop control region
  - future stream-anchor placeholder region
- Keep selectors stable.
- Do not add the real stream anchor yet.

Done when:

- milestone 4 can mount streaming behavior without redesigning the status/control layout


### 7. Update the Page Shell for Lifecycle Readiness

- Update `web/Page.kt` to expose:
  - start action target
  - stop action target
  - a stable region where running-state controls can be patched
  - a stable region reserved for the future stream anchor
- Keep UI logic minimal.
- The shell should still render correctly in idle mode with no active simulation.

Done when:

- idle and running states can be represented without changing page structure


### 8. Add Registry Tests

- Create `SimulationRegistryTest.kt`.
- Cover:
  - create returns unique IDs
  - get returns created handle
  - stop transitions the handle state
  - repeated stop behaves consistently
  - missing ID lookup returns null or equivalent

Done when:

- registry semantics are stable before engine behavior is added


### 9. Extend Route Tests

- Update `RoutesTest.kt`.
- Add coverage for:
  - `POST /simulations` success with valid input
  - `POST /simulations` failure with invalid input
  - `DELETE /simulations/:id` success for existing handle
  - `DELETE /simulations/:id` behavior for missing handle
- Assert Datastar response shape at the contract level:
  - simulation ID patched on start
  - running state patched on start
  - form errors returned on invalid start

Done when:

- route behavior is pinned down before the engine arrives


## Suggested Status Model

A practical first version:

- `running`
- `stopped`

UI mapping:

- no `sim.id` and `sim.running = false` means idle shell
- `sim.id` present and `sim.running = true` means active shell

If you need more states immediately, add:

- `failed`

Avoid over-modeling lifecycle before the engine exists.


## Suggested UI Effects

### On Start Success

Patch:

- `sim.id`
- `sim.running = true`
- `sim.status = "running"`
- clear `errors.form`

Optional fragment updates:

- disable or hide Start
- enable or show Stop
- update status badge

### On Start Validation Failure

Patch:

- field errors
- global form errors if needed

Do not patch `sim.id` or running state.

### On Stop Success

Patch:

- `sim.running = false`
- `sim.status = "idle"` or `"stopped"`
- optionally clear `errors.stream`

Optional fragment updates:

- restore Start controls
- remove running-only decorations


## Test Checklist

Minimum green test set for this milestone:

- `PageTest`
  - lifecycle control region exists
  - future stream-anchor region exists
- `RoutesTest`
  - `GET /` still works
  - `POST /simulations` valid path
  - `POST /simulations` invalid path
  - `DELETE /simulations/:id` valid path
  - `DELETE /simulations/:id` missing-ID path
- `SimulationRegistryTest`
  - create/get/stop semantics


## Recommended Implementation Order

1. Create `SimulationHandle.kt`
2. Create `SimulationRegistry.kt`
3. Decide app-scoped registry wiring in `Application.kt`
4. Add `POST /simulations`
5. Add `DELETE /simulations/:id`
6. Add lifecycle fragments and page shell hooks
7. Add registry tests
8. Extend route tests
9. Run the full test suite


## What Not To Do Yet

- do not implement the real engine
- do not add SSE
- do not add `PATCH /simulations/:id`
- do not add live metrics
- do not add chart integration
- do not couple lifecycle to any browser connection


## Acceptance Criteria

Milestone 3 is complete when:

- the app has an in-memory simulation registry
- `POST /simulations` creates a real handle with a real ID
- `DELETE /simulations/:id` stops that handle
- lifecycle state is patched back into the page through Datastar-friendly responses
- validation failures still use milestone 2 error plumbing
- milestone 4 can attach an engine to existing handles without redesigning routes


## Handoff to Milestone 4

Milestone 4 should be able to build directly on this by adding:

- limiter factory
- simulation engine
- handle-owned runtime state
- metrics snapshots
- log buffering

If milestone 4 needs to redesign lifecycle ownership, milestone 3 was too transport-shaped or too thin.

