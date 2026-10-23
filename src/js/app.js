import { App } from '@capacitor/app';
import { ScreenOrientation } from '@capacitor/screen-orientation';

const $ = s => document.querySelector(s), $$ = s => [...document.querySelectorAll(s)];
const audio = $('#audio');
let tracks = [], current = -1, favs = new Set(), shuffleMode = 0, repeat = false;
const colors = ['', 'c2', 'c3', 'c4'];

function esc(s) {
  return String(s).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}
function toast(t) {
  let x = $('#toast');
  x.textContent = t;
  x.classList.add('show');
  setTimeout(() => x.classList.remove('show'), 2600);
}
function fmt(n) {
  if (!Number.isFinite(n)) return '0:00';
  return Math.floor(n / 60) + ':' + String(Math.floor(n % 60)).padStart(2, '0');
}

function page(id) {
  $$('.page').forEach(p => p.classList.toggle('active', p.id === id));
  $$('.nav').forEach(b => b.classList.toggle('active', b.dataset.page === id));
  const mini = $('#mini');
  if (mini) mini.classList.toggle('hidden', id === 'now' || current < 0 || id === 'videoPlayer');
  if (id === 'now') syncNow();
  if (id === 'folders') renderFolders();
  if (id === 'favorites') renderFavorites();
  if (id === 'albums') renderAlbums();
  if (id === 'artists') renderArtists();
  window.scrollTo(0, 0);
}
$$('[data-page]').forEach(b => b.addEventListener('click', () => page(b.dataset.page)));

function addFiles(files) {
  let added = 0;
  for (const f of files) {
    if (!f.type.startsWith('audio/') && !/\.(mp3|m4a|aac|wav|ogg|flac|opus)$/i.test(f.name)) continue;
    let path = f.webkitRelativePath || f.name;
    if (tracks.some(t => t.name === f.name && t.size === f.size && t.path === path)) continue;
    tracks.push({
      name: f.name.replace(/\.[^.]+$/, ''),
      artist: 'Local audio',
      album: 'Unknown album',
      path,
      size: f.size,
      url: URL.createObjectURL(f),
      file: f
    });
    added++;
  }
  renderAll();
  toast(added ? `Added ${added} local audio file(s).` : 'No new audio files found.');
}

$('#scan').onclick = () => {
  if ('showDirectoryPicker' in window) {
    $('#folderPicker').click();
  } else {
    $('#folderPicker').click();
  }
};
$('#folderPicker').addEventListener('change', e => addFiles(e.target.files));
$('#filePicker').addEventListener('change', e => addFiles(e.target.files));

function songRow(t, i) {
  const active = i === current;
  return `<div class="song ${active ? 'is-playing' : ''}" data-song-row="${i}">
    <div class="art ${colors[i % 4]} ${active ? 'playing-art' : ''}">♫</div>
    <button class="songinfo" data-play="${i}">
      <b>${esc(t.name)}${active ? ' <span class="playing-badge">' + (audio.paused ? 'PAUSED' : 'PLAYING') + '</span>' : ''}</b>
      <small>${active ? (audio.paused ? 'Ⅱ Paused · ' : '♫ Now playing · ') : ''}${esc(t.path.includes('/') ? t.path.split('/').slice(0, -1).join(' / ') : 'Local audio')} · ${esc(t.artist)}</small>
    </button>
    <button class="more" data-fav="${i}" title="Favorite">${favs.has(i) ? '♥' : '♡'}</button>
  </div>`;
}

function fillList(id, arr) {
  const el = $(id);
  if (!el) return;
  el.innerHTML = arr.length
    ? arr.map(i => songRow(tracks[i], i)).join('')
    : '<p class="muted">No music loaded yet. Select a folder using Scan music.</p>';
}

function renderAll() {
  let all = tracks.map((_, i) => i);
  fillList('#recent', all.slice(-5).reverse());
  fillList('#allSongs', all);
  fillList('#libraryRecent', all.slice(-6).reverse());
  if ($('#songCount')) $('#songCount').textContent = tracks.length + ' tracks';
  if ($('#countBadge')) $('#countBadge').textContent = tracks.length + ' songs';
  if ($('#favCount')) $('#favCount').textContent = favs.size + ' saved';
  renderFavorites();
  bindRows();
}

function bindRows() {
  $$('[data-play]').forEach(b => (b.onclick = () => playTrack(+b.dataset.play)));
  $$('[data-fav]').forEach(b => (b.onclick = () => {
    let i = +b.dataset.fav;
    favs.has(i) ? favs.delete(i) : favs.add(i);
    renderAll();
  }));
}

function playTrack(i) {
  if (i < 0 || i >= tracks.length) return;
  current = i;
  audio.src = tracks[i].url;
  audio.play().then(() => {
    $('#mini').classList.remove('hidden');
    syncNow();
    page('now');
  }).catch(() => {
    syncNow();
    page('now');
    toast('Tap Play to start this local track.');
  });
  $('#mini').classList.remove('hidden');
  syncNow();
}

function syncNow() {
  if (current < 0) return;
  let t = tracks[current];
  $('#nowTitle').textContent = t.name;
  $('#nowArtist').textContent = t.path.includes('/') ? t.path.split('/').slice(0, -1).join(' / ') : 'Local audio';
  $('#miniTitle').textContent = t.name;
  $('#miniArtist').textContent = 'Local audio';
  $('#play').textContent = audio.paused ? '▶' : 'Ⅱ';
  $('#miniPlay').textContent = audio.paused ? '▶' : 'Ⅱ';
  $('#likeNow').innerHTML = favs.has(current) ? '♥ Remove from favorites' : '♡ Add to favorites';
  updatePlayingRows();
}

function updatePlayingRows() {
  $$('[data-song-row]').forEach(row => {
    const i = Number(row.dataset.songRow), active = i === current;
    row.classList.toggle('is-playing', active);
    const info = row.querySelector('.songinfo');
    if (!info) return;
    const title = info.querySelector('b'), sub = info.querySelector('small');
    if (!title || !sub) return;
    title.innerHTML = esc(tracks[i]?.name || '') + (active ? ' <span class="playing-badge">' + (audio.paused ? 'PAUSED' : 'PLAYING') + '</span>' : '');
    const t = tracks[i];
    sub.textContent = (active ? (audio.paused ? 'Ⅱ Paused · ' : '♫ Now playing · ') : '') + (t?.path?.includes('/') ? t.path.split('/').slice(0, -1).join(' / ') : 'Local audio') + ' · ' + (t?.artist || 'Local audio');
  });
}

$('#play').onclick = () => {
  if (current < 0) {
    if (tracks.length) playTrack(0);
    else toast('No songs loaded.');
    return;
  }
  audio.paused ? audio.play() : audio.pause();
  syncNow();
};
$('#miniPlay').onclick = () => $('#play').click();

function step(d) {
  if (!tracks.length) return;
  let n = (Math.max(0, current) + d + tracks.length) % tracks.length;
  playTrack(n);
}
$('#next').onclick = () => step(1);
$('#prev').onclick = () => {
  if (audio.currentTime > 3) audio.currentTime = 0;
  else step(-1);
};
$('#miniNext').onclick = () => step(1);

audio.addEventListener('play', syncNow);
audio.addEventListener('pause', syncNow);
audio.addEventListener('ended', () => (repeat ? (audio.currentTime = 0, audio.play()) : step(1)));
audio.addEventListener('timeupdate', () => {
  if (audio.duration) {
    $('#seek').value = (audio.currentTime / audio.duration) * 1000;
    $('#elapsed').textContent = fmt(audio.currentTime);
    $('#duration').textContent = fmt(audio.duration);
  }
});
$('#seek').oninput = () => {
  if (audio.duration) audio.currentTime = (+$('#seek').value / 1000) * audio.duration;
};

$('#repeat').onclick = () => {
  repeat = !repeat;
  $('#repeat').style.color = repeat ? '#d69aff' : '';
  toast('Repeat ' + (repeat ? 'on' : 'off'));
};

function renderFavorites() {
  fillList('#favoriteList', [...favs].filter(i => tracks[i]));
}
function renderFolders() {
  let map = {};
  tracks.forEach((t, i) => {
    let f = t.path.includes('/') ? t.path.split('/').slice(0, -1).join('/') : 'Root files';
    (map[f] ??= []).push(i);
  });
  $('#folderList').innerHTML = Object.keys(map).length
    ? Object.entries(map).map(([f, ids]) => `<div class="song"><div class="art">▣</div><button class="songinfo" data-folder="${esc(f)}"><b>${esc(f)}</b><small>${ids.length} files</small></button></div><div class="nested" id="folder-${Object.keys(map).indexOf(f)}"></div>`).join('')
    : '<p class="muted">No folders loaded.</p>';
}
function renderAlbums() {
  let map = {};
  tracks.forEach((t, i) => (map[t.album] ??= []).push(i));
  $('#albumList').innerHTML = Object.keys(map).length
    ? Object.entries(map).map(([name, ids]) => `<button class="album" data-album="${esc(name)}"><div class="albumart">♫</div><b>${esc(name)}</b><small>${ids.length} songs</small></button>`).join('')
    : '<p class="muted">Album details will appear when metadata is read.</p>';
}
function renderArtists() {
  let map = {};
  tracks.forEach((t, i) => (map[t.artist] ??= []).push(i));
  $('#artistList').innerHTML = Object.keys(map).length
    ? Object.entries(map).map(([name, ids]) => `<div class="song"><div class="art">♙</div><button class="songinfo" data-artist="${esc(name)}"><b>${esc(name)}</b><small>${ids.length} songs</small></button></div>`).join('')
    : '<p class="muted">Artists will appear when metadata is read.</p>';
}

// ----------------------------------------------------
// PostMessage Bridge: Video Player se Home Wapas Aana
// ----------------------------------------------------
window.addEventListener('message', e => {
  if (e.data && e.data.type === 'NAVIGATE_PAGE') {
    page(e.data.target || 'home');
  }
});

// ----------------------------------------------------
// Android Hardware Back Button Handling
// ----------------------------------------------------
App.addListener('backButton', () => {
  const videoPage = document.getElementById('videoPlayer');
  const nowPage = document.getElementById('now');

  if (videoPage && videoPage.classList.contains('active')) {
    page('home');
  } else if (nowPage && nowPage.classList.contains('active')) {
    page('home');
  } else {
    App.exitApp();
  }
});

renderAll();
