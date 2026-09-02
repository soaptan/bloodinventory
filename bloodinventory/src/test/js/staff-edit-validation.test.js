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
const formStart = template.indexOf('<form id="staff-edit-form"');
const formEnd = template.indexOf("</form>", formStart);

assert.notEqual(formStart, -1, "Expected the staff edit form.");
assert.notEqual(formEnd, -1, "Expected the staff edit form to close.");

const editForm = template.slice(formStart, formEnd);

const tagById = (tagName, id) => {
    const match = editForm.match(new RegExp(`<${tagName}\\b(?=[^>]*\\bid="${id}")[^>]*>`, "u"));
    assert.ok(match, `Expected #${id} in the staff edit form.`);
    return match[0];
};

const attribute = (tag, name) => {
    const match = tag.match(new RegExp(`\\b${name}="([^"]*)"`, "u"));
    assert.ok(match, `Expected ${name} attribute in ${tag}.`);
    return match[1].replaceAll("&amp;", "&");
};

const isRequired = (tag) => /\srequired(?:\s|>)/u.test(tag);

test("staff edit identity and contact inputs expose detailed browser constraints", () => {
    const fullName = tagById("input", "modal-edit-full-name");
    const username = tagById("input", "modal-edit-username");
    const icNumber = tagById("input", "modal-edit-ic");
    const email = tagById("input", "modal-edit-email");
    const phone = tagById("input", "modal-edit-phone");

    assert.equal(attribute(fullName, "minlength"), "2");
    assert.equal(attribute(fullName, "maxlength"), "100");
    assert.equal(attribute(username, "minlength"), "4");
    assert.equal(attribute(username, "maxlength"), "50");
    assert.equal(attribute(icNumber, "maxlength"), "14");
    assert.equal(attribute(email, "maxlength"), "100");
    assert.equal(attribute(phone, "maxlength"), "20");
    assert.equal(attribute(phone, "inputmode"), "tel");
    assert.equal(attribute(phone, "autocomplete"), "tel");

    for (const tag of [fullName, username, icNumber, email, phone]) {
        assert.equal(isRequired(tag), true, `${attribute(tag, "id")} must be required.`);
    }

    for (const tag of [fullName, username, icNumber, phone]) {
        assert.doesNotThrow(() => new RegExp(`^(?:${attribute(tag, "pattern")})$`, "v"));
    }

    const phonePattern = new RegExp(`^(?:${attribute(phone, "pattern")})$`, "v");
    assert.equal(phonePattern.test("letters"), false);
    assert.equal(phonePattern.test("012-3456789"), true);
    assert.equal(phonePattern.test("+6012-3456789"), true);
});

test("staff edit password is optional but exposes policy and visibility controls", () => {
    const password = tagById("input", "modal-edit-password");
    const passwordPattern = new RegExp(`^(?:${attribute(password, "pattern")})$`, "v");
    const toggle = editForm.match(/<button\b(?=[^>]*\bdata-edit-password-toggle)[^>]*>/u);
    const rules = new Set(Array.from(
        editForm.matchAll(/\bdata-edit-password-rule="([^"]+)"/gu),
        (match) => match[1]
    ));

    assert.equal(isRequired(password), false);
    assert.equal(attribute(password, "minlength"), "8");
    assert.equal(attribute(password, "maxlength"), "72");
    assert.equal(passwordPattern.test(""), true);
    assert.equal(passwordPattern.test("NewSecurePassword2!"), true);
    assert.equal(passwordPattern.test("lowercase1!"), false);
    assert.equal(passwordPattern.test("Has Space1!"), false);
    assert.deepEqual(rules, new Set(["length", "letterCase", "number", "symbol", "noWhitespace"]));
    assert.ok(toggle, "Expected an edit-password visibility button.");
    assert.match(toggle[0], /\baria-controls="modal-edit-password"/u);
    assert.match(toggle[0], /\baria-label="Show password"/u);
});

test("staff edit role fields expose constraints and selected-role validation hooks", () => {
    const roleFields = [
        tagById("input", "modal-edit-license"),
        tagById("input", "modal-edit-position"),
        tagById("input", "modal-edit-certification"),
        tagById("input", "modal-edit-department")
    ];

    for (const tag of roleFields) {
        assert.doesNotThrow(() => new RegExp(`^(?:${attribute(tag, "pattern")})$`, "v"));
        assert.ok(attribute(tag, "title").length > 0);
    }

    assert.equal(attribute(roleFields[0], "maxlength"), "50");
    assert.equal(attribute(roleFields[1], "maxlength"), "50");
    assert.equal(attribute(roleFields[2], "maxlength"), "50");
    assert.equal(attribute(roleFields[3], "maxlength"), "100");
    assert.match(editForm, /\bdata-role-section="MEDICAL_STAFF"/u);
    assert.match(editForm, /\bdata-role-section="LAB_TECHNICIAN"/u);
    assert.match(editForm, /\bdata-role-section="BLOOD_ADMINISTRATOR"/u);
});

test("only the selected staff role panel is visible", () => {
    assert.match(
        components,
        /\.role-panel\s*\{[^}]*\bdisplay\s*:\s*none\s*;/su
    );
    assert.match(
        components,
        /\.role-panel\.is-visible\s*\{[^}]*\bdisplay\s*:\s*block\s*;/su
    );
});

test("staff edit form supports detailed client and restored server errors", () => {
    assert.match(editForm, /\snovalidate(?:\s|>)/u);
    assert.match(editForm, /class="[^"]*\bstaff-edit-validation-grid\b[^"]*"/u);
    assert.match(
        components,
        /\.staff-edit-validation-grid\s*\{[^}]*\balign-items\s*:\s*start\s*;/su
    );
    assert.match(template, /\bdata-staff-edit-server-errors\b/u);
    assert.match(template, /\bdata-staff-edit-server-error\b/u);
    assert.match(template, /const validateStaffEditField\s*=/u);
    assert.match(template, /staffEditForm\.addEventListener\("submit"/u);
});
