/* ============================================================
   ISO8583 Simulator Dashboard — Application Logic
   ============================================================ */

"use strict";

/* ── Navigation ─────────────────────────────────────────── */
const NAV_ITEMS = [
  { id: "page-dashboard", label: "Dashboard", icon: "grid" },
  { id: "page-mti", label: "MTI Config", icon: "sliders" },
  { id: "page-bitmap", label: "Bitmap Editor", icon: "layout" },
  { id: "page-fields", label: "Field Config", icon: "list" },
  { id: "page-rules", label: "Rules", icon: "filter" },
  { id: "page-scenario", label: "Scenario", icon: "clock" },
  { id: "page-profiles", label: "MTI Profiles", icon: "layers" },
  { id: "page-simulator", label: "Simulator", icon: "send" },
];

function navigateTo(pageId) {
  // Toggle pages
  document
    .querySelectorAll(".page")
    .forEach((p) => p.classList.remove("active"));
  const page = document.getElementById(pageId);
  if (page) page.classList.add("active");

  // Toggle nav items
  document.querySelectorAll("[data-nav]").forEach((el) => {
    el.classList.toggle("active", el.dataset.nav === pageId);
  });

  // Update breadcrumb
  const item = NAV_ITEMS.find((n) => n.id === pageId);
  const crumb = document.getElementById("breadcrumb-current");
  if (crumb && item) crumb.textContent = item.label;
}

/* ── Clock ──────────────────────────────────────────────── */
function startClock() {
  function tick() {
    const el = document.getElementById("topbar-clock");
    if (!el) return;
    const now = new Date();
    el.textContent = now.toISOString().replace("T", " ").slice(0, 19) + " UTC";
  }
  tick();
  setInterval(tick, 1000);
}

/* ── Toast Notifications ─────────────────────────────────── */
let _toastTimer = null;

function showToast(message, type = "success") {
  let container = document.getElementById("toast-container");
  if (!container) {
    container = document.createElement("div");
    container.id = "toast-container";
    container.style.cssText = `
      position: fixed; bottom: 24px; right: 24px;
      z-index: 9999; display: flex; flex-direction: column; gap: 8px;
    `;
    document.body.appendChild(container);
  }

  const icons = {
    success: `<svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="width:14px;height:14px;flex-shrink:0"><path d="M1 8l4 4 10-8"/></svg>`,
    danger: `<svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="width:14px;height:14px;flex-shrink:0"><circle cx="8" cy="8" r="7"/><path d="M8 5v3M8 11v.5"/></svg>`,
    warn: `<svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="width:14px;height:14px;flex-shrink:0"><path d="M8 1L1 14h14L8 1z"/><path d="M8 6v4M8 11v.5"/></svg>`,
    info: `<svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="width:14px;height:14px;flex-shrink:0"><circle cx="8" cy="8" r="7"/><path d="M8 7v4M8 5v.5"/></svg>`,
  };

  const colors = {
    success: "var(--success)",
    danger: "var(--danger)",
    warn: "var(--warn)",
    info: "var(--info)",
  };

  const toast = document.createElement("div");
  toast.style.cssText = `
    display: flex; align-items: center; gap: 10px;
    padding: 12px 16px; border-radius: 6px;
    background: var(--bg-surface); border: 1px solid var(--border-default);
    box-shadow: var(--shadow-md); font-size: 13px; color: ${colors[type] || colors.info};
    font-family: var(--font-sans); max-width: 320px;
    animation: slideInRight 0.2s ease;
  `;
  toast.innerHTML = `${icons[type] || icons.info}<span style="color:var(--text-primary)">${message}</span>`;

  const styleEl = document.getElementById("toast-style");
  if (!styleEl) {
    const s = document.createElement("style");
    s.id = "toast-style";
    s.textContent = `
      @keyframes slideInRight { from { opacity:0; transform:translateX(20px); } to { opacity:1; transform:translateX(0); } }
      @keyframes slideOutRight { from { opacity:1; transform:translateX(0); } to { opacity:0; transform:translateX(20px); } }
    `;
    document.head.appendChild(s);
  }

  container.appendChild(toast);

  setTimeout(() => {
    toast.style.animation = "slideOutRight 0.2s ease forwards";
    setTimeout(() => toast.remove(), 200);
  }, 3500);
}

/* ── State ──────────────────────────────────────────────── */
let requestFields = [];
let txFieldCount = 0;
let lastSimulatorResponse = null;
let responseViewHex = false;

const fieldNames = {
  2: "Primary Account Number (PAN)",
  3: "Processing Code",
  4: "Transaction Amount",
  7: "Transmission Date & Time",
  11: "System Trace Audit Number (STAN)",
  12: "Local Time",
  13: "Local Date",
  37: "Retrieval Reference Number",
  38: "Authorization Code",
  39: "Response Code",
  41: "Terminal ID",
  42: "Merchant ID",
  49: "Currency Code",
  70: "Network Management Code",
};

/* ── Helpers ─────────────────────────────────────────────── */
function getMti() {
  return document.getElementById("mti")?.value.trim() || "";
}

const DYNAMIC_TOKENS = {
  DATE: true,
  TIME: true,
  DATETIME: true,
  STAN: true,
  RRN: true,
};

function inferFieldMode(value) {
  if (!value) return "STATIC";

  if (value.startsWith("${REQUEST_")) return "FROM_REQUEST";

  if (value.includes("${")) {
    // Extract all tokens from the value
    const regex = /\$\{([^}]+)\}/g;
    let match;
    while ((match = regex.exec(value)) !== null) {
      const token = match[1];
      if (!DYNAMIC_TOKENS[token]) {
        // Has non-standard token, it's TEMPLATE
        return "TEMPLATE";
      }
    }
    // Only known dynamic tokens, it's DYNAMIC if it's a single token
    if (
      value.match(/^\$\{[A-Z_]+\}$/) &&
      DYNAMIC_TOKENS[value.substring(2, value.length - 1)]
    ) {
      return "DYNAMIC";
    }
    // Multiple or partial tokens, it's TEMPLATE
    return "TEMPLATE";
  }

  return "STATIC";
}

function updateFieldValueInput() {
  const mode = document.getElementById("fieldMode")?.value || "STATIC";
  const valueGroup = document.getElementById("fieldValueGroup");
  const dynamicGroup = document.getElementById("dynamicValueGroup");
  const fromRequestGroup = document.getElementById("fromRequestGroup");
  const templateGroup = document.getElementById("templateGroup");

  valueGroup.style.display = "none";
  dynamicGroup.style.display = "none";
  fromRequestGroup.style.display = "none";
  templateGroup.style.display = "none";

  switch (mode) {
    case "STATIC":
      valueGroup.style.display = "block";
      document.getElementById("fieldValue").placeholder = "e.g. 00";
      break;
    case "DYNAMIC":
      dynamicGroup.style.display = "block";
      break;
    case "FROM_REQUEST":
      fromRequestGroup.style.display = "block";
      break;
    case "TEMPLATE":
      templateGroup.style.display = "block";
      break;
  }
}

function responseCodeBadge(code) {
  if (!code) return `<span class="tx-badge muted">—</span>`;
  if (code === "00") return `<span class="tx-badge success">${code}</span>`;
  if (["05", "14", "54", "57", "58"].includes(code))
    return `<span class="tx-badge danger">${code}</span>`;
  if (["51", "61", "65"].includes(code))
    return `<span class="tx-badge warn">${code}</span>`;
  return `<span class="tx-badge info">${code}</span>`;
}

function processingTimeBadge(ms) {
  if (ms < 200) return `<span class="tx-badge success">${ms}ms</span>`;
  if (ms < 1000) return `<span class="tx-badge warn">${ms}ms</span>`;
  return `<span class="tx-badge danger">${ms}ms</span>`;
}

function setLoadingBtn(btn, loading) {
  if (!btn) return;
  if (loading) {
    btn.dataset.originalHtml = btn.innerHTML;
    btn.innerHTML = `<span class="spinner"></span> Saving…`;
    btn.disabled = true;
  } else {
    btn.innerHTML = btn.dataset.originalHtml || btn.innerHTML;
    btn.disabled = false;
  }
}

function setConfigLoadState(message, type = "info") {
  const el = document.getElementById("configLoadState");
  if (!el) return;
  el.textContent = message || "";
  el.dataset.type = type;
}

/* ── Bitmap Rendering ────────────────────────────────────── */
function renderBitmaps() {
  const reqGrid = document.getElementById("requestBitmapGrid");
  const resGrid = document.getElementById("responseBitmapGrid");
  if (!reqGrid || !resGrid) return;

  reqGrid.innerHTML = "";
  resGrid.innerHTML = "";

  for (let i = 2; i <= 128; i++) {
    reqGrid.appendChild(makeBitmapCell("reqBit", i));
    resGrid.appendChild(makeBitmapCell("resBit", i));
  }
}

function makeBitmapCell(prefix, i) {
  const cell = document.createElement("div");
  cell.className = "bitmap-cell";

  const cb = document.createElement("input");
  cb.type = "checkbox";
  cb.id = prefix + i;
  cb.value = i;

  const lbl = document.createElement("label");
  lbl.htmlFor = prefix + i;
  lbl.textContent = i;

  cell.appendChild(cb);
  cell.appendChild(lbl);
  return cell;
}

/* ── Config Load ─────────────────────────────────────────── */
async function loadConfig() {
  const mti = getMti();
  if (!mti) {
    setConfigLoadState("");
    return;
  }

  setConfigLoadState(`Loading config for ${mti}...`, "loading");

  try {
    const r = await fetch("/api/config/profile/" + mti);
    if (!r.ok) {
      setConfigLoadState(`Failed to load config for ${mti}`, "danger");
      showToast(`Failed to load MTI ${mti}`, "danger");
      console.log("Profile source: LOCAL (fallback)");
      return;
    }
    const config = await r.json();
    console.log("Profile source:", config.source ? config.source : "SERVER");

    // Update requestFields state
    if (Array.isArray(config.requestFields)) {
      requestFields = config.requestFields.map((f) => ({
        field: f.field,
        mandatory: !!f.mandatory,
        length: f.length || 0,
        type: f.type || "",
        value: f.value || "",
        mode: f.mode || inferFieldMode(f.value),
      }));
    } else {
      requestFields = [];
    }

    // Apply mode inference to response fields for backward compatibility
    if (Array.isArray(config.responseFields)) {
      config.responseFields = config.responseFields.map((f) => ({
        ...f,
        mode: f.mode || inferFieldMode(f.value),
      }));
    }

    renderFieldTable(config);
    loadBitmapState(config);
    setConfigLoadState(`Loaded config for ${mti}`, "success");
  } catch (e) {
    console.warn("Failed to load config:", e);
    setConfigLoadState(`Failed to load config for ${mti}`, "danger");
    showToast("Could not load MTI config", "danger");
  }
}

function loadBitmapState(config) {
  if (!config.bitmap) return;
  const requestBitsRaw = config.bitmap.requestBits || [];
  const responseBitsRaw = config.bitmap.responseBits || [];
  const mandatoryBits = config.bitmap.mandatoryBits || [];
  const optionalBits = config.bitmap.optionalBits || [];

  const requestBits =
    requestBitsRaw.length > 0
      ? requestBitsRaw
      : [...new Set([...(mandatoryBits || []), ...(optionalBits || [])])];

  let responseBits = responseBitsRaw;
  if ((!responseBits || responseBits.length === 0) && Array.isArray(config.responseFields)) {
    responseBits = [...new Set(config.responseFields.map((f) => f?.field).filter((n) => Number.isInteger(n)))];
  }

  for (let i = 2; i <= 128; i++) {
    const req = document.getElementById("reqBit" + i);
    const res = document.getElementById("resBit" + i);
    if (req) req.checked = requestBits.includes(i);
    if (res) res.checked = responseBits.includes(i);
  }
  const secBit = document.getElementById("secondaryBitmap");
  if (secBit) secBit.checked = !!config.bitmap.secondaryBitmap;
}

/* ── Field Table ─────────────────────────────────────────── */
function renderFieldTable(config) {
  const tbody = document.getElementById("fieldTableBody");
  if (!tbody) return;

  tbody.innerHTML = "";

  const allFields = [
    ...(config.requestFields || []).map((f) => ({ ...f, scope: "REQ" })),
    ...(config.responseFields || []).map((f) => ({ ...f, scope: "RES" })),
  ];

  if (allFields.length === 0) {
    tbody.innerHTML = `<tr class="empty-row"><td colspan="8">No fields configured. Add a field below.</td></tr>`;
    return;
  }

  allFields.forEach((f) => {
    const fieldLabel = fieldNames[f.field] || "Unknown Field";
    const mode = f.mode || (f.value ? inferFieldMode(f.value) : "-");
    const value = f.value || "-";
    const tr = document.createElement("tr");
    tr.innerHTML = `
      <td><span class="scope-tag ${f.scope.toLowerCase()}">${f.scope}</span></td>
      <td class="mono">DE${f.field ?? "—"} - ${fieldLabel}</td>
      <td class="mono">${f.type || "—"}</td>
      <td>${f.mandatory ? "✔" : "✘"}</td>
      <td class="mono">${f.length ?? "—"}</td>
      <td class="mono">${mode}</td>
      <td class="mono">${value}</td>
      <td>
        <button class="btn btn-danger btn-sm" onclick="deleteField(${f.field})">
          <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M2 4h12M5 4V2h6v2M6 7v5M10 7v5M3 4l1 10h8l1-10"/></svg>
          Delete
        </button>
      </td>`;
    tbody.appendChild(tr);
  });
}

/* ── Rules ───────────────────────────────────────────────── */
async function loadRules() {
  const mti = getMti();
  if (!mti) return;

  try {
    const res = await fetch("/api/config/" + mti);
    if (!res.ok) return;
    const config = await res.json();

    loadBitmapState(config);
    renderRuleTable(config.rules || []);
  } catch (e) {
    console.warn("loadRules error:", e);
  }
}

function normalizeRuleConditions(rule) {
  if (Array.isArray(rule.conditions) && rule.conditions.length > 0) {
    return rule.conditions;
  }

  if (
    Number.isInteger(rule.field) &&
    rule.operator &&
    typeof rule.value !== "undefined"
  ) {
    return [
      {
        field: rule.field,
        operator: rule.operator,
        value: rule.value,
      },
    ];
  }

  return [];
}

function renderRuleTable(rules) {
  const tbody = document.getElementById("ruleTableBody");
  if (!tbody) return;
  tbody.innerHTML = "";

  if (rules.length === 0) {
    tbody.innerHTML = `<tr class="empty-row"><td colspan="5">No rules configured for this MTI.</td></tr>`;
    return;
  }

  rules.forEach((rule) => {
    const conditions = normalizeRuleConditions(rule);
    const logic = (rule.logic || "AND").toUpperCase();
    const conditionsSummary =
      conditions.length > 0
        ? conditions
            .map(
              (c) =>
                `DE${c.field ?? "-"} ${c.operator || "="} ${c.value ?? ""}`,
            )
            .join(` ${logic} `)
        : "—";

    const tr = document.createElement("tr");
    tr.innerHTML = `
      <td class="mono">${rule.ruleId || "AUTO"}</td>
      <td class="mono">${logic}</td>
      <td class="mono">${conditionsSummary}</td>
      <td>${responseCodeBadge(rule.responseCode)}</td>
      <td>
        <button class="btn btn-danger btn-sm" onclick="deleteRuleById('${rule.ruleId || ""}')">Delete</button>
      </td>`;
    tbody.appendChild(tr);
  });
}

function addRuleConditionRow(condition = {}) {
  const container = document.getElementById("ruleConditionsContainer");
  if (!container) return;

  const row = document.createElement("div");
  row.className = "rule-condition-row";
  row.style.cssText =
    "display:grid;grid-template-columns:1fr 1fr 2fr auto;gap:8px;margin-bottom:8px";

  row.innerHTML = `
    <input class="ruleConditionField" type="number" min="2" max="128" placeholder="Field (DE #)" value="${
      condition.field ?? ""
    }" />
    <select class="ruleConditionOperator">
      <option value="=" ${condition.operator === "=" ? "selected" : ""}>=</option>
      <option value=">" ${condition.operator === ">" ? "selected" : ""}>&gt;</option>
      <option value="<" ${condition.operator === "<" ? "selected" : ""}>&lt;</option>
      <option value="startsWith" ${
        condition.operator === "startsWith" ? "selected" : ""
      }>startsWith</option>
      <option value="contains" ${
        condition.operator === "contains" ? "selected" : ""
      }>contains</option>
    </select>
    <input class="ruleConditionValue" type="text" placeholder="Match value" value="${
      condition.value ?? ""
    }" />
    <button class="btn btn-ghost btn-sm" type="button" onclick="this.closest('.rule-condition-row').remove()">Remove</button>
  `;

  container.appendChild(row);
}

function getRuleConditionsFromUi() {
  const rows = document.querySelectorAll(
    "#ruleConditionsContainer .rule-condition-row",
  );
  const conditions = [];

  rows.forEach((row) => {
    const fieldRaw = row.querySelector(".ruleConditionField")?.value;
    const operator = row.querySelector(".ruleConditionOperator")?.value;
    const value = row.querySelector(".ruleConditionValue")?.value;
    const field = parseInt(fieldRaw, 10);

    if (
      !Number.isNaN(field) &&
      operator &&
      value !== undefined &&
      value !== null &&
      value !== ""
    ) {
      conditions.push({ field, operator, value });
    }
  });

  return conditions;
}

/* ── Dashboard ───────────────────────────────────────────── */
function asciiToHexDisplay(s) {
  if (s == null || s === "") return "";
  let out = "";
  for (let i = 0; i < s.length; i++) {
    out += s.charCodeAt(i).toString(16).padStart(2, "0");
  }
  return out.toUpperCase();
}

function renderTxResult(data) {
  const el = document.getElementById("txResult");
  if (!el) return;
  if (data == null) {
    el.textContent = "";
    return;
  }
  if (responseViewHex && data.fields) {
    const copy = JSON.parse(JSON.stringify(data));
    copy.fields = {};
    for (const [k, v] of Object.entries(data.fields)) {
      copy.fields[k] = asciiToHexDisplay(String(v));
    }
    el.textContent = JSON.stringify(copy, null, 2);
  } else {
    el.textContent = JSON.stringify(data, null, 2);
  }
  const btn = document.getElementById("btnRespHexToggle");
  if (btn) {
    btn.textContent = responseViewHex
      ? "Response: show ASCII"
      : "Response: show hex";
  }
}

async function loadDashboard() {
  let status = null;
  try {
    const st = await fetch("/api/runtime/status");
    if (st.ok) status = await st.json();
  } catch (_) {
    /* ignore */
  }

  try {
    const [cfgRes, txRes] = await Promise.all([
      fetch("/api/config"),
      fetch("/api/transactions"),
    ]);

    const configs = await cfgRes.json();
    const txs = await txRes.json();

    const serverTcp = document.getElementById("stat-iso-server-tcp");
    if (serverTcp && status)
      serverTcp.textContent = status.tcpServerPort ?? status.tcpPort ?? "—";
    const clientTcp = document.getElementById("stat-iso-client-tcp");
    if (clientTcp && status) clientTcp.textContent = status.tcpClientPort ?? "—";
    const hp = document.getElementById("stat-http-port");
    if (hp && status) hp.textContent = status.httpPort ?? "—";
    const ver = document.getElementById("stat-config-version");
    if (ver && status) ver.textContent = status.configurationVersion ?? "—";
    const tls = document.getElementById("stat-tls");
    if (tls && status) {
      tls.textContent = status.tcpTlsActive
        ? "ON"
        : status.tcpTlsRequested
          ? "REQ"
          : "OFF";
    }
    const pk = document.getElementById("stat-packagers");
    if (pk && status) {
      const p = status.isoPrimaryPackager || "—";
      const s = status.isoSecondaryPackager
        ? ` + ${status.isoSecondaryPackager}`
        : "";
      pk.textContent = `Packagers: ${p}${s}`;
    }
    const sm = document.getElementById("stat-simulator-mode");
    if (sm && status) sm.textContent = status.instanceRole || status.simulatorMode || "—";
    const rolePill = document.getElementById("instanceRolePill");
    const roleText = document.getElementById("instanceRoleText");
    if (status && rolePill && roleText) {
      const role = String(
        status.instanceRole || status.simulatorMode || "SERVER",
      ).toUpperCase();
      roleText.textContent = role;
      rolePill.classList.remove("server", "client");
      rolePill.classList.add(role === "CLIENT" ? "client" : "server");
    }
    const roleSub = document.getElementById("stat-role-sub");
    if (roleSub && status) {
      roleSub.textContent = status.modeSwitchEnabled
        ? "Switchable at runtime"
        : "Fixed for this deployment";
    }
    const modeSelect = document.getElementById("transportModeSelect");
    if (modeSelect && status && status.simulatorMode) {
      const m = String(status.simulatorMode).toUpperCase();
      if (m === "SERVER" || m === "CLIENT") {
        modeSelect.value = m;
      }
    }
    const modeControls = document.getElementById("transportModeControls");
    if (modeControls && status) {
      modeControls.style.display = status.modeSwitchEnabled ? "flex" : "none";
    }

    // Stats
    const count = document.getElementById("stat-message-count");
    if (count) count.textContent = Array.isArray(txs) ? txs.length : 0;

    const mtiCount = document.getElementById("stat-mti-count");
    if (mtiCount)
      mtiCount.textContent = Array.isArray(configs) ? configs.length : 0;

    // MTI list
    const mtiList = document.getElementById("dashboard-mti-list");
    if (mtiList) {
      mtiList.innerHTML = "";
      if (!Array.isArray(configs) || configs.length === 0) {
        mtiList.innerHTML = `<div style="color:var(--text-muted);font-size:13px;padding:16px;text-align:center;font-style:italic">No MTIs configured yet</div>`;
      } else {
        configs.forEach((cfg) => {
          const item = document.createElement("div");
          item.className = "mti-item";
          item.innerHTML = `
            <span class="mti-code">${cfg.mti}</span>
            <span class="mti-arrow">→</span>
            <span class="mti-response">${cfg.responseMti || "auto"}</span>
            <span class="tx-badge info" style="margin-left:auto;font-size:10px">ACTIVE</span>`;
          mtiList.appendChild(item);
        });
      }
    }

    // Recent TX for dashboard
    const recentTx = document.getElementById("dashboard-recent-tx");
    if (recentTx && Array.isArray(txs)) {
      recentTx.innerHTML = "";
      const latest = txs.slice(-5).reverse();
      if (latest.length === 0) {
        recentTx.innerHTML = `<tr class="empty-row"><td colspan="4">No transactions yet</td></tr>`;
      } else {
        latest.forEach((tx) => {
          const tr = document.createElement("tr");
          tr.innerHTML = `
            <td class="mono" style="font-size:11px;color:var(--text-muted)">${tx.timestamp}</td>
            <td class="mono">${tx.mti}</td>
            <td>${responseCodeBadge(tx.responseCode)}</td>
            <td>${processingTimeBadge(tx.processingTime)}</td>`;
          recentTx.appendChild(tr);
        });
      }
    }

    document.getElementById("systemStatus")?.classList.remove("offline");
  } catch (e) {
    document.getElementById("systemStatus")?.classList.add("offline");
    const statusText = document.getElementById("systemStatusText");
    if (statusText) statusText.textContent = "DEGRADED";
  }
}

async function switchTransportMode() {
  const mode = document.getElementById("transportModeSelect")?.value;
  if (!mode) return;
  try {
    const res = await fetch("/api/runtime/mode", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ mode }),
    });
    if (!res.ok) throw new Error("Mode switch failed");
    const data = await res.json();
    showToast(`Transport mode switched to ${data.mode || mode}`, "success");
    await loadDashboard();
    await loadStatus();
  } catch (e) {
    showToast(`Failed to switch mode: ${e.message}`, "danger");
  }
}

/* ── Transaction Monitor ─────────────────────────────────── */
async function loadTransactions() {
  try {
    const response = await fetch("/api/transactions");
    const data = await response.json();

    const tbody = document.getElementById("txMonitorBody");
    if (!tbody) return;

    tbody.innerHTML = "";

    const items = Array.isArray(data) ? data.slice().reverse() : [];

    if (items.length === 0) {
      tbody.innerHTML = `<tr class="empty-row"><td colspan="4">Waiting for transactions…</td></tr>`;
      return;
    }

    items.forEach((tx) => {
      const tr = document.createElement("tr");
      tr.innerHTML = `
        <td class="mono" style="font-size:11px">${tx.timestamp}</td>
        <td class="mono">${tx.mti}</td>
        <td>${responseCodeBadge(tx.responseCode)}</td>
        <td>${processingTimeBadge(tx.processingTime)}</td>`;
      tbody.appendChild(tr);
    });

    // Update stat
    const count = document.getElementById("stat-message-count");
    if (count) count.textContent = data.length;
  } catch (e) {
    console.warn("Transaction monitor error");
  }
}

/* ── API Calls (unchanged logic) ────────────────────────── */
function saveConfig() {
  const mti = document.getElementById("mti")?.value.trim();
  const responseMti = document.getElementById("responseMti")?.value.trim();
  const btn = document.getElementById("btnSaveMti");

  if (!mti) {
    showToast("MTI is required", "danger");
    return;
  }

  setLoadingBtn(btn, true);

  const config = {
    mti,
    responseMti,
    requestFields: requestFields.slice(),
    responseFields: [{ field: 39, value: "00", mode: "STATIC" }],
  };

  fetch("/api/config/mti", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(config),
  })
    .then((r) => r.text())
    .then((t) => {
      document.getElementById("configResult").textContent = t;
      showToast("MTI configuration saved", "success");
      loadConfig();
      loadDashboard();
    })
    .catch(() => showToast("Failed to save config", "danger"))
    .finally(() => setLoadingBtn(btn, false));
}

function saveBitmap() {
  const mti = getMti();
  if (!mti) {
    showToast("Enter an MTI first", "warn");
    return;
  }

  const requestBits = [];
  const responseBits = [];

  for (let i = 2; i <= 128; i++) {
    if (document.getElementById("reqBit" + i)?.checked) requestBits.push(i);
    if (document.getElementById("resBit" + i)?.checked) responseBits.push(i);
  }

  const bitmap = {
    requestBits,
    responseBits,
    secondaryBitmap: document.getElementById("secondaryBitmap")?.checked,
  };

  const btn = document.getElementById("btnSaveBitmap");
  setLoadingBtn(btn, true);

  fetch("/api/bitmap/" + mti, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(bitmap),
  })
    .then((r) => r.text())
    .then(() => {
      showToast("Bitmap saved successfully", "success");
      loadConfig();
    })
    .catch(() => showToast("Failed to save bitmap", "danger"))
    .finally(() => setLoadingBtn(btn, false));
}

function addField() {
  const mti = getMti();
  if (!mti) {
    showToast("Enter MTI first", "warn");
    return;
  }

  const lengthValue = parseInt(
    document.getElementById("fieldLength")?.value,
    10,
  );
  const mode = document.getElementById("fieldMode")?.value || "STATIC";

  let value = "";
  switch (mode) {
    case "STATIC":
      value = document.getElementById("fieldValue")?.value || "";
      break;
    case "DYNAMIC":
      value = document.getElementById("fieldDynamicValue")?.value || "DATE";
      // Validate that it's a known dynamic token
      if (!DYNAMIC_TOKENS[value]) {
        showToast(`Invalid dynamic token: ${value}`, "danger");
        return;
      }
      break;
    case "FROM_REQUEST":
      value = document.getElementById("fieldFromRequest")?.value || "";
      // Validate that it's numeric
      if (!value || isNaN(parseInt(value, 10))) {
        showToast(
          "FROM_REQUEST requires a numeric field number (e.g., 11)",
          "danger",
        );
        return;
      }
      break;
    case "TEMPLATE":
      value = document.getElementById("fieldTemplate")?.value || "";
      if (!value) {
        showToast(
          "TEMPLATE requires a pattern (e.g., TXN-${DATE}-${STAN})",
          "danger",
        );
        return;
      }
      break;
  }

  const field = {
    field: parseInt(document.getElementById("fieldNumber")?.value),
    mandatory: document.getElementById("fieldMandatory")?.checked,
    length: Number.isNaN(lengthValue) ? 0 : lengthValue,
    type: document.getElementById("fieldType")?.value,
    value: value,
    mode: mode,
  };

  if (Number.isNaN(field.field)) {
    showToast("Enter a valid field number", "warn");
    return;
  }

  if (!field.value && mode !== "STATIC") {
    showToast("Enter a value for the selected mode", "warn");
    return;
  }

  const requestBits = [];
  const responseBits = [];
  document
    .querySelectorAll("#requestBitmapGrid input:checked")
    .forEach((cb) => requestBits.push(parseInt(cb.value, 10)));
  document
    .querySelectorAll("#responseBitmapGrid input:checked")
    .forEach((cb) => responseBits.push(parseInt(cb.value, 10)));

  const calls = [];

  if (requestBits.includes(field.field)) {
    requestFields = requestFields.filter((f) => f.field !== field.field);
    requestFields.push({
      field: field.field,
      mandatory: field.mandatory,
      length: field.length,
      type: field.type,
    });
    calls.push(
      fetch(`/api/config/${mti}/request-field`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(field),
      }),
    );
  }

  if (responseBits.includes(field.field)) {
    calls.push(
      fetch(`/api/config/${mti}/response-field`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(field),
      }),
    );
  }

  if (calls.length === 0) {
    showToast(
      "Check the field's bit in Request or Response bitmap first",
      "warn",
    );
    return;
  }

  Promise.all(calls)
    .then(() => {
      showToast(`Field ${field.field} saved`, "success");
      loadConfig();
    })
    .catch(() => showToast("Failed to save field", "danger"));
}

function deleteField(field) {
  const mti = getMti();
  fetch(`/api/config/${mti}/${field}`, { method: "DELETE" })
    .then(() => {
      showToast(`Field ${field} deleted`, "info");
      loadConfig();
    })
    .catch(() => showToast("Delete failed", "danger"));
}

function addRule() {
  const mti = getMti();
  if (!mti) {
    showToast("Enter MTI first", "warn");
    return;
  }

  const conditions = getRuleConditionsFromUi();
  if (conditions.length === 0) {
    showToast("Add at least one valid rule condition", "warn");
    return;
  }

  const responseCode = document.getElementById("ruleResponse")?.value;
  if (!responseCode || responseCode.trim().length === 0) {
    showToast("Response code is required", "warn");
    return;
  }

  const rule = {
    conditions,
    logic: (document.getElementById("ruleLogic")?.value || "AND").toUpperCase(),
    responseCode,
    // Keep legacy fields for compatibility consumers.
    field: conditions[0].field,
    operator: conditions[0].operator,
    value: conditions[0].value,
  };

  fetch(`/api/config/${mti}/rule`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(rule),
  })
    .then(() => {
      showToast("Rule added", "success");
      const container = document.getElementById("ruleConditionsContainer");
      if (container) {
        container.innerHTML = "";
        addRuleConditionRow();
      }
      loadRules();
    })
    .catch(() => showToast("Failed to add rule", "danger"));
}

function deleteRuleById(ruleId) {
  const mti = getMti();
  if (!ruleId) {
    showToast("Rule ID missing", "warn");
    return;
  }
  fetch(`/api/config/${mti}/rule/id/${ruleId}`, { method: "DELETE" })
    .then(() => {
      showToast("Rule deleted", "info");
      loadRules();
    })
    .catch(() => showToast("Delete failed", "danger"));
}

function saveScenario() {
  const mti = getMti();
  const selectedType =
    document.querySelector(".scenario-option.active")?.dataset.type || "NONE";

  const scenario = {
    type: selectedType,
    delay: parseInt(document.getElementById("scenarioDelay")?.value) || 0,
  };

  const btn = document.getElementById("btnSaveScenario");
  setLoadingBtn(btn, true);

  fetch(`/api/config/${mti}/scenario`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(scenario),
  })
    .then(() => showToast("Scenario saved", "success"))
    .catch(() => showToast("Failed to save scenario", "danger"))
    .finally(() => setLoadingBtn(btn, false));
}

function sendTransaction() {
  const mti = document.getElementById("txMti")?.value.trim();
  if (!mti) {
    showToast("MTI is required", "danger");
    return;
  }

  const fields = {};
  document.querySelectorAll(".tx-field-row").forEach((row) => {
    const fieldInput = row.querySelector(".txField");
    const valueInput = row.querySelector(".txValue");
    if (fieldInput?.value && valueInput?.value) {
      fields[fieldInput.value] = valueInput.value;
    }
  });

  const request = {
    mti,
    fields,
    hexFieldValues: !!document.getElementById("txHexFields")?.checked,
  };
  const btn = document.getElementById("btnSendTx");
  setLoadingBtn(btn, true);
  document.getElementById("txResult").textContent = "";

  fetch("/api/simulator/send", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(request),
  })
    .then(async (r) => {
      const text = await r.text();
      if (!r.ok) throw new Error(text || "HTTP " + r.status);
      return JSON.parse(text);
    })
    .then((data) => {
      lastSimulatorResponse = data;
      responseViewHex = false;
      renderTxResult(data);
      showToast("Transaction sent successfully", "success");
      loadTransactions();
    })
    .catch((e) => {
      document.getElementById("txResult").textContent = "Error: " + e.message;
      showToast("Transaction failed: " + e.message, "danger");
    })
    .finally(() => setLoadingBtn(btn, false));
}

function addTxField() {
  txFieldCount++;
  const container = document.getElementById("txFieldsContainer");
  if (!container) return;

  const row = document.createElement("div");
  row.className = "tx-field-row";
  row.id = "txRow" + txFieldCount;
  row.innerHTML = `
    <input class="txField" placeholder="Field #" style="font-family:var(--font-mono)">
    <input class="txValue" placeholder="Value">
    <button class="btn btn-ghost btn-sm" onclick="this.closest('.tx-field-row').remove()">
      <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" style="width:14px;height:14px"><path d="M2 4h12M5 4V2h6v2M3 4l1 10h8l1-10"/></svg>
    </button>`;
  container.appendChild(row);
}

/* ── Scenario Selector ───────────────────────────────────── */
function initScenarioSelector() {
  document.querySelectorAll(".scenario-option").forEach((opt) => {
    opt.addEventListener("click", () => {
      document
        .querySelectorAll(".scenario-option")
        .forEach((o) => o.classList.remove("active"));
      opt.classList.add("active");

      const delayInput = document.getElementById("scenarioDelay");
      if (delayInput) {
        delayInput.disabled = opt.dataset.type !== "DELAY";
        delayInput.style.opacity = opt.dataset.type !== "DELAY" ? "0.4" : "1";
      }
    });
  });
}

/* ── Tabs ────────────────────────────────────────────────── */
function initTabs() {
  document.querySelectorAll(".tab-bar").forEach((bar) => {
    bar.querySelectorAll(".tab-btn").forEach((btn) => {
      btn.addEventListener("click", () => {
        const tabGroup = btn.closest(".tab-bar").dataset.group;
        bar
          .querySelectorAll(".tab-btn")
          .forEach((b) => b.classList.remove("active"));
        btn.classList.add("active");

        document
          .querySelectorAll(`.tab-content[data-group="${tabGroup}"]`)
          .forEach((c) => c.classList.remove("active"));
        const target = document.getElementById(btn.dataset.tab);
        if (target) target.classList.add("active");
      });
    });
  });
}

/* ── On MTI Input Change ─────────────────────────────────── */
function onMtiInputChange() {
  // sync MTI value to fields page
  const mtiVal = document.getElementById("mti")?.value.trim();
  const mtiDisplay = document.getElementById("mtiDisplayFields");
  if (mtiDisplay) mtiDisplay.textContent = mtiVal || "—";

  if (mtiVal.length === 4) {
    loadConfig();
    loadRules();
  }
}

function toggleAllBitmapBits(checked) {
  document
    .querySelectorAll("#requestBitmapGrid input, #responseBitmapGrid input")
    .forEach((cb) => {
      cb.checked = checked;
    });
}

/* ── Monitoring Dashboard ───────────────────────────────── */
function showTab(tabId, btn) {
  document
    .querySelectorAll("#monitoringSection .tabContent")
    .forEach((el) => (el.style.display = "none"));

  const target = document.getElementById(tabId);
  if (target) {
    target.style.display = "block";
  }

  document
    .querySelectorAll("#monitoringSection .monitor-tab-btn")
    .forEach((el) => el.classList.remove("active"));
  if (btn) {
    btn.classList.add("active");
  }
}

async function loadStatus() {
  const target = document.getElementById("statusTab");
  if (!target) return;

  try {
    const res = await fetch("/api/runtime/status");
    if (!res.ok) throw new Error("Failed to load runtime status");
    const data = await res.json();

    target.innerHTML = `
      <p>Status: ${data.status || "-"}</p>
      <p>Uptime: ${Math.floor((data.uptime || 0) / 1000)} sec</p>
      <p>Active Connections: ${data.activeConnections ?? 0}</p>
      <p>Messages Received: ${data.totalMessagesReceived ?? 0}</p>
      <p>Messages Sent: ${data.totalMessagesSent ?? 0}</p>
      <p>Error Count: ${data.errorCount ?? 0}</p>
    `;
  } catch (e) {
    target.innerHTML = `<p style="color:var(--danger)">Unable to load runtime status</p>`;
  }
}

async function loadConnections() {
  const target = document.getElementById("connectionsTab");
  if (!target) return;

  try {
    const res = await fetch("/api/runtime/connections");
    if (!res.ok) throw new Error("Failed to load connections");
    const data = await res.json();

    let html = `
      <table>
        <tr>
          <th>ID</th>
          <th>Status</th>
          <th>Messages</th>
          <th>Last Activity</th>
        </tr>
    `;

    if (Array.isArray(data) && data.length > 0) {
      data.forEach((c) => {
        html += `
          <tr>
            <td>${c.connectionId || "-"}</td>
            <td>${c.status || "-"}</td>
            <td>${c.messageCount ?? 0}</td>
            <td>${c.lastActivity || "-"}</td>
          </tr>
        `;
      });
    } else {
      html += `<tr><td colspan="4">No connections available</td></tr>`;
    }

    html += `</table>`;
    target.innerHTML = html;
  } catch (e) {
    target.innerHTML = `<p style="color:var(--danger)">Unable to load connections</p>`;
  }
}

async function loadLogs() {
  const target = document.getElementById("logsTab");
  if (!target) return;

  try {
    const res = await fetch("/api/logs/messages");
    if (!res.ok) throw new Error("Failed to load logs");
    const data = await res.json();

    let html = `
      <table>
        <tr>
          <th>Time</th>
          <th>MTI</th>
          <th>Direction</th>
          <th>Response</th>
        </tr>
    `;

    const logs = Array.isArray(data) ? data.slice(-20).reverse() : [];
    if (logs.length > 0) {
      logs.forEach((entry) => {
        html += `
          <tr>
            <td>${entry.timestamp || "-"}</td>
            <td>${entry.mti || "-"}</td>
            <td>${entry.direction || "-"}</td>
            <td>${entry.responseCode || "-"}</td>
          </tr>
        `;
      });
    } else {
      html += `<tr><td colspan="4">No logs available</td></tr>`;
    }

    html += `</table>`;
    target.innerHTML = html;
  } catch (e) {
    target.innerHTML = `<p style="color:var(--danger)">Unable to load logs</p>`;
  }
}

/* ── MTI Profiles Page ────────────────────────────────────────────── */

let _currentProfileData = null;       // profile currently shown in detail view
let _profileBuilderClientFields = []; // field list from /api/profiles/client-fields

async function fetchJsonNoCache(url, options = {}) {
  const sep = url.includes("?") ? "&" : "?";
  const cacheBustedUrl = `${url}${sep}_ts=${Date.now()}`;
  const res = await fetch(cacheBustedUrl, {
    cache: "no-store",
    ...options,
  });
  return res;
}

async function syncProfilesFromServer() {
  const remoteUrl = document.getElementById("remoteServerUrl").value.trim();
  if (!remoteUrl) {
    showToast("Please enter a remote server URL (e.g. http://localhost:8081)", "warning");
    return;
  }
  
  const btn = document.querySelector('button[onclick="syncProfilesFromServer()"]');
  const originalText = btn.innerHTML;
  btn.innerHTML = "Syncing...";
  btn.disabled = true;

  try {
    // Ensure URL doesn't end with slash
    const base = remoteUrl.endsWith('/') ? remoteUrl.slice(0, -1) : remoteUrl;
    
    // Fetch profiles from remote server
    const res = await fetchJsonNoCache(`${base}/api/profiles`);
    if (!res.ok) throw new Error("HTTP " + res.status + " from remote server");
    const profiles = await res.json();
    
    // Post them to local server
    let successCount = 0;
    for (const p of profiles) {
      const postRes = await fetch("/api/profiles", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(p)
      });
      if (postRes.ok) successCount++;
    }
    
    showToast(`Successfully synced ${successCount} profiles from server`, "success");
    await loadProfiles();

    // Keep detail pane in sync if user already opened one profile.
    if (_currentProfileData && _currentProfileData.requestMti) {
      await showProfileDetail(_currentProfileData.requestMti);
    }
  } catch (e) {
    showToast("Failed to sync profiles: " + e.message, "danger");
  } finally {
    btn.innerHTML = originalText;
    btn.disabled = false;
  }
}

async function loadProfiles() {
  const grid = document.getElementById("profilesGrid");
  if (!grid) return;
  grid.innerHTML = '<div style="color:var(--text-muted);font-style:italic;padding:16px">Loading…</div>';

  try {
    const res = await fetchJsonNoCache("/api/profiles");
    if (!res.ok) throw new Error("HTTP " + res.status);
    const profiles = await res.json();

    grid.innerHTML = "";
    if (!Array.isArray(profiles) || profiles.length === 0) {
      grid.innerHTML = '<div style="color:var(--text-muted);font-style:italic;padding:16px">No profiles configured yet.</div>';
      if (_currentProfileData) {
        _currentProfileData = null;
        const section = document.getElementById("profileDetailSection");
        if (section) section.style.display = "none";
      }
      return;
    }

    // Also populate the builder MTI select with profiles not yet in datalist
    const builderSelect = document.getElementById("profileBuilderMti");
    const existingOpts = builderSelect
      ? [...builderSelect.options].map(o => o.value)
      : [];

    profiles.forEach(p => {
      const card = document.createElement("div");
      card.style.cssText = `
        background: var(--bg-input);
        border: 1px solid var(--border-default);
        border-radius: var(--radius-sm);
        padding: 14px;
        cursor: pointer;
        transition: border-color .15s, box-shadow .15s;
      `;
      card.onmouseover = () => { card.style.borderColor = "var(--accent)"; card.style.boxShadow = "var(--shadow-sm)"; };
      card.onmouseout  = () => { card.style.borderColor = "var(--border-default)"; card.style.boxShadow = "none"; };
      card.onclick     = () => showProfileDetail(p.requestMti);

      const mtiPair = `<span class="mono" style="font-size:13px;color:var(--accent)">${p.requestMti}</span>`
        + `<span style="color:var(--border-bright);margin:0 8px">→</span>`
        + `<span class="mono" style="font-size:13px;color:var(--success)">${p.responseMti || "auto"}</span>`;

      // Dynamically build Mandatory and Response bits HTML
      let mandStr = "";
      if (p.mandatoryRequestBits && p.mandatoryRequestBits.length > 0) {
        p.mandatoryRequestBits.forEach((b, idx) => {
          mandStr += "DE" + b + (idx < p.mandatoryRequestBits.length - 1 ? ", " : "");
        });
      } else {
        mandStr = "—";
      }

      let resStr = "";
      if (p.responseBits && p.responseBits.length > 0) {
        p.responseBits.forEach((b, idx) => {
          resStr += "DE" + b + (idx < p.responseBits.length - 1 ? ", " : "");
        });
      } else {
        resStr = "—";
      }

      card.innerHTML = `
        <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:8px">
          ${mtiPair}
          <span class="tx-badge info" style="font-size:10px">ACTIVE</span>
        </div>
        <div style="font-size:12px;font-weight:600;color:var(--text-primary);margin-bottom:4px">${p.name || p.profileId}</div>
        <div style="font-size:11px;color:var(--text-muted)">
          <span style="color:var(--text-primary)" class="dynamic-mand-bits"></span>
        </div>
        <div style="font-size:11px;color:var(--text-muted);margin-top:2px">
          <span style="color:var(--text-primary)" class="dynamic-res-bits"></span>
        </div>
      `;

      // Set the dynamic bits separately
      card.querySelector('.dynamic-mand-bits').innerHTML = "Mandatory: " + mandStr;
      card.querySelector('.dynamic-res-bits').innerHTML = "Response Bits: " + resStr;

      grid.appendChild(card);

      // Add to builder select if not already there
      if (builderSelect && p.requestMti && !existingOpts.includes(p.requestMti)) {
        const opt = document.createElement("option");
        opt.value = p.requestMti;
        opt.textContent = `${p.requestMti} – ${p.name || "Profile"}`;
        builderSelect.appendChild(opt);
      }
    });

    // Auto-refresh opened profile detail after list refresh.
    if (_currentProfileData && _currentProfileData.requestMti) {
      await showProfileDetail(_currentProfileData.requestMti);
    }
  } catch(e) {
    grid.innerHTML = `<div style="color:var(--danger);padding:16px">Failed to load profiles: ${e.message}</div>`;
    showToast("Failed to load profiles", "danger");
  }
}

async function showProfileDetail(profileOrMti) {
  let profile = profileOrMti;
  
  if (typeof profileOrMti === 'string') {
    try {
      const res = await fetchJsonNoCache(`/api/config/profile/${profileOrMti}`);
      if (res.ok) {
        const cfg = await res.json();
        console.log("Raw config from server:", cfg);
        
        // Map MessageTypeConfig -> MtiProfile structure expected by UI
        profile = {
          source: cfg.source || "SERVER",
          requestMti: cfg.mti,
          responseMti: cfg.responseMti || (parseInt(cfg.mti) + 10).toString().padStart(4, '0'),
          profileId: cfg.mti + "-" + (cfg.responseMti || (parseInt(cfg.mti) + 10).toString().padStart(4, '0')),
          name: cfg.description || "Profile",
          mandatoryRequestBits: cfg.bitmap?.mandatoryBits?.length ? cfg.bitmap.mandatoryBits : (cfg.bitmap?.requestBits || []),
          optionalRequestBits: cfg.bitmap?.optionalBits || [],
          responseBits: cfg.bitmap?.responseBits || [],
          requestFields: cfg.requestFields || [],
          rules: cfg.rules || []
        };
        
        // Split response fields
        const echoFields = [];
        const staticFs = [];
        const dynamicFs = [];
        
        (cfg.responseFields || []).forEach(rf => {
          const mode = rf.mode ? rf.mode.toUpperCase() : "STATIC";
          if (mode === "FROM_REQUEST") {
            echoFields.push(rf.sourceField && rf.sourceField > 0 ? rf.sourceField : rf.field);
          } else if (mode === "DYNAMIC" || mode === "GENERATOR") {
            dynamicFs.push(rf);
          } else {
            staticFs.push(rf);
          }
        });
        
        profile.echoFields = echoFields;
        profile.staticResponseFields = staticFs;
        profile.dynamicResponseFields = dynamicFs;
        
        console.log("Profile source:", profile.source);
      } else {
        console.log("Profile source: LOCAL (fallback API failed)");
      }
    } catch (e) {
      console.log("Profile source: LOCAL (fallback network error)");
    }
  }

  _currentProfileData = profile;
  const section = document.getElementById("profileDetailSection");
  if (section) section.style.display = "block";

  // CLEAR OLD CONTENT BEFORE RENDER
  const reqLbl = document.getElementById("profileReqMtiLabel");
  const resLbl = document.getElementById("profileResMtiLabel");
  const mandBits = document.getElementById("profileMandatoryBits");
  const optBits = document.getElementById("profileOptionalBits");
  const reqFieldsList = document.getElementById("profileRequestFieldsList");
  const resBits = document.getElementById("profileResponseBits");
  const echoFlds = document.getElementById("profileEchoFields");
  const staticFlds = document.getElementById("profileStaticFields");
  const dynamicFlds = document.getElementById("profileDynamicFields");
  const rulesBody = document.getElementById("profileRulesBody");

  if (reqLbl) reqLbl.innerHTML = "";
  if (resLbl) resLbl.innerHTML = "";
  if (mandBits) mandBits.innerHTML = "";
  if (optBits) optBits.innerHTML = "";
  if (reqFieldsList) reqFieldsList.innerHTML = "";
  if (resBits) resBits.innerHTML = "";
  if (echoFlds) echoFlds.innerHTML = "";
  if (staticFlds) staticFlds.innerHTML = "";
  if (dynamicFlds) dynamicFlds.innerHTML = "";
  if (rulesBody) rulesBody.innerHTML = "";

  console.log("Rendering profile:", profile);

  // Header labels
  if (reqLbl) reqLbl.textContent = profile.requestMti || "—";
  if (resLbl) resLbl.textContent = profile.responseMti || "—";

  // Request side
  if (mandBits) {
    if (!profile.mandatoryRequestBits || profile.mandatoryRequestBits.length === 0) {
      mandBits.textContent = "—";
    } else {
      profile.mandatoryRequestBits.forEach((b, idx) => {
        mandBits.innerHTML += "DE" + b + (idx < profile.mandatoryRequestBits.length - 1 ? ", " : "");
      });
    }
  }

  if (optBits) {
    if (!profile.optionalRequestBits || profile.optionalRequestBits.length === 0) {
      optBits.textContent = "— (none)";
    } else {
      profile.optionalRequestBits.forEach((b, idx) => {
        optBits.innerHTML += "DE" + b + (idx < profile.optionalRequestBits.length - 1 ? ", " : "");
      });
    }
  }

  if (reqFieldsList) {
    if (!profile.requestFields || profile.requestFields.length === 0) {
      reqFieldsList.innerHTML = '<span style="color:var(--text-muted);font-style:italic">None defined</span>';
    } else {
      profile.requestFields.forEach(f => {
        reqFieldsList.innerHTML += 
        `<div style="display:flex;gap:8px;align-items:center;padding:3px 0;border-bottom:1px solid var(--border-subtle)">
          <span class="mono" style="color:var(--accent);min-width:40px">DE${f.field}</span>
          <span class="scope-tag ${f.mandatory?'req':'info'}" style="font-size:9px">${f.mandatory?'MAND':'OPT'}</span>
          <span style="color:var(--text-secondary);font-size:11px">${f.type||""} ${f.length>0?"len:"+f.length:""}</span>
        </div>`;
      });
    }
  }

  // Response side
  if (resBits) {
    if (!profile.responseBits || profile.responseBits.length === 0) {
      resBits.textContent = "—";
    } else {
      profile.responseBits.forEach((b, idx) => {
        resBits.innerHTML += "DE" + b + (idx < profile.responseBits.length - 1 ? ", " : "");
      });
    }
  }

  if (echoFlds) {
    if (!profile.echoFields || profile.echoFields.length === 0) {
      echoFlds.textContent = "—";
    } else {
      profile.echoFields.forEach((b, idx) => {
        echoFlds.innerHTML += "DE" + b + (idx < profile.echoFields.length - 1 ? ", " : "");
      });
    }
  }

  if (staticFlds) {
    if (!profile.staticResponseFields || profile.staticResponseFields.length === 0) {
      staticFlds.innerHTML = '<span style="color:var(--text-muted);font-style:italic">None</span>';
    } else {
      profile.staticResponseFields.forEach(f => {
        staticFlds.innerHTML += `<span class="mono" style="font-size:11px">DE${f.field}=${f.value}</span> `;
      });
    }
  }

  if (dynamicFlds) {
    if (!profile.dynamicResponseFields || profile.dynamicResponseFields.length === 0) {
      dynamicFlds.innerHTML = '<span style="color:var(--text-muted);font-style:italic">None</span>';
    } else {
      profile.dynamicResponseFields.forEach(f => {
        dynamicFlds.innerHTML += `<span class="mono" style="font-size:11px">DE${f.field}→${f.value||f.dynamicType}</span> `;
      });
    }
  }

  // Rules
  if (rulesBody) {
    const rules = profile.rules || [];
    if (rules.length === 0) {
      rulesBody.innerHTML = '<tr class="empty-row"><td colspan="3">No rules – all transactions approved (DE39=00)</td></tr>';
    } else {
      rulesBody.innerHTML = rules.map(rule => {
        const conds = normalizeRuleConditions(rule);
        const logic = (rule.logic || "AND").toUpperCase();
        const condStr = conds.map(c=>`DE${c.field} ${c.operator} ${c.value}`).join(` ${logic} `);
        return `<tr>
          <td class="mono">${logic}</td>
          <td class="mono" style="font-size:11px">${condStr || "—"}</td>
          <td>${responseCodeBadge(rule.responseCode)}</td>
        </tr>`;
      }).join("");
    }
  }

  section.scrollIntoView({ behavior: "smooth", block: "start" });
}

/* ── Profile-Driven Client Builder ─────────────────────────────── */

async function onProfileBuilderMtiChange() {
  const mti = document.getElementById("profileBuilderMti")?.value;
  const respEl  = document.getElementById("profileBuilderResponseMti");
  const infoEl  = document.getElementById("profileBuilderProfileInfo");
  const nameEl  = document.getElementById("profileBuilderProfileName");
  const mandEl  = document.getElementById("profileBuilderProfileMandatoryBits");
  const optEl   = document.getElementById("profileBuilderProfileOptionalBits");
  const fldCon  = document.getElementById("profileBuilderFieldsContainer");
  const sendBtn = document.getElementById("btnSendProfileTx");

  _profileBuilderClientFields = [];
  if (sendBtn) sendBtn.disabled = true;

  if (!mti) {
    if (respEl) respEl.textContent = "—";
    if (infoEl) infoEl.style.display = "none";
    if (fldCon) fldCon.innerHTML = '<div style="padding:20px;text-align:center;color:var(--text-muted);font-size:12px;font-style:italic">Select an MTI to load profile fields</div>';
    return;
  }

  try {
    const res = await fetch(`/api/profiles/client-fields/${mti}`);
    if (!res.ok) {
      // No profile for this MTI
      if (respEl) respEl.textContent = "—";
      if (infoEl) infoEl.style.display = "none";
      if (fldCon) fldCon.innerHTML = '<div style="padding:20px;text-align:center;color:var(--warn);font-size:12px">No profile configured for this MTI. Add fields manually in the Simulator tab.</div>';
      console.log("Profile source: LOCAL (fallback/missing)");
      return;
    }
    const data = await res.json();
    console.log("Profile source:", data.source ? data.source : "SERVER");
    _profileBuilderClientFields = data.fields || [];

    if (respEl) respEl.textContent = data.responseMti || "—";

    if (infoEl && nameEl && mandEl && optEl) {
      infoEl.style.display = "block";
      nameEl.textContent = data.profileName || data.profileId || "";

      // CLEAR OLD CONTENT BEFORE RENDER
      mandEl.innerHTML = "";
      optEl.innerHTML = "";

      const mandTitle = document.createElement("span");
      mandTitle.textContent = "Mandatory: ";
      mandEl.appendChild(mandTitle);

      if (!data.mandatoryBits || data.mandatoryBits.length === 0) {
        mandEl.innerHTML += "(none)";
      } else {
        data.mandatoryBits.forEach((bit, idx) => {
          mandEl.innerHTML += "DE" + bit + (idx < data.mandatoryBits.length - 1 ? ", " : "");
        });
      }

      const optTitle = document.createElement("span");
      optTitle.textContent = "Optional: ";
      optEl.appendChild(optTitle);

      if (!data.optionalBits || data.optionalBits.length === 0) {
        optEl.innerHTML += "(none)";
      } else {
        data.optionalBits.forEach((bit, idx) => {
          optEl.innerHTML += "DE" + bit + (idx < data.optionalBits.length - 1 ? ", " : "");
        });
      }
    }

    // Render fields
    if (fldCon) {
      if (_profileBuilderClientFields.length === 0) {
        fldCon.innerHTML = '<div style="padding:20px;text-align:center;color:var(--text-muted);font-size:12px;font-style:italic">No fields defined in this profile</div>';
      } else {
        fldCon.innerHTML = "";
        _profileBuilderClientFields.forEach(f => {
          const row = document.createElement("div");
          row.className = "tx-field-row";
          row.style.cssText = "display:grid;grid-template-columns:90px 1fr;gap:0;border-bottom:1px solid var(--border-subtle)";

          const label = document.createElement("div");
          label.style.cssText = `
            padding: 10px 12px;
            background: var(--bg-surface);
            border-right: 1px solid var(--border-subtle);
            display: flex; flex-direction: column; justify-content: center;
          `;
          label.innerHTML = `
            <div class="mono" style="font-size:12px;color:var(--accent)">DE${f.deNumber}</div>
            <div style="font-size:10px;color:var(--text-muted)">${f.mandatory?'<span style="color:var(--danger)">MANDATORY</span>':'optional'}</div>
            ${f.type ? `<div style="font-size:10px;color:var(--text-muted)">${f.type}</div>` : ""}
          `;

          const inputWrap = document.createElement("div");
          inputWrap.style.cssText = "display:flex;flex-direction:column;justify-content:center;padding:8px 12px;gap:2px";

          const deHint = fieldNames[f.deNumber] || f.fieldName || "";
          const input = document.createElement("input");
          input.dataset.de = f.deNumber;
          input.className = "profileBuilderInput";
          input.placeholder = deHint ? `${deHint}` : `Value for DE${f.deNumber}`;
          if (f.defaultValue && f.defaultMode === "STATIC") {
            input.value = f.defaultValue;
          } else if (f.defaultMode === "DYNAMIC") {
            input.placeholder += ` [auto: ${f.defaultValue}]`;
            input.dataset.autoMode = f.defaultValue;
          }
          inputWrap.appendChild(input);

          if (f.length > 0) {
            const hint = document.createElement("div");
            hint.style.cssText = "font-size:10px;color:var(--text-muted)";
            hint.textContent = `max ${f.length} chars`;
            inputWrap.appendChild(hint);
          }

          row.appendChild(label);
          row.appendChild(inputWrap);
          fldCon.appendChild(row);
        });
      }
    }

    if (sendBtn) sendBtn.disabled = false;

  } catch(e) {
    if (fldCon) fldCon.innerHTML = `<div style="padding:16px;color:var(--danger)">Error loading profile fields: ${e.message}</div>`;
    showToast("Failed to load profile fields", "danger");
  }
}

async function sendProfileTransaction() {
  const mti    = document.getElementById("profileBuilderMti")?.value;
  const result = document.getElementById("profileTxResult");
  const btn    = document.getElementById("btnSendProfileTx");

  if (!mti) { showToast("Select an MTI first", "warn"); return; }

  // Collect field values from profile-driven inputs
  const fields = {};
  document.querySelectorAll(".profileBuilderInput").forEach(input => {
    const de = input.dataset.de;
    if (!de) return;
    const val = input.value.trim();
    // If no value but there's an auto mode, leave it out (backend will fill it)
    if (val) fields[parseInt(de, 10)] = val;
  });

  const request = { mti, fields, hexFieldValues: false };

  setLoadingBtn(btn, true);
  if (result) result.textContent = "";

  try {
    const res = await fetch("/api/simulator/send", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(request),
    });
    const text = await res.text();
    if (!res.ok) throw new Error(text || "HTTP " + res.status);
    const data = JSON.parse(text);
    if (result) result.textContent = JSON.stringify(data, null, 2);
    showToast("Profile request sent successfully", "success");
    loadTransactions();
  } catch(e) {
    if (result) result.textContent = "Error: " + e.message;
    showToast("Profile request failed: " + e.message, "danger");
  } finally {
    setLoadingBtn(btn, false);
  }
}

/* ── Init ────────────────────────────────────────────────── */
document.addEventListener("DOMContentLoaded", () => {
  // Render dynamic bitmaps
  renderBitmaps();

  // Set default nav
  navigateTo("page-dashboard");

  // Clock
  startClock();

  // Tabs
  initTabs();

  // Scenario selector
  initScenarioSelector();

  // Rule builder default state
  if (document.getElementById("ruleConditionsContainer")) {
    addRuleConditionRow();
  }

  // MTI input listener
  document.getElementById("mti")?.addEventListener("input", onMtiInputChange);
  document.getElementById("mti")?.addEventListener("change", onMtiInputChange);

  document.getElementById("btnRespHexToggle")?.addEventListener("click", () => {
    responseViewHex = !responseViewHex;
    renderTxResult(lastSimulatorResponse);
  });

  // Bitmap bulk actions
  document.getElementById("selectAllBtn")?.addEventListener("click", () => {
    toggleAllBitmapBits(true);
  });
  document.getElementById("clearAllBtn")?.addEventListener("click", () => {
    toggleAllBitmapBits(false);
  });

  // Start pollers
  loadTransactions();
  loadDashboard();
  loadStatus();
  loadConnections();
  loadLogs();
  loadProfiles();
  setInterval(loadTransactions, 2000);
  setInterval(loadDashboard, 5000);
  setInterval(() => {
    loadStatus();
    loadConnections();
    loadLogs();
  }, 3000);
});
