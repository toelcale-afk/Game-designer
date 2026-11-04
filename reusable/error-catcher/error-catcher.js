/* PackFlow error catcher v1. Drop-in, no dependencies. Load it FIRST in <head>.
 * Catches window errors, unhandled promise rejections and console.error, keeps the last 50
 * (message, file, line, column, stack, time) in localStorage and shows a small floating
 * "Copy errors" button when there are any. If the page runs inside an Android app that
 * exposes window.PackFlowAndroid, each error is also sent to the app's crash log.
 * API: PackFlowErrors.list(), .clear(), .copy(), .report(err), .text()
 */
(function () {
  'use strict';
  if (window.PackFlowErrors) return;
  var KEY = '__packflow_errors';
  var MAX = 50;
  var errors = [];
  try { errors = JSON.parse(localStorage.getItem(KEY) || '[]') || []; } catch (e) { errors = []; }
  var bridge = window.PackFlowAndroid || null;
  var origError = console.error ? console.error.bind(console) : function () {};
  var button = null;
  var busy = false;

  function save() {
    try { localStorage.setItem(KEY, JSON.stringify(errors.slice(-MAX))); } catch (e) { /* storage full or blocked */ }
  }

  function str(v) {
    if (v instanceof Error) return v.name + ': ' + v.message;
    if (typeof v === 'string') return v;
    try { return JSON.stringify(v); } catch (e) { return String(v); }
  }

  function add(entry) {
    if (busy) return;
    busy = true;
    try {
      entry.time = new Date().toISOString();
      entry.page = location.pathname.split('/').pop() || 'index.html';
      errors.push(entry);
      if (errors.length > MAX) errors.splice(0, errors.length - MAX);
      save();
      if (bridge && bridge.logError) {
        try { bridge.logError(JSON.stringify(entry)); } catch (e) { /* bridge gone */ }
      }
      render();
    } finally {
      busy = false;
    }
  }

  function text() {
    if (!errors.length) return 'No errors recorded.';
    var lines = ['Errors from ' + (document.title || location.href) + ' (' + errors.length + ')', 'User agent: ' + navigator.userAgent, ''];
    errors.forEach(function (e, i) {
      lines.push((i + 1) + '. [' + e.type + '] ' + e.message);
      lines.push('   at ' + (e.file || '?') + ':' + (e.line || 0) + ':' + (e.column || 0) + '  (' + e.time + ')');
      if (e.stack) lines.push('   ' + String(e.stack).split('\n').slice(0, 8).join('\n   '));
      lines.push('');
    });
    return lines.join('\n');
  }

  function fallbackCopy(t) {
    var ta = document.createElement('textarea');
    ta.value = t;
    ta.setAttribute('readonly', '');
    ta.style.cssText = 'position:fixed;top:-1000px;opacity:0';
    document.body.appendChild(ta);
    ta.select();
    var ok = false;
    try { ok = document.execCommand('copy'); } catch (e) { ok = false; }
    document.body.removeChild(ta);
    return ok;
  }

  function copy() {
    var t = text();
    if (bridge && bridge.copyText) {
      try { bridge.copyText(t); flash('Copied'); return Promise.resolve(true); } catch (e) { /* fall through */ }
    }
    if (navigator.clipboard && navigator.clipboard.writeText) {
      return navigator.clipboard.writeText(t).then(function () { flash('Copied'); return true; }, function () {
        var ok = fallbackCopy(t); flash(ok ? 'Copied' : 'Copy failed'); return ok;
      });
    }
    var ok = fallbackCopy(t);
    flash(ok ? 'Copied' : 'Copy failed');
    return Promise.resolve(ok);
  }

  function flash(msg) {
    if (!button) return;
    button.textContent = msg;
    setTimeout(render, 1200);
  }

  function render() {
    if (!document.body) return;
    if (!errors.length) {
      if (button) { button.remove(); button = null; }
      return;
    }
    if (!button) {
      button = document.createElement('button');
      button.id = 'packflow-copy-errors';
      button.type = 'button';
      button.style.cssText = 'position:fixed;right:10px;bottom:10px;z-index:2147483647;' +
        'background:#B91C1C;color:#fff;border:0;border-radius:18px;padding:8px 12px;' +
        'font:600 12px system-ui,sans-serif;box-shadow:0 2px 8px rgba(0,0,0,.4);opacity:.92';
      button.addEventListener('click', function (ev) { ev.stopPropagation(); copy(); });
      button.addEventListener('contextmenu', function (ev) { ev.preventDefault(); clear(); });
      document.body.appendChild(button);
    }
    button.textContent = 'Copy errors (' + errors.length + ')';
    button.title = 'Tap to copy. Long-press to clear.';
  }

  function clear() {
    errors = [];
    save();
    if (bridge && bridge.clearErrors) { try { bridge.clearErrors(); } catch (e) { /* ignore */ } }
    render();
  }

  window.addEventListener('error', function (ev) {
    var t = ev.target;
    if (t && t !== window && (t.src || t.href)) {
      add({ type: 'resource', message: 'Failed to load ' + (t.tagName || '').toLowerCase() + ': ' + (t.src || t.href), file: t.src || t.href, line: 0, column: 0, stack: '' });
      return;
    }
    var err = ev.error;
    add({
      type: 'error',
      message: ev.message || str(err),
      file: ev.filename || '',
      line: ev.lineno || 0,
      column: ev.colno || 0,
      stack: err && err.stack ? String(err.stack) : ''
    });
  }, true);

  window.addEventListener('unhandledrejection', function (ev) {
    var r = ev.reason;
    add({
      type: 'promise',
      message: 'Unhandled promise rejection: ' + str(r),
      file: '', line: 0, column: 0,
      stack: r && r.stack ? String(r.stack) : ''
    });
  });

  console.error = function () {
    var args = Array.prototype.slice.call(arguments);
    origError.apply(null, args);
    var firstErr = args.filter(function (a) { return a instanceof Error; })[0];
    var where = (new Error().stack || '').split('\n')[2] || '';
    var m = /([^\s(]+):(\d+):(\d+)\)?\s*$/.exec(where);
    add({
      type: 'console',
      message: args.map(str).join(' '),
      file: m ? m[1] : '',
      line: m ? +m[2] : 0,
      column: m ? +m[3] : 0,
      stack: firstErr && firstErr.stack ? String(firstErr.stack) : ''
    });
  };

  window.PackFlowErrors = {
    list: function () { return errors.slice(); },
    clear: clear,
    copy: copy,
    text: text,
    report: function (err) { add({ type: 'manual', message: str(err), file: '', line: 0, column: 0, stack: err && err.stack ? String(err.stack) : '' }); }
  };

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', render);
  } else {
    render();
  }
})();
