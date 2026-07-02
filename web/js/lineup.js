import { api } from "./api.js";
import { openModal } from "./overlay.js";

// Minimal lineup-setup UI: 9 ordered slots, each a player picker scoped to
// the team's own active roster. Needed before the grid has any rows to
// score into — not one of §6's named overlays, but a prerequisite for §5's
// grid, which reads lineup_home/lineup_away as fixed row order.
export function openLineupEditor({ gameId, team, roster, currentLineup, onSaved }) {
  openModal(`${team === "home" ? "Home" : "Away"} lineup`, (body, close) => {
    const slots = Array.from({ length: 9 }, (_, i) => currentLineup[i] || "");
    body.innerHTML = `
      <div class="lineup-slots"></div>
      <div class="error-msg" style="display:none"></div>
      <button class="primary save-btn">Save lineup</button>
    `;
    const slotsHost = body.querySelector(".lineup-slots");
    const selects = [];
    for (let i = 0; i < 9; i++) {
      const row = document.createElement("div");
      row.className = "field-row";
      row.innerHTML = `<label>Slot ${i + 1}</label>`;
      const sel = document.createElement("select");
      sel.innerHTML = '<option value="">(empty)</option>';
      for (const p of roster) {
        const opt = document.createElement("option");
        opt.value = p.id;
        opt.textContent = p.name;
        sel.appendChild(opt);
      }
      sel.value = slots[i];
      row.appendChild(sel);
      slotsHost.appendChild(row);
      selects.push(sel);
    }

    const errEl = body.querySelector(".error-msg");
    body.querySelector(".save-btn").addEventListener("click", async () => {
      const lineup = selects.map((s) => s.value).filter(Boolean);
      if (new Set(lineup).size !== lineup.length) {
        errEl.textContent = "Each player can only appear once in the lineup.";
        errEl.style.display = "block";
        return;
      }
      try {
        const patch = {};
        patch[`lineup_${team}`] = lineup;
        const updated = await api.updateGame(gameId, patch);
        onSaved(updated);
        close();
      } catch (err) {
        errEl.textContent = err.message;
        errEl.style.display = "block";
      }
    });
  });
}
