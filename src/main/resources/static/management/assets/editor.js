(() => {
  'use strict';
  const root = document.querySelector('[data-console="editor"]');
  if (!root) return;
  const $ = id => document.getElementById(id);
  const tabId = crypto.randomUUID();
  const state = {
    session: null, grant: null, candidateId: null, baseId: null, runtimeId: null,
    revision: 0, acknowledged: 0, validated: -1, validationCandidate: null, validationSuccessful: false,
    saving: false, checking: false, failed: false, checkFailed: false, publishing: false, uncertain: false,
    editable: false, invalidated: false, fault: false, expiry: null, lastActivity: 0,
    saveTimer: null, checkTimer: null, documents: [], rest: ''
  };
  const putText = (id, value) => { $(id).textContent = value; };
  const problem = (message) => { putText('editor-message', message); $('editor-message').classList.add('error'); };
  const info = (message) => { putText('editor-message', message); $('editor-message').classList.remove('error'); };
  class ApiError extends Error {
    constructor(response, body) { super(body?.error || `Request failed (${response.status})`); this.status = response.status; this.code = body?.code; this.body = body; }
  }
  const api = async (path, method = 'GET', body) => {
    const options = { method, credentials: 'same-origin', headers: { Accept: 'application/json' } };
    if (method !== 'GET') {
      options.headers['X-CSRF-TOKEN'] = state.session.csrfToken;
      options.headers['Content-Type'] = 'application/json';
      options.body = JSON.stringify(body || {});
    }
    const response = await fetch('/api/management' + path, options);
    let data = null;
    if (response.status !== 204 && response.status !== 202) {
      try { data = await response.json(); } catch (_) { /* Transport or non-JSON error. */ }
    }
    if (response.status === 401) { sessionLost(); throw new ApiError(response, data); }
    if (response.status === 403) { sessionLost(); throw new ApiError(response, data); }
    if (!response.ok) throw new ApiError(response, data);
    return data;
  };
  const sessionLost = () => {
    state.invalidated = true; state.grant = null; state.editable = false;
    state.documents = []; state.rest = ''; clearTimeout(state.saveTimer); clearTimeout(state.checkTimer);
    renderDocuments();
    $('editor-issues').replaceChildren(); $('editor-outcome').replaceChildren();
    problem('Your management session or role changed. Sign in again; the private draft cannot be recovered from this page.');
    render();
  };
  const config = () => ({ skillDocuments: state.documents.map(d => ({ sourceName: d.sourceName, yaml: d.yaml })), restRoutesYaml: state.rest });
  const capability = () => ({ tabId, grantId: state.grant });
  const currentContent = () => JSON.stringify(config());
  const canPublish = () => state.editable && !!state.grant && !state.saving && !state.checking
    && !state.failed && !state.checkFailed && !state.publishing && !state.uncertain && !state.invalidated && !state.fault
    && (!state.expiry || Date.parse(state.expiry) > Date.now())
    && state.acknowledged === state.revision && state.validated === state.revision && state.validationSuccessful
    && state.validationCandidate === state.candidateId;
  const render = () => {
    $('editor-fields').hidden = state.invalidated || (!state.session) ? true : false;
    for (const node of root.querySelectorAll('#editor-fields input, #editor-fields textarea')) node.readOnly = !state.editable;
    $('editor-add').hidden = !state.editable;
    for (const node of root.querySelectorAll('.editor-remove')) node.hidden = !state.editable;
    $('editor-acquire').hidden = !state.session || state.session.role === 'viewer' || !!state.grant || state.invalidated || state.fault;
    $('editor-takeover').hidden = !state.session || state.session.role !== 'admin' || !!state.grant || state.invalidated || state.fault;
    $('editor-release').hidden = !state.grant;
    $('editor-discard').hidden = !state.session || state.session.role === 'viewer' || state.invalidated || !state.baseId;
    $('editor-retry').hidden = !state.editable || !state.failed;
    $('editor-recheck').hidden = !state.editable || !state.checkFailed;
    $('editor-publish').disabled = !canPublish();
    const saveLabel = state.publishing ? 'Publishing…' : state.saving ? 'Saving…' : state.failed ? 'Save failed — unsaved edits'
      : !state.editable ? 'Not editing' : state.acknowledged === state.revision ? 'Saved to private draft' : 'Unsaved';
    putText('editor-save', saveLabel);
    const validationLabel = state.checking ? 'Checking…' : state.checkFailed ? 'Check failed' : state.validated === state.revision && state.validationCandidate === state.candidateId
      ? $('editor-validation-state').dataset.result || 'Valid' : 'Out of date';
    putText('editor-validation-state', validationLabel);
    $('editor-validation-state').classList.toggle('valid', validationLabel === 'Valid');
    $('editor-validation-state').classList.toggle('error', validationLabel === 'Validation errors');
  };
  const renderDocuments = () => {
    const list = $('editor-skills'); list.replaceChildren();
    state.documents.forEach((doc, index) => {
      const section = document.createElement('section'); section.className = 'editor-document';
      const head = document.createElement('div'); head.className = 'editor-document-head';
      const sourceLabel = document.createElement('label'); sourceLabel.className = 'field';
      const sourceText = document.createElement('span'); sourceText.textContent = `Source label ${index + 1}`;
      const source = document.createElement('input'); source.value = doc.sourceName; source.setAttribute('aria-label', `Source label ${index + 1}`);
      source.addEventListener('input', () => { doc.sourceName = source.value; changed(); }); sourceLabel.append(sourceText, source);
      const remove = document.createElement('button'); remove.type = 'button'; remove.className = 'editor-remove btn-ghost-danger'; remove.textContent = 'Remove';
      remove.setAttribute('aria-label', `Remove skill document ${index + 1}`);
      remove.addEventListener('click', () => { state.documents.splice(index, 1); renderDocuments(); changed(); });
      head.append(sourceLabel, remove);
      const yaml = document.createElement('textarea'); yaml.value = doc.yaml; yaml.spellcheck = false; yaml.setAttribute('aria-label', `Skill YAML ${index + 1}`);
      yaml.addEventListener('input', () => { doc.yaml = yaml.value; changed(); });
      section.append(head, yaml); list.append(section);
    });
    if (!state.documents.length) {
      const empty = document.createElement('p'); empty.className = 'empty'; empty.textContent = 'No skill documents. REST routes can still be configured below.';
      list.append(empty);
    }
    $('editor-rest').value = state.rest;
    render();
  };
  const displayValidation = result => {
    const list = $('editor-issues'); list.replaceChildren();
    for (const issue of result.issues || []) {
      const item = document.createElement('li');
      item.textContent = [issue.severity, issue.sourceLabel, issue.skillName, issue.location, issue.message]
        .filter(value => value !== null && value !== undefined && value !== '').join(' · ');
      list.append(item);
    }
    if (!list.childElementCount) { const item = document.createElement('li'); item.textContent = 'No issues.'; list.append(item); }
    $('editor-validation-state').dataset.result = result.successful ? 'Valid' : 'Validation errors';
    state.validationSuccessful = result.successful;
    render();
  };
  const changed = () => {
    if (!state.editable) return;
    state.revision++;
    state.validated = -1; state.validationCandidate = null; state.validationSuccessful = false; state.checkFailed = false;
    state.failed = false; state.uncertain = false;
    clearTimeout(state.saveTimer); clearTimeout(state.checkTimer);
    state.saveTimer = setTimeout(save, 600);
    render();
  };
  const save = async () => {
    if (!state.editable || state.saving || state.failed || state.acknowledged === state.revision) return;
    const revision = state.revision, content = currentContent(), expected = state.candidateId, grant = state.grant;
    state.saving = true; render();
    try {
      const draft = await api('/editing/draft', 'PUT', { ...capability(), expectedCandidateId: expected, ...JSON.parse(content) });
      if (state.grant !== grant || state.invalidated || state.baseId !== draft.baseSnapshotId) return;
      state.candidateId = draft.candidateId;
      if (state.revision === revision && currentContent() === content) {
        state.acknowledged = revision; state.checkTimer = setTimeout(validate, 300);
      }
    } catch (error) {
      if (!state.invalidated) {
        state.failed = true;
        if (error.status === 409 || error.code === 'configuration_unavailable') await refreshOwnership(error.code);
        if (state.editable) problem('Save failed. Your local text is still here. Retry only after checking ownership and the runtime base.');
      }
    } finally {
      state.saving = false; render();
      if (state.editable && !state.failed && state.acknowledged !== state.revision) state.saveTimer = setTimeout(save, 0);
    }
  };
  const validate = async () => {
    if (!state.editable || state.saving || state.checking || state.failed || state.acknowledged !== state.revision) return;
    const revision = state.revision, candidate = state.candidateId, grant = state.grant;
    state.checking = true; render();
    try {
      const result = await api('/editing/draft/validate', 'POST', { ...capability(), expectedCandidateId: candidate });
      if (state.editable && state.grant === grant && revision === state.revision && candidate === state.candidateId && result.applied && result.candidateId === candidate) {
        state.validated = revision; state.validationCandidate = candidate; displayValidation(result.validation);
      }
    } catch (error) {
      if (error.status === 409 || error.code === 'configuration_unavailable') await refreshOwnership(error.code);
      if (state.editable && revision === state.revision) {
        state.checkFailed = true;
        problem('Validation could not finish. Retry validation or edit to check the draft again.');
      }
    } finally {
      state.checking = false; render();
      if (state.editable && !state.failed && !state.checkFailed && state.acknowledged === state.revision && state.validated !== state.revision)
        state.checkTimer = setTimeout(validate, 300);
    }
  };
  const setDraft = (draft, writable) => {
    state.baseId = draft.baseSnapshotId; state.candidateId = draft.candidateId;
    state.documents = draft.configuration.skillDocuments.map(d => ({ sourceName: d.sourceName, yaml: d.yaml }));
    state.rest = draft.configuration.restRoutesYaml;
    state.revision = 0; state.acknowledged = 0; state.validated = draft.validation ? 0 : -1;
    state.validationCandidate = draft.validation ? draft.candidateId : null;
    state.validationSuccessful = !!draft.validation?.successful;
    state.editable = writable; state.failed = false; state.checkFailed = false; state.invalidated = false;
    $('editor-issues').replaceChildren(); delete $('editor-validation-state').dataset.result;
    renderDocuments(); if (draft.validation) displayValidation(draft.validation);
  };
  const loseGrant = message => {
    state.grant = null; state.editable = false;
    clearTimeout(state.saveTimer); clearTimeout(state.checkTimer);
    info(message + ' Your local text remains on this page while the session and runtime base are valid.'); render();
  };
  const refreshOwnership = async reason => {
    if (state.invalidated) return;
    try {
      const checkedGrant = state.grant;
      const [current, ownership] = await Promise.all([api('/configuration/current'), api('/editing')]);
      if (checkedGrant !== state.grant || state.invalidated) return;
      if (current.published.localId !== state.runtimeId) {
        state.runtimeId = current.published.localId; state.grant = null; state.editable = false;
        state.invalidated = true; state.documents = []; state.rest = ''; $('editor-issues').replaceChildren(); renderDocuments();
        problem('The runtime configuration changed. The old-base draft and local edits are invalid; start over by reloading this page.');
        return;
      }
      state.fault = !!current.mutationFault;
      if (current.mutationFault) {
        state.editable = false;
        clearTimeout(state.saveTimer); clearTimeout(state.checkTimer);
        info('Configuration mutations are unavailable: ' + current.mutationFault + '. Runtime inspection and lease release remain available.');
        render(); return;
      }
      if (state.grant && (!ownership.mine || !ownership.held || ownership.mineTabId !== tabId
          || Date.parse(ownership.expiresAt) <= Date.now()))
        loseGrant('The editing lease ended.');
      putText('editor-owner', state.grant ? 'You are editing in this tab.' : ownership.mine
        ? 'Another tab in this session is editing. Its grant cannot be copied to this tab.'
        : ownership.held ? 'Another editor is editing.' : 'No tab currently holds the editing lease.');
      state.expiry = state.grant ? ownership.expiresAt : null;
      if (reason) info('Editing state changed (' + reason + '). Check ownership before continuing.');
      render();
    } catch (error) { if (!state.invalidated) problem('Could not inspect editing state: ' + error.message); }
  };
  const acquire = async takeover => {
    if (takeover && !confirm('Take over editing? Your own current draft will be replaced with the runtime configuration.')) return;
    try {
      const retained = !takeover && state.baseId === state.runtimeId && state.revision !== state.acknowledged
        ? currentContent() : null;
      const grant = await api(takeover ? '/editing/lease/takeover' : '/editing/lease', 'POST', { tabId });
      state.grant = grant.grantId; state.expiry = grant.expiresAt;
      setDraft(grant.draft, true); info(takeover ? 'Editing taken over from runtime configuration.' : 'Editing your private draft.');
      if (retained && grant.draft.baseSnapshotId === state.runtimeId) {
        const content = JSON.parse(retained);
        state.documents = content.skillDocuments; state.rest = content.restRoutesYaml;
        renderDocuments(); changed();
        info('Editing resumed. Unsaved local text was retained and will be saved to this private draft.');
      }
      await refreshOwnership();
    } catch (error) { problem('Could not acquire editing: ' + error.message); await refreshOwnership(); }
  };
  const release = async () => {
    try { await api('/editing/lease/release', 'POST', capability()); loseGrant('Editing released.'); await refreshOwnership(); }
    catch (error) { problem('Could not release editing: ' + error.message); await refreshOwnership(); }
  };
  const discard = async () => {
    if (!confirm('Discard your private draft and local edits?')) return;
    try {
      await api('/editing/draft', 'DELETE', state.grant ? capability() : {});
      state.grant = null; state.baseId = null; state.editable = false; state.documents = []; state.rest = '';
      state.revision = 0; state.acknowledged = 0; state.validated = -1; $('editor-issues').replaceChildren(); renderDocuments();
      info('Draft discarded. Start editing to begin again from runtime configuration.'); await refreshOwnership();
    } catch (error) { problem('Could not discard draft: ' + error.message); await refreshOwnership(); }
  };
  const describeCurrent = async prefix => {
    try {
      const current = await api('/configuration/current');
      putText('editor-outcome', `${prefix} Runtime: ${current.published.localId} (${current.published.status}). Intended: ${current.intendedId || 'none'} (${current.intendedStatus || 'none'}). Mutation fault: ${current.mutationFault || 'none'}.`);
      state.fault = !!current.mutationFault;
      if (current.published.localId !== state.runtimeId) {
        state.runtimeId = current.published.localId; state.invalidated = true; state.grant = null;
        state.documents = []; state.rest = ''; $('editor-issues').replaceChildren(); renderDocuments();
      }
      if (state.fault) { state.editable = false; render(); }
    } catch (_) { putText('editor-outcome', prefix + ' Current runtime state could not be inspected.'); }
  };
  const publish = async () => {
    if (!canPublish()) return;
    const candidate = state.candidateId;
    state.publishing = true; render();
    try {
      const result = await api('/configuration/publish', 'POST', { ...capability(), expectedCandidateId: candidate });
      state.runtimeId = result.localId; state.grant = null; state.editable = false; state.invalidated = true;
      state.documents = []; state.rest = ''; $('editor-issues').replaceChildren(); renderDocuments();
      await describeCurrent('Published successfully.');
    } catch (error) {
      state.uncertain = !Number.isInteger(error.status);
      const code = error.code || (state.uncertain ? 'unknown_connection_outcome' : 'request_rejected');
      const outcome = {
        preparation_failed: 'Publication stopped before activation.',
        commit_failed: 'Publication stopped before activation.',
        activation_failed: 'Activation failed; the previous runtime remains active.',
        revert_failed: 'Activation failed and intended selection could not be reverted.',
        outcome_recording_failed: 'The new configuration is active, but its outcome could not be recorded.',
        history_pruning_failed: 'The new configuration is active, but history pruning failed.',
        validation_required: 'The draft needs successful validation before publication.',
        candidate_conflict: 'The saved candidate changed; save and validate the current draft again.',
        base_conflict: 'The runtime base changed; start a new draft.'
      }[code] || `Publish was rejected (${code}). ${error.message}.`;
      await describeCurrent(state.uncertain
        ? 'Connection lost after Publish. The outcome is unknown; no automatic retry will occur.'
        : outcome);
      if (error.status === 409) await refreshOwnership(code);
    } finally { state.publishing = false; render(); }
  };
  const activity = event => {
    if (state.invalidated || !state.session || Date.now() - state.lastActivity < 30000) return;
    if (event.type === 'keydown' && ['Shift', 'Control', 'Alt', 'Meta'].includes(event.key)) return;
    state.lastActivity = Date.now();
    const grant = state.grant;
    const path = grant ? '/editing/lease/activity' : '/session/activity';
    api(path, 'POST', grant ? capability() : {}).then(result => {
      if (state.grant === grant && result?.expiresAt) { state.expiry = result.expiresAt; renderDeadline(); }
    }).catch(error => { if (state.grant === grant && grant && error.status === 409) loseGrant('The editing lease ended.'); });
  };
  const renderDeadline = () => {
    if (!state.session) return;
    const lease = state.session.editLeaseTimeoutSeconds / 60, login = state.session.sessionIdleTimeoutSeconds / 60;
    const remaining = state.expiry ? Date.parse(state.expiry) - Date.now() : Infinity;
    putText('editor-deadline', `Editing lease: ${lease} minutes; login idle limit: ${login} minutes. `
      + (state.grant && remaining <= 120000 ? 'Editing lease expires within two minutes. Continue editing to renew it.' : ''));
    $('editor-continue').hidden = !state.grant || remaining > 120000;
    if (state.grant && remaining <= 0) loseGrant('The editing lease expired.');
  };
  $('editor-rest').addEventListener('input', event => { state.rest = event.target.value; changed(); });
  $('editor-add').addEventListener('click', () => { state.documents.push({ sourceName: `skill-${state.documents.length + 1}.yaml`, yaml: '' }); renderDocuments(); changed(); });
  $('editor-acquire').addEventListener('click', () => acquire(false));
  $('editor-takeover').addEventListener('click', () => acquire(true));
  $('editor-release').addEventListener('click', release);
  $('editor-discard').addEventListener('click', discard);
  $('editor-retry').addEventListener('click', () => { state.failed = false; save(); });
  $('editor-recheck').addEventListener('click', () => { state.checkFailed = false; validate(); });
  $('editor-publish').addEventListener('click', publish);
  $('editor-continue').addEventListener('click', () => { state.lastActivity = 0; activity({ type: 'click' }); });
  for (const type of ['keydown', 'input', 'paste', 'click', 'scroll']) document.addEventListener(type, activity, { passive: true });
  setInterval(() => { renderDeadline(); if (state.session && !state.invalidated) refreshOwnership(); }, 10000);
  (async () => {
    try {
      state.session = await api('/session');
      const [current, ownership] = await Promise.all([api('/configuration/current'), api('/editing')]);
      state.runtimeId = current.published.localId;
      state.fault = !!current.mutationFault;
      let draft = null;
      if (state.session.role !== 'viewer') {
        try { draft = await api('/editing/draft'); } catch (error) { if (error.status !== 404) throw error; }
      }
      if (draft && draft.baseSnapshotId === state.runtimeId) setDraft(draft, false);
      else setDraft({ baseSnapshotId: state.runtimeId, candidateId: null, configuration: current.published.configuration }, false);
      info(state.fault ? 'Configuration mutations are unavailable: ' + current.mutationFault + '. Runtime inspection remains available.'
        : state.session.role === 'viewer' ? 'Runtime configuration is read-only for viewers.'
        : draft ? 'Your private draft is available. Acquire a new editing lease to resume.'
          : 'Showing runtime configuration. Acquire editing to create a private draft.');
      putText('editor-owner', ownership.mine ? 'Another tab in this session is editing.'
        : ownership.held ? 'Another editor is editing.' : 'No tab currently holds the editing lease.');
      renderDeadline(); render();
    } catch (error) { if (!state.invalidated) problem('Could not load configuration: ' + error.message); }
  })();
})();
