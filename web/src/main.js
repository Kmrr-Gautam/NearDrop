/**
 * NearDrop Web · Exact Claymorphism Application Logic
 * Replicates the complete Android App Claymorphism UI:
 * - Home, Transfers, Settings screens
 * - ClayBottomNavigation
 * - Web-to-APK (PC to Mobile)
 * - APK-to-Web (Mobile to PC)
 * - Web-to-Web (PC to PC / Browser P2P)
 */

import { WebRTCBridge } from './webrtc.js';
import { LanBridge } from './lanBridge.js';
import { FlyingAnimationEngine } from './flyingAnimation.js';
import { QrHelper } from './qrHelper.js';

class NearDropClayApp {
  constructor() {
    this.deviceName = localStorage.getItem('neardrop_device_name') || 'Windows PC';
    this.stagedFiles = [];
    this.history = JSON.parse(localStorage.getItem('neardrop_web_history') || '[]');
    this.stopScannerFn = null;
    this.activeTransfer = null;

    this.initElements();
    this.initTheme();
    this.initEngines();
    this.bindEvents();
    this.renderHistory();
    this.renderTransfersFull();
  }

  initElements() {
    // Header & Names
    this.deviceNameHeader = document.getElementById('deviceNameHeader');
    this.settingsDeviceName = document.getElementById('settingsDeviceName');
    this.localNodeName = document.getElementById('localNodeName');
    this.receiveSubText = document.getElementById('receiveSubText');

    this.setDeviceName(this.deviceName);

    // Theme & Navigation
    this.themeToggleBtn = document.getElementById('themeToggleBtn');
    this.openSettingsBtn = document.getElementById('openSettingsBtn');
    this.navTabHome = document.getElementById('navTabHome');
    this.navTabTransfers = document.getElementById('navTabTransfers');
    this.navTabSettings = document.getElementById('navTabSettings');

    // Screens
    this.screenHome = document.getElementById('screenHome');
    this.screenTransfers = document.getElementById('screenTransfers');
    this.screenSettings = document.getElementById('screenSettings');

    // Chips
    this.chipReceiving = document.getElementById('chipReceiving');
    this.chipConnection = document.getElementById('chipConnection');
    this.roomCodePill = document.getElementById('roomCodePill');

    // Receiving Toggle
    this.receivingToggle = document.getElementById('receivingToggle');

    // Buttons on Home
    this.homeSendBtn = document.getElementById('homeSendBtn');
    this.homeScanQrBtn = document.getElementById('homeScanQrBtn');
    this.homeReceiveQrBtn = document.getElementById('homeReceiveQrBtn');
    this.homePcConnectBtn = document.getElementById('homePcConnectBtn');

    // Radar & Nodes
    this.flightCanvas = document.getElementById('flightCanvas');
    this.localNode = document.getElementById('localNode');
    this.targetNode = document.getElementById('targetNode');
    this.targetNodeName = document.getElementById('targetNodeName');
    this.targetNodeStatus = document.getElementById('targetNodeStatus');
    this.targetAvatar = document.getElementById('targetAvatar');

    // Dropzone & Files
    this.dropzone = document.getElementById('dropzone');
    this.fileInput = document.getElementById('fileInput');
    this.browseFilesBtn = document.getElementById('browseFilesBtn');

    // Staged Queue
    this.stagedCard = document.getElementById('stagedCard');
    this.stagedCount = document.getElementById('stagedCount');
    this.stagedTotalSize = document.getElementById('stagedTotalSize');
    this.stagedList = document.getElementById('stagedList');
    this.clearStagedBtn = document.getElementById('clearStagedBtn');
    this.sendStagedBtn = document.getElementById('sendStagedBtn');

    // Transfer HUD
    this.transferHud = document.getElementById('transferHud');
    this.hudTitle = document.getElementById('hudTitle');
    this.cancelTransferBtn = document.getElementById('cancelTransferBtn');
    this.hudProgressFill = document.getElementById('hudProgressFill');
    this.hudSpeed = document.getElementById('hudSpeed');
    this.hudEta = document.getElementById('hudEta');
    this.hudPercent = document.getElementById('hudPercent');

    // History
    this.historyList = document.getElementById('historyList');
    this.emptyHistory = document.getElementById('emptyHistory');
    this.clearHistoryBtn = document.getElementById('clearHistoryBtn');
    this.transfersFullList = document.getElementById('transfersFullList');

    // Modals
    this.qrModal = document.getElementById('qrModal');
    this.closeQrModalBtn = document.getElementById('closeQrModalBtn');
    this.qrModalDoneBtn = document.getElementById('qrModalDoneBtn');
    this.qrDisplayCanvas = document.getElementById('qrDisplayCanvas');
    this.qrModalRoomCode = document.getElementById('qrModalRoomCode');

    this.scannerModal = document.getElementById('scannerModal');
    this.closeScannerModalBtn = document.getElementById('closeScannerModalBtn');
    this.scannerCancelBtn = document.getElementById('scannerCancelBtn');
    this.scannerVideo = document.getElementById('scannerVideo');
    this.scannerCanvas = document.getElementById('scannerCanvas');
    this.scannerStatus = document.getElementById('scannerStatus');

    this.pcConnectModal = document.getElementById('pcConnectModal');
    this.closePcConnectModalBtn = document.getElementById('closePcConnectModalBtn');
    this.pcConnectIpInput = document.getElementById('pcConnectIpInput');
    this.pcConnectPortInput = document.getElementById('pcConnectPortInput');
    this.pcConnectSubmitBtn = document.getElementById('pcConnectSubmitBtn');

    this.renameModal = document.getElementById('renameModal');
    this.renameCard = document.getElementById('renameCard');
    this.newDeviceNameInput = document.getElementById('newDeviceNameInput');
    this.cancelRenameBtn = document.getElementById('cancelRenameBtn');
    this.saveRenameBtn = document.getElementById('saveRenameBtn');

    this.devProfileCard = document.getElementById('devProfileCard');

    // Restore saved LAN IP
    const savedIp = localStorage.getItem('neardrop_apk_ip');
    if (savedIp) this.pcConnectIpInput.value = savedIp;
  }

  setDeviceName(name) {
    this.deviceName = name;
    this.deviceNameHeader.textContent = name;
    this.settingsDeviceName.textContent = name;
    this.localNodeName.textContent = name;
    this.receiveSubText.textContent = `Ready! Other devices can see you as “${name}”`;
    localStorage.setItem('neardrop_device_name', name);
  }

  initTheme() {
    const saved = localStorage.getItem('neardrop_theme') || 'dark';
    this.applyTheme(saved);

    this.themeToggleBtn.addEventListener('click', () => {
      const current = document.documentElement.getAttribute('data-theme');
      const next = current === 'dark' ? 'light' : 'dark';
      this.applyTheme(next);
    });

    // Theme Segmented Control
    const themeTabs = document.querySelectorAll('#themeSegmented .segmented-tab');
    themeTabs.forEach((tab) => {
      tab.addEventListener('click', () => {
        themeTabs.forEach(t => t.classList.remove('active-tab'));
        tab.classList.add('active-tab');
        const val = tab.getAttribute('data-theme-val');
        if (val === 'system') {
          const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
          this.applyTheme(prefersDark ? 'dark' : 'light');
        } else {
          this.applyTheme(val);
        }
      });
    });
  }

  applyTheme(theme) {
    document.documentElement.setAttribute('data-theme', theme);
    localStorage.setItem('neardrop_theme', theme);
  }

  initEngines() {
    // 1. Canvas Flight Animation
    this.flightEngine = new FlyingAnimationEngine(this.flightCanvas);

    // 2. WebRTC P2P
    this.currentRoom = WebRTCBridge.generateRoomCode();
    this.roomCodePill.textContent = `Room: ${this.currentRoom}`;

    this.webrtc = new WebRTCBridge({
      deviceName: this.deviceName,
      onStatusChange: (status) => {
        this.chipConnection.textContent = status;
      },
      onPeerConnected: (peerName) => {
        this.targetNodeName.textContent = peerName;
        this.targetNodeStatus.textContent = 'WebRTC Peer Connected';
        this.chipConnection.textContent = `Connected: ${peerName}`;
        this.targetAvatar.className = 'clay-avatar avatar-mint';
      },
      onPeerDisconnected: () => {
        this.targetNodeName.textContent = 'Android APK';
        this.targetNodeStatus.textContent = 'Waiting for connection';
        this.chipConnection.textContent = 'Disconnected';
      },
      onProgress: (p) => {
        this.renderTransferProgress(p);
      },
      onFileReceived: (fileInfo) => {
        this.handleReceivedFile(fileInfo, 'WebRTC');
      }
    });

    // Auto-connect room
    this.webrtc.connectRoom(this.currentRoom, this.deviceName);

    // 3. LAN Bridge Engine
    this.lanBridge = new LanBridge({
      onStatusChange: (status) => {
        this.chipConnection.textContent = status;
      },
      onConnected: (device) => {
        this.targetNodeName.textContent = device.name;
        this.targetNodeStatus.textContent = `${device.ip}:${device.port}`;
        this.chipConnection.textContent = `Connected: ${device.name}`;
        this.targetAvatar.className = 'clay-avatar avatar-mint';
      },
      onDisconnected: () => {
        this.targetNodeName.textContent = 'Android APK';
        this.targetNodeStatus.textContent = 'Waiting for connection';
        this.chipConnection.textContent = 'Disconnected';
      },
      onProgress: (p) => {
        this.renderTransferProgress(p);
      },
      onIncomingFromApk: (file) => {
        this.handleReceivedFile({
          name: file.name,
          size: file.size,
          url: file.downloadUrl,
          timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
        }, 'Android APK');
      }
    });
  }

  bindEvents() {
    // Bottom Navigation Bar
    const tabs = [
      { btn: this.navTabHome, screen: this.screenHome },
      { btn: this.navTabTransfers, screen: this.screenTransfers },
      { btn: this.navTabSettings, screen: this.screenSettings }
    ];

    tabs.forEach(({ btn, screen }) => {
      btn.addEventListener('click', () => {
        tabs.forEach(t => {
          t.btn.classList.remove('active-nav');
          t.screen.classList.remove('active-screen');
        });
        btn.classList.add('active-nav');
        screen.classList.add('active-screen');
      });
    });

    this.openSettingsBtn.addEventListener('click', () => {
      this.navTabSettings.click();
    });

    // Receiving Switch
    this.receivingToggle.addEventListener('change', (e) => {
      if (e.target.checked) {
        this.chipReceiving.textContent = '✓ Ready to receive';
        this.chipReceiving.className = 'clay-chip chip-mint';
        this.receiveSubText.textContent = `Ready! Other devices can see you as “${this.deviceName}”`;
      } else {
        this.chipReceiving.textContent = 'Receiving is off';
        this.chipReceiving.className = 'clay-chip chip-peach';
        this.receiveSubText.textContent = 'Turn on so other devices can send you files';
      }
    });

    // Home Action Buttons
    this.homeSendBtn.addEventListener('click', () => {
      this.fileInput.click();
    });

    this.homeScanQrBtn.addEventListener('click', () => {
      this.openQrScanner();
    });

    this.homeReceiveQrBtn.addEventListener('click', () => {
      this.openReceiveQrModal();
    });

    this.homePcConnectBtn.addEventListener('click', () => {
      this.pcConnectModal.style.display = 'flex';
    });

    // Dropzone & File Browse
    this.browseFilesBtn.addEventListener('click', (e) => {
      e.stopPropagation();
      this.fileInput.click();
    });

    this.dropzone.addEventListener('click', () => this.fileInput.click());

    this.fileInput.addEventListener('change', (e) => {
      if (e.target.files && e.target.files.length > 0) {
        this.stageFiles(Array.from(e.target.files));
        this.fileInput.value = '';
      }
    });

    ['dragenter', 'dragover'].forEach((eventName) => {
      this.dropzone.addEventListener(eventName, (e) => {
        e.preventDefault();
        e.stopPropagation();
        this.dropzone.classList.add('drag-active');
      });
    });

    ['dragleave', 'drop'].forEach((eventName) => {
      this.dropzone.addEventListener(eventName, (e) => {
        e.preventDefault();
        e.stopPropagation();
        this.dropzone.classList.remove('drag-active');
      });
    });

    this.dropzone.addEventListener('drop', (e) => {
      if (e.dataTransfer && e.dataTransfer.files && e.dataTransfer.files.length > 0) {
        this.stageFiles(Array.from(e.dataTransfer.files));
      }
    });

    // Staging Actions
    this.clearStagedBtn.addEventListener('click', () => this.clearStaged());
    this.sendStagedBtn.addEventListener('click', () => this.startTransfer());

    // Cancel Transfer
    this.cancelTransferBtn.addEventListener('click', () => {
      this.transferHud.style.display = 'none';
    });

    // Clear History
    this.clearHistoryBtn.addEventListener('click', () => this.clearHistory());

    // Modals
    this.closeQrModalBtn.addEventListener('click', () => this.qrModal.style.display = 'none');
    this.qrModalDoneBtn.addEventListener('click', () => this.qrModal.style.display = 'none');

    this.closeScannerModalBtn.addEventListener('click', () => this.closeScannerModal());
    this.scannerCancelBtn.addEventListener('click', () => this.closeScannerModal());

    this.closePcConnectModalBtn.addEventListener('click', () => this.pcConnectModal.style.display = 'none');
    this.pcConnectSubmitBtn.addEventListener('click', () => {
      const ip = this.pcConnectIpInput.value.trim();
      const port = this.pcConnectPortInput.value.trim() || 45455;
      if (!ip) {
        alert('Please enter your Android phone IP');
        return;
      }
      localStorage.setItem('neardrop_apk_ip', ip);
      this.lanBridge.connect(ip, port);
      this.pcConnectModal.style.display = 'none';
    });

    // Rename Modal
    this.renameCard.addEventListener('click', () => {
      this.newDeviceNameInput.value = this.deviceName;
      this.renameModal.style.display = 'flex';
      this.newDeviceNameInput.focus();
    });

    this.cancelRenameBtn.addEventListener('click', () => {
      this.renameModal.style.display = 'none';
    });

    this.saveRenameBtn.addEventListener('click', () => {
      const val = this.newDeviceNameInput.value.trim();
      if (val) {
        this.setDeviceName(val);
      }
      this.renameModal.style.display = 'none';
    });

    // Developer GitHub Link
    this.devProfileCard.addEventListener('click', () => {
      window.open('https://github.com/Kmrr-Gautam', '_blank');
    });
  }

  // File Staging
  stageFiles(files) {
    this.stagedFiles.push(...files);
    this.renderStaged();
  }

  removeStagedFile(index) {
    this.stagedFiles.splice(index, 1);
    this.renderStaged();
  }

  clearStaged() {
    this.stagedFiles = [];
    this.renderStaged();
  }

  renderStaged() {
    if (this.stagedFiles.length === 0) {
      this.stagedCard.style.display = 'none';
      return;
    }

    this.stagedCard.style.display = 'flex';
    this.stagedCount.textContent = this.stagedFiles.length;

    let totalBytes = 0;
    this.stagedList.innerHTML = '';

    this.stagedFiles.forEach((file, index) => {
      totalBytes += file.size;
      const item = document.createElement('div');
      item.className = 'staged-item';

      const ext = file.name.split('.').pop() || 'FILE';
      item.innerHTML = `
        <div style="display: flex; align-items: center; gap: 10px; overflow: hidden;">
          <span class="fmt-pill pill-vid">${ext.toUpperCase().substring(0, 4)}</span>
          <span style="font-weight: 600; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; max-width: 240px;">${file.name}</span>
          <span style="color: var(--text-muted); font-size: 0.8rem;">${this.formatSize(file.size)}</span>
        </div>
        <button style="background: none; border: none; color: var(--text-muted); cursor: pointer; font-size: 1.2rem;">&times;</button>
      `;

      item.querySelector('button').addEventListener('click', () => {
        this.removeStagedFile(index);
      });

      this.stagedList.appendChild(item);
    });

    this.stagedTotalSize.textContent = this.formatSize(totalBytes);
  }

  // Start Transfer
  async startTransfer() {
    if (this.stagedFiles.length === 0) return;

    const filesToSend = [...this.stagedFiles];
    this.clearStaged();

    const senderRect = this.localNode.getBoundingClientRect();
    const targetRect = this.targetNode.getBoundingClientRect();
    const canvasRect = this.flightCanvas.getBoundingClientRect();

    const startX = senderRect.left + senderRect.width / 2 - canvasRect.left;
    const startY = senderRect.top + senderRect.height / 2 - canvasRect.top;
    const endX = targetRect.left + targetRect.width / 2 - canvasRect.left;
    const endY = targetRect.top + targetRect.height / 2 - canvasRect.top;

    this.transferHud.style.display = 'flex';

    for (const file of filesToSend) {
      const ext = file.name.split('.').pop() || 'FILE';
      this.hudTitle.textContent = `Sending ${file.name}…`;

      // Launch Canvas Parabolic Flight
      this.flightEngine.launchFlight({
        startX,
        startY,
        endX,
        endY,
        fileName: file.name,
        fileExt: ext,
        color: '#5B45C9'
      });

      try {
        if (this.lanBridge.isConnected) {
          await this.lanBridge.uploadFile(file);
        } else if (this.webrtc.isConnected) {
          await this.webrtc.sendFile(file);
        } else {
          // Local demo simulation if neither is connected
          await new Promise(r => setTimeout(r, 1200));
        }

        this.addToHistory({
          name: file.name,
          size: file.size,
          direction: 'sent',
          target: this.targetNodeName.textContent,
          timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
        });
      } catch (err) {
        alert(`Transfer failed for ${file.name}: ${err.message}`);
        break;
      }
    }

    setTimeout(() => {
      this.transferHud.style.display = 'none';
    }, 1200);
  }

  renderTransferProgress(p) {
    this.transferHud.style.display = 'flex';
    this.hudTitle.textContent = p.direction === 'receive' ? `Receiving ${p.name}…` : `Sending ${p.name}…`;
    this.hudProgressFill.style.width = `${p.percent}%`;
    this.hudSpeed.textContent = p.speed;
    this.hudEta.textContent = p.eta;
    this.hudPercent.textContent = `${p.percent}%`;
  }

  handleReceivedFile(fileInfo, source) {
    this.addToHistory({
      name: fileInfo.name,
      size: fileInfo.size,
      direction: 'received',
      target: source,
      url: fileInfo.url,
      timestamp: fileInfo.timestamp || new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
    });

    if (fileInfo.url) {
      const a = document.createElement('a');
      a.href = fileInfo.url;
      a.download = fileInfo.name;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
    }

    setTimeout(() => {
      this.transferHud.style.display = 'none';
    }, 1200);
  }

  addToHistory(item) {
    this.history.unshift(item);
    if (this.history.length > 50) this.history.pop();
    localStorage.setItem('neardrop_web_history', JSON.stringify(this.history));
    this.renderHistory();
    this.renderTransfersFull();
  }

  clearHistory() {
    this.history = [];
    localStorage.removeItem('neardrop_web_history');
    this.renderHistory();
    this.renderTransfersFull();
  }

  renderHistory() {
    if (this.history.length === 0) {
      this.emptyHistory.style.display = 'block';
      this.historyList.querySelectorAll('.history-item-row').forEach(el => el.remove());
      return;
    }

    this.emptyHistory.style.display = 'none';
    this.historyList.querySelectorAll('.history-item-row').forEach(el => el.remove());

    this.history.slice(0, 5).forEach((item) => {
      const isSent = item.direction === 'sent';
      const row = document.createElement('div');
      row.className = 'history-item-row';
      row.style.cssText = 'display: flex; align-items: center; justify-content: space-between; padding: 10px 14px; background: rgba(0,0,0,0.2); border-radius: 14px; border: 1px solid var(--clay-card-border);';

      row.innerHTML = `
        <div style="display: flex; align-items: center; gap: 10px; overflow: hidden;">
          <span class="clay-chip ${isSent ? 'chip-sky' : 'chip-mint'}" style="font-size: 0.7rem; padding: 3px 8px;">${isSent ? 'Sent' : 'Received'}</span>
          <div>
            <div style="font-weight: 700; font-size: 0.9rem; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; max-width: 220px;">${item.name}</div>
            <div style="font-size: 0.75rem; color: var(--text-muted);">${this.formatSize(item.size)} · ${item.target} · ${item.timestamp}</div>
          </div>
        </div>
        ${item.url ? `<a href="${item.url}" download="${item.name}" class="clay-btn btn-mint clay-btn-sm" style="text-decoration:none;">Download</a>` : ''}
      `;
      this.historyList.appendChild(row);
    });
  }

  renderTransfersFull() {
    this.transfersFullList.innerHTML = '';
    if (this.history.length === 0) {
      this.transfersFullList.innerHTML = '<div class="clay-card" style="text-align: center; color: var(--text-muted);">No transfer records found.</div>';
      return;
    }

    this.history.forEach((item) => {
      const isSent = item.direction === 'sent';
      const card = document.createElement('div');
      card.className = 'clay-card';
      card.innerHTML = `
        <div style="display: flex; align-items: center; justify-content: space-between;">
          <div style="display: flex; align-items: center; gap: 12px;">
            <div class="clay-avatar ${isSent ? 'avatar-sky' : 'avatar-mint'}">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="20 6 9 17 4 12"/></svg>
            </div>
            <div>
              <div class="card-title">${item.name}</div>
              <div class="card-subtitle">${isSent ? 'Sent to' : 'Received from'} ${item.target} · ${this.formatSize(item.size)} · ${item.timestamp}</div>
            </div>
          </div>
          ${item.url ? `<a href="${item.url}" download="${item.name}" class="clay-btn btn-mint clay-btn-sm" style="text-decoration:none;">Download</a>` : ''}
        </div>
      `;
      this.transfersFullList.appendChild(card);
    });
  }

  // Modals
  async openReceiveQrModal() {
    this.qrModal.style.display = 'flex';
    this.qrModalRoomCode.textContent = `Room: ${this.currentRoom}`;

    const pairingPayload = JSON.stringify({
      protocol: 'neardrop-web',
      room: this.currentRoom,
      name: this.deviceName
    });

    await QrHelper.renderToCanvas(this.qrDisplayCanvas, pairingPayload, {
      width: 200,
      darkColor: '#0E0D14'
    });
  }

  openQrScanner() {
    this.scannerModal.style.display = 'flex';
    this.scannerStatus.textContent = 'Requesting camera access…';

    this.stopScannerFn = QrHelper.startScanner(
      this.scannerVideo,
      this.scannerCanvas,
      (data) => {
        this.closeScannerModal();
        this.handleScannedQr(data);
      },
      (err) => {
        this.scannerStatus.textContent = 'Camera error: ' + (err.message || 'Access denied');
      }
    );
  }

  closeScannerModal() {
    if (this.stopScannerFn) {
      this.stopScannerFn();
      this.stopScannerFn = null;
    }
    this.scannerModal.style.display = 'none';
  }

  handleScannedQr(rawText) {
    try {
      if (rawText.startsWith('{')) {
        const json = JSON.parse(rawText);
        if (json.ip) {
          this.lanBridge.connect(json.ip, json.port || 45455);
          return;
        }
        if (json.room) {
          this.webrtc.connectRoom(json.room, this.deviceName);
          return;
        }
      }
      if (rawText.startsWith('http://') || rawText.startsWith('https://')) {
        const url = new URL(rawText);
        this.lanBridge.connect(url.hostname, url.port || 45455);
        return;
      }
      alert('Scanned QR: ' + rawText);
    } catch (e) {
      alert('Error parsing QR: ' + e.message);
    }
  }

  formatSize(bytes) {
    if (!bytes || bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return (bytes / Math.pow(k, i)).toFixed(i >= 2 ? 1 : 0) + ' ' + sizes[i];
  }
}

window.addEventListener('DOMContentLoaded', () => {
  window.app = new NearDropClayApp();
});
