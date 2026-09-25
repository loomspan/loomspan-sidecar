(() => {
  'use strict';
  const root = document.querySelector('[data-console="editor"]');
  if (!root) return;
  const $ = id => document.getElementById(id);
  const state = { session: null, runtimeId: null, draft: null, grant: null, ownership: null,
    docs: [], rest: '', dirty: false, busy: false, failed: false, valid: false, fault: false,
    editing: false, timer: null, lastActivity: 0, localVersion: 0 };
  const message = (text, error = false) => { $('editor-message').textContent = text; $('editor-message').classList.toggle('error', error); };
  const api = async (path, method = 'GET', body) => {
    const options = { method, credentials: 'same-origin', headers: { Accept: 'application/json' } };
    if (method !== 'GET') {
      options.headers['X-CSRF-TOKEN'] = state.session.csrfToken;
      options.headers['Content-Type'] = 'application/json';
      options.body = JSON.stringify(body || {});
    }
    const response = await fetch('/api/management' + path, options);
    let data = null;
    try { if (response.status !== 204) data = await response.json(); } catch (_) {}
    if (!response.ok) {
      const error = new Error(data?.error || `Request failed (${response.status})`);
      error.status = response.status; error.code = data?.code;
      throw error;
    }
    return data;
  };
  const capability = () => ({ editingSessionId: state.grant?.editingSessionId, generation: state.grant?.generation });
  const candidate = () => ({ ...capability(), draftId: state.draft?.draftId,
    revision: state.draft?.revision, baseSnapshotId: state.draft?.baseSnapshotId });
  const content = () => ({ skillDocuments: state.docs.map(d => ({ sourceName: d.sourceName, yaml: d.yaml })), restRoutesYaml: state.rest });
  const render = () => {
    const editable = state.editing && !state.fault && !state.busy;
    $('editor-fields').hidden = !state.session;
    for (const node of root.querySelectorAll('#editor-fields input, #editor-fields textarea')) node.readOnly = !editable;
    for (const node of root.querySelectorAll('.editor-remove')) node.hidden = !editable;
    $('editor-add').hidden = !editable;
    $('editor-acquire').hidden = !state.session || state.session.role === 'viewer' || !!state.ownership?.held || !!state.grant || state.fault;
    $('editor-handoff').hidden = !state.session || state.session.role === 'viewer' || !state.ownership?.held || !state.ownership?.sameUser || !!state.grant || state.fault;
    $('editor-takeover').hidden = !state.session || state.session.role !== 'admin' || !state.ownership?.held || !!state.grant || state.fault;
    $('editor-release').hidden = !state.grant;
    $('editor-resume-local').hidden = !state.grant || !state.dirty || state.editing;
    $('editor-discard').hidden = !state.grant || !state.draft;
    $('editor-reconcile').hidden = !state.grant || !state.draft?.stale;
    $('editor-continue').hidden = !state.grant;
    $('editor-retry').hidden = !state.grant || !state.failed;
    $('editor-recheck').hidden = !state.grant || state.dirty || state.busy;
    $('editor-publish').disabled = !state.grant || !state.valid || state.dirty || state.busy || state.draft?.stale || state.fault;
    $('editor-save').textContent = state.busy ? 'Working…' : state.failed ? 'Unsaved local changes' : state.dirty
      ? 'Unsaved local changes' : state.draft ? `Saved draft revision ${state.draft.revision}` : 'Published configuration';
    $('editor-validation-state').textContent = state.valid && !state.dirty ? 'Valid' : state.draft?.stale ? 'Stale base' : 'Out of date';
    $('editor-owner').textContent = state.grant ? 'You hold editing control.' : state.ownership?.held
      ? `Editing control: ${state.ownership.holderLabel || 'another client'}.` : 'No editing session holds control.';
    $('editor-deadline').textContent = state.grant ? `Lease expires ${new Date(state.grant.expiresAt).toLocaleString()}. Renew only with explicit activity.` : '';
  };
  const draw = () => {
    const list = $('editor-skills'); list.replaceChildren();
    state.docs.forEach((doc, index) => {
      const section = document.createElement('section'); section.className = 'editor-document';
      const head = document.createElement('div'); head.className = 'editor-document-head';
      const label = document.createElement('label'); label.className = 'field';
      const caption = document.createElement('span'); caption.textContent = `Source label ${index + 1}`;
      const name = document.createElement('input'); name.value = doc.sourceName; name.setAttribute('aria-label', `Source label ${index + 1}`);
      name.addEventListener('input', () => { doc.sourceName = name.value; changed(); }); label.append(caption, name);
      const remove = document.createElement('button'); remove.type = 'button'; remove.className = 'editor-remove btn-ghost-danger';
      remove.textContent = 'Remove'; remove.addEventListener('click', () => { state.docs.splice(index, 1); draw(); changed(); });
      head.append(label, remove);
      const yaml = document.createElement('textarea'); yaml.value = doc.yaml; yaml.spellcheck = false;
      yaml.setAttribute('aria-label', `Skill YAML ${index + 1}`);
      yaml.addEventListener('input', () => { doc.yaml = yaml.value; changed(); });
      section.append(head, yaml); list.append(section);
    });
    if (!state.docs.length) { const empty = document.createElement('p'); empty.className = 'empty'; empty.textContent = 'No skill documents.'; list.append(empty); }
    $('editor-rest').value = state.rest;
    render();
  };
  const setDraft = (draft, overwrite = true) => {
    state.draft = draft;
    state.valid = !!draft?.validation?.successful && !draft?.stale;
    $('editor-saved-preview').hidden = !draft;
    $('editor-saved-content').textContent = draft ?
      `Revision ${draft.revision}${draft.stale ? ' (stale)' : ''}\n`
        + draft.configuration.skillDocuments.map(d => `--- ${d.sourceName} ---\n${d.yaml}`).join('\n')
        + `\n--- REST routes ---\n${draft.configuration.restRoutesYaml}` : '';
    if (overwrite) {
      state.docs = (draft?.configuration.skillDocuments || []).map(d => ({ ...d }));
      state.rest = draft?.configuration.restRoutesYaml || '';
      state.dirty = false; state.failed = false;
      draw();
    }
    $('editor-issues').replaceChildren();
    for (const issue of draft?.validation?.issues || []) {
      const item = document.createElement('li');
      item.textContent = [issue.severity, issue.sourceLabel, issue.location, issue.message].filter(Boolean).join(' · ');
      $('editor-issues').append(item);
    }
    render();
  };
  const changed = () => {
    if (!state.editing) return;
    state.dirty = true; state.failed = false; state.valid = false; state.localVersion++;
    clearTimeout(state.timer);
    if (!state.draft?.stale) state.timer = setTimeout(save, 600);
    render();
  };
  const lose = text => {
    state.grant = null; state.editing = false; state.valid = false;
    clearTimeout(state.timer);
    message(`${text} Any unsaved local text remains visible and will not be submitted automatically.`);
    render();
  };
  const save = async (reconcile = false) => {
    if (!state.grant || state.busy || !state.dirty || (!reconcile && state.draft?.stale)) return;
    const before = state.localVersion, grant = state.grant, request = { ...candidate(), ...content() };
    if (reconcile) request.baseSnapshotId = state.runtimeId;
    state.busy = true; render();
    try {
      const draft = await api(reconcile ? '/editing/draft/reconcile' : '/editing/draft', reconcile ? 'POST' : 'PUT', request);
      if (grant !== state.grant) return;
      const localChanged = before !== state.localVersion;
      setDraft(draft, false);
      if (!localChanged) { state.dirty = false; state.failed = false; }
      else { state.dirty = true; state.valid = false; }
      message(localChanged ? 'A newer local change remains unsaved.' : 'Saved to your durable draft. Validate before publishing.');
    } catch (error) {
      state.failed = true;
      if (error.status === 409) await poll();
      message('Save failed. Your local text remains visible. Inspect the saved draft before retrying.', true);
    } finally { state.busy = false; render(); }
  };
  const poll = async () => {
    if (!state.session) return;
    try {
      const checkedGrant = state.grant;
      const [current, ownership] = await Promise.all([api('/configuration/current'), api('/editing')]);
      if (checkedGrant !== state.grant) return;
      state.runtimeId = current.published.localId; state.fault = !!current.mutationFault;
      state.ownership = ownership;
      if (state.grant && (!ownership.mine || ownership.editingSessionId !== state.grant.editingSessionId
          || Date.parse(ownership.expiresAt) <= Date.now())) lose('Editing control ended.');
      let draft = null;
      try { draft = await api('/editing/draft'); } catch (error) { if (error.status !== 404) throw error; }
      if (checkedGrant !== state.grant) return;
      if (draft && (!state.draft || draft.draftId !== state.draft.draftId || draft.revision !== state.draft.revision || draft.stale !== state.draft.stale)) {
        if (state.dirty) {
          setDraft(draft, false);
          message(`Saved draft revision ${draft.revision} changed. Your unsaved local text is separate; inspect it before any new submission.`);
        } else setDraft(draft);
      } else if (!draft && state.draft && !state.dirty) setDraft(null);
      if (!draft && !state.draft && !state.dirty) {
        state.docs = current.published.configuration.skillDocuments.map(d => ({ ...d }));
        state.rest = current.published.configuration.restRoutesYaml; draw();
      }
      $('editor-outcome').textContent = `Published configuration: ${current.published.localId}. ${draft ? `Your saved draft: revision ${draft.revision}${draft.stale ? ' (stale)' : ''}.` : 'No saved draft.'}`;
      render();
    } catch (error) { message('Could not refresh saved draft: ' + error.message, true); }
  };
  const acquire = async path => {
    if (state.dirty && !confirm('Unsaved local text will remain visible, separate from the current saved draft. Continue?')) return;
    try {
      const grant = await api('/editing/lease' + path, 'POST', { label: 'Console' });
      state.grant = grant; state.editing = !state.dirty;
      // Read the server revision returned with control. Do not submit retained local text.
      setDraft(grant.draft, !state.dirty);
      message(state.dirty ? 'Editing control acquired. Compare your unsaved local text with the saved draft, then explicitly resume editing it.'
        : 'Editing control acquired. Current saved revision is shown; no local text was submitted.');
      await poll();
    } catch (error) { message('Could not take editing control: ' + error.message, true); await poll(); }
  };
  const validate = async () => {
    if (!state.grant || state.dirty || state.busy) return;
    state.busy = true; render();
    try {
      const draft = await api('/editing/draft/validate', 'POST', candidate());
      setDraft(draft, false); message(draft.validation.successful ? 'Saved revision validated.' : 'Validation found errors.');
    } catch (error) { if (error.status === 409) await poll(); message('Validation failed: ' + error.message, true); }
    finally { state.busy = false; render(); }
  };
  const publish = async () => {
    if (!state.grant || !state.valid || state.dirty || state.busy) return;
    state.busy = true; render();
    try {
      const snapshot = await api('/configuration/publish', 'POST', candidate());
      state.grant = null; state.editing = false; setDraft(null);
      message(`Published configuration ${snapshot.localId}.`); await poll();
    } catch (error) {
      message('Publication did not complete: ' + error.message + '. Inspect current state before retrying.', true);
      await poll();
    } finally { state.busy = false; render(); }
  };
  const activity = event => {
    if (!state.session || Date.now() - state.lastActivity < 30000 || (event.type === 'keydown' && ['Shift', 'Control', 'Alt', 'Meta'].includes(event.key))) return;
    state.lastActivity = Date.now();
    api('/session/activity', 'POST').catch(() => {});
    if (state.grant) api('/editing/lease/renew', 'POST', capability()).then(grant => {
      if (state.grant?.generation === grant.generation) { state.grant.expiresAt = grant.expiresAt; render(); }
    }).catch(() => lose('Editing lease ended.'));
  };
  $('editor-rest').addEventListener('input', event => { state.rest = event.target.value; changed(); });
  $('editor-add').addEventListener('click', () => { state.docs.push({ sourceName: `skill-${state.docs.length + 1}.yaml`, yaml: '' }); draw(); changed(); });
  $('editor-acquire').addEventListener('click', () => acquire(''));
  $('editor-handoff').addEventListener('click', () => acquire('/handoff'));
  $('editor-takeover').addEventListener('click', () => acquire('/takeover'));
  $('editor-release').addEventListener('click', async () => { try { await api('/editing/lease/release', 'POST', capability()); lose('Editing released.'); await poll(); } catch (error) { message(error.message, true); } });
  $('editor-resume-local').addEventListener('click', () => { state.editing = true;
    message('Editing your retained local text. Review the server-saved draft before making changes.'); render(); });
  $('editor-discard').addEventListener('click', async () => { if (!confirm('Discard your saved draft?')) return;
    try { await api('/editing/draft', 'DELETE', candidate()); state.grant = null; state.editing = false; setDraft(null); await poll(); }
    catch (error) { message(error.message, true); } });
  $('editor-reconcile').addEventListener('click', () => { if (confirm('Submit the complete visible configuration against the current published base? Review all fields first.')) { state.dirty = true; save(true); } });
  $('editor-retry').addEventListener('click', () => { state.failed = false; save(); });
  $('editor-recheck').addEventListener('click', validate);
  $('editor-publish').addEventListener('click', publish);
  $('editor-continue').addEventListener('click', () => { state.lastActivity = 0; activity({ type: 'click' }); });
  for (const type of ['keydown', 'input', 'paste', 'click', 'scroll']) document.addEventListener(type, activity, { passive: true });
  setInterval(poll, 10000);
  (async () => { try { state.session = await api('/session'); await poll(); message('Saved draft and published configuration loaded.'); }
    catch (error) { message('Could not load configuration: ' + error.message, true); } })();
})();
