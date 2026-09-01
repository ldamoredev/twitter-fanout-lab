import { $, call, mountTop, uuid } from '/lib.js';

mountTop({ current: '/modelo.html' });

const alice = uuid();
const bob = uuid();
$('#publish-author').value = bob;
$('#follow-follower').value = alice;
$('#follow-followee').value = bob;
$('#timeline-user').value = alice;

function numberOf(id) {
  return Number($(`#${id}`).value);
}

function formatBytes(bytes) {
  if (!Number.isFinite(bytes) || bytes < 0) return '—';
  if (bytes >= 1e12) {
    return `${(bytes / 1e12).toLocaleString('es-AR', {
      minimumFractionDigits: 1,
      maximumFractionDigits: 1,
    })} TB`;
  }
  if (bytes >= 1e9) {
    return `${(bytes / 1e9).toLocaleString('es-AR', { maximumFractionDigits: 0 })} GB`;
  }
  if (bytes >= 1e6) {
    return `${(bytes / 1e6).toLocaleString('es-AR', { maximumFractionDigits: 1 })} MB`;
  }
  return `${bytes.toLocaleString('es-AR')} B`;
}

function recalc() {
  const users = numberOf('users');
  const windowSize = numberOf('window');
  const idBytes = numberOf('id-bytes');
  const postBytes = numberOf('post-bytes');
  const ids = users * windowSize * idBytes;
  const full = users * windowSize * postBytes;

  $('#bytes-ids').textContent = formatBytes(ids);
  $('#bytes-full').textContent = formatBytes(full);
  $('#bytes-ids-raw').textContent = `${ids.toLocaleString('es-AR')} bytes`;
  $('#bytes-full-raw').textContent = `${full.toLocaleString('es-AR')} bytes`;

  const ratio = ids === 0 ? 0 : full / ids;
  $('#calc-ratio').textContent = Number.isFinite(ratio)
    ? `el post completo pesa ${ratio.toLocaleString('es-AR', { maximumFractionDigits: 1 })}× los IDs`
    : '';
}

['users', 'window', 'id-bytes', 'post-bytes'].forEach((id) => {
  $(`#${id}`).addEventListener('input', recalc);
});
recalc();

function show(key, result) {
  $(`#${key}-ms`).textContent = `${result.status} · ${result.ms} ms`;
  const body = result.raw === '' || result.raw == null ? '(sin cuerpo)' : result.raw;
  $(`#${key}-out`).textContent = body;
}

$('#publish').addEventListener('click', async () => {
  const result = await call('/posts', {
    method: 'POST',
    body: { authorId: $('#publish-author').value, text: $('#publish-text').value },
  });
  show('publish', result);
});

$('#follow').addEventListener('click', async () => {
  const result = await call('/follows', {
    method: 'POST',
    body: {
      followerId: $('#follow-follower').value,
      followeeId: $('#follow-followee').value,
    },
  });
  show('follow', result);
});

$('#timeline').addEventListener('click', async () => {
  const userId = $('#timeline-user').value;
  const result = await call(`/timelines/${userId}`);
  show('timeline', result);
});
