/* PackFlow grid tactics mode ('turnbased'): select a unit, move it, attack in range,
 * end turn; the enemy AI attacks the weakest target in range or advances toward you.
 * Teams in levels: 0 = player, 3 = ally (player-controlled), 1 = enemy, 2 = neutral (passive).
 */
(function () {
  'use strict';
  var PF = window.PF;

  function friendly(team) { return team === 0 || team === 3; }
  function hostile(a, b) { return (friendly(a) && b === 1) || (a === 1 && friendly(b)); }
  function dist(a, b) { return Math.abs(a.x - b.x) + Math.abs(a.y - b.y); }

  PF.registerMode('turnbased', function (engine, battle, restore) {
    var level = battle.level, mission = battle.mission;
    var defs = engine.unitDefs;
    var rand = PF.rng(restore ? restore.seed : (Date.now() & 0xffffff));
    var seed = restore ? restore.seed : 0;
    var units = [], uid = 1, turn = 1, playerTurn = true, selected = null;
    var reach = null, targets = [], floaters = [], anims = [], aiQueue = [], aiTimer = 0, over = false;

    function def(id) {
      return defs[id] || { id: id, name: id, health: 10, movement: 4, attack: 3, range: 1, defense: 0, faction: '' };
    }
    function makeUnit(id, team, x, y) {
      var d = def(id);
      return { uid: uid++, id: id, team: team, x: x, y: y, hp: d.health || 10, moved: false, acted: false };
    }

    if (restore) {
      turn = restore.turn; playerTurn = true;
      restore.units.forEach(function (u) { units.push({ uid: uid++, id: u.id, team: u.team, x: u.x, y: u.y, hp: u.hp, moved: !!u.moved, acted: !!u.acted }); });
    } else {
      (level.units || []).forEach(function (u) { units.push(makeUnit(u.unit, u.team, u.x, u.y)); });
      // Teams with spawn points but no placed units get their faction's units.
      [0, 1].forEach(function (team) {
        if (units.some(function (u) { return u.team === team; })) return;
        var faction = team === 0 ? mission.playerFaction : mission.enemyFaction;
        var pool = Object.keys(defs).filter(function (k) { return defs[k].faction === faction; });
        (level.spawns || []).filter(function (s) { return s.team === team; }).forEach(function (s, i) {
          if (pool.length) units.push(makeUnit(pool[i % pool.length], team, s.x, s.y));
        });
      });
    }

    function unitAt(x, y) {
      for (var i = 0; i < units.length; i++) if (units[i].x === x && units[i].y === y && units[i].hp > 0) return units[i];
      return null;
    }
    function blockedByObject(x, y) {
      return (level.objects || []).some(function (o) { return o.x === x && o.y === y && o.blocking !== false && o.type !== 'objective' && o.type !== 'pickup'; });
    }

    /** Cells a unit can reach this turn: map "x,y" -> {cost, from}. Allies can be passed, not stood on. */
    function reachable(u) {
      var out = {}, d = def(u.id), max = d.movement;
      var key = function (x, y) { return x + ',' + y; };
      out[key(u.x, u.y)] = { cost: 0, from: null, x: u.x, y: u.y };
      var open = [out[key(u.x, u.y)]];
      while (open.length) {
        open.sort(function (a, b) { return a.cost - b.cost; });
        var cur = open.shift();
        [[1, 0], [-1, 0], [0, 1], [0, -1]].forEach(function (dv) {
          var nx = cur.x + dv[0], ny = cur.y + dv[1];
          if (nx < 0 || ny < 0 || nx >= level.width || ny >= level.height) return;
          var t = battle.tile(nx, ny);
          if (t.walkable === false || blockedByObject(nx, ny)) return;
          var occ = unitAt(nx, ny);
          if (occ && hostile(u.team, occ.team)) return;
          if (occ && occ.team === 2) return;
          var c = cur.cost + Math.max(1, t.moveCost || 1);
          if (c > max) return;
          var k = key(nx, ny);
          if (!out[k] || out[k].cost > c) {
            out[k] = { cost: c, from: cur, x: nx, y: ny };
            open.push(out[k]);
          }
        });
      }
      Object.keys(out).forEach(function (k) { var c = out[k]; var o = unitAt(c.x, c.y); if (o && o !== u) out[k].occupied = true; });
      return out;
    }

    function targetsFor(u) {
      if (u.acted) return [];
      var r = def(u.id).range || 1;
      return units.filter(function (o) { return o.hp > 0 && hostile(u.team, o.team) && dist(u, o) <= r; });
    }

    function path(cell) {
      var p = [];
      while (cell) { p.unshift({ x: cell.x, y: cell.y }); cell = cell.from; }
      return p;
    }

    function moveUnit(u, cell, done) {
      var steps = path(cell);
      engine.sound('move');
      anims.push({ unit: u, steps: steps, i: 0, t: 0, done: done });
      u.moved = true;
    }

    function attack(a, t) {
      var da = def(a.id), dt = def(t.id);
      var cover = battle.tile(t.x, t.y).cover || 0;
      var roll = Math.floor(rand() * 3) - 1; // -1, 0 or +1
      var dmg = Math.max(1, (da.attack || 1) - (dt.defense || 0) - cover + roll);
      t.hp -= dmg;
      a.acted = true;
      a.moved = true;
      engine.sound('attack');
      floaters.push({ x: t.x, y: t.y, text: '-' + dmg, t: 0, color: '#FCA5A5' });
      if (t.hp <= 0) {
        engine.sound('death');
        floaters.push({ x: t.x, y: t.y, text: 'destroyed', t: 0, color: '#F87171' });
      } else {
        engine.sound('hit');
      }
      units = units.filter(function (u) { return u.hp > 0; });
      check();
    }

    function select(u) {
      selected = u;
      info = null;
      reach = u && !u.moved ? reachable(u) : null;
      targets = u ? targetsFor(u) : [];
      if (u) engine.sound('select');
    }

    function check() {
      if (over) return;
      var players = units.filter(function (u) { return friendly(u.team); });
      var enemies = units.filter(function (u) { return u.team === 1; });
      var win = mission.win || { type: 'ELIMINATE_ALL' }, lose = mission.lose || { type: 'ALL_UNITS_LOST' };
      var won = false, lost = false;
      switch (win.type) {
        case 'SURVIVE_TURNS': won = turn > (parseInt(win.value, 10) || 5); break;
        case 'REACH_TILE':
          var xy = String(win.value || '').split(',').map(function (s) { return parseInt(s, 10); });
          won = players.some(function (u) { return u.x === xy[0] && u.y === xy[1]; });
          break;
        case 'KILL_UNIT': won = !enemies.some(function (u) { return u.id === win.value; }); break;
        default: won = enemies.length === 0;
      }
      if (lose.type === 'TURN_LIMIT') lost = turn > (parseInt(lose.value, 10) || 99);
      else if (lose.type === 'UNIT_LOST') lost = !players.some(function (u) { return u.id === lose.value; });
      lost = lost || players.length === 0;
      if (lost || won) {
        over = true;
        setTimeout(function () { engine.finishBattle(!lost && won); }, 700);
      }
    }

    function endTurn() {
      if (!playerTurn || over || anims.length) return;
      select(null);
      playerTurn = false;
      aiQueue = units.filter(function (u) { return u.team === 1; }).map(function (u) { return u.uid; });
      aiTimer = 0.35;
    }

    function startPlayerTurn() {
      turn += 1;
      playerTurn = true;
      units.forEach(function (u) { u.moved = false; u.acted = false; });
      engine.sound('turn_start');
      check();
      if (!over) engine.autosave();
    }

    /** One enemy unit's move: attack the weakest target in range, else advance and try again. */
    function aiAct(u) {
      var inRange = targetsFor(u);
      if (inRange.length) {
        inRange.sort(function (a, b) { return a.hp - b.hp; });
        attack(u, inRange[0]);
        return;
      }
      var foes = units.filter(function (o) { return hostile(u.team, o.team); });
      if (!foes.length) return;
      var r = reachable(u), best = null, bestScore = Infinity, range = def(u.id).range || 1;
      Object.keys(r).forEach(function (k) {
        var c = r[k];
        if (c.occupied) return;
        var nearest = Infinity;
        foes.forEach(function (f) { nearest = Math.min(nearest, dist(c, f)); });
        var score = nearest <= range ? -100 + c.cost : nearest * 10 + c.cost;
        if (score < bestScore) { bestScore = score; best = c; }
      });
      if (best && (best.x !== u.x || best.y !== u.y)) {
        moveUnit(u, best, function () {
          var t = targetsFor(u);
          if (t.length) { t.sort(function (a, b) { return a.hp - b.hp; }); attack(u, t[0]); }
        });
      }
    }

    var info = null;

    function pointer(type, cell) {
      if (!playerTurn || over || anims.length) return;
      var u = unitAt(cell.x, cell.y);
      if (selected && u && hostile(selected.team, u.team) && targets.indexOf(u) >= 0) {
        attack(selected, u);
        select(null);
        return;
      }
      if (u && friendly(u.team)) { select(u === selected ? null : u); return; }
      if (selected && reach && !selected.moved) {
        var c = reach[cell.x + ',' + cell.y];
        if (c && !c.occupied && c.cost > 0) {
          var s = selected;
          moveUnit(s, c, function () { select(s); });
          reach = null;
          check();
          return;
        }
      }
      if (u && u.team === 1) { selected = null; reach = null; targets = []; info = u; return; }
      select(null);
    }

    function update(dt) {
      // movement animation
      if (anims.length) {
        var a = anims[0];
        a.t += dt * 9;
        while (a.t >= 1 && a.i < a.steps.length - 1) { a.t -= 1; a.i++; }
        var s = a.steps[a.i];
        a.unit.x = s.x; a.unit.y = s.y;
        if (a.i >= a.steps.length - 1) { anims.shift(); if (a.done) a.done(); check(); }
        return;
      }
      floaters.forEach(function (f) { f.t += dt; });
      floaters = floaters.filter(function (f) { return f.t < 1.2; });
      if (!playerTurn && !over) {
        aiTimer -= dt;
        if (aiTimer > 0) return;
        if (aiQueue.length) {
          var id = aiQueue.shift();
          var u = units.filter(function (x) { return x.uid === id; })[0];
          if (u) aiAct(u);
          aiTimer = 0.45;
        } else {
          startPlayerTurn();
        }
      }
    }

    function drawUnit(ctx, L, u) {
      var d = def(u.id), px = L.ox + u.x * L.cell, py = L.oy + u.y * L.cell;
      var f = engine.factions[d.faction];
      var color = u.team === 1 ? '#DC2626' : u.team === 2 ? '#9CA3AF' : '#3B82F6';
      var img = d.sprite && engine.image(d.sprite);
      if (img) {
        ctx.imageSmoothingEnabled = false;
        var k = Math.min(L.cell / img.naturalWidth, L.cell / img.naturalHeight);
        var w = img.naturalWidth * k, h = img.naturalHeight * k;
        ctx.fillStyle = color + '55';
        ctx.beginPath(); ctx.ellipse(px + L.cell / 2, py + L.cell * 0.88, L.cell * 0.34, L.cell * 0.1, 0, 0, Math.PI * 2); ctx.fill();
        if (u.team === 1) { ctx.save(); ctx.translate(px + L.cell, py); ctx.scale(-1, 1); ctx.drawImage(img, (L.cell - w) / 2, L.cell - h, w, h); ctx.restore(); }
        else ctx.drawImage(img, px + (L.cell - w) / 2, py + L.cell - h, w, h);
      } else {
        ctx.fillStyle = (f && f.color) || color;
        ctx.beginPath(); ctx.arc(px + L.cell / 2, py + L.cell / 2, L.cell * 0.34, 0, Math.PI * 2); ctx.fill();
        ctx.lineWidth = 3; ctx.strokeStyle = color; ctx.stroke();
        ctx.fillStyle = '#fff';
        ctx.font = '700 ' + Math.round(L.cell * 0.36) + 'px system-ui, sans-serif';
        ctx.textAlign = 'center'; ctx.textBaseline = 'middle';
        ctx.fillText((d.name || u.id).charAt(0).toUpperCase(), px + L.cell / 2, py + L.cell / 2 + 1);
      }
      var max = d.health || 10;
      ctx.fillStyle = '#111';
      ctx.fillRect(px + 3, py + 2, L.cell - 6, 4);
      ctx.fillStyle = u.hp / max > 0.5 ? '#22C55E' : u.hp / max > 0.25 ? '#EAB308' : '#EF4444';
      ctx.fillRect(px + 3, py + 2, (L.cell - 6) * Math.max(0, u.hp) / max, 4);
      if (playerTurn && friendly(u.team) && u.acted) { ctx.fillStyle = 'rgba(0,0,0,0.35)'; ctx.fillRect(px, py, L.cell, L.cell); }
    }

    function render(ctx, L) {
      if (reach) {
        ctx.fillStyle = 'rgba(59,130,246,0.28)';
        Object.keys(reach).forEach(function (k) { var c = reach[k]; if (!c.occupied) ctx.fillRect(L.ox + c.x * L.cell + 1, L.oy + c.y * L.cell + 1, L.cell - 2, L.cell - 2); });
      }
      ctx.strokeStyle = '#EF4444'; ctx.lineWidth = 3;
      targets.forEach(function (t) { ctx.strokeRect(L.ox + t.x * L.cell + 2, L.oy + t.y * L.cell + 2, L.cell - 4, L.cell - 4); });
      if (mission.win && mission.win.type === 'REACH_TILE') {
        var xy = String(mission.win.value || '').split(',').map(function (s) { return parseInt(s, 10); });
        ctx.strokeStyle = '#F3C623'; ctx.setLineDash([6, 4]);
        ctx.strokeRect(L.ox + xy[0] * L.cell + 2, L.oy + xy[1] * L.cell + 2, L.cell - 4, L.cell - 4); ctx.setLineDash([]);
      }
      units.forEach(function (u) { drawUnit(ctx, L, u); });
      if (selected) { ctx.strokeStyle = '#F3C623'; ctx.lineWidth = 3; ctx.strokeRect(L.ox + selected.x * L.cell + 1, L.oy + selected.y * L.cell + 1, L.cell - 2, L.cell - 2); }
      floaters.forEach(function (f) {
        ctx.globalAlpha = Math.max(0, 1 - f.t / 1.2);
        ctx.fillStyle = f.color; ctx.font = '800 ' + Math.round(L.cell * 0.4) + 'px system-ui, sans-serif'; ctx.textAlign = 'center';
        ctx.fillText(f.text, L.ox + (f.x + 0.5) * L.cell, L.oy + f.y * L.cell - f.t * 24);
        ctx.globalAlpha = 1;
      });
    }

    function hud() {
      var u = selected || info, d = u && def(u.id);
      return {
        turn: turn, playerTurn: playerTurn && !over,
        unit: u ? { name: d.name || u.id, hp: Math.max(0, u.hp), max: d.health, attack: d.attack, range: d.range, movement: d.movement, defense: d.defense || 0 } : null
      };
    }

    function serialize() {
      return { mode: 'turnbased', seed: seed || Math.floor(rand() * 1e6), turn: turn, units: units.map(function (u) { return { id: u.id, team: u.team, x: u.x, y: u.y, hp: u.hp, moved: u.moved, acted: u.acted }; }) };
    }

    check();
    // Exposed for tests and debugging: PF.engine.battle.mode.debug
    return {
      pointer: pointer, update: update, render: render, hud: hud, serialize: serialize, endTurn: endTurn,
      debug: { units: function () { return units; }, turn: function () { return turn; }, select: select, attack: attack, isOver: function () { return over; }, playerTurn: function () { return playerTurn; } }
    };
  });

  // Real-time mode hook: missions with "mode": "realtime" use this. Replace it with a full
  // implementation later; it receives the same engine/battle objects as 'turnbased'.
  if (!PF.modes.realtime) {
    PF.registerMode('realtime', function () {
      return {
        render: function (ctx, L) {
          ctx.fillStyle = 'rgba(0,0,0,0.6)'; ctx.fillRect(L.ox, L.oy, L.mapW, 40);
          ctx.fillStyle = '#FCD34D'; ctx.font = '14px system-ui, sans-serif'; ctx.textAlign = 'left';
          ctx.fillText('Real-time mode is not built yet (engine hook: PF.registerMode("realtime", …)).', L.ox + 10, L.oy + 24);
        },
        hud: function () { return { turn: 0, playerTurn: false }; },
        endTurn: function () {},
        serialize: function () { return null; }
      };
    });
  }
})();
