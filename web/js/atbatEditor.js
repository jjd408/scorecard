import { api } from "./api.js";
import { openModal } from "./overlay.js";
import { diamondSVG, segmentsFromAnnotations } from "./diamond.js";

const RESULT_OPTIONS = [
  "1B", "2B", "3B", "HR", "BB", "HBP", "K", "ROE", "FC", "SF", "SAC", "IP-OUT", "DP",
];
const PUTOUT_RESULTS = new Set(["IP-OUT", "DP", "FC", "SF", "SAC"]);
const BASES_WORTH = { "1B": 1, BB: 1, ROE: 1, HBP: 1, "2B": 2, "3B": 3, HR: 4 };
const DEFAULT_OUTS = { K: 1, "IP-OUT": 1, FC: 1, SF: 1, SAC: 1, DP: 2 };

function countBallsStrikes(ab) {
  let balls = 0, strikes = 0;
  for (const p of ab.pitches || []) {
    if (p.type === "ball") balls++;
    else if (p.type === "called_strike") strikes++;
    else if (p.type === "foul" && strikes < 2) strikes++;
  }
  return { balls, strikes };
}

// Naive default advancement: force every runner (and the batter) forward by
// the result's base value. Purely a starting point for the "confirm/adjust
// runners" step (§5) — the user picks the real outcome via the selects.
function defaultRunnersAfter(result, runnersBefore, batterId) {
  const worth = BASES_WORTH[result] || 0;
  const after = { "1B": null, "2B": null, "3B": null };
  if (worth === 0) {
    return { ...runnersBefore };
  }
  const baseNum = { "1B": 1, "2B": 2, "3B": 3 };
  for (const base of ["1B", "2B", "3B"]) {
    const runner = runnersBefore[base];
    if (!runner) continue;
    const newPos = baseNum[base] + worth;
    if (newPos < 4) after[["1B", "2B", "3B"][newPos - 1]] = runner;
  }
  if (worth > 0 && worth < 4 && batterId) {
    after[["1B", "2B", "3B"][worth - 1]] = batterId;
  }
  return after;
}

// ctx = { gameId, game, view, playersById, opposingRoster,
//         mode: 'new'|'existing', newAtBat?: {batterId,inning,half},
//         ab?: existing at-bat object, onUpdated({game,view}) }
export function openAtBatEditor(ctx) {
  let { game, view, ab } = ctx;
  let bodyEl, closeFn;

  openModal("At-Bat", (body, close) => {
    bodyEl = body;
    closeFn = close;
    body.parentElement.parentElement.classList.add("atbat-editor");
    render(body, close);
  }, { extraClass: "atbat-editor" });

  function rerender() {
    render(bodyEl, closeFn);
  }

  function applyUpdate(result) {
    game = result.game;
    view = result.view;
    ab = ab ? game.at_bats.find((a) => a.id === ab.id) : ab;
    ctx.onUpdated({ game, view });
  }

  function render(body, close) {
    body.innerHTML = `
      <div class="diamond-large"></div>
      <div class="atbat-body"></div>
    `;
    const diamondHost = body.querySelector(".diamond-large");
    const bodyHost = body.querySelector(".atbat-body");

    const annotations = ab ? (game.annotations || []).filter((n) => n.at_bat_id === ab.id) : [];
    diamondHost.innerHTML = diamondSVG(segmentsFromAnnotations(annotations), ab ? ab.result || "" : "");

    if (!ab) {
      renderStartForm(bodyHost, close);
    } else {
      renderExisting(bodyHost, close);
    }
  }

  function renderStartForm(host, close) {
    const batter = ctx.playersById[ctx.newAtBat.batterId];
    const lastAb = (game.at_bats || [])
      .filter((a) => a.half === ctx.newAtBat.half)
      .slice(-1)[0];
    const defaultPitcher = lastAb ? lastAb.pitcher_id : "";

    host.innerHTML = `
      <p><strong>${batter ? batter.name : ctx.newAtBat.batterId}</strong> — inning ${ctx.newAtBat.inning}, ${ctx.newAtBat.half}</p>
      <div class="field-row">
        <label>Pitcher</label>
        <select class="pitcher-select"></select>
      </div>
      <div class="error-msg" style="display:none"></div>
      <button class="primary start-btn">Start at-bat</button>
    `;
    const select = host.querySelector(".pitcher-select");
    for (const p of ctx.opposingRoster) {
      const opt = document.createElement("option");
      opt.value = p.id;
      opt.textContent = p.name;
      select.appendChild(opt);
    }
    if (defaultPitcher) select.value = defaultPitcher;

    const errEl = host.querySelector(".error-msg");
    host.querySelector(".start-btn").addEventListener("click", async () => {
      try {
        // status is independent, user-set metadata (§2) — it won't flip
        // itself to in_progress just because at_bats exist, so nudge it here
        // on the very first pitch of the game as a convenience.
        if (game.status === "scheduled") {
          await api.updateGame(ctx.gameId, { status: "in_progress" });
        }
        const result = await api.openAtBat(ctx.gameId, {
          batter_id: ctx.newAtBat.batterId,
          pitcher_id: select.value,
          inning: ctx.newAtBat.inning,
          half: ctx.newAtBat.half,
        });
        game = result.game;
        view = result.view;
        ab = game.at_bats.find(
          (a) => a.batter_id === ctx.newAtBat.batterId && a.inning === ctx.newAtBat.inning &&
                 a.half === ctx.newAtBat.half && a.status === "in_progress"
        );
        ctx.onUpdated({ game, view });
        rerender();
      } catch (err) {
        errEl.textContent = err.message;
        errEl.style.display = "block";
      }
    });
  }

  function renderExisting(host, close) {
    const batter = ctx.playersById[ab.batter_id];
    const pitcher = ctx.playersById[ab.pitcher_id];
    const isLive = ab.status === "in_progress";
    const { balls, strikes } = countBallsStrikes(ab);

    host.innerHTML = `
      <p><strong>${batter ? batter.name : ab.batter_id}</strong> vs ${pitcher ? pitcher.name : ab.pitcher_id}
        — inning ${ab.inning}, ${ab.half}</p>
      ${isLive ? `<div class="count-display">Count: ${balls}-${strikes} (${ab.pitches.filter(p=>p.type==="foul").length} fouls)</div>
      <div class="pitch-buttons"></div>` : ""}
      <div class="result-section"></div>
    `;

    if (isLive) {
      const pitchBtns = host.querySelector(".pitch-buttons");
      for (const type of ["ball", "called_strike", "foul", "hbp", "in_play"]) {
        const btn = document.createElement("button");
        btn.textContent = type.replace("_", " ");
        btn.addEventListener("click", async () => {
          const result = await api.recordPitch(ctx.gameId, ab.id, type);
          applyUpdate(result);
          rerender();
        });
        pitchBtns.appendChild(btn);
      }
    }

    renderResultForm(host.querySelector(".result-section"), close);
  }

  function renderResultForm(host, close) {
    const isCorrection = ab.status === "complete";
    const runnersBefore = isCorrection ? ab.runners_before : view.runners;
    let selectedResult = ab.result || null;

    host.innerHTML = `
      <div class="result-buttons"></div>
      <div class="putout-row field-row" style="display:none">
        <label>Put-out sequence (e.g. 6-4-3)</label>
        <input type="text" class="putout-input">
      </div>
      <div class="field-row">
        <label>Outs recorded</label>
        <input type="number" class="outs-input" min="0" max="3" value="${ab.outs_recorded || 0}">
      </div>
      <div class="field-row">
        <label>RBI</label>
        <input type="number" class="rbi-input" min="0" value="${ab.rbi || 0}">
      </div>
      <div class="runner-confirm">
        <div><label>1B</label><select class="r1b"></select></div>
        <div><label>2B</label><select class="r2b"></select></div>
        <div><label>3B</label><select class="r3b"></select></div>
      </div>
      <div class="error-msg" style="display:none"></div>
      <button class="primary confirm-btn">${isCorrection ? "Save correction" : "Confirm"}</button>
      <div class="annotations-section" style="margin-top:16px"></div>
    `;

    const resultBtnsEl = host.querySelector(".result-buttons");
    for (const r of RESULT_OPTIONS) {
      const btn = document.createElement("button");
      btn.textContent = r;
      if (r === selectedResult) btn.classList.add("selected");
      btn.addEventListener("click", () => {
        selectedResult = r;
        resultBtnsEl.querySelectorAll("button").forEach((b) => b.classList.remove("selected"));
        btn.classList.add("selected");
        host.querySelector(".putout-row").style.display = PUTOUT_RESULTS.has(r) ? "block" : "none";
        applyDefaultsForResult(r);
      });
      resultBtnsEl.appendChild(btn);
    }
    host.querySelector(".putout-row").style.display =
      selectedResult && PUTOUT_RESULTS.has(selectedResult) ? "block" : "none";
    if (ab.put_out_sequence) host.querySelector(".putout-input").value = ab.put_out_sequence;

    const candidates = [ab.batter_id, runnersBefore["1B"], runnersBefore["2B"], runnersBefore["3B"]]
      .filter(Boolean);
    const selects = {
      "1B": host.querySelector(".r1b"),
      "2B": host.querySelector(".r2b"),
      "3B": host.querySelector(".r3b"),
    };
    for (const base of ["1B", "2B", "3B"]) {
      const sel = selects[base];
      sel.innerHTML = '<option value="">(empty)</option>';
      for (const pid of candidates) {
        const opt = document.createElement("option");
        opt.value = pid;
        opt.textContent = ctx.playersById[pid] ? ctx.playersById[pid].name : pid;
        sel.appendChild(opt);
      }
    }

    const initialAfter = isCorrection ? ab.runners_after : defaultRunnersAfter(selectedResult, runnersBefore, ab.batter_id);
    for (const base of ["1B", "2B", "3B"]) {
      if (initialAfter[base]) selects[base].value = initialAfter[base];
    }

    function applyDefaultsForResult(result) {
      const after = defaultRunnersAfter(result, runnersBefore, ab.batter_id);
      let scored = 0;
      for (const base of ["1B", "2B", "3B"]) {
        selects[base].value = after[base] || "";
      }
      const worth = BASES_WORTH[result] || 0;
      if (worth === 4) scored++;
      for (const base of ["1B", "2B", "3B"]) {
        const baseNum = { "1B": 1, "2B": 2, "3B": 3 }[base];
        if (runnersBefore[base] && baseNum + worth >= 4) scored++;
      }
      host.querySelector(".rbi-input").value = scored;
      host.querySelector(".outs-input").value = DEFAULT_OUTS[result] || 0;
    }

    const errEl = host.querySelector(".error-msg");
    host.querySelector(".confirm-btn").addEventListener("click", async () => {
      if (!selectedResult) {
        errEl.textContent = "Pick a result.";
        errEl.style.display = "block";
        return;
      }
      try {
        const body = {
          result: selectedResult,
          rbi: Number(host.querySelector(".rbi-input").value) || 0,
          outs_recorded: Number(host.querySelector(".outs-input").value) || 0,
          runners_after: {
            "1B": selects["1B"].value || null,
            "2B": selects["2B"].value || null,
            "3B": selects["3B"].value || null,
          },
        };
        const putoutInput = host.querySelector(".putout-input");
        if (PUTOUT_RESULTS.has(selectedResult) && putoutInput.value) {
          body.put_out_sequence = putoutInput.value;
        }
        const result = await api.finalizeAtBat(ctx.gameId, ab.id, body);
        applyUpdate(result);
        rerender();
      } catch (err) {
        errEl.textContent = err.message;
        errEl.style.display = "block";
      }
    });

    if (isCorrection) {
      renderAnnotations(host.querySelector(".annotations-section"));
    }
  }

  function renderAnnotations(host) {
    if (!host) return;
    const notes = (game.annotations || []).filter(
      (n) => n.at_bat_id === ab.id && !(n.from_base === "HOME" && n.runner_id === ab.batter_id)
    );
    host.innerHTML = `
      <h3 style="font-size:13px;margin:8px 0 4px">Other runner movement</h3>
      <ul style="font-size:13px;margin:0 0 8px;padding-left:18px"></ul>
      <div class="field-row"><label>Add annotation</label></div>
      <div class="pitch-buttons annotation-form"></div>
    `;
    const ul = host.querySelector("ul");
    if (notes.length === 0) {
      ul.innerHTML = "<li>none</li>";
    } else {
      for (const n of notes) {
        const li = document.createElement("li");
        const name = ctx.playersById[n.runner_id] ? ctx.playersById[n.runner_id].name : n.runner_id;
        li.textContent = `${name}: ${n.code} (${n.from_base} → ${n.to_base}, ${n.safe ? "safe" : "out"})`;
        ul.appendChild(li);
      }
    }

    const form = host.querySelector(".annotation-form");
    form.style.flexDirection = "column";
    form.innerHTML = `
      <select class="ann-runner"></select>
      <select class="ann-code"></select>
      <select class="ann-from">
        <option value="1B">1B</option><option value="2B">2B</option><option value="3B">3B</option>
      </select>
      <select class="ann-to">
        <option value="2B">2B</option><option value="3B">3B</option><option value="HOME">HOME</option>
      </select>
      <label style="font-size:12px"><input type="checkbox" class="ann-safe" checked> safe</label>
      <button class="ann-add">Add</button>
    `;
    const runnerSel = form.querySelector(".ann-runner");
    const candidates = [ab.runners_before["1B"], ab.runners_before["2B"], ab.runners_before["3B"]].filter(Boolean);
    for (const pid of candidates) {
      const opt = document.createElement("option");
      opt.value = pid;
      opt.textContent = ctx.playersById[pid] ? ctx.playersById[pid].name : pid;
      runnerSel.appendChild(opt);
    }
    api.listAnnotationCodes().then((codes) => {
      const codeSel = form.querySelector(".ann-code");
      for (const c of codes) {
        const opt = document.createElement("option");
        opt.value = c.code;
        opt.textContent = `${c.code} — ${c.description}`;
        codeSel.appendChild(opt);
      }
    });
    form.querySelector(".ann-add").addEventListener("click", async () => {
      if (!runnerSel.value) return;
      const result = await api.createAnnotation(ctx.gameId, ab.id, {
        code: form.querySelector(".ann-code").value,
        from_base: form.querySelector(".ann-from").value,
        to_base: form.querySelector(".ann-to").value,
        safe: form.querySelector(".ann-safe").checked,
        runner_id: runnerSel.value,
      });
      applyUpdate(result);
      renderAnnotations(host);
    });
  }
}
