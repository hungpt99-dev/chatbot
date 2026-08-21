'use strict';

const API = '/api';
let convId = null;
let convOver = false;

const $ = (id) => document.getElementById(id);

function escapeHtml(s) {
  return (s == null ? '' : String(s))
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

async function api(method, path, body) {
  const opts = { method, headers: { 'Content-Type': 'application/json' } };
  if (body !== undefined) opts.body = JSON.stringify(body);
  const res = await fetch(API + path, opts);
  if (res.status === 204) return null;
  const text = await res.text();
  let data = null;
  try { data = text ? JSON.parse(text) : null; } catch (_) { data = { _raw: text }; }
  if (!res.ok) throw Object.assign(new Error((data && (data.message || data.error)) || res.statusText), { status: res.status, data });
  return data;
}

function statusBadge(status) {
  return `<span class="badge ${status}">${escapeHtml(status)}</span>`;
}

function renderConv(conv) {
  if (!conv) return;
  convId = conv.id;
  const over = ['RESOLVED', 'ESCALATED'].includes(conv.status);
  convOver = over;

  $('conv-title').textContent = conv.sopId
    ? `SOP: ${conv.sopId}`
    : 'No SOP matched';
  const meta = [];
  meta.push(statusBadge(conv.status));
  if (conv.currentStepKey) meta.push(`<span class="badge">step ${escapeHtml(conv.currentStepKey)}</span>`);
  if (conv.employee) meta.push(`<span>👤 ${escapeHtml(conv.employee)}</span>`);
  if (conv.problem) meta.push(`<span>📝 ${escapeHtml(conv.problem)}</span>`);
  $('conv-meta').innerHTML = meta.join(' ');

  $('start-form').classList.add('hidden');
  $('thread').classList.remove('hidden');
  $('msg-form').classList.toggle('hidden', over);

  renderThread(conv);

  if (over) {
    const kind = conv.status === 'RESOLVED' ? '✅ Resolved' : '⚠️ Escalated to IT';
    addSystem(`${kind}. A case has been created on the operator board.`);
  }
}

function renderThread(conv) {
  const thread = $('thread');
  thread.innerHTML = '';
  (conv.messages || []).forEach(renderMessage);
  thread.scrollTop = thread.scrollHeight;
}

function renderMessage(m) {
  const thread = $('thread');
  const el = document.createElement('div');
  const role = (m.role || '').toLowerCase();
  if (role === 'user') {
    el.className = 'msg user';
    el.innerHTML = `<div class="role">You</div>${escapeHtml(m.content)}`;
  } else if (role === 'system') {
    el.className = 'msg system';
    el.textContent = m.content;
  } else {
    el.className = 'msg ai';
    let html = `<div class="role">Assistant</div>${escapeHtml(m.content)}`;
    if (m.stepKey) html += `<div class="step-tag">step ${escapeHtml(m.stepKey)}</div>`;
    el.innerHTML = html;
  }
  thread.appendChild(el);
  thread.scrollTop = thread.scrollHeight;
}

function addSystem(text) {
  renderMessage({ role: 'system', content: text });
}

async function startConversation(e) {
  e.preventDefault();
  $('start-error').textContent = '';
  const employee = $('employee').value.trim();
  const problem = $('problem').value.trim();
  if (!problem) { $('start-error').textContent = 'Please describe the problem.'; return; }
  $('start-btn').disabled = true;
  try {
    const conv = await api('POST', '/conversations', { employee: employee || 'employee', problem });
    renderConv(conv);
    loadCases();
  } catch (err) {
    $('start-error').textContent = friendlyError(err);
  } finally {
    $('start-btn').disabled = false;
  }
}

async function sendMessage(e) {
  e.preventDefault();
  if (convOver) return;
  const input = $('msg-input');
  const text = input.value.trim();
  if (!text) return;
  input.value = '';
  // optimistic user bubble
  renderMessage({ role: 'user', content: text });
  // disable send while awaiting
  $('msg-input').disabled = true;
  try {
    const conv = await api('POST', `/conversations/${convId}/messages`, { message: text });
    // replace thread with authoritative server state
    renderThread(conv);
    $('conv-meta').innerHTML = [
      statusBadge(conv.status),
      conv.currentStepKey ? `<span class="badge">step ${escapeHtml(conv.currentStepKey)}</span>` : '',
      conv.employee ? `<span>👤 ${escapeHtml(conv.employee)}</span>` : ''
    ].join(' ');
    convOver = ['RESOLVED', 'ESCALATED'].includes(conv.status);
    $('msg-form').classList.toggle('hidden', convOver);
    if (convOver) {
      addSystem(conv.status === 'RESOLVED' ? '✅ Resolved — case created.' : '⚠️ Escalated to IT — case created.');
    }
    loadCases();
  } catch (err) {
    if (err.status === 409) {
      addSystem('This conversation is closed. Start a new one to continue.');
      convOver = true;
      $('msg-form').classList.add('hidden');
    } else if (err.status === 422) {
      addSystem('No SOP matched this problem.');
    } else {
      addSystem('Error: ' + friendlyError(err));
    }
  } finally {
    $('msg-input').disabled = false;
    $('msg-input').focus();
  }
}

function friendlyError(err) {
  if (err && err.message) return err.message;
  return 'Something went wrong.';
}

/* ---------------- Operator board ---------------- */

async function loadCases() {
  const status = $('status-filter').value;
  const qs = status ? `?status=${encodeURIComponent(status)}` : '';
  try {
    const cases = await api('GET', '/cases' + qs);
    renderCaseList(cases || []);
  } catch (_) { /* non-fatal */ }
}

function renderCaseList(cases) {
  const list = $('case-list');
  list.innerHTML = '';
  if (!cases.length) {
    list.innerHTML = '<div class="empty">No cases.</div>';
    return;
  }
  cases.forEach((c) => {
    const li = document.createElement('li');
    li.className = 'case-item';
    li.innerHTML = `
      <div class="ci-top">
        <span class="ci-ref">${escapeHtml(c.reference || c.conversationId)}</span>
        ${statusBadge(c.status)}
      </div>
      <div class="ci-prob">${escapeHtml(c.problem || c.sopId || '')}</div>`;
    li.onclick = () => showCaseDetail(c.reference || c.conversationId);
    list.appendChild(li);
  });
}

async function showCaseDetail(ref) {
  if (!ref) return;
  try {
    const d = await api('GET', '/cases/' + encodeURIComponent(ref));
    const el = $('case-detail');
    el.classList.remove('hidden');
    el.innerHTML = `
      <h4>${escapeHtml(d.reference)}</h4>
      <dl>
        <dt>Status</dt><dd>${escapeHtml(d.status)}</dd>
        <dt>SOP</dt><dd>${escapeHtml(d.sopId || '')}</dd>
        <dt>Employee</dt><dd>${escapeHtml(d.employee || '')}</dd>
        <dt>Problem</dt><dd>${escapeHtml(d.problem || '')}</dd>
        <dt>Failed step</dt><dd>${escapeHtml(d.failedStepKey || '—')}</dd>
        <dt>Reason</dt><dd>${escapeHtml(d.escalationReason || '—')}</dd>
        <dt>Started</dt><dd>${escapeHtml(d.startedAt ? new Date(d.startedAt).toLocaleString() : '—')}</dd>
        ${d.resolvedAt ? `<dt>Resolved</dt><dd>${escapeHtml(new Date(d.resolvedAt).toLocaleString())}</dd>` : ''}
        ${d.escalatedAt ? `<dt>Escalated</dt><dd>${escapeHtml(new Date(d.escalatedAt).toLocaleString())}</dd>` : ''}
      </dl>
      <button class="btn ghost close" onclick="document.getElementById('case-detail').classList.add('hidden')">Close</button>`;
  } catch (_) { /* ignore */ }
}

function detectMode() {
  // Best-effort: probe whether an LLM key is configured by checking /api/sops (always 200).
  // The real mode is backend-driven; we just label "online/offline" heuristically.
  // The app reports offline when no key is set; we can't read config, so leave neutral.
  $('mode-pill').textContent = 'ready';
}

/* ---------------- wiring ---------------- */
$('start-btn').addEventListener('click', startConversation);
$('msg-form').addEventListener('submit', sendMessage);
$('refresh-btn').addEventListener('click', loadCases);
$('status-filter').addEventListener('change', loadCases);
detectMode();
loadCases();
