# Password Visibility Toggle Design

## Goal

Add an accessible password visibility control to both fields on the reset-password page without changing the password-reset API or validation rules.

## Interface

- Wrap each password input in a positioned container.
- Place a native `button type="button"` at the right edge of each input.
- Render the eye and eye-off states with inline SVG so the page needs no icon dependency.
- Give each button an explicit target input through a data attribute.
- Keep each field independent: toggling one field must not change the other.
- Reserve input padding for the button so entered text never overlaps the icon.

## Behaviour and Accessibility

- Passwords start concealed with `type="password"`.
- Activating a toggle changes only its target input between `password` and `text`.
- The button updates its icon, `aria-label`, `title`, and `aria-pressed` state to describe the current action and state.
- Toggling visibility preserves the input value, focus, validation state, and form submission behaviour.
- The control remains keyboard accessible and displays a visible focus indicator.

## Scope

The change is limited to the reset-password template, the shared authentication-page stylesheet, the authentication validation script, and relevant tests. Server-side password reset and validation logic remain unchanged.

## Verification

- Add a template-level regression test proving both inputs have independently targeted toggle buttons with accessible labels.
- Add a JavaScript behaviour test proving one toggle reveals and conceals only its own field while updating accessibility state.
- Run the authentication tests, JavaScript syntax/behaviour tests, and the complete Maven test suite.
- Inspect the localhost reset page at desktop and narrow viewport sizes and exercise both toggles using mouse and keyboard.
