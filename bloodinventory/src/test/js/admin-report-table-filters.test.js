const test = require("node:test");
const assert = require("node:assert/strict");

const {
    filterAndSortRecords
} = require("../../main/resources/static/js/admin-report-table-filters.js");

test("filters by every active field and sorts numeric values in descending order", () => {
    const records = [
        { id: "rbc-available", values: { "component-type": "RBC", status: "AVAILABLE", units: "12" } },
        { id: "plasma-available", values: { "component-type": "PLASMA", status: "AVAILABLE", units: "30" } },
        { id: "rbc-reserved", values: { "component-type": "RBC", status: "RESERVED", units: "20" } },
        { id: "rbc-available-low", values: { "component-type": "RBC", status: "AVAILABLE", units: "4" } }
    ];

    const result = filterAndSortRecords(
            records,
            [
                { key: "component-type", value: "rbc", mode: "exact" },
                { key: "status", value: "available", mode: "exact" }
            ],
            { key: "units", type: "number", direction: "desc" }
    );

    assert.deepEqual(result.map((record) => record.id), ["rbc-available", "rbc-available-low"]);
});

test("matches text without case sensitivity and sorts timestamps from earliest to latest", () => {
    const records = [
        { id: "later", values: { module: "Authentication", time: "2026-08-29T10:45:00" } },
        { id: "unrelated", values: { module: "Inventory", time: "2026-08-29T08:00:00" } },
        { id: "earlier", values: { module: "AUTHENTICATION SECURITY", time: "2026-08-29T09:15:00" } }
    ];

    const result = filterAndSortRecords(
            records,
            [{ key: "module", value: "auth", mode: "contains" }],
            { key: "time", type: "date", direction: "asc" }
    );

    assert.deepEqual(result.map((record) => record.id), ["earlier", "later"]);
});
