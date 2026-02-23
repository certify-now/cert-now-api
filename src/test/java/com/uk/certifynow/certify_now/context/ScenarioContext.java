package com.uk.certifynow.certify_now.context;

import io.cucumber.spring.ScenarioScope;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
@ScenarioScope
public class ScenarioContext {

  public static final String LAST_RESPONSE = "last_response";
  public static final String LAST_REQUEST_BODY = "last_request_body";
  public static final String LAST_RESPONSE_TIME_MS = "last_response_time_ms";
  public static final String REGISTERED_USER_ID = "registered_user_id";
  public static final String REGISTERED_USER_EMAIL = "registered_user_email";
  public static final String REGISTERED_USER_PHONE = "registered_user_phone";
  public static final String REGISTERED_USER_ROLE = "registered_user_role";
  public static final String ACCESS_TOKEN = "access_token";
  public static final String ACCESS_TOKEN_A = "access_token_a";
  public static final String ACCESS_TOKEN_B = "access_token_b";
  public static final String REFRESH_TOKEN = "refresh_token";
  public static final String VERIFICATION_TOKEN = "verification_token";
  public static final String CLIENT_IP = "client_ip";
  public static final String X_FORWARDED_FOR = "x_forwarded_for";
  public static final String GENERATED_FULL_NAME = "generated_full_name";
  public static final String BASELINE_USER_COUNT = "baseline_user_count";
  public static final String LAST_EMAIL = "last_email";
  public static final String MEASURED_FRESH_MS = "measured_fresh_ms";
  public static final String MEASURED_DUPLICATE_MS = "measured_duplicate_ms";
  public static final String CONCURRENT_RESPONSES = "concurrent_responses";
  public static final String CONCURRENT_EMAIL = "concurrent_email";
  public static final String CONCURRENT_PHONE = "concurrent_phone";

  private final Map<String, Object> store = new HashMap<>();

  public void put(final String key, final Object value) {
    store.put(key, value);
  }

  public boolean contains(final String key) {
    return store.containsKey(key);
  }

  public <T> T get(final String key, final Class<T> type) {
    final Object value = store.get(key);
    if (value == null) {
      return null;
    }
    if (!type.isInstance(value)) {
      throw new IllegalStateException(
          "Scenario context key '" + key + "' has unexpected type: " + value.getClass().getName());
    }
    return type.cast(value);
  }
}
