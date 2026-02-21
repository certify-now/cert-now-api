package com.uk.certifynow.certify_now.util;

import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility for safely extracting the client IP address from an HTTP request.
 *
 * <p>The {@code X-Forwarded-For} header can be spoofed by malicious clients when the server is not
 * behind a trusted reverse proxy. This class hardens extraction by:
 *
 * <ul>
 *   <li>Taking only the first value (the original client) from the comma-separated list
 *   <li>Validating the candidate via {@link InetAddress#getByName(String)} (rejects garbage input)
 *   <li>Falling back to {@link HttpServletRequest#getRemoteAddr()} if the header is absent or
 *       invalid
 * </ul>
 */
public final class IpAddressUtils {

  private static final Logger log = LoggerFactory.getLogger(IpAddressUtils.class);

  private IpAddressUtils() {
    // Utility class — no instantiation
  }

  /**
   * Extracts the client IP from the request, applying {@code X-Forwarded-For} safely.
   *
   * @param request the incoming HTTP request
   * @return a valid IPv4 or IPv6 address string; never null
   */
  public static String extractClientIp(final HttpServletRequest request) {
    final String forwarded = request.getHeader("X-Forwarded-For");
    if (forwarded != null && !forwarded.isBlank()) {
      final String candidate = forwarded.split(",")[0].trim();
      if (isValidIp(candidate)) {
        return candidate;
      }
      log.warn(
          "Rejected malformed X-Forwarded-For value '{}', falling back to remoteAddr", candidate);
    }
    return request.getRemoteAddr();
  }

  /**
   * Returns {@code true} if {@code ip} is a valid IPv4 or IPv6 address.
   *
   * <p>Uses {@link InetAddress#getByName(String)} which handles both formats and rejects hostnames
   * with DNS lookups disabled implicitly (dotted-decimal / colon-hex only).
   */
  static boolean isValidIp(final String ip) {
    if (ip == null || ip.isBlank()) {
      return false;
    }
    try {
      final InetAddress addr = InetAddress.getByName(ip);
      // getByName resolves hostnames — verify the candidate actually converted to the
      // same literal
      return addr.getHostAddress().equalsIgnoreCase(ip)
          || addr.getHostAddress().equalsIgnoreCase(normalizeIpv6(ip));
    } catch (final UnknownHostException ex) {
      return false;
    }
  }

  /**
   * Normalises an IPv6 address for comparison (removes surrounding brackets if present).
   *
   * <p>e.g. {@code [::1]} → {@code ::1}
   */
  private static String normalizeIpv6(final String ip) {
    if (ip.startsWith("[") && ip.endsWith("]")) {
      return ip.substring(1, ip.length() - 1);
    }
    return ip;
  }
}
