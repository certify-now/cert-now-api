package com.uk.certifynow.certify_now.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("IpAddressUtils")
class IpAddressUtilsTest {

  @Mock private HttpServletRequest request;

  private static final String REMOTE_ADDR = "10.0.0.1";

  @BeforeEach
  void setUp() {
    // lenient to avoid UnnecessaryStubbingException for extractClientIp tests
    // that return early from valid X-Forwarded-For without hitting getRemoteAddr()
    lenient().when(request.getRemoteAddr()).thenReturn(REMOTE_ADDR);
  }

  @Nested
  @DisplayName("extractClientIp()")
  class ExtractClientIp {

    @Test
    @DisplayName("should return first valid IPv4 from X-Forwarded-For")
    void shouldReturnFirstValidIpv4FromHeader() {
      when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.5, 10.0.0.2");

      assertThat(IpAddressUtils.extractClientIp(request)).isEqualTo("203.0.113.5");
    }

    @Test
    @DisplayName("should return single valid IPv4 from X-Forwarded-For")
    void shouldReturnSingleIpFromHeader() {
      when(request.getHeader("X-Forwarded-For")).thenReturn("198.51.100.42");

      assertThat(IpAddressUtils.extractClientIp(request)).isEqualTo("198.51.100.42");
    }

    @Test
    @DisplayName("should fall back to remoteAddr when X-Forwarded-For is absent")
    void shouldFallbackWhenHeaderAbsent() {
      when(request.getHeader("X-Forwarded-For")).thenReturn(null);

      assertThat(IpAddressUtils.extractClientIp(request)).isEqualTo(REMOTE_ADDR);
    }

    @Test
    @DisplayName("should fall back to remoteAddr when X-Forwarded-For is blank")
    void shouldFallbackWhenHeaderBlank() {
      when(request.getHeader("X-Forwarded-For")).thenReturn("   ");

      assertThat(IpAddressUtils.extractClientIp(request)).isEqualTo(REMOTE_ADDR);
    }

    @Test
    @DisplayName("should fall back to remoteAddr when X-Forwarded-For is malformed")
    void shouldFallbackWhenHeaderMalformed() {
      when(request.getHeader("X-Forwarded-For")).thenReturn("not-an-ip-address!!!");

      assertThat(IpAddressUtils.extractClientIp(request)).isEqualTo(REMOTE_ADDR);
    }

    @Test
    @DisplayName("should fall back to remoteAddr when X-Forwarded-For has a hostname (not IP)")
    void shouldFallbackWhenHeaderContainsHostname() {
      when(request.getHeader("X-Forwarded-For")).thenReturn("evil.example.com");

      assertThat(IpAddressUtils.extractClientIp(request)).isEqualTo(REMOTE_ADDR);
    }
  }

  @Nested
  @DisplayName("isValidIp()")
  class IsValidIp {

    @Test
    @DisplayName("should accept valid IPv4 addresses")
    void shouldAcceptValidIpv4() {
      assertThat(IpAddressUtils.isValidIp("192.168.1.1")).isTrue();
      assertThat(IpAddressUtils.isValidIp("0.0.0.0")).isTrue();
      assertThat(IpAddressUtils.isValidIp("255.255.255.255")).isTrue();
    }

    @Test
    @DisplayName("should accept valid IPv6 loopback address")
    void shouldAcceptValidIpv6() {
      // InetAddress.getByName("::1").getHostAddress() returns the full expanded form
      // Both "::1" and the expanded form should be valid inputs
      assertThat(IpAddressUtils.isValidIp("0:0:0:0:0:0:0:1")).isTrue();
      assertThat(IpAddressUtils.isValidIp("2001:db8:85a3:0:0:8a2e:370:7334")).isTrue();
    }

    @Test
    @DisplayName("should reject null and blank")
    void shouldRejectNullAndBlank() {
      assertThat(IpAddressUtils.isValidIp(null)).isFalse();
      assertThat(IpAddressUtils.isValidIp("")).isFalse();
      assertThat(IpAddressUtils.isValidIp("   ")).isFalse();
    }

    @Test
    @DisplayName("should reject hostnames")
    void shouldRejectHostnames() {
      assertThat(IpAddressUtils.isValidIp("localhost")).isFalse();
      assertThat(IpAddressUtils.isValidIp("example.com")).isFalse();
    }

    @Test
    @DisplayName("should reject garbage strings")
    void shouldRejectGarbageStrings() {
      assertThat(IpAddressUtils.isValidIp("not-an-ip")).isFalse();
      assertThat(IpAddressUtils.isValidIp("999.999.999.999")).isFalse();
      assertThat(IpAddressUtils.isValidIp("<script>alert(1)</script>")).isFalse();
    }
  }
}
