(() => {
  'use strict';
  const accountsRoot = document.querySelector('[data-console="accounts"]');
  const currentRoot = document.querySelector('[data-console="current"]');
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
    } else {
      document.getElementById('current-refresh')?.remove();
      document.getElementById('current-content')?.replaceChildren();
      status('current-status', 'This account cannot view the current configuration. Sign in again if your role changed.', true);
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
      let details = '';
      try { details = (await response.json()).error || ''; } catch (_) { /* status is sufficient */ }
      throw new Error(details || 'Request failed (' + response.status + ')');
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
  const renderCurrent = (data) => {
    const root = document.getElementById('current-content');
    root.replaceChildren();
    const published = data.published;
    root.append(el('h2', 'Runtime-published snapshot'),
      field('Local ID', published.localId), field('Status', published.status),
      field('Source ID', published.sourceId), field('Submission sequence', published.submissionSequence));
    const configuration = published.configuration;
    root.append(el('h2', 'Skill documents'));
    if (!configuration.skillDocuments.length) root.append(el('p', 'No skill documents are published.'));
    for (const document of configuration.skillDocuments) {
      const section = el('section');
      section.append(el('h3', document.sourceName), el('pre', document.yaml));
      root.append(section);
    }
    root.append(el('h2', 'REST routes and targets'), el('pre', configuration.restRoutesYaml),
      el('h2', 'Restart selection and diagnostics'), field('Intended ID', data.intendedId),
      field('Intended status', data.intendedStatus), field('Mutation fault', data.mutationFault));
  };
  const loadCurrent = async () => {
    try {
      renderCurrent(await api('/configuration/current'));
      status('current-status', 'Showing the runtime-published configuration. Authored values may contain secrets.');
    } catch (error) { if (error.message !== 'Session expired' && error.message !== 'Access denied') status('current-status', error.message, true); }
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
      loadCurrent();
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
