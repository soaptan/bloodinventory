const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");

const modalTemplate = fs.readFileSync(
    path.resolve(__dirname, "../../main/resources/templates/medical-donation-record.html"),
    "utf8"
);
const pageTemplate = fs.readFileSync(
    path.resolve(__dirname, "../../main/resources/templates/medical-donations.html"),
    "utf8"
);
const workflowStyles = fs.readFileSync(
    path.resolve(__dirname, "../../main/resources/static/css/medical-workflow.css"),
    "utf8"
);

const tagForId = (id) => {
    const match = modalTemplate.match(new RegExp(`<(?:input|select)\\b(?=[^>]*\\bid="${id}")[^>]*>`, "u"));
    assert.ok(match, `Expected field #${id}.`);
    return match[0];
};

test("donation form applies browser constraints to every required collection field", () => {
    assert.match(tagForId("donorId"), /\srequired(?:\s|>)/u);
    assert.match(tagForId("locationId"), /\srequired(?:\s|>)/u);
    assert.match(tagForId("collectionTimestamp"), /\srequired(?:\s|>)/u);
    assert.match(tagForId("collectionTimestamp"), /data-current-or-past-datetime/u);
    assert.match(modalTemplate, /th:errors="\*\{componentTypes\}"/u);
});

test("donation timestamp maximum is refreshed in the browser before validation", () => {
    assert.match(pageTemplate, /\[data-current-or-past-datetime\]/u);
    assert.match(pageTemplate, /setAttribute\("max",/u);
});

test("collection donor search rebuilds native options instead of hiding them", () => {
    assert.match(pageTemplate, /const donorPlaceholder[\s\S]*?cloneNode\(true\)/u);
    assert.match(pageTemplate, /const donorOptions[\s\S]*?filter\(\(option\) => option\.value && !option\.disabled\)[\s\S]*?map\(\(option\) => option\.cloneNode\(true\)\)/u);
    assert.match(pageTemplate, /donorSelect\.replaceChildren\(/u);
    assert.match(pageTemplate, /const matchingSelectedOption = matchingOptions\.find\(\(option\) => option\.value === selectedValue\)/u);
    assert.match(pageTemplate, /const nextDonorValue = matchingSelectedOption\?\.value \|\| \(query \? matchingOptions\[0\]\?\.value : ""\)/u);
    assert.match(pageTemplate, /donorSelect\.value = nextDonorValue/u);
    assert.doesNotMatch(pageTemplate, /option\.hidden = !matches/u);
});

test("deferred donors cannot be selected for collection", () => {
    assert.match(modalTemplate, /th:disabled="\$\{!donor\.eligible\}"/u);
    assert.match(pageTemplate, /option\.value && !option\.disabled/u);
    assert.match(modalTemplate, /Please select an eligible donor\./u);
});

test("component selector supports individual, multiple, and select-all choices", () => {
    assert.match(modalTemplate, /data-component-select-all/u);
    assert.match(modalTemplate, /id="componentTypesChoices"/u);
    assert.match(modalTemplate, /data-required-checkbox-group/u);
    assert.match(pageTemplate, /\[data-component-select-all\]/u);
    assert.match(pageTemplate, /componentInputs\.forEach\(\(input\) => \{/u);
    assert.match(pageTemplate, /componentSelectAll\.indeterminate/u);
    assert.match(workflowStyles, /\.check-chip input\[type="checkbox"\]\s*\{[\s\S]*?width: 16px;[\s\S]*?min-height: 16px;[\s\S]*?padding: 0;[\s\S]*?\}/u);
    assert.match(workflowStyles, /\.component-select-all\s*\{[\s\S]*?border-radius: 10px;[\s\S]*?white-space: nowrap;[\s\S]*?\}/u);
});

test("component validation shows the message without a red selection box", () => {
    assert.doesNotMatch(workflowStyles, /\.choice-row\.is-invalid,[\s\S]*?background: rgba\(239, 68, 68, 0\.08\)/u);
    assert.doesNotMatch(workflowStyles, /\.choice-row\.is-invalid,[\s\S]*?border: 1px solid #ef4444/u);
    assert.doesNotMatch(workflowStyles, /body\.theme-dark \.choice-row\.is-invalid,[\s\S]*?background: rgba\(239, 68, 68, 0\.1\)/u);
});
