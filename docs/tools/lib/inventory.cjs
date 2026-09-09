const fs = require("fs");
const path = require("path");
const { spawnSync } = require("child_process");

function walk(directory, predicate) {
  if (!fs.existsSync(directory)) return [];
  return fs.readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const fullPath = path.join(directory, entry.name);
    if (entry.isDirectory()) return walk(fullPath, predicate);
    return predicate(fullPath) ? [fullPath] : [];
  });
}

function runGit(repoRoot, args) {
  const result = spawnSync("git", args, {
    cwd: repoRoot,
    encoding: "utf8",
    maxBuffer: 64 * 1024 * 1024
  });
  if (result.status !== 0) {
    throw new Error(`git ${args.join(" ")} failed: ${result.stderr.trim()}`);
  }
  return result.stdout;
}

function javaSources(repoRoot, gitRef) {
  if (!gitRef) {
    return walk(path.join(repoRoot, "src/main/java"), (file) => file.endsWith(".java"))
      .map((file) => ({
        name: path.relative(repoRoot, file).split(path.sep).join("/"),
        source: fs.readFileSync(file, "utf8")
      }));
  }

  const names = runGit(repoRoot, ["ls-tree", "-r", "--name-only", gitRef, "--", "src/main/java"])
    .split(/\r?\n/)
    .filter((name) => name.endsWith(".java"));
  return names.map((name) => ({
    name,
    source: runGit(repoRoot, ["show", `${gitRef}:${name}`])
  }));
}

function annotationStrings(expression = "") {
  return [...expression.matchAll(/"((?:\\.|[^"\\])*)"/g)]
    .map((match) => match[1])
    .join("");
}

function normalizeApiPath(value) {
  const normalized = value.replace(/\/+/g, "/") || "/";
  return normalized.length > 1 && normalized.endsWith("/")
    ? normalized.slice(0, -1)
    : normalized;
}

function extractSpringOperations(repoRoot, gitRef = null) {
  const handlers = [];
  for (const { name, source } of javaSources(repoRoot, gitRef)) {
    if (!source.includes("@RestController")) continue;
    const classAt = source.search(/public\s+class\s+\w+/);
    if (classAt < 0) continue;

    const beforeClass = source.slice(0, classAt);
    const classMapping = [...beforeClass.matchAll(/@RequestMapping\s*\(([\s\S]*?)\)/g)].at(-1);
    const basePath = classMapping ? annotationStrings(classMapping[1]) : "";
    const className = source.slice(classAt).match(/public\s+class\s+(\w+)/)?.[1]
      || path.basename(name, ".java");
    const classRole = beforeClass.match(/@PreAuthorize\s*\(\s*"([^"]+)"\s*\)/)?.[1] || "";
    const methodPattern = /@(Get|Post|Put|Patch|Delete)Mapping(?:\s*\(([\s\S]*?)\))?([\s\S]*?)(?:public\s+(?:[\w<>?,.\[\]\s]+)\s+(\w+)\s*\()/g;

    for (const match of source.slice(classAt).matchAll(methodPattern)) {
      const verb = match[1].toUpperCase();
      const methodPath = annotationStrings(match[2] || "");
      const methodRole = (match[3] || "").match(/@PreAuthorize\s*\(\s*"([^"]+)"\s*\)/)?.[1] || "";
      handlers.push({
        verb,
        path: normalizeApiPath(`${basePath}${methodPath}`),
        controller: className,
        method: match[4] || "",
        role: methodRole || classRole,
        file: name
      });
    }
  }

  const unique = new Map();
  for (const handler of handlers) {
    const key = `${handler.verb} ${handler.path}`;
    if (!unique.has(key)) {
      unique.set(key, { ...handler, handlers: [handler.method] });
    } else {
      unique.get(key).handlers.push(handler.method);
    }
  }

  const operations = [...unique.values()].sort(
    (left, right) => left.path.localeCompare(right.path) || left.verb.localeCompare(right.verb)
  );
  const byVerb = operations.reduce((counts, operation) => {
    counts[operation.verb] = (counts[operation.verb] || 0) + 1;
    return counts;
  }, {});

  return { handlers, operations, byVerb };
}

function extractFrontendRoutes(frontendRoot) {
  const routeConfig = fs.readFileSync(path.join(frontendRoot, "src/routeConfig.js"), "utf8");
  const router = fs.readFileSync(path.join(frontendRoot, "src/router.jsx"), "utf8");
  const configured = [...routeConfig.matchAll(/\bpath:\s*"([^"]+)"/g)].map((match) => match[1]);
  const explicit = [...router.matchAll(/<Route\s+[^>]*\bpath="([^"]+)"/g)].map((match) => match[1]);
  return [...new Set([...configured, ...explicit].filter((route) => route !== "*"))].sort();
}

module.exports = {
  extractFrontendRoutes,
  extractSpringOperations,
  walk
};
