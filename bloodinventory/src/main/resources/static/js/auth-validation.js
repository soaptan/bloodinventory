document.querySelectorAll('[data-validate-form]').forEach((form) => {
    const fields = Array.from(form.querySelectorAll('input:not([type="hidden"])'));

    function messageFor(input) {
        const matchTarget = input.dataset.match && document.getElementById(input.dataset.match);
        if (matchTarget && input.value !== matchTarget.value) return 'Passwords do not match.';
        if (input.validity.valueMissing) return `${input.labels?.[0]?.textContent.trim() || 'This field'} is required.`;
        if (input.validity.typeMismatch) return 'Enter a valid email address.';
        if (input.validity.tooShort || input.validity.tooLong) {
            return `Use between ${input.minLength} and ${input.maxLength} characters.`;
        }
        if (input.validity.patternMismatch) {
            if (input.id === 'icNumber') return 'Enter a 12-digit IC number, for example 850101-10-2001.';
            if (input.id === 'verificationCode') return 'Enter the 6-digit verification code.';
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

    form.addEventListener('submit', (event) => {
        const firstInvalid = fields.find((field) => !validate(field));
        if (firstInvalid) {
            event.preventDefault();
            firstInvalid.focus();
        }
    });
});
