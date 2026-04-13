# Milestone 6 Fixes 2

## Purpose

This document captures the remaining issues found during the second browser validation pass after the first Milestone 6 fixes were applied.

The latest browser pass confirmed that:

- the page loads correctly
- controls are real editable inputs
- Start works
- SSE updates work
- presets work while running
- Stop works

The remaining gaps are narrower:

- manual live config edits do not appear to affect runtime behavior reliably
- warning/error panel behavior should be clarified on stop/reset


## Current Findings

### 1. Manual Live Control Changes May Not Be Applying to Runtime

Observed behavior:

- Starting a simulation works
- Applying the `Burst (100 rps)` preset while running works and clearly changes behavior
- Changing `Overflow Mode` from `Queue` to `Reject` while running did not obviously change runtime semantics

What was observed after switching to `Reject`:

- status remained `running`
- `sim.id` stayed stable
- the app did not crash
- queue depth remained very high
- `Denied` stayed `0`
- `Reject rate` stayed `0`

Expected behavior if reject mode applied:

- queue growth should stop or reduce sharply
- denials should appear under overload
- `Reject rate` should become non-zero

Possible causes:

- the PATCH request is not being sent for select changes
- the PATCH request is sent, but the updated config is not applied
- the update path restarts runtime, but `overflowMode` is not actually used in the restarted engine
- the browser-side signal value changes, but the server receives stale or incomplete config
- update timing or debounce behavior masks the effect in a way that needs clearer verification


### 2. Warning Persistence After Stop May Need UX Cleanup

Observed behavior:

- after stopping the simulation, the page returned to `idle`
- `sim.id` cleared correctly
- the errors panel still displayed `config updated`

This may be acceptable if warnings are intended to be a persistent activity trail.
It may also be undesirable if the errors/warnings area should reset on stop.

This needs a product decision rather than a blind code change.


## Fix Goals

The fixes should tighten live-update correctness and clean up the stop-state UX.

Success means:

- manual edits to running controls reliably affect runtime behavior
- the update path is verified for both presets and direct control changes
- warning persistence is either intentionally preserved or intentionally cleared


## Fix Scope

In scope:

- verifying and fixing manual update request flow
- verifying and fixing update application semantics for non-preset controls
- clarifying warning behavior on stop
- adding tests that cover manual live updates, not just presets

Out of scope:

- architecture redesign
- SSE redesign
- lifecycle ownership changes


## Fix Plan

### Fix 1: Verify PATCH Traffic for Direct Control Changes

Add explicit verification around control-driven updates.

What to inspect:

- whether changing a select field while running issues `PATCH /simulations/:id`
- whether numeric inputs also issue `PATCH` after debounce
- whether the full current config is included in the PATCH payload

Recommended actions:

- add route-level logging temporarily during development if needed
- add browser-level verification using tests where practical
- add test coverage for manual update payload shapes, not only preset-driven ones

Done when:

- changing `Overflow Mode` directly is confirmed to send the expected PATCH request


### Fix 2: Verify Server Parsing for Running Updates

The request parsing path was fixed for start and update generally, but the manual update issue suggests verifying the actual shape used during live control changes.

Inspect:

- whether the PATCH request body differs from preset-triggered update requests
- whether select values such as `reject` are arriving under the expected `config` fields

Recommended actions:

- add or extend route tests for PATCH payloads representing direct control edits
- include enum-field changes like:
  - `overflowMode = reject`
  - `limiterType = smooth`
  - `apiTarget = jsonplaceholder`

Done when:

- direct control PATCH payloads are tested and parsed correctly


### Fix 3: Verify Update Semantics in `SimulationRegistry.update`

The current update path preserves `sim.id` and restarts runtime.
That contract is correct, but it needs stronger verification that the new config is the config the restarted engine actually uses.

Recommended actions:

- add targeted tests that update a running handle from:
  - queue -> reject
  - bursty -> smooth
  - low request rate -> high request rate
- assert changed runtime behavior, not just changed stored config

Examples:

- queue -> reject under overload should produce denials
- smooth with warmup should change pacing
- high request rate should increase pressure metrics relative to low rate

Done when:

- update tests prove that runtime behavior changes with the new config, not just the handle fields


### Fix 4: Add Engine/Integration Coverage for Enum-Driven Behavior Changes

The current suite likely proves engine behavior for fixed configs and proves update plumbing, but not enough for live behavior changes caused by updated enum fields.

Add tests that cover:

- updating `overflowMode`
- updating `limiterType`
- updating `apiTarget` if live target changes are supported

Most important first:

- `overflowMode` update under overload

Done when:

- the failure mode seen in the browser is represented in automated tests


### Fix 5: Clarify Debounce + Update Trigger Semantics

The current page uses signal-change debounce on the controls panel.

Verify:

- select changes trigger the same debounced path as number changes
- there is no mismatch between `data-bind` and `data-on-signal-change-config__debounce.300ms`
- updates are not suppressed because the signal shape or change event does not match the expected Datastar behavior

If needed:

- move some select-driven updates to explicit `data-on-change`
- keep number fields on debounced signal changes

Recommended rule if current approach proves unreliable:

- selects use immediate `@patch`
- numeric fields stay debounced

Done when:

- direct select changes reliably update the running simulation


### Fix 6: Decide Warning Lifecycle on Stop

Make an explicit product choice for `config updated` warnings and similar runtime notices.

Two reasonable options:

#### Option A: Preserve Warnings Across Stop

Interpret warnings as an audit/history feed.

Effects:

- stopping the simulation does not clear warnings
- users can still see recent state changes after stop

#### Option B: Clear Warnings on Stop

Interpret warnings as run-scoped transient notices.

Effects:

- stopping the simulation resets warnings
- idle state appears cleaner

Recommended choice:

- clear warnings on stop unless they are intentionally being used as a persistent run history

Reason:

- the warnings panel currently lives inside `Errors`, and stale warnings after stop read like unresolved current issues

Done when:

- warning behavior on stop is intentional and tested


## Suggested File Changes

Likely updates:

- `src/main/kotlin/com/example/web/Page.kt`
- `src/main/kotlin/com/example/web/Routes.kt`
- `src/main/kotlin/com/example/simulation/SimulationRegistry.kt`
- `src/main/kotlin/com/example/simulation/SimulationHandle.kt`
- `src/test/kotlin/com/example/web/RoutesTest.kt`
- `src/test/kotlin/com/example/web/SseStreamTest.kt`
- `src/test/kotlin/com/example/simulation/SimulationRegistryTest.kt`
- `src/test/kotlin/com/example/simulation/SimulationEngineTest.kt`


## Recommended Implementation Order

1. Add tests for direct-control PATCH payloads
2. Add update-behavior tests for queue -> reject
3. Inspect and fix select-triggered update flow if needed
4. Re-run browser flow for manual select changes
5. Decide warning persistence behavior
6. Add tests for warning behavior on stop


## Acceptance Criteria

These fixes are complete when:

- changing `Overflow Mode` from `Queue` to `Reject` while running reliably changes behavior
- direct control edits are as trustworthy as preset-driven updates
- update tests cover behavior changes, not only stored config changes
- stop-state warning behavior is intentional and verified


## Non-Goals

Do not use this fix pass to:

- redesign lifecycle or stream ownership
- replace Datastar update wiring wholesale unless necessary
- move transport concerns into the simulation layer

