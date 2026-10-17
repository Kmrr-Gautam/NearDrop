/**
 * NearDrop WebRTC P2P DataChannel Engine
 * Supports Web-to-Web, Web-to-Mobile, and serverless direct QR pairing.
 */

const CHUNK_SIZE = 64 * 1024; // 64 KB chunks
const BUFFER_THRESHOLD = 1024 * 1024; // 1 MB backpressure limit

const ICE_SERVERS = {
  iceServers: [
    { urls: 'stun:stun.l.google.com:19302' },
    { urls: 'stun:stun1.l.google.com:19302' },
    { urls: 'stun:stun2.l.google.com:19302' },
    { urls: 'stun:stun3.l.google.com:19302' },
    { urls: 'stun:stun4.l.google.com:19302' }
  ]
};

export class WebRTCBridge {
  constructor(options = {}) {
    this.options = options;
    this.peerConnection = null;
    this.dataChannel = null;
    this.roomId = null;
    this.role = null; // 'offerer' | 'answerer'
    this.isConnected = false;
    this.peerName = 'Remote Device';
    this.transferQueue = [];
    this.currentTransfer = null;
    this.receivingFile = null;
    this.callbacks = {
      onStatusChange: options.onStatusChange || (() => {}),
      onPeerConnected: options.onPeerConnected || (() => {}),
      onPeerDisconnected: options.onPeerDisconnected || (() => {}),
      onProgress: options.onProgress || (() => {}),
      onFileReceived: options.onFileReceived || (() => {}),
      onError: options.onError || (() => {}),
      onSignalingTicket: options.onSignalingTicket || (() => {})
    };

    // Public lightweight signaling via public websocket broker for effortless zero-setup pairing
    this.ws = null;
    this.signalChannel = null;
  }

  /**
   * Generates a 6-digit room code
   */
  static generateRoomCode() {
    const part1 = Math.floor(100 + Math.random() * 900);
    const part2 = Math.floor(100 + Math.random() * 900);
    return `${part1}-${part2}`;
  }

  /**
   * Initializes a peer connection
   */
  initPeerConnection() {
    this.cleanup();
    this.peerConnection = new RTCPeerConnection(ICE_SERVERS);

    this.peerConnection.onconnectionstatechange = () => {
      const state = this.peerConnection.connectionState;
      this.callbacks.onStatusChange(`Connection: ${state}`);
      if (state === 'connected') {
        this.isConnected = true;
        this.callbacks.onPeerConnected(this.peerName);
      } else if (state === 'disconnected' || state === 'failed' || state === 'closed') {
        this.isConnected = false;
        this.callbacks.onPeerDisconnected();
      }
    };

    this.peerConnection.ondatachannel = (event) => {
      this.setupDataChannel(event.channel);
    };

    this.peerConnection.onicecandidate = (event) => {
      if (event.candidate && this.ws && this.ws.readyState === WebSocket.OPEN) {
        this.sendSignal({ type: 'candidate', candidate: event.candidate });
      }
    };
  }

  /**
   * Sets up binary data channel
   */
  setupDataChannel(channel) {
    this.dataChannel = channel;
    this.dataChannel.binaryType = 'arraybuffer';

    this.dataChannel.onopen = () => {
      this.isConnected = true;
      this.callbacks.onPeerConnected(this.peerName);
      this.sendControlMessage({ type: 'hello', name: this.options.deviceName || 'NearDrop PC Web' });
    };

    this.dataChannel.onclose = () => {
      this.isConnected = false;
      this.callbacks.onPeerDisconnected();
    };

    this.dataChannel.onerror = (err) => {
      this.callbacks.onError('Data channel error: ' + (err.message || err));
    };

    this.dataChannel.onmessage = (event) => {
      if (typeof event.data === 'string') {
        this.handleControlMessage(JSON.parse(event.data));
      } else {
        this.handleBinaryChunk(event.data);
      }
    };
  }

  /**
   * Join or Host a Room via public signaling (fallback to direct QR ticket)
   */
  connectRoom(roomId, deviceName) {
    this.roomId = roomId;
    this.options.deviceName = deviceName;
    this.initPeerConnection();

    // Use public signaling server or direct ticket
    const signalingUrl = `wss://socketsbay.com/wss/v2/1/neardrop-${roomId}/`;
    try {
      this.ws = new WebSocket(signalingUrl);
      this.ws.onopen = async () => {
        this.callbacks.onStatusChange(`Connected to Room ${roomId}`);
        // Create offer if first peer
        this.createOffer();
      };

      this.ws.onmessage = async (evt) => {
        try {
          const msg = JSON.parse(evt.data);
          this.handleSignalingMessage(msg);
        } catch (_) {}
      };

      this.ws.onerror = () => {
        // Fallback gracefully to offline QR ticket mode
        this.callbacks.onStatusChange(`Room ${roomId} (Offline/QR Ticket Ready)`);
      };
    } catch (_) {
      this.callbacks.onStatusChange(`Room ${roomId} (Offline Mode)`);
    }
  }

  /**
   * Creates an SDP Offer
   */
  async createOffer() {
    this.role = 'offerer';
    const channel = this.peerConnection.createDataChannel('neardrop-transfer', {
      ordered: true
    });
    this.setupDataChannel(channel);

    const offer = await this.peerConnection.createOffer();
    await this.peerConnection.setLocalDescription(offer);

    // Wait briefly for ICE candidates to collect
    await new Promise((resolve) => {
      if (this.peerConnection.iceGatheringState === 'complete') {
        resolve();
      } else {
        const check = () => {
          if (this.peerConnection.iceGatheringState === 'complete') {
            this.peerConnection.removeEventListener('icegatheringstatechange', check);
            resolve();
          }
        };
        this.peerConnection.addEventListener('icegatheringstatechange', check);
        setTimeout(resolve, 1500); // Max 1.5s wait
      }
    });

    const ticket = JSON.stringify(this.peerConnection.localDescription);
    this.callbacks.onSignalingTicket({ type: 'offer', ticket, roomId: this.roomId });
    this.sendSignal({ type: 'offer', sdp: this.peerConnection.localDescription, name: this.options.deviceName });
  }

  /**
   * Handles an SDP Offer and creates an Answer
   */
  async handleOffer(offerSdp, peerName) {
    if (peerName) this.peerName = peerName;
    this.role = 'answerer';
    if (!this.peerConnection) this.initPeerConnection();

    await this.peerConnection.setRemoteDescription(new RTCSessionDescription(offerSdp));
    const answer = await this.peerConnection.createAnswer();
    await this.peerConnection.setLocalDescription(answer);

    await new Promise((resolve) => {
      if (this.peerConnection.iceGatheringState === 'complete') {
        resolve();
      } else {
        const check = () => {
          if (this.peerConnection.iceGatheringState === 'complete') {
            this.peerConnection.removeEventListener('icegatheringstatechange', check);
            resolve();
          }
        };
        this.peerConnection.addEventListener('icegatheringstatechange', check);
        setTimeout(resolve, 1500);
      }
    });

    const ticket = JSON.stringify(this.peerConnection.localDescription);
    this.callbacks.onSignalingTicket({ type: 'answer', ticket, roomId: this.roomId });
    this.sendSignal({ type: 'answer', sdp: this.peerConnection.localDescription, name: this.options.deviceName });
  }

  /**
   * Handles an incoming Answer
   */
  async handleAnswer(answerSdp, peerName) {
    if (peerName) this.peerName = peerName;
    if (this.peerConnection && this.peerConnection.signalingState !== 'stable') {
      await this.peerConnection.setRemoteDescription(new RTCSessionDescription(answerSdp));
    }
  }

  sendSignal(data) {
    if (this.ws && this.ws.readyState === WebSocket.OPEN) {
      this.ws.send(JSON.stringify(data));
    }
  }

  async handleSignalingMessage(msg) {
    if (!msg || !msg.type) return;
    if (msg.type === 'offer' && this.role !== 'offerer') {
      await this.handleOffer(msg.sdp, msg.name);
    } else if (msg.type === 'answer' && this.role === 'offerer') {
      await this.handleAnswer(msg.sdp, msg.name);
    } else if (msg.type === 'candidate' && this.peerConnection) {
      try {
        await this.peerConnection.addIceCandidate(new RTCIceCandidate(msg.candidate));
      } catch (_) {}
    }
  }

  sendControlMessage(obj) {
    if (this.dataChannel && this.dataChannel.readyState === 'open') {
      this.dataChannel.send(JSON.stringify(obj));
    }
  }

  handleControlMessage(msg) {
    switch (msg.type) {
      case 'hello':
        this.peerName = msg.name || 'Remote Device';
        this.callbacks.onPeerConnected(this.peerName);
        break;

      case 'file-header':
        this.receivingFile = {
          id: msg.id,
          name: msg.name,
          size: msg.size,
          mimeType: msg.mimeType || 'application/octet-stream',
          chunks: [],
          receivedBytes: 0,
          startTime: Date.now(),
          lastUpdate: Date.now()
        };
        this.callbacks.onProgress({
          direction: 'receive',
          name: msg.name,
          receivedBytes: 0,
          totalBytes: msg.size,
          percent: 0,
          speed: '0 KB/s',
          eta: 'Calculating...'
        });
        break;

      case 'file-end':
        if (this.receivingFile) {
          const blob = new Blob(this.receivingFile.chunks, { type: this.receivingFile.mimeType });
          const fileInfo = {
            id: this.receivingFile.id,
            name: this.receivingFile.name,
            size: this.receivingFile.size,
            mimeType: this.receivingFile.mimeType,
            blob,
            url: URL.createObjectURL(blob),
            timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
          };
          this.callbacks.onFileReceived(fileInfo);
          this.receivingFile = null;
        }
        break;

      case 'cancel':
        this.callbacks.onStatusChange('Transfer cancelled by peer');
        this.receivingFile = null;
        break;
    }
  }

  handleBinaryChunk(chunk) {
    if (!this.receivingFile) return;
    this.receivingFile.chunks.push(chunk);
    this.receivingFile.receivedBytes += chunk.byteLength;

    const now = Date.now();
    if (now - this.receivingFile.lastUpdate > 100 || this.receivingFile.receivedBytes >= this.receivingFile.size) {
      const elapsed = (now - this.receivingFile.startTime) / 1000;
      const speedBps = elapsed > 0 ? this.receivingFile.receivedBytes / elapsed : 0;
      const speedStr = speedBps > 1024 * 1024
        ? `${(speedBps / (1024 * 1024)).toFixed(1)} MB/s`
        : `${Math.round(speedBps / 1024)} KB/s`;

      const remainingBytes = this.receivingFile.size - this.receivingFile.receivedBytes;
      const etaSeconds = speedBps > 0 ? Math.ceil(remainingBytes / speedBps) : 0;
      const etaStr = etaSeconds > 60 ? `${Math.ceil(etaSeconds / 60)} min` : `${etaSeconds}s`;

      const percent = Math.min(100, Math.round((this.receivingFile.receivedBytes / this.receivingFile.size) * 100));

      this.callbacks.onProgress({
        direction: 'receive',
        name: this.receivingFile.name,
        receivedBytes: this.receivingFile.receivedBytes,
        totalBytes: this.receivingFile.size,
        percent,
        speed: speedStr,
        eta: etaStr
      });
      this.receivingFile.lastUpdate = now;
    }
  }

  /**
   * Sends a file to the connected peer over DataChannel
   */
  async sendFile(file) {
    if (!this.dataChannel || this.dataChannel.readyState !== 'open') {
      throw new Error('Not connected to a peer');
    }

    const fileId = 'file_' + Math.random().toString(36).substring(2, 9);
    this.sendControlMessage({
      type: 'file-header',
      id: fileId,
      name: file.name,
      size: file.size,
      mimeType: file.type
    });

    const reader = file.stream().getReader();
    let sentBytes = 0;
    const startTime = Date.now();
    let lastUpdate = Date.now();

    while (true) {
      // Check backpressure
      if (this.dataChannel.bufferedAmount > BUFFER_THRESHOLD) {
        await new Promise((resolve) => {
          const onLow = () => {
            this.dataChannel.removeEventListener('bufferedamountlow', onLow);
            resolve();
          };
          this.dataChannel.addEventListener('bufferedamountlow', onLow);
        });
      }

      const { done, value } = await reader.read();
      if (done) break;

      // Send in CHUNK_SIZE slices
      for (let offset = 0; offset < value.byteLength; offset += CHUNK_SIZE) {
        const slice = value.buffer.slice(value.byteOffset + offset, value.byteOffset + Math.min(offset + CHUNK_SIZE, value.byteLength));
        this.dataChannel.send(slice);
        sentBytes += slice.byteLength;

        const now = Date.now();
        if (now - lastUpdate > 100 || sentBytes >= file.size) {
          const elapsed = (now - startTime) / 1000;
          const speedBps = elapsed > 0 ? sentBytes / elapsed : 0;
          const speedStr = speedBps > 1024 * 1024
            ? `${(speedBps / (1024 * 1024)).toFixed(1)} MB/s`
            : `${Math.round(speedBps / 1024)} KB/s`;

          const remainingBytes = file.size - sentBytes;
          const etaSeconds = speedBps > 0 ? Math.ceil(remainingBytes / speedBps) : 0;
          const etaStr = etaSeconds > 60 ? `${Math.ceil(etaSeconds / 60)} min` : `${etaSeconds}s`;
          const percent = Math.min(100, Math.round((sentBytes / file.size) * 100));

          this.callbacks.onProgress({
            direction: 'send',
            name: file.name,
            sentBytes,
            totalBytes: file.size,
            percent,
            speed: speedStr,
            eta: etaStr
          });
          lastUpdate = now;
        }
      }
    }

    this.sendControlMessage({ type: 'file-end', id: fileId });
    return { id: fileId, name: file.name, size: file.size };
  }

  cleanup() {
    if (this.dataChannel) {
      try { this.dataChannel.close(); } catch (_) {}
      this.dataChannel = null;
    }
    if (this.peerConnection) {
      try { this.peerConnection.close(); } catch (_) {}
      this.peerConnection = null;
    }
    if (this.ws) {
      try { this.ws.close(); } catch (_) {}
      this.ws = null;
    }
    this.isConnected = false;
  }
}
