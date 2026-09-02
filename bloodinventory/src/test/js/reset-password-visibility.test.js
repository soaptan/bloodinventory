const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");

const templatePath = path.resolve(
    __dirname,
    "../../main/resources/templates/reset-password.html"
);
const validationPath = path.resolve(
    __dirname,
    "../../main/resources/static/js/auth-validation.js"
);
const template = fs.readFileSync(templatePath, "utf8");
const validation = fs.readFileSync(validationPath, "utf8");

const inputTag = (id) => {
    const match = template.match(new RegExp(`<input\\b(?=[^>]*\\bid="${id}")[^>]*>`, "u"));
    assert.ok(match, `Expected reset-password input #${id}.`);
    return match[0];
};

const toggleTag = (inputId) => {
    const match = template.match(
        new RegExp(`<button\\b(?=[^>]*\\bdata-password-toggle="${inputId}")[^>]*>`, "u")
    );
    assert.ok(match, `Expected a visibility toggle for #${inputId}.`);
    return match[0];
};

test("reset password fields expose accessible visibility toggles", () => {
    for (const inputId of ["newPassword", "confirmPassword"]) {
        assert.match(inputTag(inputId), /\btype="password"/u);
        const toggle = toggleTag(inputId);
        assert.match(toggle, new RegExp(`\\baria-controls="${inputId}"`, "u"));
        assert.match(toggle, /\baria-label="Show /u);
        assert.match(toggle, /\baria-pressed="false"/u);
    }

    assert.equal(
        (template.match(/\bdata-password-eye\b/gu) || []).length,
        2,
        "Expected an eye icon for each reset password field."
    );
});

test("authentication validation toggles reset password visibility", () => {
    assert.match(validation, /querySelectorAll\('[^']*data-password-toggle[^']*'\)/u);
    assert.match(validation, /input\.type\s*=\s*willShow\s*\?\s*['"]text['"]\s*:\s*['"]password['"]/u);
    assert.match(validation, /aria-pressed.*String\(willShow\)/u);
});
