# Simplified Staff Management Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the oversized staff-management summary and dense table with one compact, responsive workspace while preserving every existing staff action and safeguard.

**Architecture:** Restructure the existing Thymeleaf page into one workspace card with a concise header, count chips, filters, contextual selection actions, and an essential-column table. Keep all controller routes and row data attributes intact; a small dependency-free JavaScript helper owns selection-bar visibility and is called by the existing list controller.

**Tech Stack:** Spring Boot 3.5, Thymeleaf, HTML/CSS, browser JavaScript, JUnit 5/MockMvc, Node.js built-in test runner.

## Global Constraints

- Render one `Staff Management` heading and one workspace card.
- Keep Blood Administrator, Medical Staff, Lab Technician, and Archived Account counts as compact text chips.
- Keep Add Staff, search, role filter, status filter, sorting, reset, editing, archive, and restore behaviour.
- Show Archive and Restore only in a contextual selection bar when at least one row is selected.
- Preserve the current bulk-action endpoints, CSRF inputs, confirmation metadata, disabled rules, and self-account protection.
- Render only Selection, Staff, Role, Contact, Status, and Actions table columns.
- Keep IC number, gender, and role-specific values in row data attributes and in the edit modal.
- Do not change controllers, database queries, account lifecycle rules, or server-side validation.
- Keep keyboard focus, live selection announcements, responsive wrapping, and touch targets accessible.

---

### Task 1: Compact Workspace and Essential Table

**Files:**
- Modify: `bloodinventory/src/test/java/com/fyp/bloodinventory/DashboardTemplateRenderingTests.java:217-237`
- Modify: `bloodinventory/src/main/resources/templates/staff-profiles.html:24-354`
- Modify: `bloodinventory/src/main/resources/static/css/components.css:986-1065,1300-1346`

**Interfaces:**
- Consumes: Existing controller model values `staffCount`, `administratorCount`, `medicalCount`, `labCount`, `archivedCount`, and `staffProfiles`.
- Produces: `data-staff-management-workspace`, `data-staff-summary-chips`, `data-staff-selection-bar`, and the existing filter/action hooks in a compact hierarchy. Task 2 consumes `data-staff-selection-bar`.

- [ ] **Step 1: Replace the old rendering assertions with a failing compact-workspace regression test**

Replace `staffManagementArchivesAccountsInsteadOfDeletingRecords` with:

```java
@Test
void staffManagementUsesOneCompactWorkspaceAndPreservesSafeAccountActions() throws Exception {
    mockMvc.perform(get("/admin/staff/management").principal(ADMIN_AUTHENTICATION))
            .andExpect(expect(status().isOk()))
            .andExpect(expect(content().string(containsString("data-staff-management-workspace"))))
            .andExpect(expect(content().string(containsString("data-staff-summary-chips"))))
            .andExpect(expect(content().string(containsString("data-staff-summary-chip=\"administrator\""))))
            .andExpect(expect(content().string(containsString("data-staff-summary-chip=\"medical\""))))
            .andExpect(expect(content().string(containsString("data-staff-summary-chip=\"lab\""))))
            .andExpect(expect(content().string(containsString("data-staff-summary-chip=\"archived\""))))
            .andExpect(expect(content().string(containsString("data-staff-selection-bar"))))
            .andExpect(expect(content().string(containsString("href=\"/admin/staff/register\""))))
            .andExpect(expect(content().string(containsString("action=\"/admin/staff/archive-selected\""))))
            .andExpect(expect(content().string(containsString("action=\"/admin/staff/restore-selected\""))))
            .andExpect(expect(content().string(containsString("<th>Staff</th>"))))
            .andExpect(expect(content().string(containsString("<th>Role</th>"))))
            .andExpect(expect(content().string(containsString("<th>Contact</th>"))))
            .andExpect(expect(content().string(containsString("<th>Status</th>"))))
            .andExpect(expect(content().string(containsString("<th class=\"table-cell-action\">Actions</th>"))))
            .andExpect(expect(content().string(not(containsString("People Workspace")))))
            .andExpect(expect(content().string(not(containsString("Manage Staff Records")))))
            .andExpect(expect(content().string(not(containsString("All Staff Accounts")))))
            .andExpect(expect(content().string(not(containsString("<th>IC Number</th>")))))
            .andExpect(expect(content().string(not(containsString("<th>Gender</th>")))))
            .andExpect(expect(content().string(not(containsString("<th>Role Details</th>")))))
            .andExpect(expect(content().string(containsString("Only archived staff accounts can be restored."))))
            .andExpect(expect(content().string(containsString("unlock the account, and allow sign-in again"))))
            .andExpect(expect(content().string(containsString("end active sessions"))))
            .andExpect(expect(content().string(not(containsString("action=\"/admin/staff/delete-selected\"")))));
}
```

- [ ] **Step 2: Run the focused test and verify the new workspace hook is missing**

Run:

```powershell
$env:BLOODINVENTORY_JAVA_HOME='C:\Users\User\.jdk\jdk-25.0.2'
.\mvnw.cmd -q '-Dtest=DashboardTemplateRenderingTests#staffManagementUsesOneCompactWorkspaceAndPreservesSafeAccountActions' test
```

Expected: FAIL because the current response does not contain `data-staff-management-workspace`.

- [ ] **Step 3: Replace the two top-level cards with one compact workspace**

In `staff-profiles.html`, delete the old summary section at lines 26-71. Replace the `All Staff Accounts` header at lines 73-80 with this exact workspace opening, header, and chip row:

```html
<section id="update" class="card card-lg staff-management-workspace" data-staff-management-workspace>
    <header class="staff-management-header">
        <div class="staff-management-heading">
            <div class="section-subtitle">Staff Management</div>
            <h1 class="section-title">Staff Management</h1>
            <p>Search staff, update account details, and manage account access.</p>
        </div>
        <div class="staff-management-primary-actions">
            <div class="badge badge-primary staff-count-badge"
                 data-staff-count-badge
                 th:text="${staffCount + ' staff records'}">0 staff records</div>
            <a th:href="@{/admin/staff/register}"
               class="btn btn-primary staff-add-button"
               data-register-open>
                <svg viewBox="0 0 24 24" aria-hidden="true">
                    <path d="M12 5v14"></path>
                    <path d="M5 12h14"></path>
                </svg>
                Add Staff Member
            </a>
        </div>
    </header>

    <div class="staff-summary-chips" data-staff-summary-chips aria-label="Staff account summary">
        <div class="staff-summary-chip" data-staff-summary-chip="administrator">
            <span>Administrators</span><strong th:text="${administratorCount}">0</strong>
        </div>
        <div class="staff-summary-chip" data-staff-summary-chip="medical">
            <span>Medical</span><strong th:text="${medicalCount}">0</strong>
        </div>
        <div class="staff-summary-chip" data-staff-summary-chip="lab">
            <span>Lab</span><strong th:text="${labCount}">0</strong>
        </div>
        <div class="staff-summary-chip" data-staff-summary-chip="archived">
            <span>Archived</span><strong th:text="${archivedCount}">0</strong>
        </div>
    </div>
```

Keep the new section open. Move the existing staff controls, server-side empty state, table, and filter empty state directly after the chip row, and keep the existing closing `</section>` after them. Remove `data-staff-management-summary`, `data-staff-records-overview`, the duplicate headings, the protection card, and the old toolbar-level Add Staff link.

- [ ] **Step 4: Consolidate search and filters into one concise toolbar**

Replace the controls at lines 82-201 with the following exact toolbar and contextual action structure. The Restore and Archive forms are the current forms moved intact into `.staff-selection-actions`:

```html
<div class="staff-filter-toolbar">
    <div class="form-group staff-search-col">
        <label for="staff-list-search" class="form-label">Search Staff</label>
        <input id="staff-list-search" class="form-input" type="search"
               placeholder="Search by name, username, email, or IC number" data-staff-search>
    </div>
    <div class="form-group staff-filter-col">
        <label for="staff-role-filter" class="form-label">Role</label>
        <select id="staff-role-filter" class="form-select" data-staff-role-filter>
            <option value="all">All roles</option>
            <option value="administrator">Blood Administrator</option>
            <option value="medical">Medical Staff</option>
            <option value="lab">Lab Technician</option>
        </select>
    </div>
    <div class="form-group staff-filter-col">
        <label for="staff-status-filter" class="form-label">Status</label>
        <select id="staff-status-filter" class="form-select" data-staff-status-filter>
            <option value="all">All statuses</option>
            <option value="active">Active</option>
            <option value="archived">Archived</option>
            <option value="locked">Locked</option>
        </select>
    </div>
    <div class="form-group staff-filter-col">
        <label for="staff-sort" class="form-label">Sort</label>
        <select id="staff-sort" class="form-select" data-staff-sort>
            <option value="name-asc">Name A-Z</option>
            <option value="name-desc">Name Z-A</option>
            <option value="id-desc">Newest Staff ID</option>
            <option value="id-asc">Oldest Staff ID</option>
            <option value="role-asc">Role</option>
            <option value="status-asc">Status</option>
        </select>
    </div>
    <button type="button" class="btn btn-tertiary staff-filter-reset" data-clear-staff-filters>
        <svg viewBox="0 0 24 24" aria-hidden="true">
            <path d="M3 12a9 9 0 1 0 3-6.7"></path>
            <path d="M3 4v6h6"></path>
        </svg>
        Reset Filters
    </button>
</div>

<div class="staff-list-status">
    <div class="form-help" data-staff-results-summary>Showing all staff records.</div>
</div>

<div class="staff-selection-bar"
     data-staff-selection-bar
     role="status"
     aria-live="polite"
     hidden>
    <div class="staff-selection-copy" data-staff-selection-summary>0 rows selected</div>
    <div class="staff-selection-actions">
        <form method="post"
              id="restore"
              th:action="@{/admin/staff/restore-selected}"
              class="staff-toolbar-restore-form"
              data-action-confirm
              data-action-label="restore selected staff accounts"
              data-action-confirm-text="Confirm Restore"
              data-selected-staff-restore-form>
            <input type="hidden" th:if="${_csrf != null}"
                   th:name="${_csrf.parameterName}" th:value="${_csrf.token}">
            <span data-selected-staff-restore-fields></span>
            <button type="submit" class="btn btn-secondary"
                    data-selected-staff-restore-button disabled>
                <svg viewBox="0 0 24 24" aria-hidden="true">
                    <path d="M3 12a9 9 0 1 0 3-6.7"></path>
                    <path d="M3 4v6h6"></path>
                </svg>
                Restore
            </button>
        </form>
        <form method="post"
              id="archive"
              th:action="@{/admin/staff/archive-selected}"
              class="staff-toolbar-archive-form"
              data-action-confirm
              data-action-label="archive selected staff accounts"
              data-action-variant="danger"
              data-action-confirm-text="Confirm Archive"
              data-selected-staff-archive-form>
            <input type="hidden" th:if="${_csrf != null}"
                   th:name="${_csrf.parameterName}" th:value="${_csrf.token}">
            <span data-selected-staff-archive-fields></span>
            <button type="submit" class="btn btn-danger"
                    data-selected-staff-archive-button disabled>
                <svg viewBox="0 0 24 24" aria-hidden="true">
                    <path d="M4 7h16v13H4z"></path>
                    <path d="M2 4h20v3H2z"></path>
                    <path d="M9 11h6"></path>
                </svg>
                Archive
            </button>
        </form>
    </div>
</div>
```

Wrap the complete fragment in the existing `th:if="${!#lists.isEmpty(staffProfiles)}"` container, changing its class to `staff-management-controls` while retaining `data-staff-controls`.

- [ ] **Step 5: Reduce and reorder the rendered table columns**

Change the header order to:

```html
<tr>
    <th class="table-cell-checkbox">
        <input type="checkbox" class="staff-select-all" data-staff-select-all aria-label="Select all visible staff">
    </th>
    <th>Staff</th>
    <th>Role</th>
    <th>Contact</th>
    <th>Status</th>
    <th class="table-cell-action">Actions</th>
</tr>
```

For each row, retain the existing selection, Staff, Role, Contact, Status, and edit-button cells in this same order. Delete only the rendered IC Number, Gender, and Role Details cells. Keep all `data-ic-number`, `data-gender`, `data-license-no`, `data-position`, `data-certification-no`, and `data-department` row attributes because search and the edit modal consume them.

- [ ] **Step 6: Add compact workspace styles and remove obsolete staff-summary rules**

In `components.css`, replace `.staff-records-overview` and `.staff-toolbar-add-button` rules and update the filter rules with:

```css
.staff-management-workspace {
    display: grid;
    gap: var(--gap-md);
}

.staff-management-header,
.staff-management-primary-actions,
.staff-filter-toolbar,
.staff-list-status,
.staff-selection-bar,
.staff-selection-actions {
    display: flex;
    align-items: center;
}

.staff-management-header,
.staff-list-status,
.staff-selection-bar {
    justify-content: space-between;
}

.staff-management-header {
    gap: var(--gap-lg);
}

.staff-management-heading {
    min-width: 0;
}

.staff-management-heading p {
    margin: 4px 0 0;
    color: var(--text-muted);
}

.staff-management-primary-actions,
.staff-selection-actions {
    gap: var(--gap-sm);
    flex: 0 0 auto;
}

.staff-summary-chips {
    display: flex;
    flex-wrap: wrap;
    gap: var(--gap-sm);
}

.staff-summary-chip {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    min-height: 34px;
    padding: 7px 11px;
    border: 1px solid var(--border);
    border-radius: 999px;
    background: var(--surface-soft);
    color: var(--text-muted);
    font-size: 12px;
    font-weight: 750;
}

.staff-summary-chip strong {
    color: var(--text-strong);
    font-size: 14px;
}

.staff-management-controls {
    display: grid;
    gap: var(--gap-sm);
}

.staff-filter-toolbar {
    align-items: end;
    gap: var(--gap-sm);
    flex-wrap: wrap;
}

.staff-search-col {
    flex: 1 1 320px;
    margin-bottom: 0;
}

.staff-filter-col {
    flex: 0 1 160px;
    width: auto;
}

.staff-selection-bar {
    gap: var(--gap-md);
    padding: 10px 12px;
    border: 1px solid color-mix(in srgb, var(--color-primary) 28%, var(--border));
    border-radius: var(--radius-md);
    background: color-mix(in srgb, var(--color-primary) 7%, var(--surface));
}

.staff-selection-bar[hidden] {
    display: none;
}

.staff-selection-copy {
    color: var(--text-strong);
    font-size: 13px;
    font-weight: 800;
}
```

Extend the existing narrow-screen media query:

```css
.staff-management-header,
.staff-selection-bar {
    align-items: stretch;
    flex-direction: column;
}

.staff-management-primary-actions,
.staff-management-primary-actions .btn,
.staff-selection-actions,
.staff-selection-actions form,
.staff-selection-actions .btn {
    width: 100%;
}
```

- [ ] **Step 7: Run the focused rendering test**

Run the command from Step 2.

Expected: PASS with one test and no failures or errors.

- [ ] **Step 8: Commit the compact workspace**

```powershell
git add -- src/main/resources/templates/staff-profiles.html src/main/resources/static/css/components.css src/test/java/com/fyp/bloodinventory/DashboardTemplateRenderingTests.java
git commit -m "refactor: simplify staff management workspace"
```

### Task 2: Contextual Selection-Bar Behaviour

**Files:**
- Create: `bloodinventory/src/main/resources/static/js/staff-selection-bar.js`
- Create: `bloodinventory/src/test/js/staff-selection-bar.test.js`
- Modify: `bloodinventory/src/main/resources/templates/staff-profiles.html:815-816,1038-1172`

**Interfaces:**
- Consumes: `data-staff-selection-bar`, `data-staff-selection-summary`, and the selected-row count calculated by `updateSelectionSummary()`.
- Produces: `StaffSelectionBar.syncSelectionBar(selectionBar, selectionSummary, selectedCount): void` in the browser and a CommonJS export with the same function for Node tests.

- [ ] **Step 1: Write the failing selection-bar behaviour test**

Create `src/test/js/staff-selection-bar.test.js`:

```javascript
const assert = require('node:assert/strict');
const test = require('node:test');

const { syncSelectionBar } = require('../../main/resources/static/js/staff-selection-bar.js');

test('selection bar is visible only while staff rows are selected', () => {
    const selectionBar = { hidden: false };
    const selectionSummary = { textContent: '' };

    syncSelectionBar(selectionBar, selectionSummary, 0);
    assert.equal(selectionBar.hidden, true);
    assert.equal(selectionSummary.textContent, '0 rows selected');

    syncSelectionBar(selectionBar, selectionSummary, 2);
    assert.equal(selectionBar.hidden, false);
    assert.equal(selectionSummary.textContent, '2 rows selected');

    syncSelectionBar(selectionBar, selectionSummary, 0);
    assert.equal(selectionBar.hidden, true);
    assert.equal(selectionSummary.textContent, '0 rows selected');
});
```

- [ ] **Step 2: Run the test and verify the helper module is missing**

Run:

```powershell
node --test src/test/js/staff-selection-bar.test.js
```

Expected: FAIL with `MODULE_NOT_FOUND` for `staff-selection-bar.js`.

- [ ] **Step 3: Implement the dependency-free selection helper**

Create `src/main/resources/static/js/staff-selection-bar.js`:

```javascript
(function exposeStaffSelectionBar(globalScope) {
    function syncSelectionBar(selectionBar, selectionSummary, selectedCount) {
        const normalizedCount = Number.isFinite(selectedCount) && selectedCount > 0
            ? Math.floor(selectedCount)
            : 0;

        if (selectionBar) {
            selectionBar.hidden = normalizedCount === 0;
        }
        if (selectionSummary) {
            selectionSummary.textContent = `${normalizedCount} row${normalizedCount === 1 ? '' : 's'} selected`;
        }
    }

    const api = Object.freeze({ syncSelectionBar });
    if (typeof module !== 'undefined' && module.exports) {
        module.exports = api;
    }
    if (globalScope) {
        globalScope.StaffSelectionBar = api;
    }
}(typeof window !== 'undefined' ? window : globalThis));
```

- [ ] **Step 4: Load and call the helper from the staff page**

Before the existing inline script in the `custom-js` fragment, add:

```html
<script th:src="@{/js/staff-selection-bar.js}"></script>
```

Inside `initStaffListControls`, select the bar beside the existing summary selectors:

```javascript
const selectionBar = document.querySelector("[data-staff-selection-bar]");
```

Replace the existing direct `selectionSummary.textContent` block in `updateSelectionSummary()` with:

```javascript
window.StaffSelectionBar.syncSelectionBar(selectionBar, selectionSummary, selectedCount);
```

Keep all existing action-button, hidden-input, row-highlight, and select-all calculations unchanged.

- [ ] **Step 5: Run JavaScript behaviour and syntax checks**

Run:

```powershell
node --test src/test/js/staff-selection-bar.test.js
node --check src/main/resources/static/js/staff-selection-bar.js
```

Expected: 1 passing Node test and no syntax errors.

- [ ] **Step 6: Run focused and complete Maven suites**

Run:

```powershell
$env:BLOODINVENTORY_JAVA_HOME='C:\Users\User\.jdk\jdk-25.0.2'
.\mvnw.cmd -q -Dtest=DashboardTemplateRenderingTests test
.\mvnw.cmd test
```

Expected: Dashboard rendering tests pass; the complete suite reports `BUILD SUCCESS` with no failures or errors.

- [ ] **Step 7: Verify the localhost page without submitting mutations**

Open `http://localhost:8082/admin/staff/management` as an administrator and verify at desktop and narrow viewport widths:

1. Only one page heading and one workspace card appear before the table.
2. All four compact chips and the total count render correctly.
3. Search, role, status, sort, and reset continue filtering the same records.
4. The table shows only Selection, Staff, Role, Contact, Status, and Actions.
5. The selection bar is initially hidden, appears after selecting an eligible row, and hides after clearing the selection.
6. Archive or Restore enables only for a valid homogeneous selection; do not submit either form.
7. The edit modal still contains IC, gender, and role-specific details and opens for the selected staff record; do not submit edits.
8. The header, chips, filters, selection bar, and table remain readable at a narrow viewport.

- [ ] **Step 8: Commit contextual selection behaviour**

```powershell
git add -- src/main/resources/static/js/staff-selection-bar.js src/test/js/staff-selection-bar.test.js src/main/resources/templates/staff-profiles.html
git commit -m "feat: show contextual staff selection actions"
```
