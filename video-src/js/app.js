const $ = id => document.getElementById(id);
const video = $('video'), app = $('app');
let allVideos = [], currentVideos = [], selected = -1;
let hideTimer = null, shown = true, locked = false;
let fit = 0, isLandscape = false, currentFilterFolder = '__all__';

const ui = [$('toolbar'), $('center'), $('seekArea'), $('bottom')];

// Android Native Bridge Communication
function callNative(action, payload = {}) {
  return new Promise((resolve, reject) => {
    if (!window.parent || window.parent === window) return reject(new Error("Not in app"));
    const requestId = Date.now().toString();
    
    const listener = (event) => {
      const msg = event.data;
      if (msg && msg.bridge === 'meraj-video-native-result' && msg.requestId === requestId) {
        window.removeEventListener('message', listener);
        if (msg.ok) resolve(msg.result);
        else reject(new Error(msg.error));
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

// Auto Scan Native Videos
async function scanNativeVideos() {
  try {
    await callNative('permission');
    const result = await callNative('scan', { type: 'video' });
    if (result && result.items) {
      allVideos = result.items.map(item => ({
        name: item.name,
        url: item.uri,
        folder: item.relativePath || 'Internal Storage',
        native: true
      }));
      currentVideos = allVideos;
      renderFolderChips();
      renderLibraryGrid();
    }
  } catch (e) {
    alert("Scan failed: " + e.message);
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
        <div style="width:100%;height:100%;display:grid;place-items:center;background:#222;color:#888">▶</div>
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

function renderPlayerThumbs() {
  const container = $('thumbs');
  container.innerHTML = '';
  currentVideos.forEach((it, i) => {
    const card = document.createElement('div');
    card.className = `thumb-card ${i === selected ? 'selected' : ''}`;
    card.innerHTML = `<div class="thumb-title">${it.name}</div>`;
    card.onclick = () => playVideo(i);
    container.appendChild(card);
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
    // Launch native Android player
    try {
      await callNative('launchVideo', { 
        uri: item.url, 
        name: item.name,
        queue: currentVideos.map(v => ({uri: v.url, name: v.name, mimeType: 'video/*'}))
      });
      $('playerView').classList.add('hidden');
      $('libraryView').classList.remove('hidden');
    } catch (e) {
      alert("Could not play native video: " + e.message);
    }
  } else {
    // Fallback for web
    video.src = item.url;
    video.load();
    video.play().catch(() => {});
    controls(true);
  }
  renderPlayerThumbs();
}

// Share Video Button Fix
$('shareBtn').onclick = async () => {
  if (selected < 0) return;
  const cur = currentVideos[selected];
  try {
    await callNative('shareMedia', { uri: cur.url, name: cur.name, mimeType: 'video/*' });
  } catch (e) {
    alert("Share failed: " + e.message);
  }
};

// Rotation Fix
$('rotateBtn').onclick = async () => {
  isLandscape = !isLandscape;
  try {
    if (isLandscape) await callNative('orientationLock', { orientation: 'landscape' });
    else await callNative('orientationUnlock');
    $('rotateBtn').style.color = isLandscape ? 'var(--accent)' : '#fff';
  } catch (e) { alert("Rotate failed"); }
};

// Lock System Fix
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

// Volume & Brightness Swipe (Overlay)
let touchStartY = 0, isSwiping = false, swipeType = '';
const stage = $('stage');

stage.addEventListener('touchstart', (e) => {
  if (locked) return;
  touchStartY = e.touches[0].clientY;
  isSwiping = true;
  const screenHalf = window.innerHeight / 2;
  swipeType = e.touches[0].clientX < window.innerWidth / 2 ? 'brightness' : 'volume';
});

stage.addEventListener('touchmove', (e) => {
  if (!isSwiping || locked) return;
  const deltaY = touchStartY - e.touches[0].clientY;
  const percent = Math.min(100, Math.max(0, (deltaY / 300) * 100));
  
  const line = $('levelLine');
  line.classList.add('show');
  line.style.height = percent + '%';
  line.style.top = (50 - percent/2) + '%';
  
  // Native call to adjust brightness/volume
  if (swipeType === 'volume' && video.native !== true) {
    video.volume = Math.min(1, Math.max(0, percent / 100));
  }
}, { passive: true });

stage.addEventListener('touchend', () => {
  isSwiping = false;
  $('levelLine').classList.remove('show');
});

// UI Controls
$('libBackBtn').onclick = () => window.parent.postMessage({ type: 'NAVIGATE_PAGE', target: 'home' }, '*');
$('collapseBtn').onclick = () => {
  video.pause();
  $('playerView').classList.add('hidden');
  $('libraryView').classList.remove('hidden');
};

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

// Scan Button Override (Native)
$('libScanBtn').onclick = () => scanNativeVideos();

// Web Video Fallback controls
$('playBtn').onclick = () => (video.paused ? video.play() : video.pause());
video.addEventListener('play', () => { $('playBtn').textContent = 'Ⅱ'; armHide(); });
video.addEventListener('pause', () => { $('playBtn').textContent = '▶'; clearTimeout(hideTimer); controls(true); });
$('backBtn').onclick = () => { video.currentTime -= 10; controls(true); };
$('forwardBtn').onclick = () => { video.currentTime += 10; controls(true); };
video.addEventListener('timeupdate', () => {
  $('current').textContent = fmt(video.currentTime);
  $('seek').value = video.duration ? (video.currentTime / video.duration) * 1000 : 0;
});
video.addEventListener('loadedmetadata', () => { $('duration').textContent = fmt(video.duration); });
$('seek').oninput = e => { if (video.duration) video.currentTime = (video.duration * e.target.value) / 1000; };

$('fitBtn').onclick = () => {
  fit = (fit + 1) % 3;
  video.style.objectFit = ['contain', 'cover', 'fill'][fit];
  controls(true);
};
$('speedBtn').onclick = () => { $('speedMenu').hidden = !$('speedMenu').hidden; controls(true); };
$('speedMenu').querySelectorAll('button').forEach(b => {
  b.onclick = () => {
    video.playbackRate = Number(b.dataset.speed);
    $('speedBtn').textContent = b.dataset.speed + '× ▾';
    $('speedMenu').hidden = true;
    controls(true);
  };
});
$('volBtn').onclick = () => {
  video.muted = !video.muted;
  $('volBtn').textContent = video.muted ? '🔇' : '🔊';
  controls(true);
};
$('repeatBtn').onclick = () => { video.currentTime = 0; video.play(); controls(true); };

$('stage').addEventListener('click', e => {
  if (e.target.closest('button, input')) return;
  if (!locked) controls(!shown);
});

// Init Scan on Load
window.addEventListener('load', () => {
  if (window.parent !== window) {
    scanNativeVideos();
  }
});
