package com.uk.certifynow.certify_now.service.pdf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class QrCodeServiceTest {

  private final QrCodeService qrCodeService = new QrCodeService();

  @Test
  @DisplayName("Generated QR code decodes back to the original URL")
  void generateQrPng_decodesBackToOriginalUrl() throws IOException, NotFoundException {
    final String url = "https://app.certifynow.io/certificates/test-certificate-id";

    final byte[] pngBytes = qrCodeService.generateQrPng(url, 200);

    assertThat(pngBytes).isNotEmpty();

    final BufferedImage image = ImageIO.read(new ByteArrayInputStream(pngBytes));
    assertThat(image).isNotNull();

    final BinaryBitmap bitmap =
        new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(image)));
    final String decoded = new MultiFormatReader().decode(bitmap).getText();

    assertThat(decoded).isEqualTo(url);
  }

  @Test
  @DisplayName("QR image has the requested pixel dimensions")
  void generateQrPng_producesCorrectSize() throws IOException {
    final byte[] pngBytes = qrCodeService.generateQrPng("https://example.com", 150);

    final BufferedImage image = ImageIO.read(new ByteArrayInputStream(pngBytes));
    assertThat(image.getWidth()).isEqualTo(150);
    assertThat(image.getHeight()).isEqualTo(150);
  }

  @Test
  @DisplayName("Empty content throws IllegalArgumentException")
  void generateQrPng_emptyContent_throwsIllegalArgumentException() {
    assertThatThrownBy(() -> qrCodeService.generateQrPng("", 200))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
