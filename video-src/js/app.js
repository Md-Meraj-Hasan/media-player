const $ = id => document.getElementById(id);
let allVideos = [], currentVideos = [], currentFilterFolder = '__all__';

// Native Bridge Communication
function callNative(action, payload = {}) {
  return new Promise((resolve, reject) => {
    if (!window.parent || window.parent === window) return reject(new Error("Not in app"));
    const requestId = Math.random().toString(36).substring(7);
    const listener = (event) => {
      const msg = event.data;
      if (msg && msg.bridge === 'meraj-video-native-result' && msg.requestId === requestId) {
        window.removeEventListener('message', listener);
        if (msg.ok) resolve(msg.result);
        else reject(new Error(msg.error || "Native error"));
      }
    };
    window.addEventListener('message', listener);
    window.parent.postMessage({ bridge: 'meraj-video-native', action, payload, requestId }, '*');
  });
}

async function scanNativeVideos() {
  $('videoGrid').innerHTML = '<div class="empty-state">Scanning device...</div>';
  try {
    const perm = await callNative('permission');
    if (!perm || !perm.granted) {
      $('videoGrid').innerHTML = '<div class="empty-state">Permission denied. Please allow video access.</div>';
      return;
    }
    const result = await callNative('scan', { type: 'video' });
    if (result && result.items && result.items.length > 0) {
      allVideos = result.items.map(item => ({
        name: item.name || 'Unknown Video',
        url: item.uri,
        folder: item.relativePath || 'Internal Storage',
        native: true
      }));
      currentVideos = allVideos;
      renderFolderChips();
      renderLibraryGrid();
    } else {
      $('videoGrid').innerHTML = '<div class="empty-state">No videos found on device.</div>';
    }
  } catch (e) {
    $('videoGrid').innerHTML = '<div class="empty-state">Scan failed: ' + e.message + '</div>';
  }
}

function renderLibraryGrid() {
  const grid = $('videoGrid');
  grid.innerHTML = '';
  const list = currentFilterFolder === '__all__' ? allVideos : allVideos.filter(v => v.folder === currentFilterFolder);

  if (!list.length) {
    grid.innerHTML = '<div class="empty-state">No videos here.</div>';
    return;
  }

  list.forEach((item, index) => {
    const card = document.createElement('div');
    card.className = 'video-card';
    card.innerHTML = `
      <div class="video-thumb-wrap">
        <img class="video-thumb-img" src="" alt="">
      </div>
      <div class="card-info">
        <div class="card-title">${item.name}</div>
        <div class="card-folder">${item.folder}</div>
      </div>
    `;
    callNative('getThumbnail', { uri: item.url }).then(res => {
      if (res && res.thumbnail) card.querySelector('.video-thumb-img').src = res.thumbnail;
    }).catch(() => {});
    
    card.onclick = () => {
      currentVideos = list;
      playVideo(index);
    };
    grid.appendChild(card);
  });
}

function renderFolderChips() {
  const chipBar = $('folderChips');
  chipBar.innerHTML = '';
  const folders = Array.from(new Set(allVideos.map(v => v.folder)));
  const allChip = document.createElement('div');
  allChip.className = `folder-chip ${currentFilterFolder === '__all__' ? 'active' : ''}`;
  allChip.textContent = 'All Videos';
  allChip.onclick = () => { currentFilterFolder = '__all__'; renderFolderChips(); renderLibraryGrid(); };
  chipBar.appendChild(allChip);
  folders.forEach(f => {
    const chip = document.createElement('div');
    chip.className = `folder-chip ${currentFilterFolder === f ? 'active' : ''}`;
    chip.textContent = f;
    chip.onclick = () => { currentFilterFolder = f; renderFolderChips(); renderLibraryGrid(); };
    chipBar.appendChild(chip);
  });
}

async function playVideo(index) {
  if (index < 0 || index >= currentVideos.length) return;
  const item = currentVideos[index];
  try {
    await callNative('launchVideo', { uri: item.url, name: item.name });
  } catch (e) {
    alert("Could not play video: " + e.message);
  }
}

$('libBackBtn').onclick = () => window.parent.postMessage({ type: 'NAVIGATE_PAGE', target: 'home' }, '*');
$('libScanBtn').onclick = () => scanNativeVideos();
$('tabVideos').onclick = () => { $('tabVideos').classList.add('active'); $('tabFolders').classList.remove('active'); $('folderChips').hidden = true; currentFilterFolder = '__all__'; renderLibraryGrid(); };
$('tabFolders').onclick = () => { $('tabFolders').classList.add('active'); $('tabVideos').classList.remove('active'); $('folderChips').hidden = false; renderFolderChips(); renderLibraryGrid(); };
window.addEventListener('load', () => { if (window.parent !== window) setTimeout(() => scanNativeVideos(), 800); });
