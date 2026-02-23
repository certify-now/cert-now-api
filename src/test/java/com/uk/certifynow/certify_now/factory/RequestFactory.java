package com.uk.certifynow.certify_now.factory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class RequestFactory {

  private RequestFactory() {}

  public static Map<String, Object> validCustomerRegistration() {
    final Map<String, Object> request = new HashMap<>();
    request.put("email", "customer+" + UUID.randomUUID() + "@example.com");
    request.put("password", "Password1!");
    request.put("full_name", "Customer User");
    request.put("phone", "+447911123456");
    request.put("role", "CUSTOMER");
    return request;
  }

  public static Map<String, Object> validEngineerRegistration() {
    final Map<String, Object> request = new HashMap<>();
    request.put("email", "engineer+" + UUID.randomUUID() + "@example.com");
    request.put("password", "Password1!");
    request.put("full_name", "Engineer User");
    request.put("phone", "+447911654321");
    request.put("role", "ENGINEER");
    return request;
  }

  public static Map<String, Object> validLogin(final String email, final String password) {
    final Map<String, Object> request = new HashMap<>();
    request.put("email", email);
    request.put("password", password);
    request.put("device_info", "cucumber-tests");
    return request;
  }

  public static Map<String, Object> validRefresh(final String refreshToken) {
    final Map<String, Object> request = new HashMap<>();
    request.put("refresh_token", refreshToken);
    return request;
  }

  public static Map<String, Object> validLogout(final String refreshToken) {
    final Map<String, Object> request = new HashMap<>();
    request.put("refresh_token", refreshToken);
    return request;
  }

  public static Map<String, Object> validVerifyEmail(final String token) {
    final Map<String, Object> request = new HashMap<>();
    request.put("token", token);
    return request;
  }
}
