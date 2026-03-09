package com.uk.certifynow.certify_now.service.pdf;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Generates QR code images using ZXing (Apache 2.0). */
@Service
public class QrCodeService {

  private static final Logger log = LoggerFactory.getLogger(QrCodeService.class);

  /**
   * Encodes {@code content} as a QR code and returns the result as a PNG byte array.
   *
   * @param content the text/URL to encode
   * @param sizePixels width and height in pixels
   * @return PNG-encoded QR code image bytes
   */
  public byte[] generateQrPng(final String content, final int sizePixels) {
    try {
      final QRCodeWriter writer = new QRCodeWriter();
      final Map<EncodeHintType, Object> hints =
          Map.of(
              EncodeHintType.MARGIN,
              1,
              EncodeHintType.CHARACTER_SET,
              StandardCharsets.UTF_8.name());
      final BitMatrix matrix =
          writer.encode(content, BarcodeFormat.QR_CODE, sizePixels, sizePixels, hints);
      final ByteArrayOutputStream baos = new ByteArrayOutputStream();
      MatrixToImageWriter.writeToStream(matrix, "PNG", baos);
      return baos.toByteArray();
    } catch (WriterException e) {
      throw new IllegalArgumentException("Failed to encode QR content: " + content, e);
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to write QR PNG bytes", e);
    }
  }

  /**
   * Generates a QR code and returns it as a {@code data:image/png;base64,…} URI, or {@code null} if
   * generation fails for any reason. The template shows a plain-text URL fallback when this returns
   * {@code null}.
   *
   * @param content the text/URL to encode
   * @param sizePixels width and height in pixels
   * @return data URI string or {@code null} on failure
   */
  public String generateQrDataUriOrNull(final String content, final int sizePixels) {
    try {
      final byte[] pngBytes = generateQrPng(content, sizePixels);
      return "data:image/png;base64," + Base64.getEncoder().encodeToString(pngBytes);
    } catch (Exception e) {
      log.warn(
          "QR generation failed for '{}', using plain-text URL fallback: {}",
          content,
          e.getMessage());
      return null;
    }
  }
}
