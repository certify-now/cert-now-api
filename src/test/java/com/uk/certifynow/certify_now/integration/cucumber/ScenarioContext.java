package com.uk.certifynow.certify_now.integration.cucumber;

import io.cucumber.spring.ScenarioScope;
import io.restassured.response.Response;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
@ScenarioScope
public class ScenarioContext {

  private Object requestBody;
  private final Map<String, String> headers = new HashMap<>();
  private Response lastResponse;
  private final Map<String, String> namedValues = new HashMap<>();

  public Object getRequestBody() {
    return requestBody;
  }

  public void setRequestBody(final Object requestBody) {
    this.requestBody = requestBody;
  }

  public Map<String, String> getHeaders() {
    return headers;
  }

  public Response getLastResponse() {
    return lastResponse;
  }

  public void setLastResponse(final Response lastResponse) {
    this.lastResponse = lastResponse;
  }

  public void putValue(final String key, final String value) {
    namedValues.put(key, value);
  }

  public String getValue(final String key) {
    return namedValues.get(key);
  }

  public void clear() {
    requestBody = null;
    headers.clear();
    lastResponse = null;
    namedValues.clear();
  }
}
