package com.lexflow.widget;

/**
 * Drifting color blobs behind the note page, ported from ChronoFlow's
 * AmbientBlobLayer (Cosmos). Same rules: 4 blurred circles, one per color,
 * multiply blend, constant-speed drift, bounce off the screen edges, and
 * soft repulsion only between the listed color pairs (slate and matcha may
 * overlap freely).
 *
 * Phone adjustments: size is 50-60% of the screen width (the original
 * 319-367px fills a whole phone), blur keeps the original blur/size ratio,
 * and speed is halved so a small screen does not feel rushed. Repulsion is
 * scaled to match. Respects prefers-reduced-motion (blobs stay still).
 */
final class NoteBlob {
    private NoteBlob() {
    }

    static final String CSS = """
            #blobs{position:fixed;top:0;left:0;right:0;bottom:0;pointer-events:none;overflow:hidden;z-index:0}
            .blob{position:absolute;top:0;left:0;border-radius:50%;mix-blend-mode:multiply;will-change:transform}
            #page{position:relative;z-index:1}
            """;

    static final String JS = """
            (function () {
              var PALETTE = [
                ['apricot', '#EABB86'],
                ['mauve', '#E29CAA'],
                ['matcha', '#AACE8D'],
                ['slate', '#98B9D8']
              ];
              var PAIRS = {
                'matcha|mauve': 1, 'mauve|slate': 1, 'apricot|matcha': 1,
                'apricot|mauve': 1, 'apricot|slate': 1
              };
              var SPEED_BOOST = 7;   // original 14, halved for a phone screen
              var REPEL = 190;       // original 320 px/s2, scaled to the smaller blobs
              var MAX_SPEED = 85;    // original 170 px/s, halved with the speed

              function rand(a, b) { return Math.random() * (b - a) + a; }
              function pairKey(a, b) { return [a, b].sort().join('|'); }

              var layer = document.getElementById('blobs');
              if (!layer) return;
              var W = window.innerWidth, H = window.innerHeight;

              // 2x2 grid, one blob per cell, so they start spread out.
              var cells = [[0, 0], [0, 1], [1, 0], [1, 1]];
              for (var k = cells.length - 1; k > 0; k--) {
                var j = Math.floor(Math.random() * (k + 1));
                var tmp = cells[k]; cells[k] = cells[j]; cells[j] = tmp;
              }

              var blobs = [];
              function place(b) {
                var r = b.size / 2;
                b.el.style.transform = 'translate3d(' + (b.x - r) + 'px,' + (b.y - r) + 'px,0)';
              }

              for (var i = 0; i < 4; i++) {
                var cell = cells[i];
                var size = rand(0.5, 0.6) * W;
                var blur = size * rand(0.072, 0.093);
                var d = rand(26, 38);
                var ampX = rand(-70, 70) || 40;
                var ampY = rand(-60, 60) || 35;
                var b = {
                  role: PALETTE[i][0],
                  size: size,
                  x: (cell[1] * 0.5 + 0.25 + rand(-0.28, 0.28) * 0.5) * W,
                  y: (cell[0] * 0.5 + 0.25 + rand(-0.28, 0.28) * 0.5) * H,
                  vx: (ampX / (d / 2)) * SPEED_BOOST,
                  vy: (ampY / (d / 2)) * SPEED_BOOST
                };
                var el = document.createElement('div');
                el.className = 'blob';
                el.style.width = size + 'px';
                el.style.height = size + 'px';
                el.style.backgroundColor = PALETTE[i][1];
                el.style.filter = 'blur(' + blur + 'px)';
                el.style.opacity = String(rand(0.12, 0.18));
                layer.appendChild(el);
                b.el = el;
                blobs.push(b);
                place(b);
              }

              if (window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches) return;

              window.addEventListener('resize', function () {
                W = window.innerWidth;
                H = window.innerHeight;
              });

              var last = null;
              function tick(ts) {
                if (last === null) last = ts;
                var dt = Math.min((ts - last) / 1000, 0.05);
                last = ts;

                // 1) move, bounce off the edges (by the edge, not the center)
                for (var i = 0; i < blobs.length; i++) {
                  var b = blobs[i], r = b.size / 2;
                  b.x += b.vx * dt;
                  b.y += b.vy * dt;
                  if (b.x - r < 0) { b.x = r; b.vx = Math.abs(b.vx); }
                  else if (b.x + r > W) { b.x = W - r; b.vx = -Math.abs(b.vx); }
                  if (b.y - r < 0) { b.y = r; b.vy = Math.abs(b.vy); }
                  else if (b.y + r > H) { b.y = H - r; b.vy = -Math.abs(b.vy); }
                }

                // 2) soft repulsion, only for the listed color pairs
                for (var p = 0; p < blobs.length; p++) {
                  for (var q = p + 1; q < blobs.length; q++) {
                    var a = blobs[p], c = blobs[q];
                    if (!PAIRS[pairKey(a.role, c.role)]) continue;
                    var dx = c.x - a.x, dy = c.y - a.y;
                    var dist = Math.hypot(dx, dy);
                    var range = a.size / 2 + c.size / 2;
                    if (dist > 0 && dist < range) {
                      var force = REPEL * (1 - dist / range);
                      var nx = dx / dist, ny = dy / dist;
                      a.vx -= nx * force * dt; a.vy -= ny * force * dt;
                      c.vx += nx * force * dt; c.vy += ny * force * dt;
                    }
                  }
                }

                // 3) speed cap, then draw
                for (var s = 0; s < blobs.length; s++) {
                  var bb = blobs[s];
                  var sp = Math.hypot(bb.vx, bb.vy);
                  if (sp > MAX_SPEED) { bb.vx *= MAX_SPEED / sp; bb.vy *= MAX_SPEED / sp; }
                  place(bb);
                }
                window.requestAnimationFrame(tick);
              }
              window.requestAnimationFrame(tick);
            })();
            """;
}
