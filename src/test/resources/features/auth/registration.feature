Feature: Registration flow
  Registration creates accounts, profiles, consents, and initial tokens while preventing email enumeration.

  Scenario: Successful customer registration issues tokens and persists audit data
    Given I set header "X-Forwarded-For" to "203.0.113.10"
    And I set a registration request with:
      | email                 | password     | fullName     | phone          | role     |
      | customer@example.com  | P@ssw0rd!A   | Alice Customer | +447700900001 | CUSTOMER |
    When I POST to "/api/v1/auth/register"
    Then the response status should be 201
    And the response data field "accessToken" should be present
    And the response data field "refreshToken" should be present
    And the response data field "user.email" should equal "customer@example.com"
    And the response data field "user.role" should equal "CUSTOMER"
    And the response data field "user.status" should equal "ACTIVE"
    And the response data field "user.emailVerified" should equal "false"
    And the response should include a request id
    And user "customer@example.com" should have 2 consent records with ip "203.0.113.10"
    And an email verification token should be created for email "customer@example.com"
    And the created email verification token hash should be 64 hex characters for email "customer@example.com"

  Scenario: Duplicate email registration is silently handled
    Given a user exists with email "duplicate@example.com" and password "P@ssw0rd!A" and role "CUSTOMER"
    And I set a registration request with:
      | email                | password     | fullName         | phone          | role     |
      | duplicate@example.com | P@ssw0rd!A   | Duplicate Attempt | +447700900011 | CUSTOMER |
    When I POST to "/api/v1/auth/register"
    Then the response status should be 201
    And the response data field "accessToken" should be null
    And the response data field "refreshToken" should be null
    And the response data field "user" should be null
    And there should be 1 users with email "duplicate@example.com"

  Scenario: Duplicate phone registration is silently handled
    Given I set a registration request with:
      | email                   | password     | fullName    | phone          | role     |
      | first-phone@example.com | P@ssw0rd!A   | First Phone | +447700900022 | CUSTOMER |
    When I POST to "/api/v1/auth/register"
    Then the response status should be 201
    And there should be 1 users with phone "+447700900022"
    Given I set a registration request with:
      | email                     | password     | fullName     | phone          | role     |
      | second-phone@example.com  | P@ssw0rd!A   | Second Phone | +447700900022 | CUSTOMER |
    When I POST to "/api/v1/auth/register"
    Then the response status should be 201
    And the response data field "accessToken" should be null
    And there should be 1 users with phone "+447700900022"

  Scenario: Invalid registration payload returns validation errors
    Given I set JSON request body:
      """
      {
        "email": "not-an-email",
        "password": "weak",
        "fullName": "A",
        "phone": "12345",
        "role": null
      }
      """
    When I POST to "/api/v1/auth/register"
    Then the response status should be 422
    And the response error code should be "VALIDATION_ERROR"
    And the response error message should contain "validation failed"
