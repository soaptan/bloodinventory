const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");

const templatePath = path.resolve(
    __dirname,
    "../../main/resources/templates/staff-profiles.html"
);
const componentsPath = path.resolve(
    __dirname,
    "../../main/resources/static/css/components.css"
);
const template = fs.readFileSync(templatePath, "utf8");
const components = fs.readFileSync(componentsPath, "utf8");

const inputTag = (id) => {
    const match = template.match(new RegExp(`<input\\b(?=[^>]*\\bid="${id}")[^>]*>`, "u"));
    assert.ok(match, `Expected registration input #${id}.`);
    return match[0];
};

const attribute = (tag, name) => {
    const match = tag.match(new RegExp(`\\b${name}="([^"]*)"`, "u"));
    assert.ok(match, `Expected ${name} attribute in ${tag}.`);
    return match[1].replaceAll("&amp;", "&");
};

test("all staff registration patterns compile with browser Unicode rules", () => {
    const patternAttributes = Array.from(
        template.matchAll(/\bpattern="([^"]*)"/gu),
        (match) => match[1].replaceAll("&amp;", "&")
    );

    assert.ok(patternAttributes.length > 0, "Expected registration patterns in the template.");
    for (const pattern of patternAttributes) {
        assert.doesNotThrow(() => new RegExp(`^(?:${pattern})$`, "v"), pattern);
    }
});

test("phone input is required and rejects malformed values", () => {
    const tag = inputTag("register-phone");
    const phonePattern = new RegExp(`^(?:${attribute(tag, "pattern")})$`, "v");

    assert.match(tag, /\srequired(?:\s|>)/u);
    assert.equal(phonePattern.test("dwdwdwdwd"), false);
    assert.equal(phonePattern.test("011-123"), false);
    assert.equal(phonePattern.test("012-3456789"), true);
    assert.equal(phonePattern.test("+6012-3456789"), true);
});

test("password input exposes its policy and an accessible visibility toggle", () => {
    const tag = inputTag("register-password");
    const passwordPattern = new RegExp(`^(?:${attribute(tag, "pattern")})$`, "v");
    const toggle = template.match(/<button\b(?=[^>]*\bdata-register-password-toggle)[^>]*>/u);
    const rules = new Set(Array.from(
        template.matchAll(/\bdata-register-password-rule="([^"]+)"/gu),
        (match) => match[1]
    ));

    assert.equal(passwordPattern.test("SecurePassword123!"), true);
    assert.equal(passwordPattern.test("lowercase1!"), false);
    assert.equal(passwordPattern.test("UPPERCASE1!"), false);
    assert.equal(passwordPattern.test("NoNumber!"), false);
    assert.equal(passwordPattern.test("NoSymbol1"), false);
    assert.equal(passwordPattern.test("Has Space1!"), false);
    assert.deepEqual(rules, new Set(["length", "letterCase", "number", "symbol", "noWhitespace"]));
    assert.ok(toggle, "Expected a password visibility button.");
    assert.match(toggle[0], /\baria-controls="register-password"/u);
    assert.match(toggle[0], /\baria-label="Show password"/u);
    assert.match(toggle[0], /\baria-pressed="false"/u);
    assert.match(template, /\bdata-register-password-eye\b/u);
});

test("registration grid keeps neighboring fields at their intrinsic height", () => {
    const formStart = template.indexOf('<form th:action="@{/admin/staff/register}"');
    const formEnd = template.indexOf("</form>", formStart);

    assert.notEqual(formStart, -1, "Expected the staff registration form.");
    assert.notEqual(formEnd, -1, "Expected the staff registration form to close.");
    const registrationForm = template.slice(formStart, formEnd);

    assert.match(
        registrationForm,
        /class="[^"]*\bstaff-registration-grid\b[^"]*"/u,
        "Expected a registration-only grid hook."
    );
    assert.match(
        components,
        /\.staff-registration-grid\s*\{[^}]*\balign-items\s*:\s*start\s*;/su,
        "Expected registration fields to align at the top instead of stretching."
    );
});

test("registration role details use the approved department and clinical position dropdowns", () => {
    assert.match(template, /<select\b(?=[^>]*\bid="register-position")(?=[^>]*\bdata-clinical-position-select)[^>]*>/u);
    assert.match(template, /th:each="position : \$\{clinicalPositions\}"/u);
    assert.match(template, /<select\b(?=[^>]*\bid="register-department")(?=[^>]*\bdata-department-select)[^>]*>/u);
    assert.match(template, /th:each="department : \$\{administratorDepartments\}"/u);
});
