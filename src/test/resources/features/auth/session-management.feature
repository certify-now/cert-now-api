Feature: Login, refresh, and logout
  Session management covers credential checks, refresh rotation, token family security, and revocation.

  Scenario: Login succeeds with valid credentials
    Given a user exists with email "login-user@example.com" and password "P@ssw0rd!A" and role "CUSTOMER"
    And I set a login request with email "login-user@example.com" and password "P@ssw0rd!A"
    When I POST to "/api/v1/auth/login"
    Then the response status should be 200
    And the response data field "accessToken" should be present
    And the response data field "refreshToken" should be present
    And the response data field "user.email" should equal "login-user@example.com"

  Scenario: Login fails with invalid credentials
    Given a user exists with email "bad-login@example.com" and password "P@ssw0rd!A" and role "CUSTOMER"
    And I set a login request with email "bad-login@example.com" and password "WrongPassword!1"
    When I POST to "/api/v1/auth/login"
    Then the response status should be 401
    And the response error code should be "INVALID_CREDENTIALS"

  Scenario: Refresh rotation preserves family and detects token reuse
    Given a user exists with email "rotate@example.com" and password "P@ssw0rd!A" and role "CUSTOMER"
    And I set a login request with email "rotate@example.com" and password "P@ssw0rd!A"
    When I POST to "/api/v1/auth/login"
    Then the response status should be 200
    Given I save response data field "refreshToken" as "tokenA"
    And I set a refresh request with stored token "tokenA"
    When I POST to "/api/v1/auth/refresh"
    Then the response status should be 200
    Given I save response data field "refreshToken" as "tokenB"
    Then stored refresh tokens "tokenA" and "tokenB" should share the same family
    And stored refresh token "tokenA" should be revoked
    And stored refresh token "tokenB" should not be revoked
    Given I set a refresh request with stored token "tokenA"
    When I POST to "/api/v1/auth/refresh"
    Then the response status should be 403
    And the response error code should be "TOKEN_REUSE_DETECTED"
    Given I set a refresh request with stored token "tokenB"
    When I POST to "/api/v1/auth/refresh"
    Then the response status should be 403
    And the response error code should be "TOKEN_REUSE_DETECTED"

  Scenario: Logout revokes refresh token and deny-lists access token
    Given a user exists with email "logout@example.com" and password "P@ssw0rd!A" and role "CUSTOMER"
    And I set a login request with email "logout@example.com" and password "P@ssw0rd!A"
    When I POST to "/api/v1/auth/login"
    Then the response status should be 200
    Given I save response data field "refreshToken" as "logoutRefresh"
    And I use bearer token from response data field "accessToken"
    And I set a logout request with stored token "logoutRefresh"
    When I POST to "/api/v1/auth/logout"
    Then the response status should be 204
    And stored refresh token "logoutRefresh" should be revoked
    When I POST to "/api/v1/test-protected/privileged"
    Then the response status should be 401
    And the response error code should be "TOKEN_REVOKED"
