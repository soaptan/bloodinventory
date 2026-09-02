const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");

const projectRoot = path.resolve(__dirname, "../..");
const createTemplate = fs.readFileSync(
    path.join(projectRoot, "main/resources/templates/admin-storage-create.html"),
    "utf8"
);
const storageTemplate = fs.readFileSync(
    path.join(projectRoot, "main/resources/templates/admin-storage.html"),
    "utf8"
);
const modalScript = fs.readFileSync(
    path.join(projectRoot, "main/resources/static/js/admin-create-modals.js"),
    "utf8"
);
const migration = fs.readFileSync(
    path.join(projectRoot, "main/resources/db/migration/V18__prevent_duplicate_storage_location_names.sql"),
    "utf8"
);

test("storage create form renders duplicate description errors beside the field", () => {
    assert.match(createTemplate, /th:classappend="\$\{#fields\.hasErrors\('description'\)\} \? 'form-error' : ''"/u);
    assert.match(createTemplate, /th:errors="\*\{description\}"/u);
    assert.match(createTemplate, /Storage location name already exists\./u);
    assert.match(createTemplate, /maxlength="255"/u);
});

test("storage create form renders as a staff registration modal overlay", () => {
    assert.match(createTemplate, /th:fragment="storageCreateModal"/u);
    assert.match(createTemplate, /class="modal-backdrop admin-create-backdrop"/u);
    assert.match(createTemplate, /data-storage-create-modal/u);
    assert.match(createTemplate, /openStorageCreateModal == true/u);
    assert.match(createTemplate, /class="card card-lg staff-register-modal admin-create-modal"/u);
    assert.match(createTemplate, /role="dialog"\s+aria-modal="true"/u);
    assert.match(createTemplate, /data-storage-create-close/u);
    assert.match(createTemplate, /class="staff-register-header admin-create-modal-header"/u);
    assert.match(createTemplate, /class="staff-register-content admin-create-modal-content"/u);
    assert.match(createTemplate, /class="form-grid form-grid-2 admin-create-form-grid"/u);
    assert.match(createTemplate, /class="form-label"/u);
    assert.match(createTemplate, /class="form-input"/u);
    assert.match(createTemplate, /class="flex flex-between gap-md staff-register-footer"/u);
    assert.match(storageTemplate, /admin-storage-create :: storageCreateModal/u);
    assert.match(storageTemplate, /\/js\/admin-create-modals\.js/u);
    assert.doesNotMatch(createTemplate, /standalone-admin-form-layout/u);
    assert.doesNotMatch(createTemplate, /admin-form-summary-list/u);
});

test("storage database migration enforces normalized unique names", () => {
    assert.match(migration, /CREATE UNIQUE INDEX IF NOT EXISTS ux_storage_location_description_normalized/u);
    assert.match(migration, /LOWER\(BTRIM\(description\)\)/u);
    assert.match(migration, /RAISE EXCEPTION 'Storage location name already exists\.'/u);
});

test("create modal script supports close behavior and permanent deferral locks", () => {
    assert.match(modalScript, /data-admin-create-modal/u);
    assert.match(modalScript, /event\.key !== "Escape"/u);
    assert.match(modalScript, /event\.target === modal/u);
    assert.match(modalScript, /document\.body\.style\.overflow/u);
    assert.match(modalScript, /data-lock-type-select/u);
    assert.match(modalScript, /coolingInput\.disabled = isPermanent/u);
    assert.match(modalScript, /Not used for permanent/u);
});
