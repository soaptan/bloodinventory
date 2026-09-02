const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");

const projectRoot = path.resolve(__dirname, "../..");
const createTemplate = fs.readFileSync(
    path.join(projectRoot, "main/resources/templates/admin-deferral-rule-create.html"),
    "utf8"
);
const deferralTemplate = fs.readFileSync(
    path.join(projectRoot, "main/resources/templates/admin-deferral-rules.html"),
    "utf8"
);
const migration = fs.readFileSync(
    path.join(projectRoot, "main/resources/db/migration/V19__prevent_duplicate_deferral_rule_names.sql"),
    "utf8"
);

test("deferral rule create form renders duplicate description errors beside the field", () => {
    assert.match(createTemplate, /th:classappend="\$\{#fields\.hasErrors\('description'\)\} \? 'form-error' : ''"/u);
    assert.match(createTemplate, /th:errors="\*\{description\}"/u);
    assert.match(createTemplate, /Deferral rule name already exists\./u);
    assert.match(createTemplate, /maxlength="255"/u);
});

test("deferral create form renders as a staff registration modal overlay", () => {
    assert.match(createTemplate, /th:fragment="deferralRuleCreateModal"/u);
    assert.match(createTemplate, /class="modal-backdrop admin-create-backdrop"/u);
    assert.match(createTemplate, /data-deferral-rule-create-modal/u);
    assert.match(createTemplate, /openDeferralRuleCreateModal == true/u);
    assert.match(createTemplate, /class="card card-lg staff-register-modal admin-create-modal"/u);
    assert.match(createTemplate, /role="dialog"\s+aria-modal="true"/u);
    assert.match(createTemplate, /data-deferral-rule-create-close/u);
    assert.match(createTemplate, /class="staff-register-header admin-create-modal-header"/u);
    assert.match(createTemplate, /class="staff-register-content admin-create-modal-content"/u);
    assert.match(createTemplate, /class="form-grid form-grid-2 admin-create-form-grid"/u);
    assert.match(createTemplate, /class="form-label"/u);
    assert.match(createTemplate, /class="form-input"/u);
    assert.match(createTemplate, /class="form-select"/u);
    assert.match(createTemplate, /class="flex flex-between gap-md staff-register-footer"/u);
    assert.match(createTemplate, /data-lock-type-select/u);
    assert.match(deferralTemplate, /admin-deferral-rule-create :: deferralRuleCreateModal/u);
    assert.match(deferralTemplate, /\/js\/admin-create-modals\.js/u);
    assert.doesNotMatch(createTemplate, /standalone-admin-form-layout/u);
    assert.doesNotMatch(createTemplate, /admin-form-summary-list/u);
});

test("deferral rule database migration enforces normalized unique names", () => {
    assert.match(migration, /CREATE UNIQUE INDEX IF NOT EXISTS ux_deferral_reason_description_normalized/u);
    assert.match(migration, /LOWER\(BTRIM\(description\)\)/u);
    assert.match(migration, /RAISE EXCEPTION 'Deferral rule name already exists\.'/u);
});
