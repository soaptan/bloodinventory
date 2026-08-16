# Unified Component Filters Design

## Goal

Combine the compatibility criteria and table filters on the medical components page into one coherent filter area. Users should be able to set all criteria and use a single **Apply Filters** action or a single **Reset Filters** action.

## Selected approach

Use one unified GET form inside the **Available Components** card.

- Keep `recipientBloodGroup` and `componentType` as server-backed compatibility criteria because they determine the component list and compatibility notes returned by `MedicalWorkflowService`.
- Give the existing search, donor group, status, match, expiry, location, and sort controls query-string names.
- Submit every control together through one **Apply Filters** button.
- After the server renders the compatibility result, initialize the table controls from the query string and apply their client-side filtering and sorting immediately.
- Make **Reset Filters** navigate to `/medical/components` without query parameters, restoring all defaults.

This keeps the current service boundaries intact while making the interface behave like one filter system.

## Alternatives considered

### Fully server-side filtering

Move every filter and sort operation into the controller and service. This would provide canonical shareable results but would add request fields, filtering code, and service tests for behavior that already works in the browser. It is more invasive than this layout change requires.

### Fully client-side filtering

Load all components once and calculate compatibility entirely in JavaScript. This would duplicate clinical compatibility logic in the frontend and risk divergence from the server rules, so it is not suitable.

## Page structure

The separate **Match Criteria** card and **Components in Review** card will be removed.

The **Available Components** card will contain:

1. A heading row with the title, explanatory copy, and the current component count.
2. One responsive filter grid containing:
   - Recipient Blood Group
   - Component Type
   - Search Components
   - Donor Group
   - Status
   - Match
   - Expiry
   - Location
   - Sort By
3. One action row containing **Apply Filters** and **Reset Filters**.
4. The results summary and component table.

On narrower screens, the filter grid will collapse into fewer columns and then a single column using the page's existing responsive breakpoints.

## Data flow

1. The user selects any combination of criteria and submits the unified GET form.
2. Spring binds `recipientBloodGroup` and `componentType` to `MedicalSafeMatchRequest` and renders the compatible component result as it does today.
3. The remaining query parameters remain available in the browser URL.
4. On page initialization, JavaScript restores the table control values from those query parameters.
5. JavaScript filters and sorts the server-rendered rows and updates the visible-results summary.
6. Reset removes all query parameters and returns both server-backed and client-side controls to their defaults.

## Empty and invalid states

- If the server-backed criteria return no components, the existing empty-state message remains visible and the unified filter form remains available so the user can change or reset criteria.
- Unknown or stale client-side query values fall back to each control's default instead of causing an error.
- The results summary continues to report visible rows after client-side filtering.

## Accessibility

- Keep explicit labels connected to every input and select.
- Use a semantic GET form and submit button so filtering works with keyboard navigation and without custom click handling.
- Keep Reset as a clearly labeled navigation action or button with equivalent keyboard behavior.
- Preserve the current table semantics and accessible empty-state text.

## Testing

- Add a template-rendering/controller test that verifies the page contains one unified GET form with the two compatibility criteria and the table filters.
- Verify the separate **Match Criteria** card and duplicate **Apply Match** action are absent.
- Verify query parameters preserve and restore client-side filter values.
- Verify Reset clears all criteria.
- Run the focused controller/template tests, then the full Maven test suite.
- Use the in-app browser at `/medical/components` to confirm the combined layout, responsive behavior, Apply behavior, Reset behavior, result count, and absence of console errors.

## Out of scope

- Changing blood compatibility rules.
- Changing reservation or release behavior.
- Moving all filter logic into the backend.
- Redesigning unrelated medical workflow pages.
