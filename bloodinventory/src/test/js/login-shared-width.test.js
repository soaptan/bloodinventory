const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");

const styles = fs.readFileSync(
    path.resolve(__dirname, "../../main/resources/static/css/login.css"),
    "utf8"
);

test("login content shares the width of the remember and recovery actions", () => {
    assert.match(styles, /\.login-panel\s*\{[^}]*padding:\s*56px 40px/u);
    assert.match(styles, /\.login-card\s*\{[^}]*max-width:\s*400px/u);
});

test("login content keeps the shared width at the tablet breakpoint", () => {
    assert.match(
        styles,
        /@media\s*\(max-width:\s*820px\)[\s\S]*\.login-panel\s*\{[^}]*padding:\s*44px 30px/u
    );
});
