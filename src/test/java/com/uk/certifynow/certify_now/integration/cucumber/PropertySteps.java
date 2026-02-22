package com.uk.certifynow.certify_now.integration.cucumber;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.uk.certifynow.certify_now.domain.CustomerProfile;
import com.uk.certifynow.certify_now.domain.User;
import com.uk.certifynow.certify_now.repos.CustomerProfileRepository;
import com.uk.certifynow.certify_now.repos.UserRepository;
import com.uk.certifynow.certify_now.service.auth.UserStatus;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.RestAssured;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

public class PropertySteps {

  @Autowired private ScenarioContext context;
  @Autowired private UserRepository userRepository;
  @Autowired private CustomerProfileRepository customerProfileRepository;

  private UUID currentUserId;
  private String createdPropertyId;
  private String accessToken;

  @Given("a logged in customer")
  public void aLoggedInCustomer() {
    String email = "customer_" + System.currentTimeMillis() + "@test.com";
    String json =
        """
        {
          "email": "%s",
          "password": "Password123!",
          "full_name": "Test Customer",
          "phone": null,
          "role": "CUSTOMER"
        }
        """
            .formatted(email);

    var request = RestAssured.given().contentType(MediaType.APPLICATION_JSON_VALUE).body(json);
    Response response = request.when().post("/api/v1/auth/register");
    assertEquals(201, response.statusCode());

    accessToken = response.jsonPath().getString("data.access_token");
    assertNotNull(accessToken);
    context.getHeaders().put(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);

    User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
    user.setStatus(UserStatus.ACTIVE);
    userRepository.save(user);

    currentUserId = user.getId();

    CustomerProfile profile = customerProfileRepository.findFirstByUserId(currentUserId);
    if (profile == null) {
      profile = new CustomerProfile();
      profile.setUser(user);
      profile.setTotalProperties(0);
      customerProfileRepository.save(profile);
    }
  }

  @When("they create a property with the following details:")
  public void theyCreateAPropertyWithTheFollowingDetails(DataTable dataTable) {
    final Map<String, String> row = dataTable.asMap(String.class, String.class);
    final String json =
        """
        {
          "address_line1": "%s",
          "city": "%s",
          "country": "GB",
          "postcode": "%s",
          "property_type": "%s",
          "bedrooms": %s,
          "has_electric": %s,
          "has_gas_supply": %s,
          "compliance_status": "PENDING"
        }
        """
            .formatted(
                row.get("addressLine1"),
                row.get("city"),
                row.get("postcode"),
                row.get("propertyType"),
                row.get("bedrooms"),
                row.get("hasElectric"),
                row.get("hasGasSupply"));

    var request =
        RestAssured.given()
            .contentType(MediaType.APPLICATION_JSON_VALUE)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
            .body(json);
    Response response = request.when().post("/api/v1/properties");
    context.setLastResponse(response);
  }

  @Then("the property is created successfully")
  public void thePropertyIsCreatedSuccessfully() {
    context.getLastResponse().prettyPrint();
    assertEquals(201, context.getLastResponse().statusCode());
    createdPropertyId = context.getLastResponse().jsonPath().get("data.id");
    assertNotNull(createdPropertyId);
  }

  @Then("the customer's property count is incremented")
  public void theCustomerPropertyCountIsIncremented() throws InterruptedException {
    Thread.sleep(1000); // allow async event to process
    CustomerProfile profile = customerProfileRepository.findFirstByUserId(currentUserId);
    assertNotNull(profile);
    assertEquals(1, profile.getTotalProperties());
  }

  @Then("the customer can retrieve their property from the list")
  public void theCustomerCanRetrieveTheirPropertyFromTheList() throws Exception {
    final URL url =
        URI.create("http://localhost:" + RestAssured.port + "/api/v1/properties").toURL();
    final HttpURLConnection connection = (HttpURLConnection) url.openConnection();
    connection.setRequestMethod("GET");
    connection.setRequestProperty(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
    connection.setRequestProperty(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);

    final int statusCode = connection.getResponseCode();
    assertEquals(200, statusCode);

    final String body =
        new String(connection.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    java.util.List<Object> properties = JsonPath.from(body).getList("data.content");
    assertTrue(properties.size() >= 1);
  }
}
