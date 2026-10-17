/**
 * NearDrop Web Flying File Animation Engine
 * Canvas-based physics-inspired parabolic Bezier flight trajectory,
 * 3D banking, comet stardust trails, and receiver absorption pulses.
 */

export class FlyingAnimationEngine {
  constructor(canvas) {
    this.canvas = canvas;
    this.ctx = canvas.getContext('2d');
    this.particles = [];
    this.flyingFiles = [];
    this.pulses = [];
    this.animId = null;
    this.resize();
    window.addEventListener('resize', () => this.resize());
    this.loop();
  }

  resize() {
    if (!this.canvas) return;
    const rect = this.canvas.parentElement.getBoundingClientRect();
    const dpr = window.devicePixelRatio || 1;
    this.canvas.width = rect.width * dpr;
    this.canvas.height = rect.height * dpr;
    this.ctx.scale(dpr, dpr);
    this.width = rect.width;
    this.height = rect.height;
  }

  /**
   * Spawns a flying file packet along a parabolic path
   */
  launchFlight({ startX, startY, endX, endY, fileName, fileExt, color = '#6366f1', onComplete }) {
    const dx = endX - startX;
    const dy = endY - startY;
    const distance = Math.hypot(dx, dy);

    // Dynamic apex control point
    const midX = (startX + endX) / 2;
    const arcHeight = Math.min(180, Math.max(80, distance * 0.35));
    const midY = Math.min(startY, endY) - arcHeight;

    this.flyingFiles.push({
      p0: { x: startX, y: startY },
      p1: { x: midX, y: midY },
      p2: { x: endX, y: endY },
      progress: 0,
      duration: Math.max(900, Math.min(1800, distance * 2.2)),
      startTime: performance.now(),
      fileName: fileName.length > 14 ? fileName.substring(0, 12) + '…' : fileName,
      fileExt: fileExt || 'FILE',
      color,
      onComplete
    });
  }

  spawnParticle(x, y, color) {
    const angle = Math.random() * Math.PI * 2;
    const speed = Math.random() * 2.5 + 0.5;
    this.particles.push({
      x,
      y,
      vx: Math.cos(angle) * speed,
      vy: Math.sin(angle) * speed,
      size: Math.random() * 3.5 + 1.5,
      alpha: 0.9,
      decay: Math.random() * 0.025 + 0.015,
      color
    });
  }

  spawnPulse(x, y, color) {
    this.pulses.push({
      x,
      y,
      radius: 8,
      maxRadius: 45,
      alpha: 0.8,
      color
    });
  }

  loop() {
    this.animId = requestAnimationFrame(() => this.loop());
    if (!this.ctx) return;

    this.ctx.clearRect(0, 0, this.width, this.height);
    const now = performance.now();

    // 1. Update and draw pulses
    for (let i = this.pulses.length - 1; i >= 0; i--) {
      const p = this.pulses[i];
      p.radius += 1.8;
      p.alpha -= 0.025;
      if (p.alpha <= 0 || p.radius >= p.maxRadius) {
        this.pulses.splice(i, 1);
        continue;
      }
      this.ctx.save();
      this.ctx.beginPath();
      this.ctx.arc(p.x, p.y, p.radius, 0, Math.PI * 2);
      this.ctx.strokeStyle = p.color;
      this.ctx.globalAlpha = p.alpha;
      this.ctx.lineWidth = 2.5;
      this.ctx.stroke();
      this.ctx.restore();
    }

    // 2. Update and draw stardust particles
    for (let i = this.particles.length - 1; i >= 0; i--) {
      const pt = this.particles[i];
      pt.x += pt.vx;
      pt.y += pt.vy;
      pt.alpha -= pt.decay;
      if (pt.alpha <= 0) {
        this.particles.splice(i, 1);
        continue;
      }
      this.ctx.save();
      this.ctx.beginPath();
      this.ctx.arc(pt.x, pt.y, pt.size, 0, Math.PI * 2);
      this.ctx.fillStyle = pt.color;
      this.ctx.globalAlpha = pt.alpha;
      this.ctx.shadowColor = pt.color;
      this.ctx.shadowBlur = 6;
      this.ctx.fill();
      this.ctx.restore();
    }

    // 3. Update and draw flying file items
    for (let i = this.flyingFiles.length - 1; i >= 0; i--) {
      const f = this.flyingFiles[i];
      const elapsed = now - f.startTime;
      f.progress = Math.min(1, elapsed / f.duration);

      // Quadratic Bezier interpolation
      const t = f.progress;
      const invT = 1 - t;
      const x = invT * invT * f.p0.x + 2 * invT * t * f.p1.x + t * t * f.p2.x;
      const y = invT * invT * f.p0.y + 2 * invT * t * f.p1.y + t * t * f.p2.y;

      // Tangent for 3D banking
      const dx = 2 * (1 - t) * (f.p1.x - f.p0.x) + 2 * t * (f.p2.x - f.p1.x);
      const dy = 2 * (1 - t) * (f.p1.y - f.p0.y) + 2 * t * (f.p2.y - f.p1.y);
      const angle = Math.atan2(dy, dx);

      // Spawn stardust comet tail
      if (Math.random() < 0.65) {
        this.spawnParticle(x + (Math.random() - 0.5) * 8, y + (Math.random() - 0.5) * 8, f.color);
      }

      // Draw dashed trajectory path
      this.ctx.save();
      this.ctx.beginPath();
      this.ctx.moveTo(f.p0.x, f.p0.y);
      this.ctx.quadraticCurveTo(f.p1.x, f.p1.y, f.p2.x, f.p2.y);
      this.ctx.strokeStyle = f.color;
      this.ctx.globalAlpha = 0.22;
      this.ctx.lineWidth = 2;
      this.ctx.setLineDash([6, 6]);
      this.ctx.stroke();
      this.ctx.restore();

      // Draw stylized file packet with 3D tilt
      this.ctx.save();
      this.ctx.translate(x, y);
      this.ctx.rotate(angle * 0.45); // Smooth banking tilt

      const scale = 0.85 + Math.sin(t * Math.PI) * 0.25; // Expands mid-air
      this.ctx.scale(scale, scale);

      // Card shadow
      this.ctx.shadowColor = f.color;
      this.ctx.shadowBlur = 14;
      this.ctx.shadowOffsetY = 4;

      // Card background
      this.ctx.fillStyle = '#ffffff';
      this.roundRect(-28, -16, 56, 32, 8);
      this.ctx.fill();

      // Card border
      this.ctx.strokeStyle = f.color;
      this.ctx.lineWidth = 1.8;
      this.ctx.stroke();

      // Badge pill
      this.ctx.shadowBlur = 0;
      this.ctx.fillStyle = f.color;
      this.roundRect(-22, -10, 20, 20, 5);
      this.ctx.fill();

      // Icon symbol (folded page)
      this.ctx.fillStyle = '#ffffff';
      this.ctx.font = 'bold 8px system-ui, sans-serif';
      this.ctx.textAlign = 'center';
      this.ctx.textBaseline = 'middle';
      this.ctx.fillText(f.fileExt.substring(0, 3).toUpperCase(), -12, 0);

      // File name
      this.ctx.fillStyle = '#1e293b';
      this.ctx.font = '600 9px system-ui, sans-serif';
      this.ctx.textAlign = 'left';
      this.ctx.fillText(f.fileName.substring(0, 5), 2, 0);

      this.ctx.restore();

      // Arrival trigger
      if (f.progress >= 1) {
        this.spawnPulse(f.p2.x, f.p2.y, f.color);
        for (let k = 0; k < 12; k++) {
          this.spawnParticle(f.p2.x, f.p2.y, f.color);
        }
        if (f.onComplete) f.onComplete();
        this.flyingFiles.splice(i, 1);
      }
    }
  }

  roundRect(x, y, w, h, r) {
    this.ctx.beginPath();
    this.ctx.moveTo(x + r, y);
    this.ctx.arcTo(x + w, y, x + w, y + h, r);
    this.ctx.arcTo(x + w, y + h, x, y + h, r);
    this.ctx.arcTo(x, y + h, x, y, r);
    this.ctx.arcTo(x, y, x + w, y, r);
    this.ctx.closePath();
  }

  destroy() {
    if (this.animId) cancelAnimationFrame(this.animId);
  }
}
