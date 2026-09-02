const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");

const templatePath = path.resolve(
    __dirname,
    "../../main/resources/templates/medical-donor-assessment.html"
);
const eligibilityTemplatePath = path.resolve(
    __dirname,
    "../../main/resources/templates/medical-donor-eligibility.html"
);
const componentsStylesPath = path.resolve(
    __dirname,
    "../../main/resources/static/css/components.css"
);
const template = fs.readFileSync(templatePath, "utf8");
const eligibilityTemplate = fs.readFileSync(eligibilityTemplatePath, "utf8");
const componentsStyles = fs.readFileSync(componentsStylesPath, "utf8");

const inputTag = (id) => {
    const match = template.match(new RegExp(`<input\\b(?=[^>]*\\bid="${id}")[^>]*>`, "u"));
    assert.ok(match, `Expected input #${id}.`);
    return match[0];
};

const attribute = (tag, name) => {
    const match = tag.match(new RegExp(`\\b${name}="([^"]*)"`, "u"));
    assert.ok(match, `Expected ${name} on ${tag}.`);
    return match[1];
};

test("donor identity fields reject malformed browser input", () => {
    const fullName = inputTag("fullName");
    const icNumber = inputTag("icNumber");
    const fullNamePattern = new RegExp(`^(?:${attribute(fullName, "pattern")})$`, "v");
    const icNumberPattern = new RegExp(`^(?:${attribute(icNumber, "pattern")})$`, "v");

    assert.match(fullName, /\srequired(?:\s|>)/u);
    assert.equal(attribute(fullName, "minlength"), "2");
    assert.equal(attribute(fullName, "maxlength"), "100");
    assert.equal(fullNamePattern.test("Nur A/P Ali"), true);
    assert.equal(fullNamePattern.test("John123"), false);
    assert.equal(icNumberPattern.test("900101-10-1234"), true);
    assert.equal(icNumberPattern.test("900101101234"), false);
});

test("deferral selectors are required and retain field-error hooks", () => {
    const donorSelect = template.match(/<select\b(?=[^>]*\bid="donorId")[^>]*>/u);
    const reasonSelect = template.match(/<select\b(?=[^>]*\bid="reasonId")[^>]*>/u);

    assert.ok(donorSelect);
    assert.ok(reasonSelect);
    assert.match(donorSelect[0], /\srequired(?:\s|>)/u);
    assert.match(reasonSelect[0], /\srequired(?:\s|>)/u);
    assert.match(template, /th:errors="\*\{donorId\}"/u);
    assert.match(template, /th:errors="\*\{reasonId\}"/u);
});

test("donor search rebuilds native options instead of hiding them", () => {
    assert.match(eligibilityTemplate, /const deferralDonorPlaceholder[\s\S]*?cloneNode\(true\)/u);
    assert.match(eligibilityTemplate, /const deferralDonorOptions[\s\S]*?filter\(\(option\) => option\.value && !option\.disabled\)[\s\S]*?map\(\(option\) => option\.cloneNode\(true\)\)/u);
    assert.match(eligibilityTemplate, /deferralDonorSelect\.replaceChildren\(/u);
    assert.match(eligibilityTemplate, /const matchingSelectedOption = matchingOptions\.find\(\(option\) => option\.value === selectedValue\)/u);
    assert.match(eligibilityTemplate, /const nextDeferralDonorValue = matchingSelectedOption\?\.value \|\| \(query \? matchingOptions\[0\]\?\.value : ""\)/u);
    assert.match(eligibilityTemplate, /deferralDonorSelect\.value = nextDeferralDonorValue/u);
    assert.doesNotMatch(eligibilityTemplate, /option\.hidden = !matches/u);
});

test("invalid dark-theme selects keep readable contrast", () => {
    assert.match(
        componentsStyles,
        /body\.theme-dark \.form-group select\.is-invalid,[\s\S]*?body\.theme-dark \.needs-validation\.was-validated \.form-group select:invalid \{[\s\S]*?background: #0a1622;[\s\S]*?color: var\(--text-strong\);/u
    );
});
