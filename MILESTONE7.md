# Milestone 7

## Objective

Refactor the dashboard control surface so the user can reason about the simulation in two separate dimensions:

- `Limiter`: which limiter is active and how that limiter is configured
- `Traffic`: how much demand is applied and what the request stream looks like

This milestone is about improving the information architecture and interaction model of the UI without redesigning the transport or simulation backend.


## Scope

In scope:

- split the current `Controls` section into `Limiter` and `Traffic`
- keep limiter configuration visually separate from traffic generation
- conditionally show limiter-specific fields based on `limiterType`
- clarify the role of presets by treating them as scenarios or splitting them into smaller preset groups
- preserve live-update behavior while making the page easier to understand
- update page tests to reflect the new UI contract

Out of scope:

- splitting the backend `SimulationConfig` model into nested types
- changing route contracts
- changing simulation engine semantics
- redesigning the SSE or Datastar architecture
- major visual redesign beyond what supports the refactor


## Deliverables

Likely updates:

- `src/main/kotlin/com/example/web/Page.kt`
- `src/main/kotlin/com/example/web/Fragments.kt`
- `src/test/kotlin/com/example/web/PageTest.kt`

Optional if needed:

- `src/test/kotlin/com/example/web/RoutesTest.kt`


## Milestone Outcome

At the end of milestone 7:

- users can immediately tell the difference between limiter capacity and traffic demand
- the limiter section only shows fields relevant to the selected limiter
- the traffic section owns request-rate and workload behavior
- presets no longer blur the mental model of what is being configured
- the live dashboard keeps the existing start, update, stream, and stop behavior


## Design Constraints

- keep the current flat `config.*` signal structure for this pass
- keep the current `POST`, `PATCH`, `DELETE`, and SSE route contracts
- do not force validation or backend parsing changes unless the UI contract truly requires them
- preserve the current live-update semantics:
  - debounced numeric edits
  - immediate select updates where appropriate
- prioritize clarity of control grouping over adding more controls


## Proposed Control Model

### Limiter

This section answers: "What capacity policy are we simulating?"

Fields:

- `limiterType`
- `permits`
- `perSeconds`
- `warmupSeconds` only when `limiterType = smooth`
- `secondaryPermits` only when `limiterType = composite`
- `secondaryPerSeconds` only when `limiterType = composite`


### Traffic

This section answers: "What pressure are we applying to the limiter?"

Fields:

- `requestsPerSecond`
- `overflowMode`
- `apiTarget`
- `serviceTimeMs`
- `jitterMs`
- `failureRate`
- `workerConcurrency`


## Preset Strategy

The current preset buttons update both limiter settings and traffic settings. That makes them scenario shortcuts, not pure limiter presets.

Recommended direction:

- rename the current preset area to `Scenarios`
- keep scenario buttons above the `Limiter` and `Traffic` sections
- let scenario buttons continue to patch both limiter and traffic fields

Optional later:

- add a separate `Limiter presets` row if the UI needs quick capacity-only shortcuts
- add a separate `Traffic presets` row if the UI needs quick load-only shortcuts


## First-Pass Task List

### 1. Split the Controls Shell

- Replace the single `Controls` section with:
  - `Scenarios` if retained
  - `Limiter`
  - `Traffic`
- Keep existing field ids and `data-bind` paths where possible to reduce churn.

Done when:

- the page no longer presents all controls as one undifferentiated block


### 2. Move Fields Into the Correct Section

- Move limiter-selection and limiter-shaping inputs into `Limiter`
- Move request-rate and workload inputs into `Traffic`
- Keep field-error slots attached to the same fields

Done when:

- each field clearly belongs to one conceptual section


### 3. Add Limiter-Specific Visibility Rules

- `smooth` reveals `warmupSeconds`
- `composite` reveals `secondaryPermits` and `secondaryPerSeconds`
- `bursty` hides smooth/composite-only fields

Recommended first version:

- use simple Datastar-driven conditional rendering or visibility toggles
- do not remove the underlying fields from the overall config model

Done when:

- the user does not see irrelevant limiter inputs for the selected limiter


### 4. Reclassify Presets as Scenarios

- Rename the preset region to reflect what it actually does
- Keep the current buttons if they are still useful
- Ensure scenario actions still work in both idle and running states

Done when:

- users do not mistake a full scenario button for a limiter-only preset


### 5. Preserve Live Update Semantics

- Keep the current running-update behavior intact while reorganizing the page
- Preserve debounced numeric update flow
- Preserve immediate select updates where already used
- Ensure moving fields between sections does not break `PATCH /simulations/:id`

Done when:

- a field can be moved in the UI without changing how live updates behave


### 6. Improve Section Labels and Microcopy

- Add short section descriptions if needed
- Make `Limiter` read like capacity policy
- Make `Traffic` read like demand/workload configuration

Recommended first version:

- keep copy short and functional
- avoid adding explanatory paragraphs unless they are necessary

Done when:

- the page is understandable without prior knowledge of the codebase


### 7. Extend Page Tests

- Update `PageTest.kt` to assert:
  - `Limiter` section exists
  - `Traffic` section exists
  - limiter fields render in the limiter section
  - traffic fields render in the traffic section
  - scenario buttons still exist if retained
- Keep tests focused on the page contract, not the exact visual styling

Done when:

- the new information architecture is pinned down in tests


### 8. Run Browser Validation

- Start a simulation from the refactored page
- Apply a scenario while running
- Change limiter fields while running
- Change traffic fields while running
- Stop the simulation
- Verify the page remains understandable on desktop and narrow viewport

Done when:

- the refactor improves comprehension without regressing behavior


## Suggested UI Behavior

### Idle State

- users can edit both `Limiter` and `Traffic`
- scenario buttons update both sections
- start remains the primary action


### Running State

- users can still edit both `Limiter` and `Traffic`
- changing limiter fields updates the active simulation
- changing traffic fields updates the active simulation
- scenario buttons continue to work as full scenario shortcuts


### Limiter-Type Changes

- switching limiter type immediately updates visible limiter-specific fields
- hidden fields should not dominate the screen
- the current config model may still carry their values under the hood


## Recommended Implementation Order

1. Split the page into `Limiter` and `Traffic` sections in `Page.kt`
2. Move existing controls into the correct section without changing ids
3. Rename or reframe presets as `Scenarios`
4. Add limiter-specific visibility rules
5. Update `PageTest.kt`
6. Run `./gradlew test`
7. Run a browser pass for desktop and narrow viewport


## What Not To Overdo

- do not split `SimulationConfig` into nested transport models yet
- do not redesign validation around the UI grouping
- do not change route payloads just to match visual grouping
- do not let a cosmetic refactor become a backend architecture rewrite


## Acceptance Criteria

Milestone 7 is complete when:

- the page has distinct `Limiter` and `Traffic` sections
- limiter configuration is visually decoupled from traffic configuration
- limiter-specific fields only appear when relevant
- the current preset region is reclassified or reorganized so its behavior is honest
- live updates still work from both sections while running
- page tests reflect the new UI contract


## Likely Final Follow-Up

After milestone 7, possible follow-up work includes:

- styling polish once the information architecture is stable
- improved empty-state and warning-state UX
- optional future backend model cleanup if the flat config shape becomes a maintenance burden
