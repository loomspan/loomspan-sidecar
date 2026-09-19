(() => {
  'use strict';
  if (!document.querySelector('[data-console="import"]')) return;
  const $ = id => document.getElementById(id);
  let csrf = null;
  let reviewed = null;
  let selected = null;
  let busy = false;
  const status = (message, error = false) => {
    $('import-status').textContent = message;
    $('import-status').classList.toggle('error', error);
  };
  const outcome = (message, error = false) => {
    $('import-outcome').textContent = message;
    $('import-outcome').classList.toggle('error', error);
  };
  const invalidate = () => {
    reviewed = null;
    $('import-summary').hidden = true;
    $('import-confirm').disabled = true;
    $('import-details').replaceChildren();
    $('import-issues').replaceChildren();
    outcome('');
  };
  const detail = (label, value) => {
    const name = document.createElement('dt'); name.textContent = label;
    const content = document.createElement('dd'); content.textContent = String(value ?? 'None');
    $('import-details').append(name, content);
  };
  const observation = data => {
    detail('Runtime-published snapshot', data?.publishedId);
    detail('Editing grant', data?.grantId);
    detail('Lease owner', data?.leaseOwner);
  };
  const render = data => {
    $('import-details').replaceChildren();
    $('import-issues').replaceChildren();
    detail('Source snapshot', data.sourceSnapshotId);
    detail('Sidecar producer version', data.sidecarVersion);
    detail('Framework producer version', data.frameworkVersion);
    detail('Skill documents', data.skillDocuments);
    detail('Validated skills', data.validatedSkills);
    detail('REST routes', data.routes);
    observation(data.observation);
    for (const issue of data.validation.issues) {
      const item = document.createElement('li');
      item.textContent = [issue.severity, issue.sourceLabel, issue.skillName, issue.location, issue.message]
        .filter(Boolean).join(' · ');
      $('import-issues').append(item);
    }
    if (!data.validation.issues.length) {
      const item = document.createElement('li'); item.textContent = 'No validation issues.';
      $('import-issues').append(item);
    }
    $('import-summary').hidden = false;
    reviewed = data.reviewId && data.validation.successful ? data : null;
    $('import-confirm').disabled = !reviewed;
    status(reviewed ? 'Review passed. Read the disruption warning before confirming.'
      : 'Destination validation failed. Choose a corrected bundle and review again.', !reviewed);
  };
  const upload = async (path, fields) => {
    const form = new FormData();
    form.append('bundle', selected, selected.name);
    for (const [key, value] of Object.entries(fields)) if (value != null) form.append(key, value);
    const response = await fetch('/api/management/configuration/import/' + path, {
      method: 'POST', credentials: 'same-origin', headers: { Accept: 'application/json', 'X-CSRF-TOKEN': csrf }, body: form
    });
    let data = null;
    try { data = await response.json(); } catch (_) { /* A disconnect may have no response. */ }
    if (response.status === 401 || response.status === 403) {
      invalidate();
      throw new Error('Management session or import access ended. Sign in again.');
    }
    if (!response.ok) {
      const error = new Error(data?.error || 'Request failed (' + response.status + ')');
      error.status = response.status; error.body = data;
      throw error;
    }
    return data;
  };
  $('import-file').addEventListener('change', () => {
    selected = $('import-file').files[0] || null;
    invalidate();
    status(selected ? 'Selected ' + selected.name + '. Review it before confirming.' : 'Choose a bundle to review.');
  });
  $('import-review').addEventListener('click', async () => {
    if (!selected || busy) return;
    const requestedFile = selected;
    busy = true; invalidate(); status('Reviewing bundle against this destination…');
    try {
      const result = await upload('review', {});
      if (selected === requestedFile) render(result);
    } catch (error) {
      if (selected === requestedFile) status(error.message || 'Review could not complete.', true);
    }
    finally { busy = false; }
  });
  $('import-confirm').addEventListener('click', async () => {
    if (!reviewed || !selected || busy) return;
    busy = true;
    const accepted = reviewed;
    $('import-confirm').disabled = true;
    outcome('Publishing imported configuration…');
    try {
      const snapshot = await upload('confirm', { reviewId: accepted.reviewId,
        expectedPublishedId: accepted.observation.publishedId,
        expectedGrantId: accepted.observation.grantId });
      reviewed = null;
      outcome('Import published as local snapshot ' + snapshot.localId + '. Inspect current runtime and history.');
      status('Import completed. A new review is required for another import.');
    } catch (error) {
      if (error.status === 409 && error.body?.code === 'confirmation_stale' && error.body?.observation) {
        render({ ...accepted, observation: error.body.observation });
        outcome('Confirmation changed. Review the refreshed runtime and lease owner, then explicitly confirm again.', true);
      } else if (error.status === 409) {
        reviewed = null;
        outcome('Review no longer valid. Review the bundle again before confirming; no import was accepted.', true);
      } else if (error.status) {
        reviewed = null;
        const code = error.body?.code;
        const location = ' Check current runtime and history before another attempt.';
        if (code === 'outcome_recording_failed' || code === 'history_pruning_failed') {
          outcome('Import activated, but bookkeeping failed and configuration mutations stopped.' + location, true);
        } else if (code === 'revert_failed') {
          outcome('Import activation failed and the intended pointer could not be restored; configuration mutations stopped.' + location, true);
        } else if (code === 'activation_failed') {
          outcome('Import activation failed; the intended pointer was restored.' + location, true);
        } else {
          outcome('Import did not return a successful outcome: ' + error.message + '.' + location, true);
        }
      } else {
        reviewed = null;
        outcome('Connection lost; import outcome is unknown. Check current runtime and history. Do not retry blindly.', true);
      }
    } finally { busy = false; }
  });
  fetch('/api/management/session', { credentials: 'same-origin', headers: { Accept: 'application/json' } })
    .then(response => { if (!response.ok) throw new Error(); return response.json(); })
    .then(data => { csrf = data.csrfToken; })
    .catch(() => status('Sign in again before reviewing an import.', true));
})();
