const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");

const layoutPath = path.resolve(__dirname, "../../main/resources/static/css/dashboard-layout.css");
const layoutStyles = fs.readFileSync(layoutPath, "utf8");

test("dashboard footer stays at the bottom after short main-stack content", () => {
    assert.match(
        layoutStyles,
        /\.main\s*\{[^}]*\bdisplay\s*:\s*flex\s*;[^}]*\bflex-direction\s*:\s*column\s*;/su
    );
    assert.match(
        layoutStyles,
        /\.main\s*>\s*\.main-stack\s*\{[^}]*\bflex\s*:\s*1\s+0\s+auto\s*;/su
    );
});
