document.querySelectorAll('[data-validate-form]').forEach((form) => {
    const fields = Array.from(form.querySelectorAll('input:not([type="hidden"])'));
    const passwordInput = form.querySelector('#newPassword');
    const passwordRequirements = form.querySelector('[data-password-requirements]');
    const visibleEye = '<path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"></path><circle cx="12" cy="12" r="3"></circle>';
    const hiddenEye = '<path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"></path><line x1="1" y1="1" x2="23" y2="23"></line>';

    form.querySelectorAll('[data-password-toggle]').forEach((toggle) => {
        if (!(toggle instanceof HTMLButtonElement)) return;

        toggle.addEventListener('click', () => {
            const input = document.getElementById(toggle.dataset.passwordToggle);
            if (!(input instanceof HTMLInputElement) || !form.contains(input)) return;

            const willShow = input.type === 'password';
            const fieldName = input.id === 'confirmPassword' ? 'confirmed password' : 'new password';
            input.type = willShow ? 'text' : 'password';
            toggle.setAttribute('aria-pressed', String(willShow));
            toggle.setAttribute('aria-label', `${willShow ? 'Hide' : 'Show'} ${fieldName}`);
            toggle.title = `${willShow ? 'Hide' : 'Show'} ${fieldName}`;
            const icon = toggle.querySelector('[data-password-eye]');
            if (icon instanceof SVGElement) icon.innerHTML = willShow ? hiddenEye : visibleEye;
            input.focus();
        });
    });

    function utf8Length(value) {
        return new TextEncoder().encode(value).length;
    }

    function updatePasswordRequirements() {
        if (!passwordInput || !passwordRequirements) return;

        const value = passwordInput.value;
        const ruleResults = {
            length: value.length >= 8 && value.length <= 72,
            byteLength: value.length > 0 && utf8Length(value) <= 72,
            letterCase: /[a-z]/.test(value) && /[A-Z]/.test(value),
            number: /[0-9]/.test(value),
            symbol: /[^A-Za-z0-9\s]/.test(value),
            noWhitespace: value.length > 0 && !/\s/.test(value)
        };

        passwordRequirements.querySelectorAll('[data-password-rule]').forEach((item) => {
            const isMet = Boolean(ruleResults[item.dataset.passwordRule]);
            item.classList.toggle('is-met', isMet);
            item.setAttribute('data-requirement-met', String(isMet));
        });
    }

    function messageFor(input) {
        const matchTarget = input.dataset.match && document.getElementById(input.dataset.match);
        if (matchTarget && input.value !== matchTarget.value) return 'Passwords do not match.';
        if (input.validity.valueMissing) return `${input.labels?.[0]?.textContent.trim() || 'This field'} is required.`;
        if (input.id === 'newPassword' && utf8Length(input.value) > 72) {
            return 'Password must not exceed 72 bytes.';
        }
        if (input.validity.typeMismatch) return 'Enter a valid email address.';
        if (input.validity.tooShort || input.validity.tooLong) {
            return `Use between ${input.minLength} and ${input.maxLength} characters.`;
        }
        if (input.validity.patternMismatch) {
            if (input.id === 'icNumber') return 'Enter a 12-digit IC number, for example 850101-10-2001.';
            if (input.id === 'newPassword') {
                return 'Use uppercase and lowercase letters, a number, and a special character, with no spaces.';
            }
            return 'Use only letters, numbers, dots, underscores, or hyphens.';
        }
        return '';
    }

    function validate(input) {
        const message = messageFor(input);
        const error = document.getElementById(`${input.id}-error`);
        input.setAttribute('aria-invalid', String(Boolean(message)));
        input.closest('.form-group')?.classList.toggle('has-error', Boolean(message));
        if (error) error.textContent = message;
        return !message;
    }

    fields.forEach((field) => {
        field.setAttribute('aria-describedby', `${field.id}-error`);
        field.addEventListener('blur', () => validate(field));
        field.addEventListener('input', () => {
            if (field.getAttribute('aria-invalid') === 'true') validate(field);
        });
    });

    if (passwordInput) {
        passwordInput.addEventListener('input', updatePasswordRequirements);
        updatePasswordRequirements();
    }

    form.addEventListener('submit', (event) => {
        const firstInvalid = fields.find((field) => !validate(field));
        if (firstInvalid) {
            event.preventDefault();
            firstInvalid.focus();
        }
    });
});
