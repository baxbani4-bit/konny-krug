#!/usr/bin/env node
/**
 * Сервер синхронизации «Конный круг».
 * Без внешних зависимостей: только стандартный Node.js (>= 18).
 *
 * Запуск:
 *   node server.js                 # порт 8080
 *   PORT=3000 node server.js
 *
 * Данные лежат в data.json рядом со скриптом. Сделайте резервную копию этого файла.
 */
const http = require('http');
const fs = require('fs');
const path = require('path');

const PORT = Number(process.env.PORT || 8080);
const DATA_FILE = process.env.DATA_FILE || path.join(__dirname, 'data.json');

/**
 * Структура базы:
 * {
 *   cursor: 17,
 *   rooms: {
 *     club: {
 *       trainers: { "<id>": { doc, seq } },
 *       clients: {...}, subscriptions: {...}, lessons: {...}
 *     }
 *   }
 * }
 */
let db = { cursor: 0, rooms: {} };

function load() {
  try {
    db = JSON.parse(fs.readFileSync(DATA_FILE, 'utf8'));
    if (!db.rooms) db.rooms = {};
    if (typeof db.cursor !== 'number') db.cursor = 0;
  } catch (e) {
    db = { cursor: 0, rooms: {} };
  }
}

let saveTimer = null;
function save() {
  if (saveTimer) return;
  saveTimer = setTimeout(() => {
    saveTimer = null;
    try {
      fs.writeFileSync(DATA_FILE, JSON.stringify(db));
    } catch (e) {
      console.error('Не удалось сохранить базу:', e.message);
    }
  }, 200);
}

function room(name) {
  const key = String(name || 'club').replace(/[^\w-]/g, '') || 'club';
  if (!db.rooms[key]) db.rooms[key] = { trainers: {}, clients: {}, subscriptions: {}, lessons: {} };
  return db.rooms[key];
}

const COLLECTIONS = ['trainers', 'clients', 'subscriptions', 'lessons'];

/** Слияние по принципу «последняя запись побеждает» + выдача нового курсора. */
function mergeCollection(target, incoming) {
  if (!Array.isArray(incoming)) return 0;
  let changed = 0;
  for (const doc of incoming) {
    if (!doc || !doc.id) continue;
    const existing = target[doc.id];
    const incomingAt = Number(doc.updatedAt || Date.now());
    if (existing && Number(existing.doc.updatedAt || 0) > incomingAt) continue;
    target[doc.id] = { doc: { ...doc, updatedAt: incomingAt }, seq: ++db.cursor };
    changed++;
  }
  return changed;
}

function pullCollection(source, since) {
  const out = [];
  for (const id of Object.keys(source)) {
    const entry = source[id];
    if (entry.seq > since) out.push(entry.doc);
  }
  return out;
}

function readBody(req) {
  return new Promise((resolve, reject) => {
    let raw = '';
    req.on('data', (chunk) => {
      raw += chunk;
      if (raw.length > 20 * 1024 * 1024) {
        reject(new Error('Слишком большой запрос'));
        req.destroy();
      }
    });
    req.on('end', () => resolve(raw));
    req.on('error', reject);
  });
}

function send(res, code, payload, headers = {}) {
  const body = typeof payload === 'string' ? payload : JSON.stringify(payload);
  res.writeHead(code, {
    'Content-Type': typeof payload === 'string' ? 'text/html; charset=utf-8' : 'application/json; charset=utf-8',
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Headers': 'Content-Type',
    'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
    'Cache-Control': 'no-store',
    ...headers,
  });
  res.end(body);
}

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, `http://${req.headers.host}`);

  if (req.method === 'OPTIONS') return send(res, 204, '');

  try {
    if (url.pathname === '/api/health') {
      return send(res, 200, { ok: true, time: Date.now(), rooms: Object.keys(db.rooms) });
    }

    if (url.pathname === '/api/push' && req.method === 'POST') {
      const body = JSON.parse((await readBody(req)) || '{}');
      const r = room(body.room);
      let saved = 0;
      for (const name of COLLECTIONS) saved += mergeCollection(r[name], body[name]);
      save();
      return send(res, 200, { ok: true, saved, cursor: db.cursor });
    }

    if (url.pathname === '/api/pull' && req.method === 'GET') {
      const r = room(url.searchParams.get('room'));
      const since = Number(url.searchParams.get('since') || 0);
      const payload = { ok: true, cursor: db.cursor, time: Date.now() };
      for (const name of COLLECTIONS) payload[name] = pullCollection(r[name], since);
      return send(res, 200, payload);
    }

    if (url.pathname === '/' || url.pathname === '/index.html') {
      return send(res, 200, dashboard(url.searchParams.get('room') || 'club'));
    }

    return send(res, 404, { ok: false, error: 'Не найдено' });
  } catch (e) {
    console.error(e);
    return send(res, 500, { ok: false, error: String(e.message || e) });
  }
});

/** Простая веб-панель, чтобы проверить, что данные доехали до сервера. */
function dashboard(roomName) {
  const r = room(roomName);
  const lessons = Object.values(r.lessons).map((x) => x.doc).filter((l) => !l.deleted);
  const clients = Object.values(r.clients).map((x) => x.doc).filter((c) => !c.deleted);
  const subs = Object.values(r.subscriptions).map((x) => x.doc).filter((s) => !s.deleted);
  const trainers = Object.values(r.trainers).map((x) => x.doc);

  const byDate = {};
  for (const l of lessons) (byDate[l.date] = byDate[l.date] || []).push(l);
  const dates = Object.keys(byDate).sort();

  const rows = dates
    .map((d) => {
      const items = byDate[d]
        .sort((a, b) => String(a.time).localeCompare(String(b.time)))
        .map((l) => {
          const client = clients.find((c) => c.id === l.clientId);
          const trainer = trainers.find((t) => t.id === l.trainerId);
          const sub = subs.find((s) => s.id === l.subscriptionId);
          const left = sub ? sub.total - sub.used : null;
          return `<tr>
            <td>${d}</td>
            <td>${esc(l.time)}</td>
            <td><b>${esc((client && client.name) || l.clientName)}</b></td>
            <td>${esc((trainer && trainer.name) || l.trainerName || '—')}</td>
            <td>${l.kind === 'SUBSCRIPTION' ? 'абонемент' + (left !== null ? ` (осталось ${left})` : '') : 'разовая' + (l.price ? ` · ${l.price} ₽` : '')}</td>
            <td>${l.status === 'DONE' ? 'выполнено' : l.status === 'CANCELLED' ? 'отменено' : 'запланировано'}</td>
            <td>${esc(l.notes || '')}</td>
          </tr>`;
        })
        .join('');
      return `<h3>${d}</h3><table><tr><th>Дата</th><th>Время</th><th>Клиент</th><th>Тренер</th><th>Тип</th><th>Статус</th><th>Заметка</th></tr>${items}</table>`;
    })
    .join('');

  return `<!doctype html>
<html lang="ru"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Конный круг — сервер</title>
<style>
  body{font-family:-apple-system,Segoe UI,Roboto,sans-serif;background:#FAF6EF;color:#1F2A24;margin:0;padding:24px}
  h1{color:#1B4332;margin:0 0 4px}
  .sub{color:#5C6B62;margin-bottom:20px}
  table{border-collapse:collapse;width:100%;background:#fff;border-radius:12px;overflow:hidden;box-shadow:0 1px 3px rgba(0,0,0,.08);margin-bottom:18px}
  th{background:#2D6A4F;color:#fff;font-size:13px;text-align:left;padding:10px 12px}
  td{padding:9px 12px;border-top:1px solid #F3EADA;font-size:14px}
  h3{color:#2D6A4F}
  .card{display:inline-block;background:#fff;border-radius:12px;padding:12px 18px;margin:0 12px 12px 0;box-shadow:0 1px 3px rgba(0,0,0,.08)}
  .big{font-size:22px;font-weight:700;color:#1B4332}
  .empty{color:#5C6B62;background:#fff;padding:20px;border-radius:12px}
</style></head>
<body>
<h1>Конный круг</h1>
<div class="sub">Сервер синхронизации · клуб «${esc(roomName)}» · последний курсор: ${db.cursor}</div>
<div class="card"><div class="big">${trainers.length}</div>тренеров</div>
<div class="card"><div class="big">${clients.length}</div>клиентов</div>
<div class="card"><div class="big">${subs.length}</div>абонементов</div>
<div class="card"><div class="big">${lessons.length}</div>записей</div>
${rows || '<div class="empty">Пока нет записей. Как только тренер запишет клиента и синхронизируется — всё появится здесь.</div>'}
</body></html>`;
}

function esc(s) {
  return String(s == null ? '' : s).replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));
}

load();
server.listen(PORT, '0.0.0.0', () => {
  console.log(`Сервер «Конный круг» слушает http://0.0.0.0:${PORT}`);
  console.log(`Веб-панель: http://localhost:${PORT}/   API: /api/health, /api/push, /api/pull`);
});
