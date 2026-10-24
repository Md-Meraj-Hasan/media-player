import { Capacitor, registerPlugin } from '@capacitor/core';
import { App } from '@capacitor/app';
import { ScreenOrientation } from '@capacitor/screen-orientation';

const $ = s => document.querySelector(s), $$ = s => [...document.querySelectorAll(s)];

// --- NATIVE PLUGINS REGISTRATION ---
const VideoPermissionBridge = registerPlugin('MediaPermission');
const VideoScannerBridge = registerPlugin('MediaLibraryScanner');
const NativePlayer = registerPlugin('MerajMediaPlayer');

// --- STATE VARIABLES ---
let tracks = [], current = -1, favs = new Set(), shuffleMode = 0, repeatMode = 0;
let nativeMode = false, nativePlayback = false, nativeIsPlaying = false, nativePosition = 0, nativeDuration = 0;
let nativeEndedHandled = false, historyPages = ['home'], playbackSpeed = 1, equalizerPreset = 'normal';
const colors = ['', 'c2', 'c3', 'c4'];
const audio = $('#audio');

// --- THEME LOGIC ---
const themeNames = ['default', 'green', 'red'];
function applyTheme(theme, save = true) {
  if (!themeNames.includes(theme)) theme = 'default';
  document.documentElement.dataset.theme = theme;
  $$('.theme-option').forEach(b => b.classList.toggle('selected', b.dataset.theme === theme));
  if (save) { try { localStorage.setItem('merajMusicTheme', theme); } catch (e) {} }
}
const themeToggle = $('#themeToggle'), themePopover = $('#themePopover');
if (themeToggle && themePopover) {
  themeToggle.addEventListener('click', e => { e.stopPropagation(); themePopover.hidden = !themePopover.hidden; });
  themePopover.addEventListener('click', e => {
    const b = e.target.closest('[data-theme]');
    if (!b) return;
    applyTheme(b.dataset.theme);
    themePopover.hidden = true;
  });
  document.addEventListener('click', e => { if (!themePopover.contains(e.target) && e.target !== themeToggle) themePopover.hidden = true; });
  let saved = 'default'; try { saved = localStorage.getItem('merajMusicTheme') || 'default'; } catch (e) {}
  applyTheme(saved, false);
}

// --- UI UTILITIES ---
function esc(s) { return String(s).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c])); }
function toast(t) { let x = $('#toast'); x.textContent = t; x.classList.add('show'); setTimeout(() => x.classList.remove('show'), 2600); }
function fmt(n) { if (!Number.isFinite(n)) return '0:00'; return Math.floor(n / 60) + ':' + String(Math.floor(n % 60)).padStart(2, '0'); }

// --- PAGE NAVIGATION ---
function page(id) {
  const oldPage = $$('.page.active')[0]?.id;
  if (oldPage === 'videoPlayer' && id !== 'videoPlayer') {
    const frame = document.getElementById('videoPlayerFrame');
    if (frame) frame.contentWindow?.postMessage({ type: 'meraj-video-parent-hide' }, '*');
  }
  if (!window.__pageBack) { if (historyPages[historyPages.length - 1] !== id) historyPages.push(id); }
  window.__pageBack = false;
  $$('.page').forEach(p => p.classList.toggle('active', p.id === id));
  document.body.classList.toggle('video-mode', id === 'videoPlayer');
  $$('.nav').forEach(b => b.classList.toggle('active', b.dataset.page === id));
  const mini = $('#mini'); if (mini) mini.classList.toggle('hidden', id === 'now' || current < 0);
  if (id === 'now') syncNow();
  if (id === 'folders') renderFolders();
  if (id === 'favorites') renderFavorites();
  if (id === 'albums') renderAlbums();
  if (id === 'artists') renderArtists();
  window.scrollTo(0, 0);
}
$$('[data-page]').forEach(b => b.addEventListener('click', () => page(b.dataset.page)));
$$('.back').forEach(b => b.addEventListener('click', () => goBack()));
function goBack() {
  if (historyPages.length > 1) historyPages.pop();
  const target = historyPages[historyPages.length - 1] || 'home';
  window.__pageBack = true;
  page(target);
}

// --- VIDEO IFRAME BRIDGE (This connects to video-src/app.js) ---
const videoFrameBridge = document.getElementById('videoPlayerFrame');
window.addEventListener('message', async (event) => {
  if (!videoFrameBridge || event.source !== videoFrameBridge.contentWindow) return;
  const message = event.data;
  
  if (message?.type === 'NAVIGATE_PAGE') {
     page(message.target || 'home');
     return;
  }
  if (!message || message.bridge !== 'meraj-video-native' || !message.requestId) return;
  
  const reply = { bridge: 'meraj-video-native-result', requestId: message.requestId };
  try {
    switch (message.action) {
      case 'permission': reply.result = await VideoPermissionBridge.requestIfNeeded(); break;
      case 'scan': reply.result = await VideoScannerBridge.scan({ type: message.payload?.type || 'video' }); break;
      case 'launchVideo': reply.result = await VideoScannerBridge.launchVideo(message.payload || {}); break;
      case 'getThumbnail': reply.result = await VideoScannerBridge.getThumbnail(message.payload || {}); break;
      case 'shareMedia': reply.result = await VideoScannerBridge.shareMedia(message.payload || {}); break;
      case 'orientationLock': await ScreenOrientation.lock({ orientation: message.payload?.orientation || 'landscape' }); reply.result = { locked: true }; break;
      case 'orientationUnlock': await ScreenOrientation.unlock(); reply.result = { locked: false }; break;
      case 'exitApp': await App.exitApp(); reply.result = { exited: true }; break;
      default: throw new Error('Unsupported action: ' + message.action);
    }
    reply.ok = true;
  } catch (error) {
    reply.ok = false;
    reply.error = String(error?.message || error || 'Native operation failed');
  }
  event.source.postMessage(reply, '*');
});

// --- AUDIO PLAYER LOGIC ---
audio.addEventListener('play', () => syncNow());
audio.addEventListener('pause', () => syncNow());
audio.addEventListener('ended', () => { if (repeatMode === 2) { audio.currentTime = 0; audio.play(); } else if (repeatMode === 1) step(1); });

function addFiles(files) {
  let added = 0;
  for (const f of files) {
    if (!f.type.startsWith('audio/') && !/\.(mp3|m4a|aac|wav|ogg|flac|opus)$/i.test(f.name)) continue;
    let path = f.webkitRelativePath || f.name;
    if (tracks.some(t => t.name === f.name && t.size === f.size && t.path === path)) continue;
    tracks.push({ name: f.name.replace(/\.[^.]+$/, ''), artist: 'Local audio', album: 'Unknown album', path, size: f.size, url: URL.createObjectURL(f), file: f, native: false });
    added++;
  }
  renderAll();
  toast(added ? `Added ${added} audio file(s).` : 'No new files found.');
}

// --- INITIALIZATION ---
window.addEventListener('load', () => {
  renderAll();
  if (Capacitor.isNativePlatform()) {
    scanAndroidLibrary();
  }
});

async function scanAndroidLibrary() {
  if (!Capacitor.isNativePlatform()) return false;
  nativeMode = true;
  try {
    const perm = await VideoPermissionBridge.requestIfNeeded();
    if (!perm || !perm.granted) { toast('Audio permission needed.'); return true; }
    const result = await VideoScannerBridge.scan({ type: 'audio' });
    const items = (result && result.items) || [];
    tracks = items.map((x) => ({ id: String(x.id), uri: x.uri, name: (x.name || 'Unknown').replace(/\.[^.]+$/, ''), artist: x.artist || 'Unknown', album: x.album || 'Unknown', path: x.relativePath || 'Music', size: Number(x.size || 0), duration: Number(x.duration || 0), native: true }));
    renderAll();
    if (tracks.length) toast(`Found ${tracks.length} songs.`);
  } catch (err) { console.error('Scan failed', err); }
  return true;
}

// --- RENDER FUNCTIONS ---
function renderAll() {
  let all = tracks.map((_, i) => i);
  fillList('#recent', all.slice(-5).reverse());
  fillList('#allSongs', all);
  fillList('#libraryRecent', all.slice(-6).reverse());
  $('#songCount').textContent = tracks.length + ' tracks';
  $('#countBadge').textContent = tracks.length + ' songs';
  $('#favCount').textContent = favs.size + ' saved';
  renderFavorites();
  bindRows();
}

function songRow(t, i) {
  const active = i === current;
  return `<div class="song ${active ? 'is-playing' : ''}" data-song-row="${i}">
    <div class="art ${colors[i % 4]}">♫</div>
    <button class="songinfo" data-play="${i}">
      <b>${esc(t.name)}${active ? ' <span class="playing-badge">PLAYING</span>' : ''}</b>
      <small>${esc(t.path)} · ${esc(t.artist)}</small>
    </button>
    <button class="more" data-fav="${i}">${favs.has(i) ? '♥' : '♡'}</button>
  </div>`;
}

function fillList(id, arr) {
  $(id).innerHTML = arr.length ? arr.map(i => songRow(tracks[i], i)).join('') : '<p class="muted">No music loaded.</p>';
}

function bindRows() {
  $$('[data-play]').forEach(b => b.onclick = () => playTrack(+b.dataset.play));
  $$('[data-fav]').forEach(b => b.onclick = () => { let i = +b.dataset.fav; favs.has(i) ? favs.delete(i) : favs.add(i); renderAll(); });
}

function playTrack(i) {
  if (i < 0 || i >= tracks.length) return;
  current = i;
  audio.src = tracks[i].uri || tracks[i].url;
  audio.play().catch(() => {});
  $('#mini').classList.remove('hidden');
  syncNow();
  page('now');
}

function syncNow() {
  if (current < 0) return;
  let t = tracks[current];
  $('#nowTitle').textContent = t.name;
  $('#nowArtist').textContent = t.path;
  $('#miniTitle').textContent = t.name;
  $('#play').textContent = audio.paused ? '▶' : 'Ⅱ';
  updatePlayingRows();
}

function updatePlayingRows() {
  $$('[data-song-row]').forEach(row => {
    const i = Number(row.dataset.songRow), active = i === current;
    row.classList.toggle('is-playing', active);
  });
}

$('#play').onclick = () => { if (current < 0) return; audio.paused ? audio.play() : audio.pause(); syncNow(); };
$('#next').onclick = () => step(1);
$('#prev').onclick = () => step(-1);
function step(d) {
  if (!tracks.length) return;
  let n = (Math.max(0, current) + d + tracks.length) % tracks.length;
  playTrack(n);
}

audio.addEventListener('timeupdate', () => {
  if (audio.duration) {
    $('#seek').value = audio.currentTime / audio.duration * 1000;
    $('#elapsed').textContent = fmt(audio.currentTime);
    $('#duration').textContent = fmt(audio.duration);
  }
});
$('#seek').oninput = () => { if (audio.duration) audio.currentTime = +$('#seek').value / 1000 * audio.duration; };

function renderFavorites() { fillList('#favoriteList', [...favs].filter(i => tracks[i])); }
function renderFolders() {
  let map = {};
  tracks.forEach((t, i) => { let f = t.path || 'Root'; (map[f] ??= []).push(i); });
  $('#folderList').innerHTML = Object.keys(map).length ? Object.entries(map).map(([f, ids], idx) => `<div class="song"><div class="art">▣</div><button class="songinfo"><b>${esc(f)}</b><small>${ids.length} songs</small></button></div>`).join('') : '<p class="muted">No folders loaded.</p>';
}
function renderAlbums() { $('#albumList').innerHTML = '<p class="muted">Albums will appear here.</p>'; }
function renderArtists() { $('#artistList').innerHTML = '<p class="muted">Artists will appear here.</p>'; }
