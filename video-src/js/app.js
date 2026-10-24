const $ = id => document.getElementById(id);
const video = $('video'), app = $('app');
let allVideos = [], currentVideos = [], selected = -1;
let hideTimer = null, shown = true, locked = false;
let fit = 0, isLandscape = false, currentFilterFolder = '__all__';

const ui = [$('toolbar'), $('center'), $('seekArea'), $('bottom')];

// 1. ANDROID NATIVE BRIDGE (This connects HTML to Android)
function callNative(action, payload = {}) {
  return new Promise((resolve, reject) => {
    if (!window.parent || window.parent === window) {
      return reject(new Error("Parent window not found. Are you inside the app?"));
    }
    
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

function fmt(s) {
  if (!Number.isFinite(s) || s < 0) s = 0;
  let m = Math.floor(s / 60), sec = Math.floor(s % 60);
  return `${String(m).padStart(2, '0')}:${String(sec).padStart(2, '0')}`;
}

function controls(show) {
  if (locked) return;
  shown = show;
  ui.forEach(el => el && el.classList.toggle('hidden-ui', !show));
  if ($('hint')) $('hint').style.opacity = show ? '1' : '0';
  if (show) armHide();
  else clearTimeout(hideTimer);
}

function armHide() {
  clearTimeout(hideTimer);
  if (!locked && !video.paused) hideTimer = setTimeout(() => controls(false), 4000);
}

// 2. SCAN VIDEOS (Auto load from phone storage)
async function scanNativeVideos() {
  $('videoGrid').innerHTML = '<div class="empty-state">Scanning device... Please wait.</div>';
  try {
    const perm = await callNative('permission');
    if (!perm || !perm.granted) {
      $('videoGrid').innerHTML = '<div class="empty-state">Permission denied. Please allow video access in settings.</div>';
      return;
    }
    
    const result = await callNative('scan', { type: 'video' });
    
    if (result && result.items && result.items.length > 0) {
      allVideos = result.items.map(item => ({
        name: item.name,
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
    console.error("Scan failed", e);
    $('videoGrid').innerHTML = '<div class="empty-state">Scan failed: ' + e.message + '</div>';
  }
}

function renderLibraryGrid() {
  const grid = $('videoGrid');
  grid.innerHTML = '';
  const list = currentFilterFolder === '__all__' ? allVideos : allVideos.filter(v => v.folder === currentFilterFolder);

  if (!list.length) {
    grid.innerHTML = '<div class="empty-state">No videos found. Tap <b>⟳ Scan</b>.</div>';
    return;
  }

  list.forEach((item, index) => {
    const card = document.createElement('div');
    card.className = 'video-card';
    card.innerHTML = `
      <div class="video-thumb-wrap">
        <div style="width:100%;height:100%;display:grid;place-items:center;background:#222;color:#888;font-size:24px">▶</div>
      </div>
      <div class="card-info">
        <div class="card-title">${item.name}</div>
        <div class="card-folder">${item.folder}</div>
      </div>
    `;
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
  allChip.onclick = () => {
    currentFilterFolder = '__all__';
    renderFolderChips();
    renderLibraryGrid();
  };
  chipBar.appendChild(allChip);

  folders.forEach(f => {
    const chip = document.createElement('div');
    chip.className = `folder-chip ${currentFilterFolder === f ? 'active' : ''}`;
    chip.textContent = f;
    chip.onclick = () => {
      currentFilterFolder = f;
      renderFolderChips();
      renderLibraryGrid();
    };
    chipBar.appendChild(chip);
  });
}

async function playVideo(index) {
  if (index < 0 || index >= currentVideos.length) return;
  selected = index;
  const item = currentVideos[index];

  $('libraryView').classList.add('hidden');
  $('playerView').classList.remove('hidden');
  $('title').textContent = item.name;
  
  if (item.native) {
    try {
      await callNative('launchVideo', { 
        uri: item.url, 
        name: item.name,
        queue: currentVideos.map(v => ({uri: v.url, name: v.name, mimeType: 'video/*'}))
      });
      // Native player opens, hide web UI
      $('playerView').classList.add('hidden');
      $('libraryView').classList.remove('hidden');
    } catch (e) {
      alert("Could not play video: " + e.message);
    }
  }
}

// 3. BUTTONS FIX
$('libBackBtn').onclick = () => window.parent.postMessage({ type: 'NAVIGATE_PAGE', target: 'home' }, '*');

$('libScanBtn').onclick = () => scanNativeVideos();

$('tabVideos').onclick = () => {
  $('tabVideos').classList.add('active');
  $('tabFolders').classList.remove('active');
  $('folderChips').hidden = true;
  currentFilterFolder = '__all__';
  renderLibraryGrid();
};

$('tabFolders').onclick = () => {
  $('tabFolders').classList.add('active');
  $('tabVideos').classList.remove('active');
  $('folderChips').hidden = false;
  renderFolderChips();
  renderLibraryGrid();
};

$('shareBtn').onclick = async () => {
  if (selected < 0) return;
  const cur = currentVideos[selected];
  try {
    await callNative('shareMedia', { uri: cur.url, name: cur.name, mimeType: 'video/*' });
  } catch (e) { alert("Share failed: " + e.message); }
};

$('rotateBtn').onclick = async () => {
  isLandscape = !isLandscape;
  try {
    if (isLandscape) await callNative('orientationLock', { orientation: 'landscape' });
    else await callNative('orientationUnlock');
  } catch (e) { alert("Rotate failed"); }
};

$('lockBtn').onclick = () => {
  locked = true;
  app.classList.add('locked');
  $('unlockTap').hidden = false;
  clearTimeout(hideTimer);
};

function unlockControls() {
  locked = false;
  app.classList.remove('locked');
  $('unlockTap').hidden = true;
  controls(true);
}
$('unlockTap').onclick = unlockControls;

// Auto Scan on Load
window.addEventListener('load', () => {
  if (window.parent !== window) {
    setTimeout(() => scanNativeVideos(), 1000);
  }
});
