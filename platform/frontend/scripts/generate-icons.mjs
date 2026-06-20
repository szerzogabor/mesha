/**
 * Dependency-free PWA icon generator.
 *
 * Rasterizes the Mesha brand mark (a white "M" on the brand-accent background)
 * into PNG files using only Node's built-in `zlib` — no native image tooling
 * (ImageMagick / rsvg) is required, which keeps icon generation reproducible in
 * CI and on contributor machines.
 *
 * Run with: `node scripts/generate-icons.mjs`
 * Outputs into `public/icons/` plus the app-level `apple-touch-icon.png`.
 */
import { deflateSync } from "node:zlib";
import { writeFileSync, mkdirSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

const __dirname = dirname(fileURLToPath(import.meta.url));
const PUBLIC_DIR = join(__dirname, "..", "public");
const ICONS_DIR = join(PUBLIC_DIR, "icons");
mkdirSync(ICONS_DIR, { recursive: true });

// Brand accent (matches --accent in globals.css) and white foreground.
const ACCENT = [94, 106, 210];
const WHITE = [255, 255, 255];

function hexToRgb([r, g, b]) {
  return { r, g, b };
}

// Signed distance from point p to segment a-b (used to stroke the "M").
function distToSegment(px, py, ax, ay, bx, by) {
  const dx = bx - ax;
  const dy = by - ay;
  const lenSq = dx * dx + dy * dy;
  let t = lenSq === 0 ? 0 : ((px - ax) * dx + (py - ay) * dy) / lenSq;
  t = Math.max(0, Math.min(1, t));
  const cx = ax + t * dx;
  const cy = ay + t * dy;
  return Math.hypot(px - cx, py - cy);
}

/**
 * Build an RGBA pixel buffer for the icon.
 * @param {number} size edge length in px
 * @param {boolean} maskable when true the background fills the whole canvas
 *        (Android masks it into a circle/squircle); otherwise corners are rounded
 *        and transparent.
 */
function renderIcon(size, maskable) {
  const buf = Buffer.alloc(size * size * 4, 0);
  const accent = hexToRgb(ACCENT);
  const white = hexToRgb(WHITE);

  const radius = maskable ? 0 : size * 0.22;
  // Keep the glyph inside the maskable "safe zone" (~80% of the canvas).
  const pad = maskable ? size * 0.26 : size * 0.22;
  const stroke = size * 0.085;

  const xL = pad;
  const xR = size - pad;
  const xM = size / 2;
  const yT = pad;
  const yB = size - pad;
  const yMid = size * 0.56;

  const segments = [
    [xL, yB, xL, yT],
    [xL, yT, xM, yMid],
    [xM, yMid, xR, yT],
    [xR, yT, xR, yB],
  ];

  for (let y = 0; y < size; y++) {
    for (let x = 0; x < size; x++) {
      const i = (y * size + x) * 4;

      // Background (with rounded corners for non-maskable variant).
      let inside = true;
      if (radius > 0) {
        const rx = Math.min(x, size - 1 - x);
        const ry = Math.min(y, size - 1 - y);
        if (rx < radius && ry < radius) {
          inside = Math.hypot(radius - rx, radius - ry) <= radius;
        }
      }
      if (!inside) continue;

      buf[i] = accent.r;
      buf[i + 1] = accent.g;
      buf[i + 2] = accent.b;
      buf[i + 3] = 255;

      // Foreground glyph with a 1px anti-aliased edge.
      let d = Infinity;
      for (const [ax, ay, bx, by] of segments) {
        d = Math.min(d, distToSegment(x, y, ax, ay, bx, by));
      }
      const edge = stroke / 2;
      if (d <= edge) {
        const aa = Math.min(1, Math.max(0, edge - d));
        buf[i] = Math.round(accent.r * (1 - aa) + white.r * aa);
        buf[i + 1] = Math.round(accent.g * (1 - aa) + white.g * aa);
        buf[i + 2] = Math.round(accent.b * (1 - aa) + white.b * aa);
        buf[i + 3] = 255;
      }
    }
  }
  return buf;
}

// Minimal PNG (RGBA, 8-bit, no interlace) encoder.
function crc32(buf) {
  let c = ~0;
  for (let n = 0; n < buf.length; n++) {
    c ^= buf[n];
    for (let k = 0; k < 8; k++) c = c & 1 ? (c >>> 1) ^ 0xedb88320 : c >>> 1;
  }
  return ~c >>> 0;
}

function chunk(type, data) {
  const typeBuf = Buffer.from(type, "ascii");
  const len = Buffer.alloc(4);
  len.writeUInt32BE(data.length, 0);
  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(crc32(Buffer.concat([typeBuf, data])), 0);
  return Buffer.concat([len, typeBuf, data, crc]);
}

function encodePng(size, rgba) {
  const sig = Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]);
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(size, 0);
  ihdr.writeUInt32BE(size, 4);
  ihdr[8] = 8; // bit depth
  ihdr[9] = 6; // color type RGBA
  // ihdr[10..12] = 0 (deflate, adaptive filter, no interlace)

  // Add a 0 filter byte to the front of every scanline.
  const stride = size * 4;
  const raw = Buffer.alloc((stride + 1) * size);
  for (let y = 0; y < size; y++) {
    raw[y * (stride + 1)] = 0;
    rgba.copy(raw, y * (stride + 1) + 1, y * stride, y * stride + stride);
  }
  const idat = deflateSync(raw, { level: 9 });

  return Buffer.concat([
    sig,
    chunk("IHDR", ihdr),
    chunk("IDAT", idat),
    chunk("IEND", Buffer.alloc(0)),
  ]);
}

function write(path, size, maskable) {
  const png = encodePng(size, renderIcon(size, maskable));
  writeFileSync(path, png);
  console.log(`wrote ${path} (${size}x${size}, ${png.length} bytes)`);
}

write(join(ICONS_DIR, "icon-192.png"), 192, false);
write(join(ICONS_DIR, "icon-512.png"), 512, false);
write(join(ICONS_DIR, "icon-maskable-192.png"), 192, true);
write(join(ICONS_DIR, "icon-maskable-512.png"), 512, true);
write(join(PUBLIC_DIR, "apple-touch-icon.png"), 180, true);
