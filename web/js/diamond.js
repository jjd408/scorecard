// Shared diamond-drawing logic: an at-bat's annotations (§2/§6) are the sole
// source of truth for what gets colored — including the batter's own result,
// since the server auto-creates a from_base:"HOME" annotation for that on
// finalize. Safe = green, out = red, per the design doc's coloring rule.

const POINTS = { HOME: [50, 92], "1B": [92, 50], "2B": [50, 8], "3B": [8, 50] };
const ORDER = ["HOME", "1B", "2B", "3B"];
const SEGMENT_KEYS = ["H1", "12", "23", "3H"];

function baseIndex(name) {
  return ORDER.indexOf(name);
}

// Segment keys spanned going from fromIdx to toIdx, advancing forward
// (HOME -> 1B -> 2B -> 3B -> HOME). Empty if fromIdx === toIdx (an out with
// no bases run).
function segmentsBetween(fromIdx, toIdx) {
  const segs = [];
  let i = fromIdx;
  let guard = 0;
  while (i !== toIdx && guard < 4) {
    segs.push(SEGMENT_KEYS[i]);
    i = (i + 1) % 4;
    guard++;
  }
  return segs;
}

// annotations: the subset of game.annotations whose at_bat_id matches this
// cell's at-bat. Returns { segments: {key: {color,label}}, outAtHome, outLabel }.
export function segmentsFromAnnotations(annotations) {
  const segments = {};
  let outAtHome = false;
  let outLabel = null;
  for (const note of annotations || []) {
    const fromIdx = baseIndex(note.from_base);
    const toIdx = baseIndex(note.to_base);
    if (fromIdx === -1 || toIdx === -1) continue;
    const color = note.safe ? "green" : "red";
    if (fromIdx === toIdx) {
      if (note.from_base !== "HOME") continue;
      if (note.safe) {
        // HR: a full trip around the bases, not "no movement" — HOME both
        // starts and ends the loop.
        SEGMENT_KEYS.forEach((key, i) => {
          segments[key] = { color, label: i === SEGMENT_KEYS.length - 1 ? note.code : undefined };
        });
      } else {
        outAtHome = true;
        outLabel = note.code;
      }
      continue;
    }
    const segs = segmentsBetween(fromIdx, toIdx);
    segs.forEach((key, i) => {
      segments[key] = { color, label: i === segs.length - 1 ? note.code : undefined };
    });
  }
  return { segments, outAtHome, outLabel };
}

function colorVar(color) {
  return color === "green" ? "var(--green)" : color === "red" ? "var(--red)" : "#ccc";
}

// Builds an inline SVG string for one diamond. `info` is the return value of
// segmentsFromAnnotations. `label` (optional) is centered text, typically
// the batter's own result code.
export function diamondSVG(info, label) {
  const { segments, outAtHome, outLabel } = info;
  const defs = [
    ["HOME", "1B", "H1"],
    ["1B", "2B", "12"],
    ["2B", "3B", "23"],
    ["3B", "HOME", "3H"],
  ];
  let svg = `<svg viewBox="0 0 100 100" class="diamond-svg" preserveAspectRatio="xMidYMid meet">`;
  for (const [a, b, key] of defs) {
    const seg = segments[key];
    const [x1, y1] = POINTS[a];
    const [x2, y2] = POINTS[b];
    svg += `<line x1="${x1}" y1="${y1}" x2="${x2}" y2="${y2}" stroke="${colorVar(seg && seg.color)}" stroke-width="${seg ? 3 : 1.5}" stroke-linecap="round" />`;
    if (seg && seg.label) {
      const mx = (x1 + x2) / 2;
      const my = (y1 + y2) / 2;
      svg += `<text x="${mx}" y="${my - 3}" text-anchor="middle" font-size="6" fill="${colorVar(seg.color)}">${seg.label}</text>`;
    }
  }
  for (const key of ["1B", "2B", "3B"]) {
    const [x, y] = POINTS[key];
    svg += `<rect x="${x - 4}" y="${y - 4}" width="8" height="8" fill="#fff" stroke="#999" stroke-width="1" transform="rotate(45 ${x} ${y})" />`;
  }
  const homeFill = outAtHome ? "var(--red)" : "#fff";
  svg += `<circle cx="50" cy="92" r="6" fill="${homeFill}" stroke="${outAtHome ? "var(--red)" : "#999"}" stroke-width="1.5" />`;
  if (outLabel) {
    svg += `<text x="50" y="76" text-anchor="middle" font-size="6" fill="var(--red)">${outLabel}</text>`;
  }
  if (label) {
    svg += `<text x="50" y="54" text-anchor="middle" font-size="11" font-weight="700" fill="var(--ink)">${label}</text>`;
  }
  svg += `</svg>`;
  return svg;
}
