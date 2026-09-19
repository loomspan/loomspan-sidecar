(() => {
  'use strict';
  const accountsRoot = document.querySelector('[data-console="accounts"]');
  const currentRoot = document.querySelector('[data-console="current"]');
  const historyRoot = document.querySelector('[data-console="history"]');
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
    const options = { method, credentials: 'same-origin', keepalive: path === '/session/activity',
      headers: { Accept: 'application/json' } };
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
    api('/session/activity', 'POST').catch(() => {});
  };
  for (const type of ['click', 'keydown', 'input', 'paste', 'scroll']) {
    document.addEventListener(type, reportActivity, { passive: true });
  }
  const field = (label, value) => {
    const p = el('p');
    const strong = el('strong', label + ': ');
    p.append(strong, document.createTextNode(value === null || value === undefined ? 'None' : String(value)));
    return p;
  };
  const recordedStatus = value => {
    if (value === 'PENDING') return 'PENDING — recorded outcome unknown; this record alone does not prove activation success or failure.';
    if (value === 'PUBLISHED') return 'PUBLISHED — bookkeeping recorded publication; runtime identity is shown separately.';
    if (value === 'FAILED') return 'FAILED — recorded failed; retained content remains available for inspection.';
    return String(value || 'Unknown');
  };
  const renderSnapshot = (root, snapshot, heading) => {
    root.append(el('h2', heading), field('Local ID', snapshot.localId),
      field('Recorded status', recordedStatus(snapshot.status)), field('Source ID', snapshot.sourceId),
      field('Submission sequence', snapshot.submissionSequence));
    const configuration = snapshot.configuration;
    root.append(el('h3', 'Skill documents'));
    if (!configuration.skillDocuments.length) root.append(el('p', 'No skill documents were submitted.'));
    for (const document of configuration.skillDocuments) {
      const section = el('section');
      section.append(el('h4', document.sourceName), el('pre', document.yaml));
      root.append(section);
    }
    root.append(el('h3', 'REST routes and targets'), el('pre', configuration.restRoutesYaml));
  };
  const renderRuntimeState = (root, data) => {
    const published = data.published;
    root.append(el('h2', 'Runtime and restart state'), field('Runtime-published local ID', published.localId),
      field('Runtime recorded status', recordedStatus(published.status)), field('Intended restart local ID', data.intendedId),
      field('Intended recorded status', recordedStatus(data.intendedStatus)));
    if (published.localId === data.intendedId) {
      root.append(el('p', 'Runtime-published and intended restart snapshots match. This identity match does not change or resolve the recorded status.'));
    } else {
      root.append(el('p', 'Runtime-published and intended restart snapshots differ. A restart follows the intended selection; this mismatch does not prove the outcome of an earlier operation.', 'warning'));
    }
    if (data.mutationFault) {
      root.append(field('Mutation fault', data.mutationFault),
        el('p', 'Managed configuration acquisition, save, activity renewal, validation, and publication are blocked. Inspection, release or discard of private editing state, and administrator account management remain available.', 'error'));
      const guidance = el('p');
      const link = el('a', 'Read the offline operator recovery procedure');
      link.href = '/management/configuration/recovery';
      guidance.append(link);
      root.append(guidance);
    } else {
      root.append(field('Mutation fault', 'None'));
    }
  };
  const renderCurrent = (data) => {
    const root = document.getElementById('current-content');
    root.replaceChildren();
    renderSnapshot(root, data.published, 'Runtime-published snapshot');
    renderRuntimeState(root, data);
  };
  let historyRevision = 0;
  let historyDetailRevision = 0;
  let currentRevision = 0;
  let historyCurrent = null;
  const renderHistoryList = snapshots => {
    const root = document.getElementById('history-list');
    root.replaceChildren();
    if (!snapshots.length) {
      root.append(el('p', 'No submitted snapshots are retained.'));
      return;
    }
    for (const snapshot of snapshots) {
      const article = el('article', undefined, 'history-entry');
      const button = el('button', 'Sequence ' + snapshot.submissionSequence + ' — ' + snapshot.localId);
      button.type = 'button';
      button.dataset.snapshotId = snapshot.localId;
      button.addEventListener('click', () => loadHistoryDetail(snapshot.localId));
      article.append(button, field('Recorded status', recordedStatus(snapshot.status)),
        field('Source ID', snapshot.sourceId),
        el('p', historyCurrent?.published?.localId === snapshot.localId
          ? 'Current runtime-published snapshot' : 'Not the current runtime-published snapshot',
        historyCurrent?.published?.localId === snapshot.localId ? 'current-marker' : ''));
      root.append(article);
    }
  };
  const loadHistoryDetail = async id => {
    const revision = ++historyDetailRevision;
    const root = document.getElementById('history-detail');
    root.replaceChildren(el('p', 'Loading exact snapshot detail…'));
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
        root.append(el('p', 'This snapshot expired from retained history. Its former content is not displayed. Refresh retained history or inspect the current configuration.'));
        const refreshed = await loadHistory(false);
        if (refreshed !== null) {
          status('history-status', refreshed
            ? 'The selected snapshot expired from retained history. The retained list and current state were refreshed.'
            : 'The selected snapshot expired from retained history, and the retained list/current state could not be refreshed. Try Refresh retained history.', true);
        }
      } else if (error.message !== 'Session expired' && error.message !== 'Access denied') {
        root.append(el('p', 'Snapshot detail could not be shown. Refresh retained history before trying again.'));
        status('history-status', error.message, true);
      }
      document.getElementById('history-status')?.focus();
    }
  };
  const loadHistory = async (announce = true) => {
    const revision = ++historyRevision;
    document.getElementById('history-current').replaceChildren();
    document.getElementById('history-list').replaceChildren(el('p', 'Loading retained submissions…'));
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
          'Retained submissions could not be loaded. Try Refresh retained history or inspect the current configuration.'));
        status('history-status', error.message, true);
      }
      return false;
    }
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
    for (const account of accounts) {
      const section = el('section', undefined, 'account');
      section.append(el('h2', account.email), field('State', (account.enabled ? 'Enabled' : 'Disabled') + ', ' + (account.activated ? 'activated' : 'awaiting password setup')));
      const form = el('form');
      const roleLabel = el('label', 'Role ');
      const select = el('select');
      select.name = 'role';
      for (const role of ['viewer', 'editor', 'admin']) {
        const option = el('option', role === 'admin' ? 'Administrator' : role[0].toUpperCase() + role.slice(1));
        option.value = role;
        option.selected = account.role === role;
        select.append(option);
      }
      roleLabel.append(select);
      const enabledLabel = el('label', 'Enabled ');
      const enabled = el('input'); enabled.type = 'checkbox'; enabled.checked = account.enabled;
      enabledLabel.append(enabled);
      const save = el('button', 'Save account'); save.type = 'submit';
      form.append(roleLabel, enabledLabel, save);
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
        const resend = el('button', 'Resend password setup link'); resend.type = 'button';
        resend.addEventListener('click', async () => {
          try { await api('/accounts/' + encodeURIComponent(account.id) + '/resend-invite', 'POST'); status('accounts-status', 'Password setup link sent.'); }
          catch (error) { status('accounts-status', error.message + '. Check email configuration and retry resend later.', true); }
        });
        section.append(resend);
      }
      root.append(section);
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
    if (currentRoot) {
      document.getElementById('current-refresh').addEventListener('click', loadCurrent);
      document.getElementById('current-export').addEventListener('click', downloadCurrent);
      loadCurrent();
    }
    if (historyRoot) {
      document.getElementById('history-refresh').addEventListener('click', () => {
        historyDetailRevision++;
        document.getElementById('history-detail').replaceChildren(el('p', 'Select a retained submission to load its exact content.'));
        loadHistory();
      });
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
