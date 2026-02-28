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
    request.put("code", token);
    return request;
  }

  public static Map<String, Object> validUpdateMe() {
    final Map<String, Object> request = new HashMap<>();
    request.put("full_name", "Updated Name");
    request.put("phone", "+447911000111");
    request.put("avatar_url", "https://cdn.example.com/avatar.png");
    return request;
  }

  public static Map<String, Object> validPropertyCreate() {
    final Map<String, Object> request = new HashMap<>();
    request.put("bedrooms", 3);
    request.put("country", "GB");
    request.put("floor_area_sqm", "92.50");
    request.put("floors", 2);
    request.put("gas_appliance_count", 1);
    request.put("has_electric", true);
    request.put("has_gas_supply", true);
    request.put("postcode", "SW1A 1AA");
    request.put("uprn", "123456789012");
    request.put("epc_register_ref", "EPC-REF-123");
    request.put("city", "London");
    request.put("county", "Greater London");
    request.put("address_line1", "10 Downing Street");
    request.put("address_line2", "");
    request.put("property_type", "TERRACED");
    request.put("location", "51.5034,-0.1276");
    return request;
  }

  public static Map<String, Object> validPropertyUpdate() {
    final Map<String, Object> request = validPropertyCreate();
    request.put("is_active", true);
    request.put("bedrooms", 4);
    request.put("city", "Bristol");
    request.put("property_type", "DETACHED");
    return request;
  }
}
