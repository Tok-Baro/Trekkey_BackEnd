#!/usr/bin/env node
const fs = require("fs");
const path = require("path");
const { extractFrontendRoutes, extractSpringOperations, walk } = require("./lib/inventory.cjs");

const repoRoot = path.resolve(__dirname, "../..");
const docsRoot = path.join(repoRoot, "docs");
const frontendRoot = path.resolve(process.env.TREKKEY_FRONTEND_ROOT || path.join(repoRoot, "../Trekkey"));
const backendRef = process.env.TREKKEY_DOCS_BACKEND_REF || null;
const backendLabel = backendRef || "working tree (including uncommitted sources)";
const failures = [];

const required = [
  "docs/README.md",
  "docs/implemented-features.md",
  "docs/project-status.md",
  "docs/agent-handoff.md",
  "docs/spec/pages.md",
  "docs/spec/api-catalog.md",
  "docs/tools/README.md"
];
for (const relativePath of required) {
  if (!fs.existsSync(path.join(repoRoot, relativePath))) failures.push(`missing required document: ${relativePath}`);
}

const markdownPaths = walk(docsRoot, (file) => file.endsWith(".md"))
  .map((file) => path.relative(repoRoot, file).split(path.sep).join("/"))
  .sort();
const htmlPath = path.join(docsRoot, "documentation-home.html");
if (!fs.existsSync(htmlPath)) {
  failures.push("missing generated reader: docs/documentation-home.html");
} else {
  const html = fs.readFileSync(htmlPath, "utf8");
  const manifestMatch = html.match(/<script id="documentation-manifest" type="application\/json">([\s\S]*?)<\/script>/);
  if (!manifestMatch) {
    failures.push("generated reader has no documentation manifest");
  } else {
    const manifestPaths = JSON.parse(manifestMatch[1]).map((entry) => entry.path).sort();
    if (JSON.stringify(manifestPaths) !== JSON.stringify(markdownPaths)) {
      const missing = markdownPaths.filter((entry) => !manifestPaths.includes(entry));
      const stale = manifestPaths.filter((entry) => !markdownPaths.includes(entry));
      failures.push(`reader parity mismatch; missing=[${missing.join(", ")}], stale=[${stale.join(", ")}]`);
    }
  }
}

function validateLocalLinks(relativePath) {
  const absolutePath = path.join(repoRoot, relativePath);
  const source = fs.readFileSync(absolutePath, "utf8");
  for (const match of source.matchAll(/!?\[[^\]]*\]\(([^)]+)\)/g)) {
    const rawTarget = match[1].trim().replace(/^<|>$/g, "").split(/\s+["']/)[0];
    if (!rawTarget || /^(?:[a-z]+:|#)/i.test(rawTarget)) continue;
    let target = rawTarget.split("#")[0].split("?")[0];
    try { target = decodeURIComponent(target); } catch (_) { /* keep literal target */ }
    const resolved = path.resolve(path.dirname(absolutePath), target);
    if (!fs.existsSync(resolved)) failures.push(`${relativePath}: missing local link target ${rawTarget}`);
  }
}
markdownPaths.forEach(validateLocalLinks);

function rowsBetween(source, startMarker, endMarker, rowPattern) {
  const start = source.indexOf(startMarker);
  const end = source.indexOf(endMarker);
  if (start < 0 || end < 0 || end <= start) return null;
  return [...source.slice(start, end).matchAll(rowPattern)].map((match) => match.slice(1));
}

const apiCatalog = fs.readFileSync(path.join(repoRoot, "docs/spec/api-catalog.md"), "utf8");
const documentedApiRows = rowsBetween(
  apiCatalog,
  "<!-- API-CATALOG-START -->",
  "<!-- API-CATALOG-END -->",
  /^\|\s*`(GET|POST|PUT|PATCH|DELETE)`\s*\|\s*`([^`]+)`\s*\|/gm
);
if (!documentedApiRows) {
  failures.push("API catalog markers are missing");
} else {
  const documented = new Set(documentedApiRows.map(([verb, apiPath]) => `${verb} ${apiPath}`));
  const extracted = extractSpringOperations(repoRoot, backendRef);
  const actual = new Set(extracted.operations.map((operation) => `${operation.verb} ${operation.path}`));
  const missing = [...actual].filter((operation) => !documented.has(operation));
  const stale = [...documented].filter((operation) => !actual.has(operation));
  if (documented.size !== documentedApiRows.length) failures.push("API catalog contains duplicate path+verb rows");
  if (missing.length || stale.length) failures.push(`API parity mismatch for ${backendLabel}; missing=[${missing.join(", ")}], stale=[${stale.join(", ")}]`);
  if (extracted.operations.length !== 108) failures.push(`expected 108 operations at ${backendLabel}, found ${extracted.operations.length}`);
}

const pages = fs.readFileSync(path.join(repoRoot, "docs/spec/pages.md"), "utf8");
const documentedRouteRows = rowsBetween(
  pages,
  "<!-- ROUTE-CATALOG-START -->",
  "<!-- ROUTE-CATALOG-END -->",
  /^\|\s*`([^`]+)`\s*\|/gm
);
if (!documentedRouteRows) {
  failures.push("route catalog markers are missing");
} else if (!fs.existsSync(frontendRoot)) {
  failures.push(`frontend repository not found: ${frontendRoot}`);
} else {
  const documented = new Set(documentedRouteRows.map(([route]) => route));
  const actual = new Set(extractFrontendRoutes(frontendRoot));
  const missing = [...actual].filter((route) => !documented.has(route));
  const stale = [...documented].filter((route) => !actual.has(route));
  if (documented.size !== documentedRouteRows.length) failures.push("route catalog contains duplicate paths");
  if (missing.length || stale.length) failures.push(`frontend route parity mismatch; missing=[${missing.join(", ")}], stale=[${stale.join(", ")}]`);
  if (actual.size !== 33) failures.push(`expected 33 explicit frontend routes, found ${actual.size}`);
}

const secretPatterns = [
  /-----BEGIN [A-Z ]*PRIVATE KEY-----/,
  /\bAKIA[0-9A-Z]{16}\b/,
  /\bntn_[A-Za-z0-9_-]{20,}\b/
];
for (const relativePath of markdownPaths) {
  const source = fs.readFileSync(path.join(repoRoot, relativePath), "utf8");
  if (secretPatterns.some((pattern) => pattern.test(source))) failures.push(`${relativePath}: possible secret material detected`);
}

if (failures.length) {
  process.stderr.write(`Documentation verification failed (${failures.length}):\n- ${failures.join("\n- ")}\n`);
  process.exit(1);
}

process.stdout.write([
  "Documentation verification passed.",
  `- Markdown reader parity: ${markdownPaths.length} documents`,
  "- Frontend route parity: 33 explicit routes",
  `- Backend API parity (${backendLabel}): 108 unique operations`,
  "- Local links and secret-pattern checks: passed"
].join("\n") + "\n");
