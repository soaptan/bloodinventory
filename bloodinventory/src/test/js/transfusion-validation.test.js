const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");

const modalTemplate = fs.readFileSync(
    path.resolve(__dirname, "../../main/resources/templates/medical-transfusion-record.html"),
    "utf8"
);
const pageTemplate = fs.readFileSync(
    path.resolve(__dirname, "../../main/resources/templates/medical-transfusion.html"),
    "utf8"
);

const tagForId = (id) => {
    const match = modalTemplate.match(new RegExp(`<(?:input|select)\\b(?=[^>]*\\bid="${id}")[^>]*>`, "u"));
    assert.ok(match, `Expected field #${id}.`);
    return match[0];
};

test("transfusion form exposes matching browser constraints and field errors", () => {
    assert.match(modalTemplate, /name="patientMode"[\s\S]*value="existing"/u);
    assert.match(modalTemplate, /name="patientMode"[\s\S]*value="new"/u);
    assert.match(tagForId("patientId"), /data-required-when-active/u);
    assert.match(tagForId("patientId"), /#fields\.hasErrors\('patientId'\)/u);
    assert.match(tagForId("patientName"), /minlength="2"/u);
    assert.match(tagForId("patientName"), /maxlength="100"/u);
    assert.match(tagForId("patientName"), /pattern="/u);
    assert.match(tagForId("patientName"), /#fields\.hasErrors\('patientName'\)/u);
    assert.match(tagForId("condition"), /maxlength="200"/u);
    assert.match(tagForId("condition"), /#fields\.hasErrors\('condition'\)/u);
    assert.match(tagForId("componentId"), /\srequired(?:\s|>)/u);
    assert.match(tagForId("componentId"), /#fields\.hasErrors\('componentId'\)/u);
    assert.match(modalTemplate, /th:errors="\*\{patientId\}"/u);
    assert.match(modalTemplate, /th:errors="\*\{patientName\}"/u);
    assert.match(modalTemplate, /th:errors="\*\{condition\}"/u);
    assert.match(modalTemplate, /th:errors="\*\{componentId\}"/u);
});

test("transfusion patient mode preserves conditional panel state after a redirect", () => {
    assert.match(modalTemplate, /transfusionRequest\.patientMode == 'existing'/u);
    assert.match(modalTemplate, /transfusionRequest\.patientMode == 'new'/u);
    assert.match(pageTemplate, /data-required-when-active/u);
    assert.match(pageTemplate, /field\.disabled = !isActive/u);
    assert.match(pageTemplate, /field\.required = isActive/u);
});
