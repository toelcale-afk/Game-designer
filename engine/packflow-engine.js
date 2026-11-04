/* PackFlow 2D engine v1: canvas renderer, scenes, input, sound hookups, saves.
 * Loads the Game Designer's JSON (data/units.json, campaign.json, ui.json, sounds.json,
 * save.json) and levels/*.json, with window.GAME_DATA (data/game-data.js) as a fallback.
 * Game rules live in "modes": tactics.js registers 'turnbased'; a real-time mode can be
 * added later with PF.registerMode('realtime', factory) and "mode": "realtime" on a mission.
 */
(function () {
  'use strict';
  var PF = window.PF = window.PF || {};
  PF.version = '1.0';
  PF.modes = PF.modes || {};

  /** Mode factory: function (engine, battle) -> { pointer(type, cell, pos), update(dt), render(ctx), hud(), serialize(), status() } */
  PF.registerMode = function (name, factory) { PF.modes[name] = factory; };

  // ------------------------------------------------------------------ data
  function loadJson(path) {
    return fetch(path, { cache: 'no-store' }).then(function (r) {
      if (!r.ok) throw new Error('HTTP ' + r.status);
      return r.json();
    }).catch(function () { return null; });
  }

  PF.loadData = function () {
    var G = window.GAME_DATA || {};
    var names = ['units', 'campaign', 'ui', 'sounds', 'save'];
    return Promise.all(names.map(function (n) { return loadJson('data/' + n + '.json'); })).then(function (res) {
      var data = { levels: {} };
      names.forEach(function (n, i) { data[n] = res[i] || G[n] || null; });
      var missions = (data.campaign && data.campaign.missions) || [];
      var paths = missions.map(function (m) { return m.level; }).filter(Boolean);
      return Promise.all(paths.map(function (p) { return loadJson(p); })).then(function (lv) {
        paths.forEach(function (p, i) { data.levels[p] = lv[i] || (G.levels && G.levels[p]) || null; });
        return data;
      });
    });
  };

  // ------------------------------------------------------------------ helpers
  PF.clamp = function (v, a, b) { return Math.max(a, Math.min(b, v)); };

  PF.wrapText = function (ctx, text, maxW) {
    var out = [];
    String(text || '').split('\n').forEach(function (para) {
      var line = '';
      para.split(' ').forEach(function (w) {
        var t = line ? line + ' ' + w : w;
        if (ctx.measureText(t).width > maxW && line) { out.push(line); line = w; } else line = t;
      });
      out.push(line);
    });
    return out;
  };

  /** Deterministic random numbers (so a saved battle replays the same). */
  PF.rng = function (seed) {
    var s = (seed >>> 0) || 1;
    return function () { s ^= s << 13; s ^= s >>> 17; s ^= s << 5; return ((s >>> 0) % 100000) / 100000; };
  };

  // ------------------------------------------------------------------ engine
  function Engine(canvas) {
    this.canvas = canvas;
    this.ctx = canvas.getContext('2d');
    this.images = {};
    this.audio = {};
    this.buttons = [];
    this.scene = 'loading';
    this.message = 'Loading…';
    this.settings = { sound: true, music: true };
    this.pointer = null;
    this.lastTime = 0;
    this.battle = null;
    this.data = null;
    this.missionId = null;
    var self = this;
    try { var s = JSON.parse(localStorage.getItem('packflow-settings') || 'null'); if (s) this.settings = s; } catch (e) { /* storage blocked */ }
    this.resize();
    window.addEventListener('resize', function () { self.resize(); });
    this.bindInput();
  }
  PF.Engine = Engine;

  Engine.prototype.resize = function () {
    var dpr = window.devicePixelRatio || 1;
    this.dpr = dpr;
    this.w = this.canvas.clientWidth || window.innerWidth;
    this.h = this.canvas.clientHeight || window.innerHeight;
    this.canvas.width = Math.round(this.w * dpr);
    this.canvas.height = Math.round(this.h * dpr);
  };

  Engine.prototype.start = function () {
    var self = this;
    return PF.loadData().then(function (data) {
      self.data = data;
      if (!data.units || !data.campaign) {
        self.scene = 'error';
        self.message = 'Game data not found. Open the project in the Game Designer and tap Save.';
        return;
      }
      self.unitDefs = {};
      (data.units.units || []).forEach(function (u) { self.unitDefs[u.id] = u; });
      self.factions = {};
      (data.units.factions || []).forEach(function (f) { self.factions[f.id] = f; });
      self.ui = data.ui || { menu: {}, hud: {} };
      self.hooks = (data.sounds && data.sounds.hookups) || {};
      self.saveCfg = data.save || { enabled: false };
      self.preload();
      self.scene = 'menu';
      self.playMusic('music_menu');
    }).catch(function (e) {
      self.scene = 'error';
      self.message = 'Could not start: ' + (e && e.message || e);
      if (window.PackFlowErrors) window.PackFlowErrors.report(e);
    }).then(function () {
      requestAnimationFrame(function (t) { self.frame(t); });
    });
  };

  Engine.prototype.preload = function () {
    var self = this;
    var paths = [];
    Object.keys(this.unitDefs).forEach(function (k) { if (self.unitDefs[k].sprite) paths.push(self.unitDefs[k].sprite); });
    if (this.ui.menu && this.ui.menu.background) paths.push(this.ui.menu.background);
    Object.keys(this.data.levels).forEach(function (p) {
      var l = self.data.levels[p];
      if (l) (l.tiles || []).forEach(function (t) { if (t.image) paths.push(t.image); });
    });
    paths.forEach(function (p) { self.image(p); });
  };

  Engine.prototype.image = function (path) {
    if (!path) return null;
    var img = this.images[path];
    if (!img) {
      img = new Image();
      img.onerror = function () { img.failed = true; };
      img.src = path;
      this.images[path] = img;
    }
    return img.complete && !img.failed && img.naturalWidth ? img : null;
  };

  // ------------------------------------------------------------------ sound
  Engine.prototype.sound = function (action) {
    if (!this.settings.sound) return;
    var path = this.hooks[action];
    if (!path) return;
    try {
      var a = new Audio(path);
      a.volume = 0.8;
      var p = a.play();
      if (p && p.catch) p.catch(function () { /* autoplay blocked until the first tap */ });
    } catch (e) { /* no audio */ }
  };

  Engine.prototype.playMusic = function (action) {
    var path = this.hooks[action];
    if (this.music && this.musicPath === path) return;
    if (this.music) { this.music.pause(); this.music = null; this.musicPath = null; }
    if (!path || !this.settings.music) return;
    try {
      var a = new Audio(path);
      a.loop = true;
      a.volume = 0.45;
      this.music = a;
      this.musicPath = path;
      var p = a.play();
      if (p && p.catch) p.catch(function () { /* resumes on the first tap */ });
    } catch (e) { /* no audio */ }
  };

  // ------------------------------------------------------------------ input
  Engine.prototype.bindInput = function () {
    var self = this;
    var c = this.canvas;
    function pos(e) {
      var r = c.getBoundingClientRect();
      return { x: e.clientX - r.left, y: e.clientY - r.top };
    }
    c.addEventListener('pointerdown', function (e) {
      var p = pos(e);
      self.pointer = { start: p, last: p, moved: false };
      if (self.music && self.music.paused && self.settings.music) self.music.play().catch(function () {});
    });
    c.addEventListener('pointermove', function (e) {
      if (!self.pointer) return;
      var p = pos(e);
      var dx = p.x - self.pointer.last.x;
      var dy = p.y - self.pointer.last.y;
      if (Math.abs(p.x - self.pointer.start.x) + Math.abs(p.y - self.pointer.start.y) > 10) self.pointer.moved = true;
      self.pointer.last = p;
      if (self.pointer.moved && self.scene === 'battle' && self.battle) self.battle.pan(dx, dy);
    });
    c.addEventListener('pointerup', function (e) {
      var p = pos(e);
      var wasDrag = self.pointer && self.pointer.moved;
      self.pointer = null;
      if (!wasDrag) self.tap(p);
    });
    c.addEventListener('pointercancel', function () { self.pointer = null; });
  };

  Engine.prototype.tap = function (p) {
    for (var i = this.buttons.length - 1; i >= 0; i--) {
      var b = this.buttons[i];
      if (p.x >= b.x && p.x <= b.x + b.w && p.y >= b.y && p.y <= b.y + b.h) {
        this.sound('button');
        b.onClick();
        return;
      }
    }
    if (this.scene === 'battle' && this.battle) this.battle.tap(p);
  };

  /** A button for this frame. */
  Engine.prototype.button = function (label, x, y, w, h, onClick, opts) {
    opts = opts || {};
    var ctx = this.ctx;
    ctx.fillStyle = opts.fill || 'rgba(20,26,38,0.92)';
    ctx.strokeStyle = opts.stroke || this.accent();
    ctx.lineWidth = 2;
    roundRect(ctx, x, y, w, h, 10);
    ctx.fill();
    ctx.stroke();
    ctx.fillStyle = opts.color || '#F1F5F9';
    ctx.font = '600 ' + (opts.size || 16) + 'px system-ui, sans-serif';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.fillText(label, x + w / 2, y + h / 2);
    if (!opts.disabled) this.buttons.push({ x: x, y: y, w: w, h: h, onClick: onClick, label: label });
  };

  Engine.prototype.accent = function () { return (this.ui && this.ui.menu && this.ui.menu.accentColor) || '#F3C623'; };

  function roundRect(ctx, x, y, w, h, r) {
    ctx.beginPath();
    ctx.moveTo(x + r, y);
    ctx.arcTo(x + w, y, x + w, y + h, r);
    ctx.arcTo(x + w, y + h, x, y + h, r);
    ctx.arcTo(x, y + h, x, y, r);
    ctx.arcTo(x, y, x + w, y, r);
    ctx.closePath();
  }
  PF.roundRect = roundRect;

  // ------------------------------------------------------------------ campaign
  Engine.prototype.mission = function (id) {
    var ms = (this.data.campaign && this.data.campaign.missions) || [];
    for (var i = 0; i < ms.length; i++) if (ms[i].id === id) return ms[i];
    return null;
  };

  Engine.prototype.firstMission = function () {
    var c = this.data.campaign;
    return (c.start && this.mission(c.start)) || (c.missions || [])[0] || null;
  };

  Engine.prototype.newGame = function () {
    var m = this.firstMission();
    if (!m) { this.scene = 'error'; this.message = 'The campaign has no missions yet.'; return; }
    this.missionId = m.id;
    this.scene = 'briefing';
  };

  Engine.prototype.startBattle = function (restoreState) {
    var m = this.mission(this.missionId);
    var level = m && this.data.levels[m.level];
    if (!level) { this.scene = 'error'; this.message = 'Mission "' + (m && m.name) + '" has no level.'; return; }
    var factory = PF.modes[m.mode || 'turnbased'];
    if (!factory) { this.scene = 'error'; this.message = 'Game mode "' + (m.mode || 'turnbased') + '" is not available.'; return; }
    this.battle = new Battle(this, m, level, factory, restoreState);
    this.scene = 'battle';
    if (!restoreState) this.autosave();
    this.playMusic('music_battle');
  };

  Engine.prototype.finishBattle = function (won) {
    this.result = won;
    this.sound(won ? 'victory' : 'defeat');
    this.scene = 'result';
    this.clearAutosave();
  };

  // ------------------------------------------------------------------ saves
  Engine.prototype.saveKey = function (slot) { return (this.saveCfg.storageKey || 'packflow-save') + ':' + slot; };

  Engine.prototype.saveTo = function (slot) {
    if (!this.saveCfg.enabled || !this.battle) return false;
    try {
      localStorage.setItem(this.saveKey(slot), JSON.stringify({
        mission: this.missionId, time: Date.now(), battle: this.battle.serialize()
      }));
      return true;
    } catch (e) { return false; }
  };

  Engine.prototype.loadFrom = function (slot) {
    try {
      var s = JSON.parse(localStorage.getItem(this.saveKey(slot)) || 'null');
      if (!s || !this.mission(s.mission)) return false;
      this.missionId = s.mission;
      this.startBattle(s.battle);
      return true;
    } catch (e) { return false; }
  };

  Engine.prototype.hasSave = function (slot) {
    try { return !!localStorage.getItem(this.saveKey(slot)); } catch (e) { return false; }
  };

  Engine.prototype.latestSlot = function () {
    var best = null, bestT = 0;
    var slots = ['auto'];
    for (var i = 1; i <= (this.saveCfg.slots || 0); i++) slots.push(String(i));
    for (var j = 0; j < slots.length; j++) {
      try {
        var s = JSON.parse(localStorage.getItem(this.saveKey(slots[j])) || 'null');
        if (s && s.time > bestT) { bestT = s.time; best = slots[j]; }
      } catch (e) { /* ignore */ }
    }
    return best;
  };

  Engine.prototype.autosave = function () { if (this.saveCfg.enabled && this.saveCfg.autosaveEachTurn) this.saveTo('auto'); };
  Engine.prototype.clearAutosave = function () { try { localStorage.removeItem(this.saveKey('auto')); } catch (e) { /* ignore */ } };

  // ------------------------------------------------------------------ battle wrapper
  function Battle(engine, mission, level, factory, restore) {
    this.engine = engine;
    this.mission = mission;
    this.level = level;
    this.ts = level.tileSize || 32;
    this.offset = { x: 0, y: 0 };
    this.zoomFit = true;
    this.mode = factory(engine, this, restore || null);
    this.paused = false;
  }
  PF.Battle = Battle;

  Battle.prototype.hudHeight = function () {
    var h = this.engine.ui.hud || {};
    return Math.round(64 * (h.scale || 1));
  };

  Battle.prototype.layout = function () {
    var e = this.engine;
    var hud = this.hudHeight();
    var top = (e.ui.hud && e.ui.hud.position === 'top') ? hud : 0;
    var availW = e.w, availH = e.h - hud;
    var cell = Math.max(28, Math.floor(Math.min(availW / this.level.width, availH / this.level.height)));
    var mapW = cell * this.level.width, mapH = cell * this.level.height;
    var ox = mapW <= availW ? (availW - mapW) / 2 : PF.clamp(this.offset.x, availW - mapW, 0);
    var oy = mapH <= availH ? top + (availH - mapH) / 2 : top + PF.clamp(this.offset.y, availH - mapH, 0);
    return { cell: cell, ox: ox, oy: oy, hudY: top ? 0 : e.h - hud, hudH: hud, mapW: mapW, mapH: mapH };
  };

  Battle.prototype.pan = function (dx, dy) { this.offset.x += dx; this.offset.y += dy; };

  Battle.prototype.cellAt = function (p) {
    var L = this.layout();
    if (p.y >= L.hudY && p.y <= L.hudY + L.hudH) return null;
    var x = Math.floor((p.x - L.ox) / L.cell), y = Math.floor((p.y - L.oy) / L.cell);
    if (x < 0 || y < 0 || x >= this.level.width || y >= this.level.height) return null;
    return { x: x, y: y };
  };

  Battle.prototype.tap = function (p) {
    if (this.paused) return;
    var c = this.cellAt(p);
    if (c && this.mode.pointer) this.mode.pointer('tap', c, p);
  };

  Battle.prototype.tile = function (x, y) {
    var l = this.level;
    var row = l.grid[y] || [];
    return l.tiles[row[x] || 0] || { walkable: true, moveCost: 1, cover: 0, color: '#3F6B2F' };
  };

  Battle.prototype.serialize = function () { return this.mode.serialize ? this.mode.serialize() : null; };

  Battle.prototype.drawMap = function (ctx, L) {
    var l = this.level, e = this.engine;
    for (var y = 0; y < l.height; y++) {
      for (var x = 0; x < l.width; x++) {
        var t = this.tile(x, y);
        var px = L.ox + x * L.cell, py = L.oy + y * L.cell;
        var img = t.image && e.image(t.image);
        if (img) {
          ctx.imageSmoothingEnabled = false;
          ctx.drawImage(img, px, py, L.cell + 1, L.cell + 1);
        } else {
          ctx.fillStyle = t.color || '#3F6B2F';
          ctx.fillRect(px, py, L.cell + 1, L.cell + 1);
        }
      }
    }
    ctx.strokeStyle = 'rgba(0,0,0,0.18)';
    ctx.lineWidth = 1;
    for (var gx = 0; gx <= l.width; gx++) { ctx.beginPath(); ctx.moveTo(L.ox + gx * L.cell, L.oy); ctx.lineTo(L.ox + gx * L.cell, L.oy + L.mapH); ctx.stroke(); }
    for (var gy = 0; gy <= l.height; gy++) { ctx.beginPath(); ctx.moveTo(L.ox, L.oy + gy * L.cell); ctx.lineTo(L.ox + L.mapW, L.oy + gy * L.cell); ctx.stroke(); }
    (l.objects || []).forEach(function (o) {
      var px = L.ox + o.x * L.cell, py = L.oy + o.y * L.cell;
      var img = o.image && e.image(o.image);
      if (img) ctx.drawImage(img, px, py, L.cell, L.cell);
      else {
        ctx.fillStyle = o.type === 'objective' ? '#F3C623' : '#6B5B3E';
        ctx.fillRect(px + L.cell * 0.2, py + L.cell * 0.2, L.cell * 0.6, L.cell * 0.6);
        ctx.strokeStyle = '#000';
        ctx.strokeRect(px + L.cell * 0.2, py + L.cell * 0.2, L.cell * 0.6, L.cell * 0.6);
      }
    });
  };

  // ------------------------------------------------------------------ frame + scenes
  Engine.prototype.frame = function (t) {
    var self = this;
    var dt = Math.min(0.05, (t - (this.lastTime || t)) / 1000);
    this.lastTime = t;
    var ctx = this.ctx;
    ctx.setTransform(this.dpr, 0, 0, this.dpr, 0, 0);
    ctx.fillStyle = '#0B1120';
    ctx.fillRect(0, 0, this.w, this.h);
    this.buttons = [];
    try {
      var scene = this.scenes[this.scene] || this.scenes.error;
      scene.call(this, ctx, dt);
    } catch (e) {
      this.scene = 'error';
      this.message = 'Error: ' + e.message;
      console.error(e);
    }
    requestAnimationFrame(function (tt) { self.frame(tt); });
  };

  function title(ctx, text, x, y, size, color) {
    ctx.fillStyle = color;
    ctx.font = '800 ' + size + 'px system-ui, sans-serif';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.fillText(text, x, y);
  }

  Engine.prototype.scenes = {
    loading: function (ctx) { title(ctx, this.message, this.w / 2, this.h / 2, 18, '#94A3B8'); },

    error: function (ctx) {
      var self = this;
      ctx.font = '16px system-ui, sans-serif';
      var lines = PF.wrapText(ctx, this.message, Math.min(560, this.w - 40));
      ctx.fillStyle = '#FCA5A5';
      ctx.textAlign = 'center';
      lines.forEach(function (l, i) { ctx.fillText(l, self.w / 2, self.h / 2 - 30 + i * 22); });
      if (this.data) this.button('Menu', this.w / 2 - 70, this.h / 2 + 40 + lines.length * 10, 140, 44, function () { self.scene = 'menu'; });
    },

    menu: function (ctx) {
      var self = this, m = this.ui.menu || {};
      var bg = m.background && this.image(m.background);
      if (bg) {
        var k = Math.max(this.w / bg.naturalWidth, this.h / bg.naturalHeight);
        ctx.drawImage(bg, (this.w - bg.naturalWidth * k) / 2, (this.h - bg.naturalHeight * k) / 2, bg.naturalWidth * k, bg.naturalHeight * k);
        ctx.fillStyle = 'rgba(5,8,14,0.55)';
        ctx.fillRect(0, 0, this.w, this.h);
      }
      title(ctx, m.title || 'My Game', this.w / 2, this.h * 0.24, Math.min(54, this.w / 12), this.accent());
      if (m.subtitle) title(ctx, m.subtitle, this.w / 2, this.h * 0.24 + 46, 18, '#CBD5E1');
      var bw = Math.min(280, this.w - 60), bx = (this.w - bw) / 2, y = this.h * 0.42;
      this.button('New game', bx, y, bw, 52, function () { self.newGame(); }); y += 64;
      var slot = this.saveCfg.enabled ? this.latestSlot() : null;
      if (m.showContinue !== false && this.saveCfg.enabled) {
        this.button('Continue', bx, y, bw, 52, function () { if (slot) self.loadFrom(slot); }, { disabled: !slot, color: slot ? '#F1F5F9' : '#64748B' }); y += 64;
      }
      if (m.showSettings !== false) { this.button('Settings', bx, y, bw, 52, function () { self.scene = 'settings'; }); y += 64; }
      if (m.showCredits) { this.button('Credits', bx, y, bw, 52, function () { self.scene = 'credits'; }); }
    },

    settings: function (ctx) {
      var self = this, bw = Math.min(280, this.w - 60), bx = (this.w - bw) / 2;
      title(ctx, 'Settings', this.w / 2, this.h * 0.22, 32, this.accent());
      this.button('Sound effects: ' + (this.settings.sound ? 'on' : 'off'), bx, this.h * 0.36, bw, 52, function () { self.settings.sound = !self.settings.sound; self.storeSettings(); });
      this.button('Music: ' + (this.settings.music ? 'on' : 'off'), bx, this.h * 0.36 + 64, bw, 52, function () {
        self.settings.music = !self.settings.music; self.storeSettings();
        if (!self.settings.music && self.music) { self.music.pause(); self.music = null; self.musicPath = null; } else self.playMusic('music_menu');
      });
      this.button('Back', bx, this.h * 0.36 + 128, bw, 52, function () { self.scene = 'menu'; });
    },

    credits: function (ctx) {
      var self = this;
      title(ctx, 'Credits', this.w / 2, this.h * 0.18, 32, this.accent());
      ctx.font = '16px system-ui, sans-serif';
      ctx.fillStyle = '#E2E8F0';
      ctx.textAlign = 'center';
      PF.wrapText(ctx, (this.ui.menu && this.ui.menu.credits) || 'Made with PackFlow Studio.', Math.min(560, this.w - 40)).forEach(function (l, i) { ctx.fillText(l, self.w / 2, self.h * 0.3 + i * 22); });
      this.button('Back', this.w / 2 - 90, this.h - 90, 180, 50, function () { self.scene = 'menu'; });
    },

    briefing: function (ctx) {
      var self = this, m = this.mission(this.missionId);
      title(ctx, m.name, this.w / 2, 70, 30, this.accent());
      var maxW = Math.min(640, this.w - 40);
      ctx.font = '17px system-ui, sans-serif';
      ctx.fillStyle = '#E2E8F0';
      ctx.textAlign = 'left';
      ctx.textBaseline = 'top';
      var y = 120;
      PF.wrapText(ctx, m.briefing || '', maxW).forEach(function (l) { ctx.fillText(l, (self.w - maxW) / 2, y); y += 24; });
      ctx.fillStyle = '#FCD34D';
      ctx.fillText('Objective: ' + PF.objectiveText(m), (this.w - maxW) / 2, y + 14);
      this.button('Start mission', this.w / 2 - 110, this.h - 100, 220, 56, function () { self.startBattle(); });
    },

    battle: function (ctx, dt) {
      var b = this.battle;
      if (b.mode.update) b.mode.update(dt);
      var L = b.layout();
      b.drawMap(ctx, L);
      if (b.mode.render) b.mode.render(ctx, L);
      this.drawHud(ctx, L);
      if (b.paused) this.drawPause(ctx);
    },

    result: function (ctx) {
      var self = this, m = this.mission(this.missionId);
      title(ctx, this.result ? 'Victory' : 'Defeat', this.w / 2, this.h * 0.3, 48, this.result ? this.accent() : '#EF4444');
      title(ctx, m.name, this.w / 2, this.h * 0.3 + 50, 18, '#CBD5E1');
      var bw = Math.min(260, this.w - 60), bx = (this.w - bw) / 2, y = this.h * 0.5;
      if (this.result && m.next && this.mission(m.next)) {
        this.button('Next mission', bx, y, bw, 52, function () { self.missionId = m.next; self.scene = 'briefing'; }); y += 64;
      } else if (this.result) {
        title(ctx, 'Campaign complete', this.w / 2, y - 20, 20, '#A7F3D0'); y += 20;
      }
      if (!this.result) { this.button('Retry', bx, y, bw, 52, function () { self.startBattle(); }); y += 64; }
      this.button('Main menu', bx, y, bw, 52, function () { self.scene = 'menu'; self.playMusic('music_menu'); });
    }
  };

  Engine.prototype.storeSettings = function () {
    try { localStorage.setItem('packflow-settings', JSON.stringify(this.settings)); } catch (e) { /* ignore */ }
  };

  Engine.prototype.drawHud = function (ctx, L) {
    var self = this, b = this.battle, hud = this.ui.hud || {};
    var y = L.hudY, h = L.hudH, k = hud.scale || 1;
    ctx.fillStyle = 'rgba(11,17,32,0.94)';
    ctx.fillRect(0, y, this.w, h);
    ctx.fillStyle = this.accent();
    ctx.fillRect(0, hud.position === 'top' ? y + h - 2 : y, this.w, 2);
    var info = b.mode.hud ? b.mode.hud() : {};
    ctx.textAlign = 'left';
    ctx.textBaseline = 'middle';
    var x = 12;
    if (hud.showTurnCounter !== false) {
      ctx.fillStyle = '#F1F5F9';
      ctx.font = '700 ' + Math.round(16 * k) + 'px system-ui, sans-serif';
      ctx.fillText('Turn ' + (info.turn || 1), x, y + h * 0.32);
      ctx.font = Math.round(12 * k) + 'px system-ui, sans-serif';
      ctx.fillStyle = info.playerTurn ? '#93C5FD' : '#FCA5A5';
      ctx.fillText(info.playerTurn ? 'Your turn' : 'Enemy turn', x, y + h * 0.7);
      x += 96 * k;
    }
    if (hud.showUnitPanel !== false && info.unit) {
      ctx.fillStyle = '#E2E8F0';
      ctx.font = '600 ' + Math.round(14 * k) + 'px system-ui, sans-serif';
      ctx.fillText(info.unit.name + '  HP ' + info.unit.hp + '/' + info.unit.max, x, y + h * 0.32);
      ctx.font = Math.round(12 * k) + 'px system-ui, sans-serif';
      ctx.fillStyle = '#94A3B8';
      ctx.fillText('Atk ' + info.unit.attack + ' · Range ' + info.unit.range + ' · Move ' + info.unit.movement + ' · Def ' + info.unit.defense, x, y + h * 0.7);
    } else if (hud.showObjective !== false) {
      ctx.fillStyle = '#FCD34D';
      ctx.font = Math.round(13 * k) + 'px system-ui, sans-serif';
      ctx.fillText(PF.objectiveText(b.mission), x, y + h / 2);
    }
    var bh = h - 16, bw = 110 * k;
    if (hud.showEndTurnButton !== false && info.playerTurn) {
      this.button('End turn', this.w - bw - 70 * k, y + 8, bw, bh, function () { b.mode.endTurn(); }, { size: Math.round(15 * k) });
    }
    this.button('☰', this.w - 58 * k, y + 8, 48 * k, bh, function () { b.paused = true; }, { size: Math.round(18 * k) });
  };

  Engine.prototype.drawPause = function (ctx) {
    var self = this, b = this.battle;
    ctx.fillStyle = 'rgba(0,0,0,0.65)';
    ctx.fillRect(0, 0, this.w, this.h);
    var bw = Math.min(260, this.w - 60), bx = (this.w - bw) / 2, y = this.h * 0.22;
    title(ctx, 'Paused', this.w / 2, y - 30, 30, this.accent());
    this.button('Resume', bx, y, bw, 48, function () { b.paused = false; }); y += 58;
    if (this.saveCfg.enabled) {
      for (var i = 1; i <= Math.min(this.saveCfg.slots || 1, 5); i++) {
        (function (slot) {
          self.button('Save to slot ' + slot + (self.hasSave(slot) ? ' (overwrite)' : ''), bx, y, bw, 44, function () {
            self.flash = self.saveTo(String(slot)) ? 'Saved to slot ' + slot : 'Could not save';
          }, { size: 14 });
        })(i);
        y += 52;
      }
    }
    this.button('Restart mission', bx, y, bw, 44, function () { self.startBattle(); }, { size: 14 }); y += 52;
    this.button('Quit to menu', bx, y, bw, 44, function () { self.scene = 'menu'; self.playMusic('music_menu'); }, { size: 14 });
    if (this.flash) title(ctx, this.flash, this.w / 2, y + 70, 14, '#A7F3D0');
  };

  PF.objectiveText = function (m) {
    var v = (m.win && m.win.value) || '';
    switch (m.win && m.win.type) {
      case 'SURVIVE_TURNS': return 'Survive ' + v + ' turns';
      case 'REACH_TILE': return 'Reach tile ' + v;
      case 'KILL_UNIT': return 'Destroy every ' + v;
      default: return 'Destroy all enemies';
    }
  };

  /** Boots the game on a canvas: PF.boot(document.getElementById('game')). */
  PF.boot = function (canvas) {
    var e = new Engine(canvas);
    PF.engine = e;
    e.start();
    return e;
  };
})();
