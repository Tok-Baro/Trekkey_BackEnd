#!/usr/bin/env node
const fs = require("fs");
const path = require("path");
const { marked } = require("marked");
const { walk } = require("./lib/inventory.cjs");

const repoRoot = path.resolve(__dirname, "../..");
const docsRoot = path.join(repoRoot, "docs");
const outputPath = path.join(docsRoot, "documentation-home.html");
const defaultDocument = "docs/implemented-features.md";

marked.setOptions({ gfm: true, breaks: false });

function escapeHtml(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");
}

function escapeJsonForScript(value) {
  return JSON.stringify(value).replaceAll("</script", "<\\/script");
}

function titleOf(markdown, fallback) {
  return markdown.match(/^#\s+(.+)$/m)?.[1].replaceAll("`", "") || fallback;
}

function plainText(markdown) {
  return markdown
    .replace(/```[\s\S]*?```/g, " ")
    .replace(/`([^`]+)`/g, "$1")
    .replace(/!\[[^\]]*\]\([^)]*\)/g, " ")
    .replace(/\[([^\]]+)\]\([^)]*\)/g, "$1")
    .replace(/[#>*_|~-]/g, " ")
    .replace(/\s+/g, " ")
    .trim();
}

function shelfFor(relativePath) {
  if (/agent-handoff|project-status|implemented-features|docs\/README/.test(relativePath)) return "현재 상태";
  if (/engineering-competition|meetings\//.test(relativePath)) return "출품·회의";
  if (/ADMIN_SECURITY|runbook|migrations\//.test(relativePath)) return "보안·운영";
  if (/api|public-activity-profile|spec\//i.test(relativePath)) return "화면·API";
  if (/blockchain|architecture|erd|schema|contract/i.test(relativePath)) return "설계·기술";
  return "기타 참고";
}

function headingInventory(markdown) {
  const used = new Map();
  return markdown.split(/\r?\n/).flatMap((line) => {
    const match = line.match(/^(#{1,4})\s+(.+)$/);
    if (!match) return [];
    const text = match[2]
      .replace(/\[([^\]]+)\]\([^)]*\)/g, "$1")
      .replace(/[*_`]/g, "")
      .trim();
    const base = text.toLowerCase()
      .replace(/[^0-9a-z가-힣\s-]/g, "")
      .trim()
      .replace(/\s+/g, "-") || "section";
    const count = used.get(base) || 0;
    used.set(base, count + 1);
    return [{ depth: match[1].length, text, id: count ? `${base}-${count + 1}` : base }];
  });
}

function rewriteLocalMarkdownLinks(html, documentPath, knownDocuments) {
  return html.replace(/<a href="([^"]+)"/g, (full, href) => {
    if (/^(?:[a-z]+:|#)/i.test(href)) return full;
    const [target] = href.split("#");
    if (!target.toLowerCase().endsWith(".md")) return full;
    const normalized = path.posix.normalize(path.posix.join(path.posix.dirname(documentPath), target));
    if (!knownDocuments.has(normalized)) return full;
    return `<a href="#doc=${encodeURIComponent(normalized)}" data-doc-link="${escapeHtml(normalized)}"`;
  });
}

const markdownFiles = walk(docsRoot, (file) => file.endsWith(".md"))
  .map((file) => ({
    absolutePath: file,
    relativePath: path.relative(repoRoot, file).split(path.sep).join("/")
  }))
  .sort((left, right) => left.relativePath.localeCompare(right.relativePath, "ko"));
const knownDocuments = new Set(markdownFiles.map((entry) => entry.relativePath));

const documents = markdownFiles.map(({ absolutePath, relativePath }) => {
  const markdown = fs.readFileSync(absolutePath, "utf8");
  const headings = headingInventory(markdown);
  let headingIndex = 0;
  let html = marked.parse(markdown).replace(/<h([1-4])>([\s\S]*?)<\/h\1>/g, (full, depth, body) => {
    const heading = headings[headingIndex++];
    return `<h${depth} id="${escapeHtml(heading?.id || `section-${headingIndex}`)}">${body}</h${depth}>`;
  });
  html = rewriteLocalMarkdownLinks(html, relativePath, knownDocuments);
  return {
    path: relativePath,
    title: titleOf(markdown, path.basename(relativePath)),
    shelf: shelfFor(relativePath),
    headings,
    html,
    searchText: plainText(markdown).toLowerCase()
  };
});

const shelves = [...new Set(documents.map((document) => document.shelf))];
const manifest = documents.map(({ path: documentPath, title, shelf, headings, searchText }) => ({
  path: documentPath,
  title,
  shelf,
  headings,
  searchText
}));

const sidebar = shelves.map((shelf) => `
  <section class="shelf" data-shelf="${escapeHtml(shelf)}">
    <h2>${escapeHtml(shelf)}</h2>
    ${documents.filter((document) => document.shelf === shelf).map((document) => `
      <button type="button" class="doc-button" data-doc="${escapeHtml(document.path)}">
        <span>${escapeHtml(document.title)}</span><small>${escapeHtml(document.path)}</small>
      </button>`).join("")}
  </section>`).join("");

const articles = documents.map((document) => `
  <article class="document" data-document="${escapeHtml(document.path)}" hidden>
    <header class="document-meta">
      <span>${escapeHtml(document.shelf)}</span>
      <code>${escapeHtml(document.path)}</code>
    </header>
    ${document.headings.length > 1 ? `<nav class="toc" aria-label="현재 문서 목차"><strong>이 문서에서</strong>${document.headings.slice(1).map((heading) => `<a href="#${escapeHtml(heading.id)}" data-heading="${escapeHtml(heading.id)}" style="--depth:${heading.depth}">${escapeHtml(heading.text)}</a>`).join("")}</nav>` : ""}
    <div class="markdown-body">${document.html}</div>
  </article>`).join("");

const html = `<!doctype html>
<html lang="ko">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <meta name="color-scheme" content="light">
  <title>Trekkey Documentation</title>
  <style>
    :root{--navy:#102b4e;--blue:#1f73b7;--sky:#eaf4fb;--ink:#1f2937;--muted:#64748b;--line:#d8e2ec;--paper:#fff;--bg:#f3f7fa;--green:#147d64;--amber:#a05a00}
    *{box-sizing:border-box}html{scroll-behavior:smooth}body{margin:0;background:var(--bg);color:var(--ink);font:15px/1.72 -apple-system,BlinkMacSystemFont,"Apple SD Gothic Neo","Noto Sans KR",sans-serif}
    button,input{font:inherit}.topbar{position:sticky;top:0;z-index:5;display:flex;align-items:center;gap:18px;min-height:68px;padding:12px 22px;background:rgba(16,43,78,.97);color:white;box-shadow:0 4px 18px #102b4e26}.brand{min-width:205px}.brand strong{display:block;font-size:20px;letter-spacing:.08em}.brand span{font-size:12px;color:#b9d7ef}.search{flex:1;display:flex;align-items:center;gap:10px;max-width:720px;background:white;border-radius:12px;padding:0 14px;color:var(--ink)}.search input{width:100%;height:42px;border:0;outline:0}.count{margin-left:auto;color:#d6e7f5;font-size:13px}
    .layout{display:grid;grid-template-columns:310px minmax(0,1fr);min-height:calc(100vh - 68px)}.sidebar{position:sticky;top:68px;height:calc(100vh - 68px);overflow:auto;padding:22px 16px 40px;background:#f8fbfd;border-right:1px solid var(--line)}.shelf{margin-bottom:24px}.shelf h2{margin:0 8px 8px;color:var(--muted);font-size:12px;letter-spacing:.08em;text-transform:uppercase}.doc-button{display:block;width:100%;padding:9px 10px;text-align:left;border:0;border-radius:9px;background:transparent;color:var(--ink);cursor:pointer}.doc-button:hover{background:var(--sky)}.doc-button.active{background:var(--navy);color:white}.doc-button span{display:block;font-weight:650}.doc-button small{display:block;overflow:hidden;text-overflow:ellipsis;color:inherit;opacity:.68;white-space:nowrap}.empty{padding:18px;color:var(--muted)}
    .content{min-width:0;padding:34px 5vw 80px}.document{max-width:1040px;margin:0 auto;padding:42px 54px 70px;background:var(--paper);border:1px solid var(--line);border-radius:18px;box-shadow:0 18px 50px #29455f12}.document-meta{display:flex;justify-content:space-between;gap:16px;padding-bottom:18px;border-bottom:1px solid var(--line);color:var(--muted);font-size:12px}.document-meta span{color:var(--blue);font-weight:750}.document-meta code{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.toc{display:grid;gap:3px;margin:22px 0;padding:18px 20px;border-left:4px solid var(--blue);background:var(--sky);border-radius:0 12px 12px 0}.toc strong{margin-bottom:6px}.toc a{padding-left:calc((var(--depth) - 2) * 14px);color:#2d587d;text-decoration:none;font-size:13px}.toc a:hover{text-decoration:underline}
    .markdown-body h1{margin:26px 0 22px;color:var(--navy);font-size:34px;line-height:1.28}.markdown-body h2{margin:42px 0 14px;padding-bottom:8px;border-bottom:2px solid var(--sky);color:var(--navy);font-size:25px;line-height:1.35}.markdown-body h3{margin:30px 0 10px;color:#245d8c;font-size:20px}.markdown-body h4{margin:24px 0 8px;color:#385a73}.markdown-body p{margin:10px 0}.markdown-body a{color:#1269a8}.markdown-body code{padding:.12em .4em;border-radius:5px;background:#edf2f7;color:#24425a;font:13px/1.5 ui-monospace,SFMono-Regular,Menlo,monospace}.markdown-body pre{overflow:auto;padding:16px;border-radius:12px;background:#0d2238;color:#e6edf3}.markdown-body pre code{padding:0;background:transparent;color:inherit}.markdown-body blockquote{margin:18px 0;padding:8px 18px;border-left:4px solid var(--amber);background:#fff9ee;color:#5f4b2d}.markdown-body table{display:block;width:100%;overflow:auto;border-collapse:collapse;margin:18px 0}.markdown-body th,.markdown-body td{min-width:110px;padding:10px 12px;border:1px solid var(--line);vertical-align:top;text-align:left}.markdown-body th{position:sticky;top:0;background:#eaf2f8;color:var(--navy)}.markdown-body ul,.markdown-body ol{padding-left:24px}.markdown-body hr{border:0;border-top:1px solid var(--line)}
    .doc-button[hidden]{display:none}
    @media(max-width:860px){.topbar{flex-wrap:wrap}.brand{min-width:0}.count{display:none}.search{order:3;flex-basis:100%;max-width:none}.layout{display:block}.sidebar{position:relative;top:0;width:100%;height:auto;max-height:310px;border-right:0;border-bottom:1px solid var(--line)}.content{padding:18px 12px 50px}.document{padding:24px 18px 42px;border-radius:12px}.markdown-body h1{font-size:27px}.markdown-body h2{font-size:22px}}
  </style>
</head>
<body>
  <header class="topbar">
    <div class="brand"><strong>TREKKEY</strong><span>코드 기반 문서 허브</span></div>
    <label class="search"><span aria-hidden="true">⌕</span><input id="search" type="search" placeholder="기능, API, 화면, 상태 검색" autocomplete="off"></label>
    <span class="count" id="count">${documents.length} documents</span>
  </header>
  <div class="layout">
    <aside class="sidebar" id="sidebar">${sidebar}<p class="empty" id="empty" hidden>검색 결과가 없습니다.</p></aside>
    <main class="content" id="content">${articles}</main>
  </div>
  <script id="documentation-manifest" type="application/json">${escapeJsonForScript(manifest)}</script>
  <script>
    const manifest = JSON.parse(document.getElementById('documentation-manifest').textContent);
    const buttons = [...document.querySelectorAll('[data-doc]')];
    const articles = [...document.querySelectorAll('[data-document]')];
    const search = document.getElementById('search');
    const count = document.getElementById('count');
    const empty = document.getElementById('empty');
    const defaultDocument = ${JSON.stringify(defaultDocument)};

    function requestedDocument() {
      const match = location.hash.match(/(?:^#|&)doc=([^&]+)/);
      return match ? decodeURIComponent(match[1]) : defaultDocument;
    }
    function showDocument(documentPath, pushHash = true) {
      const target = manifest.some((entry) => entry.path === documentPath) ? documentPath : defaultDocument;
      buttons.forEach((button) => button.classList.toggle('active', button.dataset.doc === target));
      articles.forEach((article) => { article.hidden = article.dataset.document !== target; });
      if (pushHash) history.replaceState(null, '', '#doc=' + encodeURIComponent(target));
      document.querySelector('[data-document="' + CSS.escape(target) + '"]')?.scrollIntoView({ block: 'start' });
    }
    function filterDocuments() {
      const query = search.value.trim().toLowerCase();
      const normalizedQuery = query.replace(/[#>*_|~-]/g, ' ').replace(/\\s+/g, ' ').trim();
      let visible = 0;
      buttons.forEach((button) => {
        const item = manifest.find((entry) => entry.path === button.dataset.doc);
        const matches = !query || item.title.toLowerCase().includes(query) || item.path.toLowerCase().includes(query)
          || (normalizedQuery.length > 0 && item.searchText.includes(normalizedQuery));
        button.hidden = !matches;
        if (matches) visible += 1;
      });
      document.querySelectorAll('.shelf').forEach((shelf) => {
        shelf.hidden = ![...shelf.querySelectorAll('.doc-button')].some((button) => !button.hidden);
      });
      count.textContent = visible + ' / ' + manifest.length + ' documents';
      empty.hidden = visible !== 0;
    }
    buttons.forEach((button) => button.addEventListener('click', () => showDocument(button.dataset.doc)));
    document.querySelectorAll('[data-doc-link]').forEach((link) => link.addEventListener('click', (event) => {
      event.preventDefault(); showDocument(link.dataset.docLink);
    }));
    search.addEventListener('input', filterDocuments);
    window.addEventListener('hashchange', () => showDocument(requestedDocument(), false));
    showDocument(requestedDocument(), false);
  </script>
</body>
</html>`;

fs.writeFileSync(outputPath, html.replace(/^[ \t]+$/gm, ""), "utf8");
process.stdout.write(`Built ${path.relative(repoRoot, outputPath)} with ${documents.length} Markdown documents.\n`);
