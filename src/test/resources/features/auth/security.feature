Feature: Security controls around account state and privileged access
  Access should be denied when account status or verification requirements are not met.

  Scenario: Pending verification token is blocked on protected endpoints
    Given a user exists with email "pending@example.com" and password "P@ssw0rd!A" and role "ENGINEER"
    And account for email "pending@example.com" has status "PENDING_VERIFICATION"
    And I set a login request with email "pending@example.com" and password "P@ssw0rd!A"
    When I POST to "/api/v1/auth/login"
    Then the response status should be 200
    Given I use bearer token from response data field "accessToken"
    When I POST to "/api/test/requires-verified-email"
    Then the response status should be 403
    And the response error code should be "EMAIL_NOT_VERIFIED"

  Scenario: Suspended status claim is blocked in JWT filter
    Given a user exists with email "suspended-claim@example.com" and password "P@ssw0rd!A" and role "CUSTOMER"
    And I issue an access token for email "suspended-claim@example.com" with status "SUSPENDED" as "suspendedToken"
    And I use bearer token stored as "suspendedToken"
    When I POST to "/api/test/requires-verified-email"
    Then the response status should be 403
    And the response error code should be "ACCOUNT_SUSPENDED"

  Scenario: Suspended account cannot refresh
    Given a user exists with email "suspended-refresh@example.com" and password "P@ssw0rd!A" and role "CUSTOMER"
    And I set a login request with email "suspended-refresh@example.com" and password "P@ssw0rd!A"
    When I POST to "/api/v1/auth/login"
    Then the response status should be 200
    Given I save response data field "refreshToken" as "refreshToBlock"
    And account for email "suspended-refresh@example.com" has status "SUSPENDED"
    And I set a refresh request with stored token "refreshToBlock"
    When I POST to "/api/v1/auth/refresh"
    Then the response status should be 403
    And the response error code should be "ACCOUNT_SUSPENDED"

  Scenario: RequiresVerifiedEmail aspect denies unverified users and allows verified users
    Given a user exists with email "aspect-user@example.com" and password "P@ssw0rd!A" and role "CUSTOMER"
    And account for email "aspect-user@example.com" has status "ACTIVE"
    And I set a login request with email "aspect-user@example.com" and password "P@ssw0rd!A"
    When I POST to "/api/v1/auth/login"
    Then the response status should be 200
    Given I use bearer token from response data field "accessToken"
    When I POST to "/api/test/requires-verified-email"
    Then the response status should be 403
    And the response error code should be "EMAIL_NOT_VERIFIED"
    Given a verification token "aspect-token" exists for email "aspect-user@example.com" expiring in 24 hours
    And I set a verify-email request with token "aspect-token"
    When I POST to "/api/v1/auth/verify-email"
    Then the response status should be 200
    Given I set a login request with email "aspect-user@example.com" and password "P@ssw0rd!A"
    When I POST to "/api/v1/auth/login"
    Then the response status should be 200
    Given I use bearer token from response data field "accessToken"
    When I POST to "/api/test/requires-verified-email"
    Then the response status should be 200
    And the response field "result" should equal "verified-action-ok"
