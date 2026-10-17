/**
 * NearDrop LAN Bridge Engine
 * Direct HTTP/WebSocket streaming bridge between PC Web App and NearDrop Android APK
 */

export class LanBridge {
  constructor(options = {}) {
    this.targetIp = options.ip || '';
    this.targetPort = options.port || 45455;
    this.isConnected = false;
    this.deviceInfo = null;
    this.pollInterval = null;
    this.callbacks = {
      onStatusChange: options.onStatusChange || (() => {}),
      onConnected: options.onConnected || (() => {}),
      onDisconnected: options.onDisconnected || (() => {}),
      onProgress: options.onProgress || (() => {}),
      onIncomingFromApk: options.onIncomingFromApk || (() => {}),
      onError: options.onError || (() => {})
    };
  }

  /**
   * Connect and verify connection to NearDrop Android APK
   */
  async connect(ip, port = 45455) {
    this.targetIp = ip.trim();
    this.targetPort = parseInt(port, 10) || 45455;
    const url = `http://${this.targetIp}:${this.targetPort}/api/info`;

    this.callbacks.onStatusChange(`Probing Android device at ${this.targetIp}:${this.targetPort}...`);

    try {
      const controller = new AbortController();
      const timeoutId = setTimeout(() => controller.abort(), 4000);

      const resp = await fetch(url, {
        method: 'GET',
        headers: { 'Accept': 'application/json' },
        signal: controller.signal
      });
      clearTimeout(timeoutId);

      if (resp.ok) {
        const data = await resp.json();
        this.isConnected = true;
        this.deviceInfo = {
          name: data.device || 'NearDrop Android',
          version: data.version || '0.2.1',
          ip: this.targetIp,
          port: this.targetPort
        };
        this.callbacks.onConnected(this.deviceInfo);
        this.callbacks.onStatusChange(`Connected to ${this.deviceInfo.name}`);
        this.startPollingIncoming();
        return { ok: true, data: this.deviceInfo };
      } else {
        throw new Error(`Device responded with HTTP ${resp.status}`);
      }
    } catch (err) {
      this.isConnected = false;
      const msg = err.name === 'AbortError' ? 'Connection timed out' : err.message;
      this.callbacks.onError(`Could not reach APK at ${this.targetIp}:${this.targetPort} (${msg})`);
      this.callbacks.onStatusChange('Disconnected');
      return { ok: false, error: msg };
    }
  }

  /**
   * Sends file to Android APK via streaming HTTP upload
   */
  uploadFile(file) {
    return new Promise((resolve, reject) => {
      if (!this.isConnected) {
        return reject(new Error('Not connected to Android APK'));
      }

      const url = `http://${this.targetIp}:${this.targetPort}/api/upload?name=${encodeURIComponent(file.name)}&size=${file.size}&type=${encodeURIComponent(file.type || 'application/octet-stream')}`;
      const xhr = new XMLHttpRequest();
      xhr.open('POST', url, true);

      const startTime = Date.now();
      let lastTime = startTime;

      xhr.upload.onprogress = (e) => {
        if (e.lengthComputable) {
          const now = Date.now();
          const elapsed = (now - startTime) / 1000;
          const speedBps = elapsed > 0 ? e.loaded / elapsed : 0;
          const speedStr = speedBps > 1024 * 1024
            ? `${(speedBps / (1024 * 1024)).toFixed(1)} MB/s`
            : `${Math.round(speedBps / 1024)} KB/s`;

          const remainingBytes = e.total - e.loaded;
          const etaSeconds = speedBps > 0 ? Math.ceil(remainingBytes / speedBps) : 0;
          const etaStr = etaSeconds > 60 ? `${Math.ceil(etaSeconds / 60)} min` : `${etaSeconds}s`;
          const percent = Math.min(100, Math.round((e.loaded / e.total) * 100));

          this.callbacks.onProgress({
            direction: 'send',
            name: file.name,
            sentBytes: e.loaded,
            totalBytes: e.total,
            percent,
            speed: speedStr,
            eta: etaStr
          });
          lastTime = now;
        }
      };

      xhr.onload = () => {
        if (xhr.status >= 200 && xhr.status < 300) {
          this.callbacks.onProgress({
            direction: 'send',
            name: file.name,
            sentBytes: file.size,
            totalBytes: file.size,
            percent: 100,
            speed: 'Done',
            eta: 'Complete'
          });
          resolve({ ok: true, name: file.name, size: file.size });
        } else {
          reject(new Error(`Upload rejected with HTTP ${xhr.status}: ${xhr.responseText}`));
        }
      };

      xhr.onerror = () => {
        reject(new Error('Network error during file upload to APK'));
      };

      xhr.onabort = () => {
        reject(new Error('Upload cancelled'));
      };

      xhr.send(file);
    });
  }

  /**
   * Polls the Android APK for files ready to be pulled/downloaded to PC
   */
  startPollingIncoming() {
    this.stopPollingIncoming();
    this.pollInterval = setInterval(async () => {
      if (!this.isConnected) return;
      try {
        const resp = await fetch(`http://${this.targetIp}:${this.targetPort}/api/queue`, {
          headers: { 'Accept': 'application/json' }
        });
        if (resp.ok) {
          const files = await resp.json();
          if (Array.isArray(files) && files.length > 0) {
            for (const f of files) {
              this.callbacks.onIncomingFromApk({
                id: f.id,
                name: f.name,
                size: f.size,
                downloadUrl: `http://${this.targetIp}:${this.targetPort}/api/download?id=${f.id}`
              });
            }
          }
        }
      } catch (_) {}
    }, 2000);
  }

  stopPollingIncoming() {
    if (this.pollInterval) {
      clearInterval(this.pollInterval);
      this.pollInterval = null;
    }
  }

  disconnect() {
    this.stopPollingIncoming();
    this.isConnected = false;
    this.deviceInfo = null;
    this.callbacks.onDisconnected();
    this.callbacks.onStatusChange('Disconnected');
  }
}
