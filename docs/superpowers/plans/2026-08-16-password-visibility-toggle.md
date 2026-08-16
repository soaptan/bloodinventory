# Password Visibility Toggle Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add accessible, independently controlled eye buttons to the new-password and confirmation fields on the reset-password page.

**Architecture:** The Thymeleaf template provides a native button beside each password input and identifies its target with `data-password-toggle`. The existing authentication script attaches one click handler per button and updates only the targeted input and that button's accessibility/icon state; the shared authentication stylesheet positions the buttons without changing server-side form processing.

**Tech Stack:** Spring Boot 3.5, Thymeleaf, HTML/CSS, browser JavaScript, JUnit 5/MockMvc, Node.js built-in test runner.

## Global Constraints

- Both password fields start concealed and have independently controlled visibility buttons.
- Use inline SVG icons and add no external icon or JavaScript dependency.
- Each toggle must update its icon, `aria-label`, `title`, and `aria-pressed` value.
- Toggling must preserve the field value, focus, validation state, and submitted field name.
- Keep all server-side password reset and password validation behaviour unchanged.
- Keep the controls keyboard accessible with a visible focus indicator.

---

### Task 1: Accessible Toggle Markup and Layout

**Files:**
- Modify: `bloodinventory/src/test/java/com/fyp/bloodinventory/AuthenticationFlowTests.java:217-244`
- Modify: `bloodinventory/src/main/resources/templates/reset-password.html:24-50`
- Modify: `bloodinventory/src/main/resources/static/css/auth-pages.css:83-124`

**Interfaces:**
- Consumes: Existing `newPassword` and `confirmPassword` input IDs and `.form-group` styling.
- Produces: `.password-input-wrap`, `.password-visibility-toggle`, `data-password-toggle="<input-id>"`, `data-icon-show`, and `data-icon-hide` hooks used by Task 2.

- [ ] **Step 1: Write the failing template regression test**

Add this test to `AuthenticationFlowTests`:

```java
@Test
void resetPasswordPageHasAccessibleIndependentVisibilityToggles() throws Exception {
    MockHttpSession verifiedSession = new MockHttpSession();
    verifiedSession.setAttribute("passwordResetStaffId", 1L);
    verifiedSession.setAttribute("passwordResetVerifiedAt", System.currentTimeMillis());

    mockMvc.perform(get("/reset-password").session(verifiedSession))
            .andExpect(expect(status().isOk()))
            .andExpect(expect(content().string(containsString(
                    "data-password-toggle=\"newPassword\""))))
            .andExpect(expect(content().string(containsString(
                    "data-password-toggle=\"confirmPassword\""))))
            .andExpect(expect(content().string(containsString(
                    "aria-label=\"Show new password\""))))
            .andExpect(expect(content().string(containsString(
                    "aria-label=\"Show confirmed password\""))))
            .andExpect(expect(content().string(containsString(
                    "aria-pressed=\"false\""))));
}
```

- [ ] **Step 2: Run the test and verify the missing controls cause failure**

Run:

```powershell
$env:BLOODINVENTORY_JAVA_HOME='C:\Users\User\.jdk\jdk-25.0.2'
.\mvnw.cmd -q '-Dtest=AuthenticationFlowTests#resetPasswordPageHasAccessibleIndependentVisibilityToggles' test
```

Expected: FAIL because the response does not contain `data-password-toggle="newPassword"`.

- [ ] **Step 3: Add the two independently targeted buttons**

Wrap each existing input in `reset-password.html` with this structure, using the matching input ID and accessible label for each field:

```html
<div class="password-input-wrap">
    <input id="newPassword" name="newPassword" type="password" autocomplete="new-password"
           required minlength="8" maxlength="72"
           pattern="(?=.*[a-z])(?=.*[A-Z])(?=.*[0-9])(?=.*[^A-Za-z0-9\s])\S{8,72}"
           title="Use uppercase and lowercase letters, a number, and a special character, with no spaces.">
    <button type="button" class="password-visibility-toggle"
            data-password-toggle="newPassword" aria-controls="newPassword"
            aria-label="Show new password" title="Show new password" aria-pressed="false">
        <svg data-icon-show aria-hidden="true" viewBox="0 0 24 24">
            <path d="M2 12s3.5-6 10-6 10 6 10 6-3.5 6-10 6S2 12 2 12Z"></path>
            <circle cx="12" cy="12" r="3"></circle>
        </svg>
        <svg data-icon-hide aria-hidden="true" viewBox="0 0 24 24" hidden>
            <path d="m3 3 18 18"></path>
            <path d="M10.6 6.2A10.8 10.8 0 0 1 12 6c6.5 0 10 6 10 6a17.8 17.8 0 0 1-2.1 2.8M6.6 6.6C3.6 8.4 2 12 2 12s3.5 6 10 6a10.7 10.7 0 0 0 4.1-.8M9.9 9.9a3 3 0 0 0 4.2 4.2"></path>
        </svg>
    </button>
</div>
```

Repeat the structure for `confirmPassword`, changing the target to `confirmPassword` and the label/title to `Show confirmed password`. Preserve every existing input attribute and field-error element.

- [ ] **Step 4: Add layout, hover, and focus-visible styling**

Add focused styles to `auth-pages.css`:

```css
.password-input-wrap {
    position: relative;
}

.form-group .password-input-wrap input {
    padding-right: 3.25rem;
}

.password-visibility-toggle {
    position: absolute;
    top: 50%;
    right: 0.75rem;
    width: 2.25rem;
    height: 2.25rem;
    padding: 0;
    border: 0;
    border-radius: 0.5rem;
    background: transparent;
    color: #426487;
    display: inline-grid;
    place-items: center;
    transform: translateY(-50%);
    cursor: pointer;
}

.password-visibility-toggle:hover {
    background: #edf5ff;
    color: #0d5baa;
}

.password-visibility-toggle:focus-visible {
    outline: 3px solid rgba(37, 99, 235, 0.3);
    outline-offset: 2px;
}

.password-visibility-toggle svg {
    width: 1.25rem;
    height: 1.25rem;
    fill: none;
    stroke: currentColor;
    stroke-width: 1.8;
    stroke-linecap: round;
    stroke-linejoin: round;
}

.password-visibility-toggle svg[hidden] {
    display: none;
}
```

- [ ] **Step 5: Run the template regression test**

Run the command from Step 2.

Expected: PASS with 1 test and no failures or errors.

- [ ] **Step 6: Commit the accessible markup and layout**

```powershell
git add -- src/main/resources/templates/reset-password.html src/main/resources/static/css/auth-pages.css src/test/java/com/fyp/bloodinventory/AuthenticationFlowTests.java
git commit -m "feat: add password visibility controls"
```

### Task 2: Independent Visibility Behaviour

**Files:**
- Create: `bloodinventory/src/test/js/password-visibility.test.js`
- Modify: `bloodinventory/src/main/resources/static/js/auth-validation.js:1-80`

**Interfaces:**
- Consumes: The Task 1 button hooks `data-password-toggle`, `data-icon-show`, and `data-icon-hide` and the existing target input IDs.
- Produces: A click handler that switches one `HTMLInputElement.type` and updates only its associated `HTMLButtonElement` state.

- [ ] **Step 1: Write the failing JavaScript behaviour test**

Create `src/test/js/password-visibility.test.js`:

```javascript
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

function visibilityFixture(targetId) {
    const input = { id: targetId, type: 'password', value: 'Secret123!' };
    const showIcon = { hidden: false };
    const hideIcon = { hidden: true };
    const attributes = new Map([
        ['aria-label', targetId === 'newPassword' ? 'Show new password' : 'Show confirmed password'],
        ['title', targetId === 'newPassword' ? 'Show new password' : 'Show confirmed password'],
        ['aria-pressed', 'false']
    ]);
    let clickHandler;
    const toggle = {
        dataset: { passwordToggle: targetId },
        addEventListener(eventName, handler) {
            if (eventName === 'click') clickHandler = handler;
        },
        getAttribute(name) { return attributes.get(name); },
        setAttribute(name, value) { attributes.set(name, value); },
        querySelector(selector) {
            if (selector === '[data-icon-show]') return showIcon;
            if (selector === '[data-icon-hide]') return hideIcon;
            return null;
        },
        click() { clickHandler(); }
    };
    return { input, toggle, showIcon, hideIcon };
}

test('each password toggle reveals and conceals only its target field', () => {
    const first = visibilityFixture('newPassword');
    const second = visibilityFixture('confirmPassword');
    const inputs = new Map([[first.input.id, first.input], [second.input.id, second.input]]);
    const document = {
        querySelectorAll(selector) {
            if (selector === '[data-validate-form]') return [];
            if (selector === '[data-password-toggle]') return [first.toggle, second.toggle];
            return [];
        },
        getElementById(id) { return inputs.get(id) || null; }
    };
    const source = fs.readFileSync(
        path.resolve(__dirname, '../../main/resources/static/js/auth-validation.js'), 'utf8');

    vm.runInNewContext(source, { document, TextEncoder });
    first.toggle.click();

    assert.equal(first.input.type, 'text');
    assert.equal(second.input.type, 'password');
    assert.equal(first.input.value, 'Secret123!');
    assert.equal(first.toggle.getAttribute('aria-label'), 'Hide new password');
    assert.equal(first.toggle.getAttribute('title'), 'Hide new password');
    assert.equal(first.toggle.getAttribute('aria-pressed'), 'true');
    assert.equal(first.showIcon.hidden, true);
    assert.equal(first.hideIcon.hidden, false);

    first.toggle.click();
    assert.equal(first.input.type, 'password');
    assert.equal(first.toggle.getAttribute('aria-label'), 'Show new password');
    assert.equal(first.toggle.getAttribute('aria-pressed'), 'false');
    assert.equal(first.showIcon.hidden, false);
    assert.equal(first.hideIcon.hidden, true);
});
```

- [ ] **Step 2: Run the behaviour test and verify it fails because no handler exists**

Run:

```powershell
node --test src/test/js/password-visibility.test.js
```

Expected: FAIL when `first.toggle.click()` cannot invoke a registered click handler.

- [ ] **Step 3: Implement the minimal independent toggle handler**

Append this focused initializer to `auth-validation.js`:

```javascript
document.querySelectorAll('[data-password-toggle]').forEach((toggle) => {
    const input = document.getElementById(toggle.dataset.passwordToggle);
    if (!input) return;

    const showIcon = toggle.querySelector('[data-icon-show]');
    const hideIcon = toggle.querySelector('[data-icon-hide]');
    const fieldName = toggle.dataset.passwordToggle === 'confirmPassword'
        ? 'confirmed password'
        : 'new password';

    toggle.addEventListener('click', () => {
        const isVisible = input.type === 'password';
        input.type = isVisible ? 'text' : 'password';
        const action = isVisible ? 'Hide' : 'Show';
        toggle.setAttribute('aria-label', `${action} ${fieldName}`);
        toggle.setAttribute('title', `${action} ${fieldName}`);
        toggle.setAttribute('aria-pressed', String(isVisible));
        if (showIcon) showIcon.hidden = isVisible;
        if (hideIcon) hideIcon.hidden = !isVisible;
    });
});
```

- [ ] **Step 4: Run JavaScript behaviour and syntax verification**

Run:

```powershell
node --test src/test/js/password-visibility.test.js
node --check src/main/resources/static/js/auth-validation.js
```

Expected: 1 passing Node test and no syntax errors.

- [ ] **Step 5: Run focused and complete server test suites**

Run:

```powershell
$env:BLOODINVENTORY_JAVA_HOME='C:\Users\User\.jdk\jdk-25.0.2'
.\mvnw.cmd -q -Dtest=AuthenticationFlowTests test
.\mvnw.cmd test
```

Expected: Authentication tests pass; complete Maven suite reports `BUILD SUCCESS` with no failures or errors.

- [ ] **Step 6: Verify the localhost interaction and responsive layout**

Open `http://localhost:8082/reset-password` through a freshly verified session. At desktop and a narrow viewport:

1. Confirm both eye buttons are visible inside their fields without covering entered text.
2. Use mouse and keyboard activation on each button.
3. Confirm each button reveals and conceals only its own field.
4. Confirm the visible focus indicator, icon swap, and accessible name update.
5. Confirm password validation and form submission still behave as before; do not submit a real password reset during the smoke test.

- [ ] **Step 7: Commit the behaviour and regression test**

```powershell
git add -- src/main/resources/static/js/auth-validation.js src/test/js/password-visibility.test.js
git commit -m "feat: toggle password field visibility"
```
