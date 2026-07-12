// Author: Othmane

const $ = id => document.getElementById(id);
let solution = null; // {routes, cost}
let coords = null;

// theme (dark default, persisted)
if (localStorage.theme) document.documentElement.dataset.theme = localStorage.theme;
$("theme").onclick = () => {
  const t = document.documentElement.dataset.theme === "dark" ? "light" : "dark";
  document.documentElement.dataset.theme = t; localStorage.theme = t;
};

async function json(url) { return (await fetch(url)).json(); }

async function loadFolders() {
  const folders = await json("/api/folders");
  $("folder").innerHTML = folders.map(f => `<option>${f}</option>`).join("");
  await loadInstances();
}
async function loadInstances() {
  const insts = await json("/api/instances?folder=" + encodeURIComponent($("folder").value));
  $("instance").innerHTML = insts.map(i => `<option>${i}</option>`).join("");
  await showInstance();
}
$("folder").onchange = loadInstances;
$("instance").onchange = showInstance;

async function loadCoords() {
  const folder = $("folder").value, file = $("instance").value;
  if (!file) return null;
  const text = await (await fetch(`/api/vrp?folder=${encodeURIComponent(folder)}&file=${encodeURIComponent(file)}`)).text();
  return parseCoords(text);
}
// draw the raw customer scatter so the map is never empty (the hero, pre-solve)
async function showInstance() {
  if (running) return;
  coords = await loadCoords();
  solution = null;
  if (coords) drawRoutes(coords, [], "all");
}

let running = false;
function setRunning(on) {
  running = on;
  $("solve").textContent = on ? "Stop" : "Solve";
  $("solve").style.background = $("solve").style.borderColor = on ? "var(--danger)" : "var(--accent2)";
  $("folder").disabled = on;
  $("instance").disabled = on;
}

$("solve").onclick = () => {
  if (running) { $("solve").disabled = true; fetch("/api/stop"); return; } // let the final result arrive over SSE
  const folder = $("folder").value, file = $("instance").value;
  const log = $("log"); log.textContent = ""; $("stats").textContent = "";
  $("solText").textContent = "";
  $("routeId").disabled = true; $("save").disabled = true;
  $("routeId").innerHTML = '<option value="all">All</option>';
  solution = null;
  if (coords) drawRoutes(coords, [], "all"); // keep the scatter visible while solving
  setRunning(true);
  const es = new EventSource(`/api/solve?folder=${encodeURIComponent(folder)}&file=${encodeURIComponent(file)}`);
  es.addEventListener("log", e => { log.textContent += e.data + "\n"; log.scrollTop = log.scrollHeight; });
  es.addEventListener("result", e => {
    es.close(); setRunning(false); $("solve").disabled = false;
    const r = JSON.parse(e.data);
    if (r.feasible) {
      solution = r;
      $("routeId").disabled = false;
      $("routeId").innerHTML = '<option value="all">All</option>' + r.routes.map((_, i) => `<option value="${i}">Route ${i+1}</option>`).join("");
      const stat = (k, v, cls = "") => `<div class="stat"><span class="k">${k}</span><span class="v ${cls}">${v}</span></div>`;
      let html = stat("Cost", r.cost) + stat("Routes", r.routes.length) + stat("Time", r.timeMs + " ms");
      if (r.optimal != null) html += stat("Optimal", r.optimal);
      if (r.gap != null) html += stat("Gap", r.gap + "%", parseFloat(r.gap) <= 0.01 ? "good" : "warn");
      $("stats").innerHTML = html;
      drawSolution();
    } else {
      $("stats").innerHTML = `<span style="color:var(--danger)">No feasible solution found.</span>`;
    }
  });
  es.addEventListener("sol", e => { $("solText").textContent = e.data; $("save").disabled = false; });
  es.onerror = () => { es.close(); setRunning(false); $("solve").disabled = false; };
};

$("save").onclick = async () => {
  const file = $("instance").value, text = $("solText").textContent;
  if (window.showSaveFilePicker) { // native "Save As" dialog, opens at Desktop
    try {
      const handle = await window.showSaveFilePicker({
        suggestedName: file + ".sol", startIn: "desktop",
        types: [{ description: "Solution file", accept: { "text/plain": [".sol"] } }],
      });
      const w = await handle.createWritable(); await w.write(text); await w.close();
      $("stats").innerHTML += ` &nbsp; <b>Saved:</b> ${handle.name}`;
    } catch (e) { if (e.name !== "AbortError") $("stats").innerHTML += ` &nbsp; <span style="color:var(--danger)">Save failed</span>`; }
    return;
  }
  const a = document.createElement("a"); // fallback (Firefox/Safari): plain download
  a.href = URL.createObjectURL(new Blob([text], { type: "text/plain" }));
  a.download = file + ".sol"; a.click(); URL.revokeObjectURL(a.href);
};

async function drawSolution() {
  if (!solution) return;
  if (!coords) coords = await loadCoords();
  if (coords) drawRoutes(coords, solution.routes, $("routeId").value);
}
$("routeId").onchange = drawSolution;

// parse CVRPLIB NODE_COORD_SECTION -> {id:[x,y]}
function parseCoords(text) {
  const coords = {}; let inSection = false;
  for (const line of text.split("\n")) {
    const t = line.trim();
    if (/^NODE_COORD_SECTION/i.test(t)) { inSection = true; continue; }
    if (inSection) {
      if (/^[A-Z_]+/i.test(t) && !/^\d/.test(t)) break; // next section
      const p = t.split(/\s+/);
      if (p.length >= 3) coords[+p[0]] = [+p[1], +p[2]];
    }
  }
  return coords;
}

function drawRoutes(coords, routes, routeId) {
  const cv = $("canvas"); cv.style.display = "block";
  const dpr = window.devicePixelRatio || 1;
  const cssW = cv.clientWidth || 1040, cssH = 560;
  cv.width = cssW * dpr; cv.height = cssH * dpr; cv.style.height = cssH + "px"; // crisp on HiDPI
  const ctx = cv.getContext("2d"); ctx.setTransform(dpr, 0, 0, dpr, 0, 0); ctx.clearRect(0, 0, cssW, cssH);
  const ids = Object.keys(coords).map(Number);
  const xs = ids.map(i => coords[i][0]), ys = ids.map(i => coords[i][1]);
  const minX = Math.min(...xs), maxX = Math.max(...xs), minY = Math.min(...ys), maxY = Math.max(...ys);
  const pad = 30, W = cssW - 2 * pad, H = cssH - 2 * pad;
  const sx = W / ((maxX - minX) || 1), sy = H / ((maxY - minY) || 1), s = Math.min(sx, sy);
  const tx = x => pad + (x - minX) * s;
  const ty = y => cssH - pad - (y - minY) * s; // flip Y
  const depot = coords[1]; // node 1 = depot
  const palette = ["#4f9cf9","#3fb950","#f0883e","#db61a2","#a371f7","#e3b341","#39c5cf","#f85149"];

  const selected = routeId === "all" ? null : +routeId;
  routes.forEach((route, k) => {
    if (selected !== null && k !== selected) return;
    ctx.strokeStyle = palette[k % palette.length]; ctx.lineWidth = selected !== null ? 2.4 : 1.6;
    ctx.beginPath(); ctx.moveTo(tx(depot[0]), ty(depot[1]));
    for (const id of route) { const c = coords[id]; if (c) ctx.lineTo(tx(c[0]), ty(c[1])); }
    ctx.lineTo(tx(depot[0]), ty(depot[1])); ctx.stroke();
  });
  // customers
  ctx.fillStyle = getComputedStyle(document.documentElement).getPropertyValue("--muted");
  for (const id of ids) { if (id === 1) continue; const c = coords[id]; ctx.beginPath(); ctx.arc(tx(c[0]), ty(c[1]), 3, 0, 7); ctx.fill(); }
  // depot
  ctx.fillStyle = "#f85149"; ctx.beginPath(); ctx.arc(tx(depot[0]), ty(depot[1]), 6, 0, 7); ctx.fill();
}

loadFolders();
