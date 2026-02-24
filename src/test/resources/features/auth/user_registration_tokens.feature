Feature: User registration token issuance
  As a newly registered user
  I want to receive valid access and refresh tokens on registration
  So that I am immediately authenticated without a separate login step

  Background:
    Given the registration service is available
    And the email service is stubbed to accept all sends

  Rule: Access token must be a correctly structured HS512-signed JWT

    @contract
    Scenario: Access token is a valid HS512-signed JWT with correct claims
      When a CUSTOMER registers with email "jane@example.com"
      Then the access_token is a valid JWT
      And the JWT algorithm is "HS512"
      And the JWT claim "sub" is the user's UUID
      And the JWT claim "email" is "jane@example.com"
      And the JWT claim "role" is "CUSTOMER"
      And the JWT claim "status" is "PENDING_VERIFICATION"
      And the JWT "exp" is approximately 15 minutes from now
      And the JWT contains a unique "jti" UUID

    @contract
    Scenario: Two distinct users receive different jti values
      When CUSTOMER A registers
      And CUSTOMER B registers
      Then CUSTOMER A's access token jti differs from CUSTOMER B's

  Rule: Refresh token must be an opaque token stored securely

    @contract
    Scenario: Refresh token is an opaque 64-char hex string stored as SHA-256 hash
      When a CUSTOMER registers
      Then the refresh_token length is 64
      And the refresh_token matches the pattern [0-9a-f]{64}
      And the SHA-256 hash of the refresh_token is stored in the database
      And the raw refresh_token is NOT stored in the database
      And the refresh token expiry is 30 days from now

    @contract
    Scenario: A new token family is created on registration
      When a CUSTOMER registers
      Then the refresh token record has a non-null family_id UUID

