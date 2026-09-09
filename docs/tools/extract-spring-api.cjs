#!/usr/bin/env node
const path = require("path");
const { extractSpringOperations } = require("./lib/inventory.cjs");

const args = process.argv.slice(2);
const valueAfter = (flag) => {
  const index = args.indexOf(flag);
  return index >= 0 ? args[index + 1] : null;
};

const repoRoot = path.resolve(valueAfter("--repo") || path.join(__dirname, "../.."));
const gitRef = valueAfter("--ref");
const result = extractSpringOperations(repoRoot, gitRef);
const payload = {
  repository: repoRoot,
  ref: gitRef || "working-tree",
  controllerHandlerCount: result.handlers.length,
  uniqueOperationCount: result.operations.length,
  byVerb: result.byVerb,
  operations: result.operations.map(({ verb, path: apiPath, controller, method, handlers, file }) => ({
    verb,
    path: apiPath,
    controller,
    method,
    handlers,
    file
  }))
};

process.stdout.write(`${JSON.stringify(payload, null, 2)}\n`);
