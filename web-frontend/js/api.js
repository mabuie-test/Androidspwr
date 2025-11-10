// simple api helper
const API_BASE = '';

function getAuthToken() {
  return localStorage.getItem('auth_token') || null;
}

function authHeaders(headers = {}) {
  const token = getAuthToken();
  const h = Object.assign({}, headers);
  if (token) h['Authorization'] = 'Bearer ' + token;
  return h;
}

async function apiFetch(path, opts = {}) {
  opts.headers = Object.assign({}, opts.headers || {});
  // default JSON header unless body is FormData
  if (!(opts.body instanceof FormData) && !opts.headers['Content-Type']) {
    opts.headers['Content-Type'] = 'application/json';
  }
  // add auth header
  opts.headers = authHeaders(opts.headers);
  if (opts.body && typeof opts.body === 'object' && !(opts.body instanceof FormData)) {
    opts.body = JSON.stringify(opts.body);
  }
  const r = await fetch(API_BASE + path, opts);
  const text = await r.text();
  try {
    return JSON.parse(text);
  } catch (e) {
    return { ok:false, raw: text, status: r.status };
  }
}
