# Milestone 7

## Revision Note

The UI did not converge on distinct `Limiter` and `Traffic` sections.
It converged on a guided step-based wizard with persistent run panels.
This document records that shipped direction and the follow-up posture from here.


## Objective

Refine the dashboard control surface so the user can get from idle to a meaningful simulation state through a short guided flow:

- choose a limiter family
- shape its capacity
- choose an incoming traffic rate
- start the simulation

This milestone is about improving comprehension without changing the transport, route contracts, or simulation backend semantics.


## Scope

In scope:

- replace the undifferentiated control surface with a guided step flow
- keep limiter-specific fields conditional on `limiterType`
- preserve live-update behavior for the visible controls
- update page tests to reflect the wizard contract
- leave room for future scenario shortcuts without making them required

Out of scope:

- splitting the backend `SimulationConfig` model into nested types
- exposing every traffic tuning field in the UI
- changing route contracts
- changing simulation engine semantics
- redesigning the SSE or Datastar architecture
- major visual redesign beyond what supports the wizard flow


## Deliverables

Likely updates:

- `src/main/kotlin/com/example/web/Page.kt`
- `src/main/kotlin/com/example/web/Fragments.kt`
- `src/test/kotlin/com/example/web/PageTest.kt`

Optional if needed:

- `src/test/kotlin/com/example/web/RoutesTest.kt`


## Milestone Outcome

At the end of milestone 7:

- the page guides the user through limiter choice, capacity shaping, traffic rate, and start
- only limiter-relevant controls are shown for smooth and composite flows
- run panels stay visible after start
- the live dashboard keeps the existing start, update, stream, stop, and resume behavior
- scenarios and presets are deferred instead of being forced into the wizard prematurely
- advanced traffic controls remain in the config and validation model but are not exposed in the page


## Design Constraints

- keep the current flat `config.*` signal structure for this pass
- keep the current `POST`, `PATCH`, `DELETE`, `POST /simulations/:id/resume`, and SSE route contracts
- do not force validation or backend parsing changes unless the UI contract truly requires them
- preserve the current live-update semantics for the visible controls
- prioritize a clear first-run path over maximum control density


## Shipped Control Model

### Step 1: Limiter

This step answers: "Which limiter family are we simulating?"

Fields:

- `limiterType`
- composite child editor only when `limiterType = composite`


### Step 2: Capacity

This step answers: "How much capacity should that limiter provide?"

Fields:

- `permits`
- `perSeconds` through duration toggles
- `warmupSeconds` only when `limiterType = smooth`
- `overflowMode`


### Step 3: Traffic

This step answers: "How much demand are we applying right now?"

Fields:

- `requestsPerSecond`


### Step 4: Start

This step answers: "Do we want to run this configuration now?"

Fields:

- start and stop controls


### Running Panels

Once the wizard has been used to start a simulation, the page keeps the runtime panels visible:

- status
- chart
- stats
- status log
- response log
- page-level errors


### Deferred Traffic Controls

The config model still carries the following traffic and workload knobs:

- `apiTarget`
- `serviceTimeMs`
- `jitterMs`
- `failureRate`
- `workerConcurrency`

These remain roadmap candidates, not part of the milestone 7 page plan.


## Scenario and Preset Direction

Current result:

- the shipped page does not mount a scenario row
- the wizard itself is the primary onboarding model

If scenarios are added later:

- treat them as quick-start recipes, not as a replacement for the wizard
- place them above step 1 or in the page header
- let a scenario patch both limiter and traffic signals and advance `ui.step`
- keep `Start` explicit; applying a scenario should not auto-start the simulation
- when a simulation is already running, allow the scenario action to reuse the existing `PATCH` flow

Implementation note:

- the existing `renderPresetsPanel()` helper is still a useful starting point
- if it is revived, it should also update `ui.step` so the wizard and the recipe buttons agree on page state

Not planned for now:

- separate limiter presets and traffic presets
- exposing advanced traffic controls just to make scenario recipes possible


## Milestone Checklist

Completed:

- convert the control surface into a guided step flow
- keep limiter-specific visibility rules
- preserve live-update semantics for the visible controls
- update page tests to reflect the wizard contract

Deferred:

- mount scenario or preset shortcuts in the page
- expose advanced traffic controls in the wizard
- revisit a section-based information architecture


## Suggested UI Behavior

### Idle State

- users move through a short setup path instead of facing all controls at once
- limiter-specific controls only appear when relevant
- `Start` remains the explicit final action


### Running State

- the wizard remains available for the currently exposed fields
- changing visible limiter fields updates the active simulation
- changing the visible traffic rate updates the active simulation
- run panels stay visible after the first start


### Limiter-Type Changes

- switching limiter type immediately updates visible limiter-specific fields
- hidden fields should not dominate the screen
- the current config model may still carry their values under the hood


## Acceptance Criteria

Milestone 7 is complete when:

- the page has a clear step-based entry flow
- limiter configuration is clearer than the prior single control block
- limiter-specific fields only appear when relevant
- live updates still work from the visible controls while running
- run panels remain understandable after start
- page tests reflect the wizard contract


## Likely Follow-Up

After milestone 7, likely follow-up work includes:

- removing the legacy WebSocket demo code so the repo has one clear direction
- adding README and CI basics
- deciding whether scenario recipes are worth adding on top of the wizard
- keeping advanced traffic controls as optional future work rather than immediate UI scope
