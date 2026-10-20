/* Spell pad (draw across a circle of runes) and the rune vault door animation.
   Runes are drawn from straight strokes in the style of old carved alphabets; all shapes are original. */
(function (root) {
  const RM = root.matchMedia && root.matchMedia("(prefers-reduced-motion: reduce)").matches;

  /* 8 runes around the circle and one in the middle; each is a list of strokes in a -1..1 box */
  const RUNES = [
    [[0,-1,0,1],[0,-.6,.6,-1],[0,-.1,.6,-.5]],
    [[-.5,1,-.5,-1],[-.5,-1,.5,-.35],[.5,-.35,.5,1]],
    [[-.35,-1,-.35,1],[-.35,-.5,.4,0],[.4,0,-.35,.5]],
    [[0,1,0,-1],[0,-.15,-.6,-.85],[0,-.15,.6,-.85]],
    [[.5,-.85,-.4,0],[-.4,0,.5,.85]],
    [[-.5,-1,-.5,1],[.5,-1,.5,1],[-.5,-1,.5,-.1],[.5,-1,-.5,-.1]],
    [[0,-1,.55,-.35],[.55,-.35,0,.3],[0,.3,-.55,-.35],[-.55,-.35,0,-1],[-.25,0,-.6,1],[.25,0,.6,1]],
    [[0,1,0,-1],[0,-1,-.55,-.4],[0,-1,.55,-.4]],
    [[-.7,0,.7,0],[0,-.7,0,.7],[-.45,-.45,.45,.45],[.45,-.45,-.45,.45]]
  ];

  function strokeRune(cx, x, y, s, i) {
    cx.beginPath();
    for (const [a, b, c, d] of RUNES[i]) { cx.moveTo(x + a * s, y + b * s); cx.lineTo(x + c * s, y + d * s); }
    if (i === 8) { cx.moveTo(x + s * .95, y); cx.arc(x, y, s * .95, 0, Math.PI * 2); }
    cx.stroke();
  }

  function runeSVG(i, s) {
    let d = RUNES[i].map(([a, b, c, e]) => `M${(a * s).toFixed(1)} ${(b * s).toFixed(1)}L${(c * s).toFixed(1)} ${(e * s).toFixed(1)}`).join("");
    if (i === 8) d += `M${(s * .95).toFixed(1)} 0A${(s * .95).toFixed(1)} ${(s * .95).toFixed(1)} 0 1 1 ${(s * .95 - .01).toFixed(2)} 0`;
    return d;
  }

  /* ---------- Spell pad ---------- */
  function SpellPad(canvas, onSpell) {
    const cx = canvas.getContext("2d");
    let W = 0, dpr = 1, pts = [], seq = [], drawing = false, finger = null, sparks = [], flash = 0, fizzle = 0, raf = 0, glow = new Array(9).fill(0), enabled = true;
    function layout() {
      dpr = Math.min(2, root.devicePixelRatio || 1);
      W = canvas.clientWidth; canvas.width = W * dpr; canvas.height = W * dpr; cx.setTransform(dpr, 0, 0, dpr, 0, 0);
      const r = W * .37, c = W / 2;
      pts = [];
      for (let k = 0; k < 8; k++) { const a = -Math.PI / 2 + k * Math.PI / 4; pts.push({x: c + r * Math.cos(a), y: c + r * Math.sin(a)}); }
      pts.push({x: c, y: c});
    }
    function hit(x, y) {
      const R = W * .085;
      for (let k = 0; k < 9; k++) { const p = pts[k]; if ((p.x - x) ** 2 + (p.y - y) ** 2 < R * R && !seq.includes(k)) return k; }
      return -1;
    }
    function add(k) {
      seq.push(k); glow[k] = 1;
      if (!RM) for (let n = 0; n < 14; n++) { const a = Math.random() * 6.28, v = .6 + Math.random() * 2.2;
        sparks.push({x: pts[k].x, y: pts[k].y, vx: Math.cos(a) * v, vy: Math.sin(a) * v, life: 30 + Math.random() * 20}); }
      if (navigator.vibrate) try { navigator.vibrate(8); } catch (e) {}
    }
    function pos(e) { const r = canvas.getBoundingClientRect(); const t = e.touches ? e.touches[0] : e; return {x: t.clientX - r.left, y: t.clientY - r.top}; }
    function down(e) {
      if (!enabled) return; e.preventDefault(); drawing = true; seq = []; fizzle = 0;
      const p = pos(e); finger = p; const k = hit(p.x, p.y); if (k >= 0) add(k); loop();
    }
    function move(e) {
      if (!drawing) return; e.preventDefault();
      const p = pos(e); finger = p; const k = hit(p.x, p.y); if (k >= 0) add(k);
      if (!RM && Math.random() < .6) sparks.push({x: p.x, y: p.y, vx: (Math.random() - .5) * .8, vy: -Math.random() * 1.2, life: 22});
    }
    function up() {
      if (!drawing) return; drawing = false; finger = null;
      const s = seq.slice();
      if (s.length >= 4) onSpell(s.join("-")); else if (s.length) api.fizzle("A spell needs at least 4 runes.");
    }
    canvas.addEventListener("touchstart", down, {passive: false});
    canvas.addEventListener("touchmove", move, {passive: false});
    canvas.addEventListener("touchend", up);
    canvas.addEventListener("mousedown", down);
    root.addEventListener("mousemove", move);
    root.addEventListener("mouseup", up);

    function frame() {
      raf = 0;
      if (!canvas.isConnected || canvas.offsetParent === null) return;
      if (W !== canvas.clientWidth) layout();
      const c = W / 2;
      cx.clearRect(0, 0, W, W);
      // stone disc
      const g = cx.createRadialGradient(c * .8, c * .7, W * .05, c, c, W * .5);
      g.addColorStop(0, "#3c463f"); g.addColorStop(.7, "#1d2722"); g.addColorStop(1, "#121a16");
      cx.fillStyle = g; cx.beginPath(); cx.arc(c, c, W * .49, 0, 6.29); cx.fill();
      cx.lineWidth = 1.5; cx.strokeStyle = "rgba(201,154,62,.55)"; cx.beginPath(); cx.arc(c, c, W * .47, 0, 6.29); cx.stroke();
      cx.lineWidth = 1; cx.strokeStyle = "rgba(201,154,62,.22)"; cx.beginPath(); cx.arc(c, c, W * .37, 0, 6.29); cx.stroke();
      // trail
      const red = fizzle > 0;
      if (seq.length) {
        cx.lineCap = "round"; cx.lineJoin = "round";
        for (const [w, a] of [[10, .16], [4, .55], [1.6, 1]]) {
          cx.lineWidth = w; cx.strokeStyle = red ? `rgba(220,80,60,${a * Math.min(1, fizzle / 20)})` : `rgba(255,214,120,${a})`;
          cx.beginPath(); cx.moveTo(pts[seq[0]].x, pts[seq[0]].y);
          for (let k = 1; k < seq.length; k++) cx.lineTo(pts[seq[k]].x, pts[seq[k]].y);
          if (finger && drawing) cx.lineTo(finger.x, finger.y);
          cx.stroke();
        }
      }
      // runes
      for (let k = 0; k < 9; k++) {
        const p = pts[k], on = seq.includes(k);
        glow[k] *= .93;
        cx.fillStyle = on ? (red ? "rgba(120,30,25,.55)" : "rgba(80,60,20,.55)") : "rgba(0,0,0,.28)";
        cx.beginPath(); cx.arc(p.x, p.y, W * .07, 0, 6.29); cx.fill();
        cx.lineWidth = 1; cx.strokeStyle = on ? "rgba(255,214,120,.9)" : "rgba(201,154,62,.45)"; cx.stroke();
        cx.save(); cx.lineWidth = 2.2; cx.lineCap = "round";
        cx.shadowColor = "rgba(255,200,90,.9)"; cx.shadowBlur = on ? 14 + glow[k] * 20 : 0;
        cx.strokeStyle = on ? (red ? "#ff8a70" : "#ffe3a0") : "rgba(214,173,87,.55)";
        strokeRune(cx, p.x, p.y, W * .036, k); cx.restore();
      }
      // sparks
      sparks = sparks.filter(s => s.life > 0);
      for (const s of sparks) { s.x += s.vx; s.y += s.vy; s.vy += .03; s.life--;
        cx.globalAlpha = Math.min(1, s.life / 20); cx.fillStyle = red ? "#ff9a7a" : "#ffd98a"; cx.beginPath(); cx.arc(s.x, s.y, 1.6, 0, 6.29); cx.fill(); }
      cx.globalAlpha = 1;
      if (flash > 0) { cx.fillStyle = `rgba(255,226,150,${flash / 40})`; cx.beginPath(); cx.arc(c, c, W * .49, 0, 6.29); cx.fill(); flash--; }
      if (fizzle > 0) { fizzle--; if (!fizzle) seq = []; }
      if (drawing || sparks.length || flash > 0 || fizzle > 0 || glow.some(v => v > .02)) loop();
    }
    function loop() { if (!raf) raf = requestAnimationFrame(frame); }
    const api = {
      reset() { seq = []; fizzle = 0; flash = 0; enabled = true; layout(); loop(); },
      fizzle(msg) {
        fizzle = 40; loop();
        canvas.animate && canvas.animate([{transform: "translateX(0)"}, {transform: "translateX(-9px)"}, {transform: "translateX(8px)"}, {transform: "translateX(-5px)"}, {transform: "translateX(0)"}], {duration: 380});
        if (msg && api.onMessage) api.onMessage(msg);
      },
      success() { flash = 40; enabled = false; for (let k = 0; k < 9; k++) glow[k] = 1; loop(); },
      enable(v) { enabled = v; },
      onMessage: null
    };
    layout(); loop();
    root.addEventListener("resize", () => { layout(); loop(); });
    return api;
  }

  /* ---------- Rune vault door ---------- */
  function vaultHTML() {
    const ring = (n, r, s, off) => Array.from({length: n}, (_, k) => {
      const a = (k / n) * 360 + off;
      return `<g transform="rotate(${a}) translate(0 ${-r})"><path d="${runeSVG(k % 8, s)}"/></g>`;
    }).join("");
    const bolts = Array.from({length: 12}, (_, k) => `<g transform="rotate(${k * 30})"><rect class="bolt" x="-7" y="-196" width="14" height="30" rx="3"/></g>`).join("");
    const svg = `<svg viewBox="-200 -200 400 400" aria-hidden="true">
      <defs>
        <radialGradient id="vStone" cx="40%" cy="35%" r="75%"><stop offset="0" stop-color="#5a6159"/><stop offset=".55" stop-color="#363d37"/><stop offset="1" stop-color="#1c211d"/></radialGradient>
        <radialGradient id="vCore" cx="45%" cy="40%" r="70%"><stop offset="0" stop-color="#c2463a"/><stop offset=".6" stop-color="#8e1f1f"/><stop offset="1" stop-color="#4a0d0d"/></radialGradient>
      </defs>
      <g class="bolts">${bolts}</g>
      <circle r="178" fill="url(#vStone)" stroke="#0f130f" stroke-width="3"/>
      <circle r="171" fill="none" stroke="rgba(214,173,87,.5)" stroke-width="1.5"/>
      <g class="ring r1"><circle r="150" fill="none" stroke="rgba(0,0,0,.45)" stroke-width="30"/><circle r="150" fill="none" stroke="rgba(214,173,87,.25)" stroke-width="1"/>
        <g class="runes">${ring(16, 150, 8.5, 0)}</g></g>
      <g class="ring r2"><circle r="108" fill="none" stroke="rgba(0,0,0,.4)" stroke-width="28"/><circle r="94" fill="none" stroke="rgba(214,173,87,.3)" stroke-width="1"/><circle r="122" fill="none" stroke="rgba(214,173,87,.3)" stroke-width="1"/>
        <g class="runes">${ring(10, 108, 8, 18)}</g></g>
      <g class="ring r3"><circle r="72" fill="#2a312b" stroke="rgba(214,173,87,.45)" stroke-width="2"/>
        ${Array.from({length: 8}, (_, k) => `<path transform="rotate(${k * 45})" d="M0 -70 L0 -50" stroke="rgba(214,173,87,.6)" stroke-width="2"/>`).join("")}</g>
      <circle class="core" r="40" fill="url(#vCore)" stroke="rgba(0,0,0,.4)" stroke-width="4"/>
      <text class="core-s" y="15" text-anchor="middle">S</text>
      <line class="seam" x1="0" y1="-200" x2="0" y2="200"/>
    </svg>`;
    return `<div class="vault-half left">${svg}</div><div class="vault-half right">${svg}</div><div class="vault-light"></div>`;
  }

  function Vault(el) {
    el.innerHTML = vaultHTML();
    const wait = ms => new Promise(r => setTimeout(r, ms));
    return {
      async open(onShown) {
        if (RM) { onShown && onShown(); return; }
        el.hidden = false; el.className = "vault"; void el.offsetWidth;
        el.classList.add("show");      await wait(380);
        onShown && onShown();
        el.classList.add("turning");   await wait(1300);
        el.classList.add("unbolt");    await wait(550);
        el.classList.add("parting");   await wait(1100);
        el.hidden = true; el.className = "vault";
      },
      async close() {
        if (RM) return;
        el.hidden = false; el.className = "vault show turning unbolt parting"; void el.offsetWidth;
        el.classList.remove("parting"); await wait(800);
        el.classList.remove("unbolt");  await wait(350);
        el.classList.remove("turning"); await wait(500);
      },
      hide() { el.hidden = true; el.className = "vault"; }
    };
  }

  root.SpellPad = SpellPad;
  root.Vault = Vault;
})(window);
