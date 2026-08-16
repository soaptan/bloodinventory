# Unified Component Filters Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the separate match-criteria card and table-filter toolbar with one responsive filter form and one Apply/Reset action pair.

**Architecture:** Keep component type bound to the existing `MedicalSafeMatchRequest` while leaving recipient blood group unset so the server returns components for general review. Submit the table refinement values in the same GET request, restore those values from `URLSearchParams`, and apply the existing client-side row filtering and sorting after the page reloads.

**Tech Stack:** Java 25, Spring Boot, MockMvc, Thymeleaf, HTML, CSS, vanilla JavaScript, Maven Wrapper

## Global Constraints

- Do not change blood compatibility rules.
- Do not change reservation or release behavior.
- Keep every filter usable when the server-backed result is empty.
- Unknown client-side query values must fall back to control defaults.
- Preserve explicit labels, keyboard form submission, table semantics, and responsive behavior.

---

### Task 1: Unified filter form and behavior

**Files:**
- Modify: `bloodinventory/src/test/java/com/fyp/bloodinventory/DashboardTemplateRenderingTests.java`
- Modify: `bloodinventory/src/main/resources/templates/medical-components.html`
- Modify: `bloodinventory/src/main/resources/static/css/medical-workflow.css`

**Interfaces:**
- Consumes: `MedicalSafeMatchRequest.componentType`, model attributes `bloodGroups`, `componentTypes`, `safeMatchLocations`, and `safeComponents`
- Produces: one form marked `data-safe-match-filter-form`; query parameters `componentType`, `search`, `donorGroup`, `status`, `match`, `expiry`, `location`, and `sort`; reset link marked `data-clear-safe-match-filters`

- [ ] **Step 1: Write the failing rendering test**

Add this test to `DashboardTemplateRenderingTests`:

```java
@Test
void medicalComponentsPageCombinesCompatibilityAndTableFilters() throws Exception {
    mockMvc.perform(get("/medical/components").principal(ADMIN_AUTHENTICATION))
            .andExpect(expect(status().isOk()))
            .andExpect(expect(content().string(containsString("data-safe-match-filter-form"))))
            .andExpect(expect(content().string(not(containsString("name=\"recipientBloodGroup\"")))))
            .andExpect(expect(content().string(not(containsString(">Recipient Blood Group<")))))
            .andExpect(expect(content().string(containsString("name=\"componentType\""))))
            .andExpect(expect(content().string(containsString("name=\"search\""))))
            .andExpect(expect(content().string(containsString("name=\"donorGroup\""))))
            .andExpect(expect(content().string(containsString("name=\"status\""))))
            .andExpect(expect(content().string(containsString("name=\"match\""))))
            .andExpect(expect(content().string(containsString("name=\"expiry\""))))
            .andExpect(expect(content().string(containsString("name=\"location\""))))
            .andExpect(expect(content().string(containsString("name=\"sort\""))))
            .andExpect(expect(content().string(containsString("href=\"/medical/components\" data-clear-safe-match-filters"))))
            .andExpect(expect(content().string(not(containsString(">Apply Match<")))))
            .andExpect(expect(content().string(not(containsString(">Match Criteria<")))));
}
```

The production change that makes this test pass is replacing the two independent filter areas with one named GET form. Removing or splitting that form later makes the test fail.

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```powershell
.\mvnw.cmd -Dtest=DashboardTemplateRenderingTests#medicalComponentsPageCombinesCompatibilityAndTableFilters test
```

Expected: FAIL because `data-safe-match-filter-form` and the unified query parameter names are absent, while the old `Apply Match` and `Match Criteria` content is present.

- [ ] **Step 3: Implement the unified form**

In `medical-components.html`:

- Remove the `two-column-grid` containing the Match Criteria and Components in Review cards.
- Keep one `workflow-card` for Available Components.
- Add the review count to the section heading.
- Place all eight labeled controls in one GET form with `data-safe-match-filter-form`.
- Use a submit button labeled `Apply Filters` and an `<a th:href="@{/medical/components}">` reset action.
- Keep the form outside the `safeComponents` non-empty condition.
- Keep the table inside its existing non-empty condition.
- Remove the old client-only Apply and Reset click handlers.
- Add initialization using `URLSearchParams`, assigning only values that exist in a select's options, then call `applySafeMatchControls()` once.

The restoration helper must follow this behavior:

```javascript
const queryParameters = new URLSearchParams(window.location.search);
const restoreControl = (control, parameterName) => {
    const requestedValue = queryParameters.get(parameterName);
    if (!control || requestedValue === null) {
        return;
    }

    if (control instanceof HTMLSelectElement
            && !Array.from(control.options).some((option) => option.value === requestedValue)) {
        return;
    }

    control.value = requestedValue;
};
```

- [ ] **Step 4: Add responsive unified-filter styles**

In `medical-workflow.css`, add page-scoped styles for:

```css
.safe-match-section-title { align-items: center; }
.safe-match-review-count { display: flex; align-items: baseline; gap: 8px; }
.safe-match-filter-grid { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 14px; align-items: end; }
.safe-match-search-field { grid-column: span 2; }
.safe-match-filter-actions { display: flex; gap: 10px; flex-wrap: wrap; margin-top: 14px; }
```

At the existing responsive breakpoints, reduce the grid to three columns, then two, then one; reset the search field to a single-column span on the narrowest layout.

- [ ] **Step 5: Run the focused test and verify GREEN**

Run:

```powershell
.\mvnw.cmd -Dtest=DashboardTemplateRenderingTests#medicalComponentsPageCombinesCompatibilityAndTableFilters test
```

Expected: PASS with zero failures.

- [ ] **Step 6: Run the full automated test suite**

Run:

```powershell
.\mvnw.cmd test
```

Expected: BUILD SUCCESS with zero test failures.

- [ ] **Step 7: Verify the rendered page in the in-app browser**

Open `http://localhost:8082/medical/components` and verify:

- one Available Components card contains all eight filters;
- Match Criteria and Apply Match are absent;
- the count appears in the Available Components heading;
- Apply submits all selected values and reloads with them restored;
- Reset returns to `/medical/components` with default values;
- the results summary and visible rows reflect the selected client-side criteria;
- there are no browser console errors;
- the layout remains usable at desktop and narrow viewport widths.

- [ ] **Step 8: Commit the implementation**

```powershell
git add -- bloodinventory/src/test/java/com/fyp/bloodinventory/DashboardTemplateRenderingTests.java bloodinventory/src/main/resources/templates/medical-components.html bloodinventory/src/main/resources/static/css/medical-workflow.css docs/superpowers/plans/2026-08-16-unified-component-filters.md
git commit -m "feat: unify medical component filters"
```
