/**
 * NearDrop Web QR Scanner and Generator Helper
 */
import QRCode from 'qrcode';
import jsQR from 'jsqr';

export class QrHelper {
  /**
   * Generates a QR code onto an HTML canvas element
   */
  static async renderToCanvas(canvas, text, options = {}) {
    const opts = {
      width: options.width || 240,
      margin: 2,
      color: {
        dark: options.darkColor || '#1e1b4b',
        light: options.lightColor || '#ffffff'
      },
      errorCorrectionLevel: 'M'
    };
    return QRCode.toCanvas(canvas, text, opts);
  }

  /**
   * Starts a webcam stream and continuously inspects frames for a QR code
   */
  static startScanner(videoEl, canvasEl, onDetected, onError) {
    let stream = null;
    let animId = null;
    let active = true;

    navigator.mediaDevices.getUserMedia({
      video: { facingMode: 'environment', width: { ideal: 640 }, height: { ideal: 480 } }
    })
      .then((s) => {
        if (!active) {
          s.getTracks().forEach((t) => t.stop());
          return;
        }
        stream = s;
        videoEl.srcObject = stream;
        videoEl.setAttribute('playsinline', 'true');
        videoEl.play();

        const ctx = canvasEl.getContext('2d');

        const scanFrame = () => {
          if (!active) return;
          if (videoEl.readyState === videoEl.HAVE_ENOUGH_DATA) {
            canvasEl.height = videoEl.videoHeight;
            canvasEl.width = videoEl.videoWidth;
            ctx.drawImage(videoEl, 0, 0, canvasEl.width, canvasEl.height);
            const imageData = ctx.getImageData(0, 0, canvasEl.width, canvasEl.height);
            const code = jsQR(imageData.data, imageData.width, imageData.height, {
              inversionAttempts: 'dontInvert'
            });

            if (code && code.data) {
              onDetected(code.data);
              return; // Stop after first valid scan
            }
          }
          animId = requestAnimationFrame(scanFrame);
        };

        animId = requestAnimationFrame(scanFrame);
      })
      .catch((err) => {
        if (onError) onError(err);
      });

    return () => {
      active = false;
      if (animId) cancelAnimationFrame(animId);
      if (stream) {
        stream.getTracks().forEach((track) => track.stop());
      }
    };
  }
}
