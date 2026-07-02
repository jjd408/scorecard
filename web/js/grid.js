import { diamondSVG, segmentsFromAnnotations } from "./diamond.js";

const ROWS = 9;
const MIN_COLS = 9;

function findAtBat(atBats, half, batterId, inning) {
  return atBats.find((ab) => ab.half === half && ab.batter_id === batterId && ab.inning === inning);
}

function countFouls(ab) {
  return (ab.pitches || []).filter((p) => p.type === "foul").length;
}

function countBallsStrikes(ab) {
  let balls = 0, strikes = 0;
  for (const p of ab.pitches || []) {
    if (p.type === "ball") balls++;
    else if (p.type === "called_strike") strikes++;
    else if (p.type === "foul" && strikes < 2) strikes++;
  }
  return { balls, strikes };
}

// ctx = { game, view, team, playersById, extraCols, onExtendCols,
//         onStartAtBat({batterId,pitcherId,inning,half}), onOpenAtBat(ab) }
export function renderGrid(container, ctx) {
  const { game, view, team, playersById, extraCols, onExtendCols, onStartAtBat, onOpenAtBat } = ctx;
  const half = team === "home" ? "bottom" : "top";
  const lineup = (game[`lineup_${team}`] || []).slice(0, ROWS);

  const atBatsForHalf = (game.at_bats || []).filter((ab) => ab.half === half);
  const dataMaxInning = atBatsForHalf.reduce((m, ab) => Math.max(m, ab.inning), 0);
  const isTeamUp = view.current_half === half;
  const liveMaxInning = isTeamUp ? view.current_inning : 0;
  const cols = Math.max(MIN_COLS, dataMaxInning, liveMaxInning, extraCols || 0);

  const completedCount = atBatsForHalf.filter((ab) => ab.status === "complete").length;
  const nextIndex = lineup.length ? completedCount % lineup.length : -1;
  const canStartNext = isTeamUp && view.current_at_bat_id == null;

  container.innerHTML = "";
  const scrollWrap = document.createElement("div");
  scrollWrap.className = "grid-scroll";
  const table = document.createElement("table");
  table.className = "at-bat-grid";

  const thead = document.createElement("thead");
  const headRow = document.createElement("tr");
  headRow.innerHTML = `<th class="corner"></th>`;
  for (let col = 1; col <= cols; col++) {
    headRow.innerHTML += `<th class="inning-header">${col}</th>`;
  }
  const addTh = document.createElement("th");
  addTh.className = "inning-header";
  const addBtn = document.createElement("button");
  addBtn.className = "add-inning-btn";
  addBtn.textContent = "+";
  addBtn.title = "Add inning column";
  addBtn.addEventListener("click", () => onExtendCols(cols + 1));
  addTh.appendChild(addBtn);
  headRow.appendChild(addTh);
  thead.appendChild(headRow);
  table.appendChild(thead);

  const tbody = document.createElement("tbody");
  for (let row = 0; row < ROWS; row++) {
    const batterId = lineup[row] || null;
    const player = batterId ? playersById[batterId] : null;
    const tr = document.createElement("tr");
    const labelTd = document.createElement("td");
    labelTd.className = "row-label";
    labelTd.textContent = player ? `${row + 1}. ${player.name}` : `${row + 1}. —`;
    tr.appendChild(labelTd);

    for (let col = 1; col <= cols; col++) {
      const td = document.createElement("td");
      const ab = batterId ? findAtBat(game.at_bats || [], half, batterId, col) : null;
      const isNextUp = canStartNext && row === nextIndex && col === view.current_inning && batterId && !ab;

      if (ab) {
        td.appendChild(renderCell(ab, game, view, playersById));
        td.querySelector(".cell").addEventListener("click", () => onOpenAtBat(ab));
      } else if (isNextUp) {
        const div = document.createElement("div");
        div.className = "cell next-up";
        div.innerHTML = `<div class="next-up-label">Start at-bat</div>`;
        div.addEventListener("click", () => onStartAtBat({ batterId, inning: col, half }));
        td.appendChild(div);
      } else {
        const div = document.createElement("div");
        div.className = "cell empty";
        td.appendChild(div);
      }
      tr.appendChild(td);
    }
    tr.appendChild(document.createElement("td"));
    tbody.appendChild(tr);
  }
  table.appendChild(tbody);
  scrollWrap.appendChild(table);
  container.innerHTML = "";
  container.appendChild(scrollWrap);
}

function renderCell(ab, game, view, playersById) {
  const wrap = document.createElement("div");
  wrap.className = "cell filled";

  const batter = playersById[ab.batter_id];
  const pitcher = playersById[ab.pitcher_id];
  const isLive = ab.status === "in_progress" && ab.id === view.current_at_bat_id;
  const { balls, strikes } = isLive ? countBallsStrikes(ab) : { balls: 0, strikes: 0 };
  const fouls = isLive ? countFouls(ab) : 0;
  const scored = (view.scored_at_bat_ids || []).includes(ab.id);

  const strip = document.createElement("div");
  strip.className = "cell-strip";
  strip.innerHTML = `
    <div class="batter">${batter ? batter.name : ab.batter_id}</div>
    <div class="pitcher">vs ${pitcher ? pitcher.name : ab.pitcher_id || "?"}</div>
    ${isLive ? `<div class="count">B${balls} S${strikes} F${fouls}</div>` : ""}
    ${ab.rbi ? `<div class="count">RBI ${ab.rbi}</div>` : ""}
    ${scored ? `<div class="count">R</div>` : ""}
  `;
  wrap.appendChild(strip);

  const diamondWrap = document.createElement("div");
  diamondWrap.className = "diamond-wrap";
  const annotations = (game.annotations || []).filter((n) => n.at_bat_id === ab.id);
  const info = segmentsFromAnnotations(annotations);
  diamondWrap.innerHTML = diamondSVG(info, ab.result || "");
  wrap.appendChild(diamondWrap);

  const outsForAb = (view.outs_by_at_bat || {})[ab.id];
  if (outsForAb && outsForAb.length) {
    const badge = document.createElement("div");
    badge.className = "out-badge";
    badge.textContent = outsForAb[outsForAb.length - 1];
    wrap.appendChild(badge);
  }

  return wrap;
}
