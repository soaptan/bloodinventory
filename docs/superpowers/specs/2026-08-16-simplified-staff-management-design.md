# Simplified Staff Management Design

## Goal

Turn the staff-management page into one compact workspace that exposes the most common actions and essential account information without placing a large dashboard above the staff list.

## Page Structure

The existing summary card and staff-list card become one staff-management card. Its content order is:

1. One page header with the `Staff Management` title, a short supporting sentence, the total account count, and the primary `Add Staff Member` action.
2. A compact row of count chips for Blood Administrators, Medical Staff, Lab Technicians, and Archived Accounts.
3. One concise filter toolbar containing search, role, status, sort, and reset controls.
4. A contextual selection bar that appears only when at least one visible row is selected.
5. The compact staff table and existing empty/no-results states.

The redundant `People Workspace`, `Manage Staff Records`, `All Staff Accounts`, and `Account Protection` blocks are removed. Archive guidance remains available through contextual button titles, confirmation dialogs, and concise selection-bar copy.

## Actions and Filters

- `Add Staff Member` stays visible in the page header and links to the existing registration route.
- Search remains the visually dominant filter and retains name, username, email, and IC-number matching.
- Role, status, and sort controls keep their current values and client-side behaviour.
- Reset remains available as a lightweight toolbar button.
- Archive and Restore are removed from the default filter row. They appear in the contextual selection bar with the selected-row count.
- The contextual action buttons retain their existing disabled-state rules, self-account protection, confirmation dialogs, form endpoints, and CSRF handling.
- Clearing the selection hides the contextual bar again.

## Summary Chips

The page shows compact chips for:

- Blood Administrators
- Medical Staff
- Lab Technicians
- Archived Accounts

Each chip pairs a short label with its current count. The total staff count remains beside the page title so the same number is not repeated as another large card.

## Compact Table

The visible table columns are reduced to:

1. Selection
2. Staff
3. Role
4. Contact
5. Status
6. Actions

The Staff cell retains the avatar, full name, username, and staff ID. Contact retains email and phone number. Actions contains the existing edit control. IC number, gender, and role-specific details are removed from the list view but remain in the existing edit modal and in the row data attributes used by search and editing.

Filtering and sorting continue to use the full row data, including fields not rendered as columns. No controller, database, route, or account-lifecycle behaviour changes are required.

## Responsive Behaviour

- On wide screens, the title/action header and filter toolbar use horizontal space efficiently.
- At narrower widths, the title action, chips, and filters wrap without horizontal overlap.
- The selection bar stacks its summary and actions when needed.
- The table remains horizontally scrollable as a fallback, but the reduced column count should fit common laptop widths without requiring scrolling.
- Touch targets and keyboard focus indicators remain visible and usable.

## Accessibility

- Existing form labels and table header semantics remain intact.
- Count chips present readable text rather than colour-only meaning.
- The contextual selection bar uses a live status region so selection changes are announced.
- Archive and Restore buttons remain native buttons with descriptive titles and disabled states.
- Existing checkbox labels, confirmation dialogs, and self-account restrictions remain unchanged.

## Verification

- Add template regression coverage for the single workspace, compact chips, compact column set, header-level Add Staff action, and contextual selection bar.
- Assert that removed overview headings and removed detail columns are absent from the rendered list page.
- Add or update client-side behaviour coverage proving the contextual bar is hidden at zero selections, visible after selection, and hidden again after clearing selection.
- Run the focused dashboard/template tests and the complete Maven suite.
- Inspect the localhost page at desktop and narrow viewport widths, including search/filter use, row selection, contextual Archive/Restore states, and the edit modal.
- Do not submit archive, restore, or staff-edit forms during the visual smoke test.
