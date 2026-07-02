// Generic modal-overlay shell + a reusable add/edit/delete table component
// used by the Teams/Players/Games/Annotations overlays (design doc §6).

const root = document.getElementById("overlay-root");

// Opens a modal with `title`; `renderBody(bodyEl, close)` fills in the
// content. Returns a close() function. Clicking the backdrop or the X closes
// it.
export function openModal(title, renderBody, opts = {}) {
  const backdrop = document.createElement("div");
  backdrop.className = "overlay-backdrop";
  const panel = document.createElement("div");
  panel.className = "overlay-panel" + (opts.extraClass ? " " + opts.extraClass : "");
  panel.innerHTML = `
    <div class="overlay-header">
      <h2></h2>
      <button class="overlay-close" aria-label="Close">×</button>
    </div>
    <div class="overlay-body"></div>
  `;
  panel.querySelector("h2").textContent = title;
  backdrop.appendChild(panel);
  root.appendChild(backdrop);

  const close = () => backdrop.remove();
  panel.querySelector(".overlay-close").addEventListener("click", close);
  backdrop.addEventListener("click", (e) => {
    if (e.target === backdrop) close();
  });

  const body = panel.querySelector(".overlay-body");
  renderBody(body, close);
  return close;
}

function resolveOptions(col) {
  return typeof col.options === "function" ? col.options() : Promise.resolve(col.options || []);
}

function inputFor(col, value) {
  if (col.type === "checkbox") {
    const el = document.createElement("input");
    el.type = "checkbox";
    el.checked = value !== false;
    return el;
  }
  if (col.type === "select") {
    const el = document.createElement("select");
    return el; // options populated async by caller
  }
  const el = document.createElement("input");
  el.type = col.type === "number" ? "number" : "text";
  el.value = value == null ? "" : value;
  return el;
}

async function fillSelect(el, col, value) {
  const opts = await resolveOptions(col);
  el.innerHTML = "";
  for (const o of opts) {
    const opt = document.createElement("option");
    opt.value = o.value;
    opt.textContent = o.label;
    el.appendChild(opt);
  }
  if (value != null) el.value = value;
}

function readValue(col, el) {
  if (col.type === "checkbox") return el.checked;
  if (col.type === "number") return el.value === "" ? undefined : Number(el.value);
  if (col.type === "tags") return el.value.split(",").map((s) => s.trim()).filter(Boolean);
  return el.value;
}

/* config = {
 *   title, columns: [{key,label,type,options,formatDisplay?(row),editable}],
 *   list(): Promise<row[]>, create(values): Promise<row>,
 *   update(id, patch): Promise<row>, del?(id): Promise<void>,
 *   deactivateLabel?: string (used instead of Delete when del is absent and
 *     rows carry an "active" field — soft delete via update({active:false})),
 *   idKey?: default "id",
 *   rowActions?: (row) => [{label, onClick(row)}]  // extra buttons, e.g. "Open"
 * }
 */
export function openEntityOverlay(config) {
  openModal(config.title, (body) => renderEntityTable(body, config));
}

// Same add/edit/delete table, rendered directly into `container` rather than
// inside a modal — used for the Games list, which doubles as the app's
// default landing page (§6: the "open" row action is the primary way into a
// game, so it can't live behind an extra click into an overlay).
export function renderEntityTable(container, config) {
  const idKey = config.idKey || "id";
  let rows = [];

  (async () => {
    container.innerHTML = `<div class="entity-table-wrap"></div>`;
    const wrap = container.querySelector(".entity-table-wrap");
    await refresh();

    async function refresh() {
      rows = await config.list();
      render();
    }

    function render() {
      wrap.innerHTML = "";
      const table = document.createElement("table");
      const thead = document.createElement("thead");
      thead.innerHTML =
        "<tr>" +
        config.columns.map((c) => `<th>${c.label}</th>`).join("") +
        "<th></th></tr>";
      table.appendChild(thead);
      const tbody = document.createElement("tbody");
      table.appendChild(tbody);

      for (const row of rows) {
        tbody.appendChild(renderDisplayRow(row));
      }
      tbody.appendChild(renderAddRow());

      wrap.appendChild(table);
    }

    function renderDisplayRow(row) {
      const tr = document.createElement("tr");
      for (const col of config.columns) {
        const td = document.createElement("td");
        if (col.renderDisplay) {
          td.appendChild(col.renderDisplay(row));
        } else {
          td.textContent = col.formatDisplay ? col.formatDisplay(row) : (row[col.key] ?? "");
        }
        tr.appendChild(td);
      }
      const actionsTd = document.createElement("td");
      actionsTd.className = "row-actions";

      const editBtn = document.createElement("button");
      editBtn.className = "link";
      editBtn.textContent = "Edit";
      editBtn.addEventListener("click", () => tr.replaceWith(renderEditRow(row)));
      actionsTd.appendChild(editBtn);

      if (config.del) {
        const delBtn = document.createElement("button");
        delBtn.className = "link danger";
        delBtn.textContent = "Delete";
        delBtn.addEventListener("click", async () => {
          if (!confirm(`Delete this ${config.title.toLowerCase()} entry?`)) return;
          await config.del(row[idKey]);
          await refresh();
        });
        actionsTd.appendChild(delBtn);
      } else if ("active" in row) {
        const toggleBtn = document.createElement("button");
        toggleBtn.className = "link" + (row.active ? " danger" : "");
        toggleBtn.textContent = row.active ? "Deactivate" : "Activate";
        toggleBtn.addEventListener("click", async () => {
          await config.update(row[idKey], { active: !row.active });
          await refresh();
        });
        actionsTd.appendChild(toggleBtn);
      }

      if (config.rowActions) {
        for (const action of config.rowActions(row)) {
          const btn = document.createElement("button");
          btn.className = "link";
          btn.textContent = action.label;
          btn.addEventListener("click", () => action.onClick(row));
          actionsTd.appendChild(btn);
        }
      }

      tr.appendChild(actionsTd);
      return tr;
    }

    function colEditable(col, row) {
      return typeof col.editable === "function" ? col.editable(row) : col.editable !== false;
    }

    function renderEditRow(row) {
      const tr = document.createElement("tr");
      const inputs = {};
      for (const col of config.columns) {
        const td = document.createElement("td");
        if (!colEditable(col, row)) {
          td.textContent = col.formatDisplay ? col.formatDisplay(row) : (row[col.key] ?? "");
        } else {
          const el = inputFor(col, row[col.key]);
          inputs[col.key] = el;
          td.appendChild(el);
          if (col.type === "select") fillSelect(el, col, row[col.key]);
        }
        tr.appendChild(td);
      }
      const actionsTd = document.createElement("td");
      actionsTd.className = "row-actions";
      const saveBtn = document.createElement("button");
      saveBtn.className = "link";
      saveBtn.textContent = "Save";
      saveBtn.addEventListener("click", async () => {
        const patch = {};
        for (const col of config.columns) {
          if (!colEditable(col, row)) continue;
          patch[col.key] = readValue(col, inputs[col.key]);
        }
        await config.update(row[idKey], patch);
        await refresh();
      });
      const cancelBtn = document.createElement("button");
      cancelBtn.className = "link";
      cancelBtn.textContent = "Cancel";
      cancelBtn.addEventListener("click", () => tr.replaceWith(renderDisplayRow(row)));
      actionsTd.appendChild(saveBtn);
      actionsTd.appendChild(cancelBtn);
      tr.appendChild(actionsTd);
      return tr;
    }

    function renderAddRow() {
      const tr = document.createElement("tr");
      const inputs = {};
      for (const col of config.columns) {
        const td = document.createElement("td");
        if (col.editableOnCreate === false) {
          td.textContent = "";
        } else {
          const el = inputFor(col, col.type === "checkbox" ? true : "");
          inputs[col.key] = el;
          td.appendChild(el);
          if (col.type === "select") fillSelect(el, col, null);
        }
        tr.appendChild(td);
      }
      const actionsTd = document.createElement("td");
      const addBtn = document.createElement("button");
      addBtn.className = "primary";
      addBtn.textContent = "Add";
      addBtn.addEventListener("click", async () => {
        const values = {};
        for (const col of config.columns) {
          if (col.editableOnCreate === false) continue;
          values[col.key] = readValue(col, inputs[col.key]);
        }
        try {
          await config.create(values);
          await refresh();
        } catch (err) {
          alert(err.message);
        }
      });
      actionsTd.appendChild(addBtn);
      tr.appendChild(actionsTd);
      return tr;
    }
  })();
}
