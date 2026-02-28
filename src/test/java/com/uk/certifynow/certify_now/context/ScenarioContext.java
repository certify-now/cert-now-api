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
  public static final String ACTIVE_CUSTOMER_TOKEN = "active_customer_token";
  public static final String ACTIVE_CUSTOMER_ID = "active_customer_id";
  public static final String ACTIVE_CUSTOMER_EMAIL = "active_customer_email";
  public static final String ACTIVE_CUSTOMER_PASSWORD = "active_customer_password";
  public static final String ACTIVE_CUSTOMER_B_TOKEN = "active_customer_b_token";
  public static final String ACTIVE_CUSTOMER_B_ID = "active_customer_b_id";
  public static final String ENGINEER_TOKEN = "engineer_token";
  public static final String ENGINEER_ID = "engineer_id";
  public static final String ENGINEER_EMAIL = "engineer_email";
  public static final String CURRENT_PROPERTY_ID = "current_property_id";
  public static final String BASELINE_TOTAL_PROPERTIES = "baseline_total_properties";

  // ── Pricing test context ──────────────────────────────────────────────────
  public static final String SARAH_TOKEN = "sarah_token";
  public static final String SARAH_USER_ID = "sarah_user_id";
  public static final String SARAH_PROPERTY_ID = "sarah_property_id";
  public static final String SARAH_SECOND_PROPERTY_ID = "sarah_second_property_id";
  public static final String ADMIN_TOKEN = "admin_token";
  public static final String ADMIN_USER_ID = "admin_user_id";
  public static final String BOB_TOKEN = "bob_token";
  public static final String BOB_USER_ID = "bob_user_id";
  public static final String BOB_PROPERTY_ID = "bob_property_id";
  public static final String MIKE_TOKEN = "mike_token";
  public static final String ROLE_USER_TOKEN = "role_user_token";
  public static final String CURRENT_RULE_ID = "current_rule_id";
  public static final String CURRENT_MODIFIER_ID = "current_modifier_id";
  public static final String CURRENT_URGENCY_MULTIPLIER_ID = "current_urgency_multiplier_id";
  public static final String FIRST_PRICE_BREAKDOWN = "first_price_breakdown";
  public static final String SECOND_PRICE_BREAKDOWN = "second_price_breakdown";

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
