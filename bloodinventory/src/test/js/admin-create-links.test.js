const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");

const templatesRoot = path.resolve(__dirname, "../../main/resources/templates");
const componentsPath = path.resolve(__dirname, "../../main/resources/static/css/components.css");
const storageTemplate = fs.readFileSync(path.join(templatesRoot, "admin-storage.html"), "utf8");
const deferralTemplate = fs.readFileSync(path.join(templatesRoot, "admin-deferral-rules.html"), "utf8");
const components = fs.readFileSync(componentsPath, "utf8");

const createLink = (template, marker) => {
    const match = template.match(new RegExp(`<a\\b(?=[^>]*\\b${marker})[^>]*>`, "u"));
    assert.ok(match, `Expected the ${marker} create link.`);
    return match[0];
};

test("storage and deferral create links use a visible dedicated action style", () => {
    const storageLink = createLink(storageTemplate, "data-create-storage-link");
    const deferralLink = createLink(deferralTemplate, "data-create-deferral-rule-link");

    assert.match(storageLink, /\btable-create-action\b/u);
    assert.match(deferralLink, /\btable-create-action\b/u);
    assert.match(storageLink, /th:href="@\{\/admin\/storage\/create\}"/u);
    assert.match(deferralLink, /th:href="@\{\/admin\/deferral-rules\/create\}"/u);
    assert.match(components, /\.table-create-action\s*\{[^}]*\bcolor\s*:\s*#fff\s*!important\s*;/su);
    assert.match(components, /\.table-create-action\s*\{[^}]*\bopacity\s*:\s*1\s*!important\s*;/su);
    assert.match(components, /\.table-create-action\s*\{[^}]*\bpointer-events\s*:\s*auto\s*!important\s*;/su);
});
