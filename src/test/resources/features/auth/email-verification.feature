Feature: Email verification flow
  Verification tokens are single-use, time-limited, and activate pending users.

  Scenario: Verifying an engineer email activates the account
    Given a user exists with email "engineer@example.com" and password "P@ssw0rd!A" and role "ENGINEER"
    And account for email "engineer@example.com" has status "PENDING_VERIFICATION"
    And a verification token "valid-engineer-token" exists for email "engineer@example.com" expiring in 24 hours
    And I set a verify-email request with token "valid-engineer-token"
    When I POST to "/api/v1/auth/verify-email"
    Then the response status should be 200
    And user "engineer@example.com" should have emailVerified "true"
    And user "engineer@example.com" should have status "ACTIVE"

  Scenario: Used verification token is rejected
    Given a user exists with email "used-token@example.com" and password "P@ssw0rd!A" and role "CUSTOMER"
    And a used verification token "already-used-token" exists for email "used-token@example.com"
    And I set a verify-email request with token "already-used-token"
    When I POST to "/api/v1/auth/verify-email"
    Then the response status should be 400
    And the response error code should be "INVALID_TOKEN"

  Scenario: Expired verification token is rejected
    Given a user exists with email "expired-token@example.com" and password "P@ssw0rd!A" and role "CUSTOMER"
    And an expired verification token "expired-token-value" exists for email "expired-token@example.com"
    And I set a verify-email request with token "expired-token-value"
    When I POST to "/api/v1/auth/verify-email"
    Then the response status should be 400
    And the response error code should be "INVALID_TOKEN"
