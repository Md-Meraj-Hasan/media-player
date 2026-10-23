const $ = id => document.getElementById(id);
const video = $('video'), app = $('app');
let allVideos = [], currentVideos = [], selected = -1;
let hideTimer = null, shown = true, locked = false;
let fit = 0, isLandscape = false, currentFilterFolder = '__all__';

const ui = [$('toolbar'), $('center'), $('seekArea'), $('bottom')];

function fmt(s) {
  if (!Number.isFinite(s) || s < 0) s = 0;
  let m = Math.floor(s / 60), sec = Math.floor(s % 60);
  return `${String(m).padStart(2, '0')}:${String(sec).padStart(2, '0')}`;
}

// Controls visibility
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

// Automatic Thumbnail Generator using HTML5 Canvas
function generateThumbnail(videoItem, callback) {
  if (videoItem.thumbUrl) {
    callback(videoItem.thumbUrl);
    return;
  }
  const tempVideo = document.createElement('video');
  tempVideo.src = videoItem.url;
  tempVideo.muted = true;
  tempVideo.currentTime = 1.5;
  tempVideo.addEventListener('seeked', () => {
    try {
      const canvas = document.createElement('canvas');
      canvas.width = 160;
      canvas.height = 90;
      const ctx = canvas.getContext('2d');
      ctx.drawImage(tempVideo, 0, 0, canvas.width, canvas.height);
      const dataUrl = canvas.toDataURL('image/jpeg', 0.6);
      videoItem.thumbUrl = dataUrl;
      callback(dataUrl);
    } catch (_) {
      callback('');
    }
  }, { once: true });
}

// Render Videos in Library Grid
function renderLibraryGrid() {
  const grid = $('videoGrid');
  grid.innerHTML = '';

  const list = currentFilterFolder === '__all__'
    ? allVideos
    : allVideos.filter(v => v.folder === currentFilterFolder);

  if (!list.length) {
    grid.innerHTML = '<div class="empty-state">No videos found. Tap <b>⟳ Scan</b> to choose videos.</div>';
    return;
  }

  list.forEach((item, index) => {
    const card = document.createElement('div');
    card.className = 'video-card';
    card.innerHTML = `
      <div class="video-thumb-wrap">
        <img class="video-thumb-canvas" id="grid-thumb-${index}" alt="" />
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

    generateThumbnail(item, (url) => {
      const img = document.getElementById(`grid-thumb-${index}`);
      if (img && url) img.src = url;
    });
  });
}

// Render Folder Filter Chips
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

// Render Horizontal Scrollable Thumbnails in Player
function renderPlayerThumbs() {
  const container = $('thumbs');
  container.innerHTML = '';

  currentVideos.forEach((it, i) => {
    const card = document.createElement('div');
    card.className = `thumb-card ${i === selected ? 'selected' : ''}`;
    card.innerHTML = `
      <img class="thumb-img" id="player-thumb-${i}" alt="" />
      <div class="thumb-title">${it.name}</div>
    `;
    card.onclick = () => playVideo(i);
    container.appendChild(card);

    if (i === selected) {
      card.scrollIntoView({ behavior: 'smooth', inline: 'center', block: 'nearest' });
    }

    generateThumbnail(it, (url) => {
      const img = document.getElementById(`player-thumb-${i}`);
      if (img && url) img.src = url;
    });
  });
}

// Play Selected Video
function playVideo(index) {
  if (index < 0 || index >= currentVideos.length) return;
  selected = index;
  const item = currentVideos[index];

  $('libraryView').classList.add('hidden');
  $('playerView').classList.remove('hidden');

  video.src = item.url;
  $('title').textContent = item.name;
  video.load();
  video.play().catch(() => {});
  $('playBtn').textContent = 'Ⅱ';

  renderPlayerThumbs();
  controls(true);
}

// Library Navigation
$('libBackBtn').onclick = () => {
  if (window.parent && window.parent !== window) {
    window.parent.postMessage({ type: 'NAVIGATE_PAGE', target: 'home' }, '*');
  }
};

$('collapseBtn').onclick = () => {
  video.pause();
  $('playerView').classList.add('hidden');
  $('libraryView').classList.remove('hidden');
};

// Tabs: Videos vs Folders
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

// Scan Videos Button
const scanBtn = $('libScanBtn');
const picker = $('videoPicker');
if (scanBtn && picker) {
  scanBtn.onclick = () => picker.click();
  picker.onchange = (e) => {
    const files = Array.from(e.target.files);
    if (!files.length) return;

    allVideos = files.map(f => {
      const path = f.webkitRelativePath || f.name;
      const folder = path.includes('/') ? path.split('/')[0] : 'Internal Storage';
      return {
        name: f.name,
        url: URL.createObjectURL(f),
        file: f,
        folder
      };
    });

    currentVideos = allVideos;
    renderFolderChips();
    renderLibraryGrid();
  };
}

// Share Video
$('shareBtn').onclick = async () => {
  if (selected < 0 || !currentVideos[selected]) return;
  const cur = currentVideos[selected];
  if (navigator.share && cur.file) {
    try {
      await navigator.share({ files: [cur.file], title: cur.name });
    } catch (_) {}
  }
};

// Orientation
$('rotateBtn').onclick = () => {
  isLandscape = !isLandscape;
  if (window.parent && window.parent !== window) {
    window.parent.postMessage({
      type: 'TOGGLE_ORIENTATION',
      orientation: isLandscape ? 'landscape' : 'portrait'
    }, '*');
  }
};

// Lock System
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

// Playback Controls
$('playBtn').onclick = () => (video.paused ? video.play() : video.pause());
video.addEventListener('play', () => { $('playBtn').textContent = 'Ⅱ'; armHide(); });
video.addEventListener('pause', () => { $('playBtn').textContent = '▶'; clearTimeout(hideTimer); controls(true); });

$('backBtn').onclick = () => { video.currentTime = Math.max(0, video.currentTime - 10); controls(true); };
$('forwardBtn').onclick = () => { video.currentTime = Math.min(video.duration || Infinity, video.currentTime + 10); controls(true); };

video.addEventListener('timeupdate', () => {
  $('current').textContent = fmt(video.currentTime);
  $('seek').value = video.duration ? (video.currentTime / video.duration) * 1000 : 0;
});
video.addEventListener('loadedmetadata', () => {
  $('duration').textContent = fmt(video.duration);
});
$('seek').oninput = e => {
  if (video.duration) video.currentTime = (video.duration * e.target.value) / 1000;
  controls(true);
};

// Fit, Speed, Volume
$('fitBtn').onclick = () => {
  fit = (fit + 1) % 3;
  video.style.objectFit = ['contain', 'cover', 'fill'][fit];
  $('fitBtn').textContent = ['⛶', '▣', '↔'][fit];
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
$('repeatBtn').onclick = () => {
  video.currentTime = 0;
  video.play();
  controls(true);
};

$('stage').addEventListener('click', e => {
  if (e.target.closest('button, input')) return;
  if (!locked) controls(!shown);
});
