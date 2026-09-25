(() => {
  'use strict';
  const accountsRoot = document.querySelector('[data-console="accounts"]');
  const currentRoot = document.querySelector('[data-console="current"]');
  const historyRoot = document.querySelector('[data-console="history"]');
  const homeRoot = document.querySelector('[data-console="home"]');
  // The editor owns its activity cadence so a lease report also renews login once.
  if (document.querySelector('[data-console="editor"]')) return;
  let csrfToken = null;
  let lastActivity = 0;
  const el = (tag, content, className) => {
    const node = document.createElement(tag);
    if (content !== undefined && content !== null) node.textContent = String(content);
    if (className) node.className = className;
    return node;
  };
  const status = (id, message, error = false) => {
    const node = document.getElementById(id);
    node.textContent = message;
    node.classList.toggle('error', error);
  };
  const lostSession = () => {
    window.location.assign('/management/login');
  };
  const forbidden = () => {
    if (accountsRoot) {
      document.getElementById('invite-form')?.remove();
      document.getElementById('accounts-refresh')?.remove();
      document.getElementById('accounts-list').replaceChildren();
      status('accounts-status', 'Administrator access is no longer available. Sign in again if your role changed.', true);
    } else if (currentRoot) {
      document.getElementById('current-refresh')?.remove();
      document.getElementById('current-export')?.remove();
      document.getElementById('current-content')?.replaceChildren();
      status('current-status', 'This account cannot view the current configuration. Sign in again if your role changed.', true);
    } else if (historyRoot) {
      document.getElementById('history-refresh')?.remove();
      document.getElementById('history-current')?.replaceChildren();
      document.getElementById('history-list')?.replaceChildren();
      document.getElementById('history-detail')?.replaceChildren();
      status('history-status', 'This account cannot inspect configuration history. Sign in again if your role changed.', true);
    }
  };
  const api = async (path, method = 'GET', body) => {
    const options = {
      method, credentials: 'same-origin', keepalive: path === '/session/activity',
      headers: { Accept: 'application/json' }
    };
    if (method !== 'GET') {
      options.headers['X-CSRF-TOKEN'] = csrfToken;
      options.headers['Content-Type'] = 'application/json';
      options.body = JSON.stringify(body || {});
    }
    const response = await fetch('/api/management' + path, options);
    if (response.status === 401) { lostSession(); throw new Error('Session expired'); }
    if (response.status === 403) { forbidden(); throw new Error('Access denied'); }
    if (!response.ok) {
      let problem = {};
      try { problem = await response.json(); } catch (_) { /* status is sufficient */ }
      const error = new Error(problem.error || 'Request failed (' + response.status + ')');
      error.status = response.status;
      error.code = problem.code;
      throw error;
    }
    return response.status === 204 || response.status === 202 ? null : response.json();
  };
  const reportActivity = () => {
    if (Date.now() - lastActivity < 30000 || !csrfToken) return;
    lastActivity = Date.now();
    api('/session/activity', 'POST').catch(() => { });
  };
  for (const type of ['click', 'keydown', 'input', 'paste', 'scroll']) {
    document.addEventListener(type, reportActivity, { passive: true });
  }
  const statusInfo = {
    PENDING: ['warn', 'Recorded outcome unknown; this record alone does not prove activation success or failure.'],
    PUBLISHED: ['ok', 'Bookkeeping recorded publication; runtime identity is shown separately.'],
    FAILED: ['bad', 'Recorded failed; retained content remains available for inspection.']
  };
  const badge = (text, tone) => el('span', text, 'badge badge-' + tone);
  const statusBadge = value => badge(value || 'Unknown', statusInfo[value]?.[0] || 'neutral');
  const recordedStatus = value => {
    const wrap = el('span', undefined, 'status-value');
    wrap.append(statusBadge(value));
    if (statusInfo[value]) wrap.append(el('span', statusInfo[value][1], 'status-note'));
    return wrap;
  };
  const facts = rows => {
    const list = el('dl', undefined, 'facts');
    for (const [label, value, mono] of rows) {
      const dd = el('dd', value instanceof Node ? undefined : (value ?? 'None'), mono && value != null ? 'mono' : '');
      if (value instanceof Node) dd.append(value);
      list.append(el('dt', label), dd);
    }
    return list;
  };
  const card = (heading, aside) => {
    const node = el('div', undefined, 'card');
    const head = el('div', undefined, 'card-head');
    head.append(el('h2', heading));
    if (aside) head.append(aside);
    node.append(head);
    return node;
  };
  const lineCount = text => text ? text.replace(/\n$/, '').split('\n').length : 0;
  const codeBlock = (title, text) => {
    const block = el('section', undefined, 'code-block');
    const head = el('header');
    const lines = lineCount(text);
    head.append(el('span', title, 'code-title'), el('span', lines + (lines === 1 ? ' line' : ' lines'), 'code-meta'));
    block.append(head, el('pre', text));
    return block;
  };
  const sectionHeading = (title, count) => {
    const head = el('div', undefined, 'section-head');
    const h = el('h3', title, 'section-title');
    if (count !== undefined) h.append(document.createTextNode(' · ' + count));
    head.append(h);
    return head;
  };
  const renderSnapshot = (root, snapshot, heading) => {
    const summary = card(heading, statusBadge(snapshot.status));
    summary.append(facts([['Local ID', snapshot.localId, true], ['Recorded status', recordedStatus(snapshot.status)],
    ['Source ID', snapshot.sourceId, true], ['Submission sequence', snapshot.submissionSequence]]));
    root.append(summary);
    const configuration = snapshot.configuration;
    const skills = el('div', undefined, 'stack');
    skills.append(sectionHeading('Skill documents', configuration.skillDocuments.length));
    if (!configuration.skillDocuments.length) skills.append(el('p', 'No skill documents were submitted.', 'empty'));
    for (const document of configuration.skillDocuments) skills.append(codeBlock(document.sourceName, document.yaml));
    const routes = el('div', undefined, 'stack');
    routes.append(sectionHeading('REST routes and targets'), codeBlock('Routes and targets', configuration.restRoutesYaml));
    root.append(skills, routes);
  };
  const renderRuntimeState = (root, data) => {
    const published = data.published;
    const matches = published.localId === data.intendedId;
    const summary = card('Runtime and restart state', data.mutationFault ? badge('Changes blocked', 'bad') : badge('Changes available', 'ok'));
    summary.append(facts([['Runtime-published local ID', published.localId, true],
    ['Runtime recorded status', recordedStatus(published.status)], ['Intended restart local ID', data.intendedId, true],
    ['Intended recorded status', recordedStatus(data.intendedStatus)],
    ['Mutation fault', data.mutationFault ? badge(data.mutationFault, 'bad') : 'None']]));
    const notes = el('div', undefined, 'stack');
    notes.style.marginTop = '1rem';
    notes.append(matches
      ? el('p', 'Runtime-published and intended restart snapshots match. This identity match does not change or resolve the recorded status.', 'notice')
      : el('p', 'Runtime-published and intended restart snapshots differ. A restart follows the intended selection; this mismatch does not prove the outcome of an earlier operation.', 'notice warning'));
    if (data.mutationFault) {
      const blocked = el('p', 'Managed configuration acquisition, save, activity renewal, validation, and publication are blocked. Inspection, release or discard of private editing state, and administrator account management remain available. ', 'notice error');
      const link = el('a', 'Read the offline operator recovery procedure');
      link.href = '/management/configuration/recovery';
      blocked.append(link);
      notes.append(blocked);
    }
    summary.append(notes);
    root.append(summary);
  };
  const renderCurrent = (data) => {
    const root = document.getElementById('current-content');
    root.replaceChildren();
    renderRuntimeState(root, data);
    renderSnapshot(root, data.published, 'Runtime-published snapshot');
  };
  let historyRevision = 0;
  let historyDetailRevision = 0;
  let currentRevision = 0;
  let historyCurrent = null;
  let selectedHistoryId = null;
  let rollbackReview = null;
  const rollbackButton = document.getElementById('rollback-review');
  const clearRollback = () => {
    rollbackReview = null;
    if (!rollbackButton) return;
    rollbackButton.disabled = !selectedHistoryId;
    document.getElementById('rollback-confirm').disabled = true;
    document.getElementById('rollback-summary').hidden = true;
    document.getElementById('rollback-details').replaceChildren();
    document.getElementById('rollback-issues').replaceChildren();
    status('rollback-status', selectedHistoryId ? 'Review the selected snapshot before confirming.' : 'Select a retained snapshot to review.');
  };
  const showRollbackReview = data => {
    const details = document.getElementById('rollback-details');
    const issues = document.getElementById('rollback-issues');
    details.replaceChildren(); issues.replaceChildren();
    details.append(...facts([['Source snapshot', data.sourceId, true],
      ['Source recorded status', recordedStatus(data.sourceStatus)], ['Submission sequence', data.submissionSequence],
      ['Skill documents', data.skillDocuments]]).childNodes);
    for (const issue of data.validation.issues) issues.append(el('li',
      [issue.severity, issue.sourceLabel, issue.skillName, issue.location, issue.message].filter(Boolean).join(' · ')));
    if (!data.validation.issues.length) issues.append(el('li', 'No validation issues.'));
    document.getElementById('rollback-summary').hidden = false;
    rollbackReview = data;
    document.getElementById('rollback-confirm').disabled = !rollbackReview;
    status('rollback-status', rollbackReview ? 'Review complete. Loading will replace your saved draft; validate and publish from the editor.'
      : 'Destination validation failed. A rollback cannot be confirmed.', !rollbackReview);
  };
  const renderHistoryList = snapshots => {
    const root = document.getElementById('history-list');
    root.replaceChildren();
    if (!snapshots.length) {
      root.append(el('p', 'No submitted snapshots are retained.', 'empty'));
      return;
    }
    for (const snapshot of snapshots) {
      const current = historyCurrent?.published?.localId === snapshot.localId;
      const article = el('article', undefined, 'history-entry' + (snapshot.localId === selectedHistoryId ? ' is-selected' : ''));
      article.dataset.snapshotId = snapshot.localId;
      const button = el('button');
      button.type = 'button';
      button.dataset.snapshotId = snapshot.localId;
      button.append(el('span', 'Sequence ' + snapshot.submissionSequence, 'entry-seq'), el('span', ' — ', 'entry-sep'),
        el('span', snapshot.localId, 'entry-id'));
      button.addEventListener('click', () => loadHistoryDetail(snapshot.localId));
      const meta = el('div', undefined, 'entry-meta');
      meta.append(statusBadge(snapshot.status));
      if (current) meta.append(badge('Current runtime-published snapshot', 'info'));
      if (snapshot.sourceId) meta.append(el('span', 'Source ' + snapshot.sourceId, 'mono'));
      article.append(button, meta);
      root.append(article);
    }
  };
  const markSelected = () => {
    for (const entry of document.querySelectorAll('#history-list .history-entry'))
      entry.classList.toggle('is-selected', entry.dataset.snapshotId === selectedHistoryId);
  };
  const loadHistoryDetail = async id => {
    const revision = ++historyDetailRevision;
    selectedHistoryId = id;
    markSelected();
    clearRollback();
    const root = document.getElementById('history-detail');
    root.replaceChildren(el('p', 'Loading exact snapshot detail…', 'empty'));
    try {
      const snapshot = await api('/configuration/history/' + encodeURIComponent(id));
      if (revision !== historyDetailRevision) return;
      if (snapshot.localId !== id) throw new Error('Snapshot detail identity did not match the selected retained entry. Refresh history before continuing.');
      root.replaceChildren();
      renderSnapshot(root, snapshot, 'Selected retained snapshot');
      status('history-status', 'Showing exact retained snapshot ' + id + '. Authored values may contain secrets.');
    } catch (error) {
      if (revision !== historyDetailRevision) return;
      root.replaceChildren();
      if (error.code === 'history_not_found') {
        selectedHistoryId = null;
        clearRollback();
        root.append(el('p', 'This snapshot expired from retained history. Its former content is not displayed. Refresh retained history or inspect the current configuration.', 'empty'));
        const refreshed = await loadHistory(false);
        if (refreshed !== null) {
          status('history-status', refreshed
            ? 'The selected snapshot expired from retained history. The retained list and current state were refreshed.'
            : 'The selected snapshot expired from retained history, and the retained list/current state could not be refreshed. Try Refresh retained history.', true);
        }
      } else if (error.message !== 'Session expired' && error.message !== 'Access denied') {
        root.append(el('p', 'Snapshot detail could not be shown. Refresh retained history before trying again.', 'empty'));
        status('history-status', error.message, true);
      }
      document.getElementById('history-status')?.focus();
    }
  };
  const loadHistory = async (announce = true) => {
    const revision = ++historyRevision;
    document.getElementById('history-current').replaceChildren();
    document.getElementById('history-list').replaceChildren(el('p', 'Loading retained submissions…', 'empty'));
    try {
      const [current, snapshots] = await Promise.all([
        api('/configuration/current'), api('/configuration/history')
      ]);
      if (revision !== historyRevision) return null;
      historyCurrent = current;
      const currentRoot = document.getElementById('history-current');
      currentRoot.replaceChildren();
      renderRuntimeState(currentRoot, current);
      renderHistoryList(snapshots);
      if (announce) status('history-status', 'Retained submissions loaded in authoritative oldest-first order. Select one to load exact detail.');
      return true;
    } catch (error) {
      if (revision !== historyRevision) return null;
      if (error.message !== 'Session expired' && error.message !== 'Access denied') {
        document.getElementById('history-list').replaceChildren(el('p',
          'Retained submissions could not be loaded. Try Refresh retained history or inspect the current configuration.', 'empty'));
        status('history-status', error.message, true);
      }
      return false;
    }
  };
  const refreshRollbackState = async () => {
    await loadHistory(false);
    if (selectedHistoryId) await loadHistoryDetail(selectedHistoryId);
  };
  const reviewRollback = async () => {
    const id = selectedHistoryId;
    if (!id || !rollbackButton) return;
    clearRollback();
    status('rollback-status', 'Validating retained source against this destination…');
    try {
      const result = await api('/configuration/rollback/' + encodeURIComponent(id) + '/review', 'POST');
      if (selectedHistoryId !== id) return;
      showRollbackReview(result);
    } catch (error) {
      if (selectedHistoryId !== id) return;
      if (error.code === 'source_not_found') {
        selectedHistoryId = null; clearRollback();
        await loadHistory(false);
        status('rollback-status', 'Source no longer retained. History and current state were refreshed.', true);
      } else status('rollback-status', error.message, true);
      document.getElementById('rollback-status').focus();
    }
  };
  const confirmRollback = async () => {
    const accepted = rollbackReview;
    if (!accepted || selectedHistoryId !== accepted.sourceId) return;
    if (!confirm('Load this retained snapshot into your saved draft? Validate and publish it from the editor.')) return;
    rollbackReview = null;
    document.getElementById('rollback-confirm').disabled = true;
    status('rollback-status', 'Loading retained content into your saved draft…');
    let resultMessage;
    try {
      const ownership = await api('/editing');
      if (ownership.held && !ownership.sameUser) throw new Error('Another user holds editing control.');
      const grant = await api(ownership.held ? '/editing/lease/handoff' : '/editing/lease', 'POST', { label: 'History console' });
      const current = await api('/configuration/current');
      const draft = grant.draft;
      const loaded = await api('/configuration/rollback/' + encodeURIComponent(accepted.sourceId) + '/load', 'POST', {
        editingSessionId: grant.editingSessionId, generation: grant.generation,
        draftId: draft.draftId, revision: draft.revision, baseSnapshotId: current.published.localId
      });
      resultMessage = [`Loaded saved draft revision ${loaded.revision}. Open the editor to validate and publish.`, false];
    } catch (error) { resultMessage = [error.message, true]; }
    finally { await refreshRollbackState(); status('rollback-status', ...resultMessage); }
  };
  const loadCurrent = async () => {
    const revision = ++currentRevision;
    document.getElementById('current-content').replaceChildren();
    status('current-status', 'Loading runtime snapshot…');
    try {
      const current = await api('/configuration/current');
      if (revision !== currentRevision) return;
      renderCurrent(current);
      status('current-status', 'Showing the runtime-published configuration. Authored values may contain secrets.');
    } catch (error) {
      if (revision === currentRevision && error.message !== 'Session expired' && error.message !== 'Access denied')
        status('current-status', error.message, true);
    }
  };
  const downloadCurrent = async () => {
    const button = document.getElementById('current-export');
    button.disabled = true;
    status('current-status', 'Preparing current configuration bundle…');
    try {
      const response = await fetch('/api/management/configuration/export', { credentials: 'same-origin', headers: { Accept: 'application/zip' } });
      if (response.status === 401) { lostSession(); return; }
      if (response.status === 403) { forbidden(); return; }
      if (!response.ok) {
        let problem = {};
        try { problem = await response.json(); } catch (_) { /* status is sufficient */ }
        throw new Error(problem.error || 'Export failed (' + response.status + ')');
      }
      const url = URL.createObjectURL(await response.blob());
      const link = el('a');
      link.href = url;
      link.download = 'sidecar-current-configuration.zip';
      document.body.append(link);
      link.click();
      link.remove();
      setTimeout(() => URL.revokeObjectURL(url), 60000);
      status('current-status', 'Current configuration downloaded. Handle the bundle as sensitive data.');
    } catch (error) { status('current-status', error.message, true); }
    finally { button.disabled = false; }
  };
  const renderAccounts = (accounts) => {
    const root = document.getElementById('accounts-list');
    root.replaceChildren();
    if (!accounts.length) root.append(el('p', 'No accounts yet.', 'empty'));
    for (const account of accounts) {
      const section = el('section', undefined, 'account');
      const identity = el('div', undefined, 'account-id');
      const text = el('div');
      const pills = el('div', undefined, 'account-pills');
      pills.append(account.enabled ? badge('Enabled', 'ok') : badge('Disabled', 'bad'),
        account.activated ? badge('Activated', 'info') : badge('Awaiting password setup', 'warn'));
      text.append(el('h3', account.email), pills);
      identity.append(el('span', account.email.charAt(0).toUpperCase(), 'avatar'), text);
      section.append(identity);
      const form = el('form');
      const select = el('select');
      select.name = 'role';
      select.setAttribute('aria-label', 'Role for ' + account.email);
      for (const role of ['viewer', 'editor', 'admin']) {
        const option = el('option', role === 'admin' ? 'Administrator' : role[0].toUpperCase() + role.slice(1));
        option.value = role;
        option.selected = account.role === role;
        select.append(option);
      }
      const enabledLabel = el('label', undefined, 'toggle');
      const enabled = el('input'); enabled.type = 'checkbox'; enabled.checked = account.enabled;
      enabledLabel.append(enabled, document.createTextNode('Enabled'));
      const save = el('button', 'Save account', 'btn-sm'); save.type = 'submit';
      form.append(select, enabledLabel, save);
      form.addEventListener('submit', async event => {
        event.preventDefault();
        try {
          await api('/accounts/' + encodeURIComponent(account.id), 'PATCH', { role: select.value, enabled: enabled.checked });
          status('accounts-status', 'Account updated. Changed or disabled accounts must sign in again.');
          await loadAccounts(false);
        } catch (error) { status('accounts-status', error.message + '. The last enabled administrator cannot be disabled or demoted.', true); await loadAccounts(false); }
      });
      section.append(form);
      if (!account.activated) {
        const resend = el('button', 'Resend password setup link', 'btn-sm resend'); resend.type = 'button';
        resend.addEventListener('click', async () => {
          try { await api('/accounts/' + encodeURIComponent(account.id) + '/resend-invite', 'POST'); status('accounts-status', 'Password setup link sent.'); }
          catch (error) { status('accounts-status', error.message + '. Check email configuration and retry resend later.', true); }
        });
        section.append(resend);
      }
      root.append(section);
    }
  };
  const loadHome = async () => {
    const put = (id, value, meta, mono) => {
      const node = document.getElementById(id);
      node.replaceChildren(...[value].flat().map(v => v instanceof Node ? v : document.createTextNode(String(v))));
      const metaNode = document.getElementById(id + '-meta');
      metaNode.textContent = meta || '';
      metaNode.classList.toggle('mono', !!mono);
    };
    try {
      const [current, snapshots] = await Promise.all([api('/configuration/current'), api('/configuration/history')]);
      const published = current.published;
      put('home-runtime', statusBadge(published.status), published.localId, true);
      const matches = published.localId === current.intendedId;
      put('home-restart', matches ? badge('Matches runtime', 'ok') : badge('Differs from runtime', 'warn'),
        current.intendedId ? 'Intended ' + current.intendedId : 'No intended snapshot recorded', true);
      const documents = published.configuration.skillDocuments.length;
      put('home-skills', documents, documents === 1 ? '1 authored skill document is running.' : documents + ' authored skill documents are running.');
      const latest = snapshots[snapshots.length - 1];
      put('home-history', snapshots.length, latest ? 'Latest submission: sequence ' + latest.submissionSequence : 'No submissions retained yet.');
      const alerts = document.getElementById('home-alerts');
      alerts.replaceChildren();
      if (current.mutationFault) {
        const fault = el('p', 'Configuration changes are blocked: ' + current.mutationFault + '. Inspection and account management remain available. ', 'notice error');
        const link = el('a', 'Open the recovery guide');
        link.href = '/management/configuration/recovery';
        fault.append(link);
        alerts.append(fault);
      }
      if (!matches) alerts.append(el('p', 'Runtime-published and intended restart snapshots differ. A restart follows the intended selection; inspect current configuration and history before intervening.', 'notice warning'));
      status('home-status', '');
    } catch (error) {
      if (error.message !== 'Session expired' && error.message !== 'Access denied') status('home-status', 'Runtime overview could not be loaded: ' + error.message, true);
    }
  };
  const loadAccounts = async (announce = true) => {
    try {
      renderAccounts(await api('/accounts'));
      if (announce) status('accounts-status', 'Accounts loaded. Email addresses are immutable.');
    } catch (error) { if (error.message !== 'Session expired' && error.message !== 'Access denied') status('accounts-status', error.message, true); }
  };
  const start = async () => {
    try { csrfToken = (await api('/session')).csrfToken; }
    catch (_) { return; }
    if (homeRoot) loadHome();
    if (currentRoot) {
      document.getElementById('current-refresh').addEventListener('click', loadCurrent);
      document.getElementById('current-export').addEventListener('click', downloadCurrent);
      loadCurrent();
    }
    if (historyRoot) {
      document.getElementById('history-refresh').addEventListener('click', () => {
        historyDetailRevision++;
        selectedHistoryId = null;
        clearRollback();
        document.getElementById('history-detail').replaceChildren(el('p', 'Select a retained submission to load its exact content.', 'empty'));
        loadHistory();
      });
      rollbackButton?.addEventListener('click', reviewRollback);
      document.getElementById('rollback-confirm')?.addEventListener('click', confirmRollback);
      loadHistory();
    }
    if (accountsRoot) {
      document.getElementById('accounts-refresh').addEventListener('click', () => loadAccounts());
      document.getElementById('invite-form').addEventListener('submit', async event => {
        event.preventDefault();
        const form = event.currentTarget;
        try {
          await api('/accounts', 'POST', { email: form.elements.email.value, role: form.elements.role.value });
          form.reset();
          status('accounts-status', 'Invitation saved and password setup link sent. The account cannot sign in until setup is complete.');
          await loadAccounts(false);
        } catch (error) {
          status('accounts-status', error.message + '. The account may be pending even if email failed. Refresh accounts, then resend its link after delivery recovers.', true);
          await loadAccounts(false);
        }
      });
      loadAccounts();
    }
  };
  start();
})();
