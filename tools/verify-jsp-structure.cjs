const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");
const root = path.resolve(__dirname, "../src/main/webapp");
function files(directory) {
    return fs.readdirSync(directory, { withFileTypes: true }).flatMap(entry => entry.isDirectory() ? files(path.join(directory, entry.name)) : [path.join(directory, entry.name)]);
}
let scripts = 0;
for (const file of files(root)) {
    if (!/\.(js|jsp|jspf)$/.test(file)) continue;
    const source = fs.readFileSync(file, "utf8");
    assert(!/Daiso\.ui\.view\(|app\.ui\.(view|part)\(|data-view-part|<template\b/.test(source), "Obsolete template system: " + file);
    if (file.endsWith(".js")) { new vm.Script(source, { filename: file }); scripts++; }
    else {
        for (const match of source.matchAll(/<script(?:\s[^>]*)?>([\s\S]*?)<\/script>/g)) {
            if (!match[1].trim()) continue;
            new vm.Script(match[1], { filename: file }); scripts++;
        }
        for (const match of source.matchAll(/<%@\s*include\s+file="([^"]+)"\s*%>/g)) {
            assert(fs.existsSync(path.resolve(path.dirname(file), match[1])), "Missing JSPF: " + file + " -> " + match[1]);
        }
    }
}
assert(!fs.existsSync(path.join(root, "WEB-INF/views/templates")), "Unused templates directory remains");
console.log(`JSP includes and ${scripts} external/inline scripts: passed; template cloning system absent`);
