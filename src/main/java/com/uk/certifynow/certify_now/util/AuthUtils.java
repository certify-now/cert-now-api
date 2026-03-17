package com.uk.certifynow.certify_now.util;

public final class AuthUtils {

  private AuthUtils() {
    // Utility class — no instantiation
  }

  public static String maskEmail(final String email) {
    if (email == null || email.length() < 3) {
      return "***";
    }
    final String[] parts = email.split("@");
    if (parts.length != 2) {
      return "***";
    }
    final String localPart = parts[0];
    final String domain = parts[1];
    if (localPart.length() <= 2) {
      return localPart.charAt(0) + "***@" + domain;
    }
    return localPart.charAt(0) + "***" + localPart.charAt(localPart.length() - 1) + "@" + domain;
  }
}
