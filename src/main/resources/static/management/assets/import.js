(() => {
  'use strict';
  if (!document.querySelector('[data-console="import"]')) return;
  const $ = id => document.getElementById(id);
  let csrf, selected, reviewed, busy = false;
  const status = (text, error = false) => { $('import-status').textContent = text; $('import-status').classList.toggle('error', error); };
  const outcome = (text, error = false) => { $('import-outcome').textContent = text; $('import-outcome').classList.toggle('error', error); };
  const json = async (path, method = 'GET', body) => {
    const response = await fetch('/api/management' + path, { method, credentials: 'same-origin',
      headers: { Accept: 'application/json', 'Content-Type': 'application/json', 'X-CSRF-TOKEN': csrf },
      body: method === 'GET' ? undefined : JSON.stringify(body || {}) });
    const data = await response.json().catch(() => null);
    if (!response.ok) throw new Error(data?.error || `Request failed (${response.status})`);
    return data;
  };
  const upload = async (path, fields = {}, file = selected) => {
    const form = new FormData(); form.append('bundle', file, file.name);
    for (const [key, value] of Object.entries(fields)) form.append(key, value);
    const response = await fetch('/api/management/configuration/import/' + path, { method: 'POST',
      credentials: 'same-origin', headers: { Accept: 'application/json', 'X-CSRF-TOKEN': csrf }, body: form });
    const data = await response.json().catch(() => null);
    if (!response.ok) throw new Error(data?.error || `Request failed (${response.status})`);
    return data;
  };
  const detail = (label, value) => { const name = document.createElement('dt'), content = document.createElement('dd');
    name.textContent = label; content.textContent = String(value ?? 'None'); $('import-details').append(name, content); };
  const invalidate = () => { reviewed = null; $('import-summary').hidden = true;
    $('import-confirm').disabled = true; $('import-details').replaceChildren(); $('import-issues').replaceChildren(); outcome(''); };
  $('import-file').addEventListener('change', () => { selected = $('import-file').files[0] || null; invalidate(); status(selected ? 'Review the selected bundle.' : 'Choose a bundle.'); });
  $('import-review').addEventListener('click', async () => {
    if (!selected || busy) return; const requestedFile = selected;
    busy = true; invalidate(); status('Reviewing bundle…');
    try { const data = await upload('review', {}, requestedFile);
      if (selected !== requestedFile) return;
      detail('Source snapshot', data.sourceSnapshotId); detail('Sidecar version', data.sidecarVersion);
      detail('Framework version', data.frameworkVersion); detail('Skill documents', data.skillDocuments);
      for (const issue of data.validation.issues) { const item = document.createElement('li'); item.textContent = issue.message; $('import-issues').append(item); }
      reviewed = data; $('import-summary').hidden = false; $('import-confirm').disabled = false;
      status('Review complete. Loading will replace your saved draft; validate and publish from the editor.');
    } catch (error) { if (selected === requestedFile) status(error.message, true); } finally { busy = false; }
  });
  $('import-confirm').addEventListener('click', async () => {
    if (!reviewed || !selected || busy) return;
    if (!confirm('Load this bundle into your saved draft? It will not publish until you validate and publish from the editor.')) return;
    const acceptedFile = selected;
    busy = true; $('import-confirm').disabled = true;
    try {
      let ownership = await json('/editing');
      if (ownership.held && !ownership.sameUser) throw new Error('Another user holds editing control.');
      const grant = await json(ownership.held ? '/editing/lease/handoff' : '/editing/lease', 'POST', { label: 'Import console' });
      const draft = grant.draft;
      const current = await json('/configuration/current');
      if (selected !== acceptedFile) throw new Error('Selected bundle changed. Review it again before loading.');
      const loaded = await upload('load', { editingSessionId: grant.editingSessionId, generation: grant.generation,
        draftId: draft.draftId, revision: draft.revision, baseSnapshotId: current.published.localId }, acceptedFile);
      outcome(`Loaded saved draft revision ${loaded.revision}. Open the editor, validate, then publish.`);
      status('Bundle loaded into your private draft.');
    } catch (error) { outcome(error.message, true); } finally { busy = false; }
  });
  fetch('/api/management/session', { credentials: 'same-origin', headers: { Accept: 'application/json' } })
    .then(r => r.json()).then(data => { csrf = data.csrfToken; }).catch(() => status('Sign in again.', true));
})();
