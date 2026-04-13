# Milestone 6

## Objective

Refine the live dashboard into the intended Datastar-first experience:

- support live config updates through `PATCH /simulations/:id`
- add debounced control behavior while running
- wire presets into the new flow
- improve live-state UI behavior and chart integration

This milestone is about polish and control flow on top of the working lifecycle, engine, and SSE foundation.


## Scope

In scope:

- `PATCH /simulations/:id`
- config updates for running simulations
- Datastar-friendly debounced control behavior
- preset application through signal updates and route calls
- chart wiring or chart polish
- improved live-state UI handling
- tests for update behavior

Out of scope:

- persistence
- multi-user coordination
- authentication
- large visual redesign beyond what supports the current UX


## Deliverables

Likely updates:

- `src/main/kotlin/com/example/web/Routes.kt`
- `src/main/kotlin/com/example/web/Page.kt`
- `src/main/kotlin/com/example/web/Fragments.kt`
- `src/main/kotlin/com/example/web/DatastarResponses.kt`
- `src/main/kotlin/com/example/simulation/SimulationRegistry.kt`
- `src/main/kotlin/com/example/simulation/SimulationHandle.kt`
- `src/main/kotlin/com/example/simulation/SimulationEngine.kt`
- `src/test/kotlin/com/example/web/RoutesTest.kt`
- `src/test/kotlin/com/example/web/PageTest.kt`
- `src/test/kotlin/com/example/web/SseStreamTest.kt`
- `src/test/kotlin/com/example/simulation/SimulationRegistryTest.kt`

Optional if needed:

- a small dedicated client-side chart helper file or isolated script block


## Milestone Outcome

At the end of milestone 6:

- the user can change config while a simulation is running
- updates are validated server-side and applied without breaking the stream model
- presets work within the new Datastar-first architecture
- the UI feels coherent and complete for the intended demo


## Design Constraints

- lifecycle ownership stays with `SimulationHandle`
- stream ownership stays with the SSE route
- update behavior must reuse milestone 2 validation instead of inventing a second path
- config updates must not force a redesign of the SSE subscriber model
- chart code must remain an island, not a second frontend app


## Update Model

There are two viable update strategies:

### Strategy A: Replace Handle Runtime In Place

- validate incoming config
- stop the current engine runtime inside the existing handle
- restart runtime in the same handle with new config
- preserve `sim.id` and subscribers

Advantages:

- stable simulation ID
- stream can stay attached

Risks:

- restart boundaries must be very clear
- metrics/log semantics around reset vs continuity must be defined

### Strategy B: Replace Handle Behind the Same ID

- validate incoming config
- swap runtime internals while preserving observable handle identity

Advantages:

- route and stream identity remain stable

Risks:

- implementation can become more implicit than necessary

Recommended approach:

- use Strategy A explicitly
- preserve `sim.id`
- stop and recreate runtime internals within the same handle
- decide whether metrics/logs reset on update or append across updates

Recommended policy:

- reset rolling metrics on update
- retain a warning/log entry indicating config changed


## First-Pass Task List

### 1. Add Update Support to the Registry/Handle Layer

- Add `update(id, config)` to `SimulationRegistry`.
- Implement update behavior on `SimulationHandle`.
- Reuse the existing engine ownership model.
- Define update semantics clearly:
  - whether metrics reset
  - whether logs reset
  - whether a warning/event is emitted

Recommended first version:

- preserve handle identity
- restart runtime with new validated config
- reset current metrics
- keep a short audit log entry like "config updated"

Done when:

- the backend can change a running simulation without changing `sim.id`


### 2. Add `PATCH /simulations/:id`

- Add the update route to `web/Routes.kt`.
- Parse incoming config through the same raw-config and validation path used by start.
- On invalid input:
  - return form error patches
  - do not change runtime
- On valid input:
  - clear form errors
  - apply update
  - patch the lifecycle/status state if needed
  - optionally patch a non-error status message or warning fragment

Done when:

- the browser can submit validated config changes against a running handle


### 3. Decide Debounce Behavior for Running Controls

- Define which controls trigger updates while running.
- Add debounced update behavior in the page.

Recommended first version:

- debounce numeric/text-like controls
- immediate update for select controls if UX feels better
- do not send updates when no simulation is running

Done when:

- the page avoids spamming the update route while still feeling responsive


### 4. Wire Presets Into the New Flow

- Update `web/Page.kt` and any fragment helpers so preset actions:
  - patch `config.*` signals
  - trigger the update route if a simulation is running
  - only change the local shell if no simulation is running

Recommended behavior:

- idle: preset updates the form only
- running: preset updates the form and issues a debounced or immediate `PATCH`

Done when:

- presets work naturally in both idle and running states


### 5. Improve Status and Warning UX

- Extend `Fragments.kt` and response helpers as needed.
- Make status changes clearer:
  - idle
  - running
  - update applied
  - validation failed
  - stream error
- Ensure warnings are distinct from validation errors.

Done when:

- the user can tell the difference between form problems, runtime warnings, and stream state


### 6. Refine the Stream Anchor and Running UI

- Ensure stream-anchor behavior remains correct across updates.
- Confirm updates do not remount or duplicate the stream unintentionally.
- Keep Start/Stop/Update responsibilities separate:
  - lifecycle controls own start/stop
  - stream-anchor owns SSE
  - config controls own update signaling

Done when:

- config changes do not break or duplicate the live stream


### 7. Add or Polish the Chart Island

- Decide whether the current chart placeholder is sufficient or needs live integration now.
- If integrating the chart:
  - feed it only the state it needs
  - keep it isolated from the rest of the page logic
  - avoid building a second app around it
- If not fully integrating:
  - make the placeholder honest and clearly labeled

Recommended path:

- add a minimal chart island that reacts to live stats

Done when:

- the chart no longer feels like a dead placeholder
- chart behavior stays isolated from Datastar control flow


### 8. Add Update-Focused Tests

- Extend `RoutesTest.kt` to cover:
  - `PATCH /simulations/:id` success
  - `PATCH /simulations/:id` validation failure
  - `PATCH /simulations/:id` missing simulation ID
- Extend simulation tests if needed to cover:
  - handle update semantics
  - runtime restart behavior
  - preservation of handle identity

Done when:

- update behavior is pinned down before final cleanup and docs work


### 9. Extend Stream Tests for Update Behavior

- Extend `SseStreamTest.kt` if update behavior affects:
  - stream continuity
  - warning events
  - metrics reset semantics
- Assert the stream remains attached and usable after a valid update.

Done when:

- live update behavior does not regress streaming correctness


### 10. Tighten the Page Contract

- Extend `PageTest.kt` to cover:
  - update-capable controls
  - preset region
  - chart mount point if newly activated
  - stable running/idle hooks

Done when:

- the page shell contract reflects the completed interaction model


## Suggested Update Semantics

Recommended first version:

- valid update preserves `sim.id`
- valid update keeps SSE subscribers attached
- valid update restarts runtime internals
- valid update resets rolling metrics
- valid update appends a warning or log entry stating config changed
- invalid update leaves the running simulation untouched


## Suggested UI Behavior

### Idle State

- form is editable
- presets update form fields only
- Start enabled
- Stop disabled or hidden

### Running State

- form remains editable
- changing controls triggers debounced update
- presets update fields and apply update
- Start disabled or hidden
- Stop enabled

### Validation Failure During Update

- field errors render in place
- simulation keeps running under prior valid config
- status badge remains running


## Test Checklist

Minimum green test set for this milestone:

- `RoutesTest`
  - `PATCH /simulations/:id` valid
  - `PATCH /simulations/:id` invalid
  - `PATCH /simulations/:id` missing ID
- `SseStreamTest`
  - stream survives valid update
  - invalid update does not corrupt stream state
- `SimulationRegistryTest`
  - update preserves handle identity
  - update changes config
  - update semantics are explicit
- `PageTest`
  - preset controls exist
  - update-capable control wiring exists
  - chart mount point remains stable


## Recommended Implementation Order

1. Add update support to `SimulationHandle`
2. Add update support to `SimulationRegistry`
3. Add `PATCH /simulations/:id`
4. Define and implement update semantics
5. Wire debounced control behavior
6. Wire presets
7. Add or polish chart island
8. Extend route, stream, registry, and page tests
9. Run the full test suite


## What Not To Overdo

- do not rebuild the page as a custom SPA
- do not move validation into browser-only logic
- do not let chart code leak into lifecycle or stream routing
- do not change `sim.id` on every config update unless there is a very strong reason


## Acceptance Criteria

Milestone 6 is complete when:

- a running simulation can be updated through `PATCH /simulations/:id`
- invalid updates are rejected without breaking the running simulation
- presets work in both idle and running states
- the stream remains stable across valid updates
- the chart is either live or explicitly and intentionally deferred
- the page feels complete as the intended Datastar-first demo


## Likely Final Follow-Up

After milestone 6, the remaining work is mostly cleanup:

- repo hygiene if anything remains
- README and run instructions
- CI and smoke checks
- pruning legacy code paths if they are still in the repo

