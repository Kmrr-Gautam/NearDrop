/**
 * NearDrop Web QR Generator Helper
 */
import QRCode from 'qrcode';

export class QrHelper {
  /**
   * Generates a QR code onto an HTML canvas element for phones and other devices to scan
   */
  static async renderToCanvas(canvas, text, options = {}) {
    const opts = {
      width: options.width || 240,
      margin: 2,
      color: {
        dark: options.darkColor || '#0E0D14',
        light: options.lightColor || '#ffffff'
      },
      errorCorrectionLevel: 'M'
    };
    return QRCode.toCanvas(canvas, text, opts);
  }
}
