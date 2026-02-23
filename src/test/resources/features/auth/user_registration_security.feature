Feature: User registration security
  As the system
  I want to prevent user enumeration and ensure safe handling of duplicate registrations
  So that attackers cannot discover which email addresses or phone numbers are already registered

  Background:
    Given the registration service is available

  Rule: Duplicate email registrations must not leak existence information

    @security @regression
    Scenario: Duplicate email returns 201 — email enumeration is prevented
      Given a CUSTOMER is already registered with email "existing@example.com"
      When a customer registers with email "existing@example.com"
      Then the response status is 201
      And the response shape is identical to a real registration response
      And no new user is created in the database
      And a security notification email is sent to "existing@example.com"
      And a DuplicateRegistrationAttemptEvent is published

    @security
    Scenario: Duplicate email check is case-insensitive
      Given a CUSTOMER is already registered with email "Existing@Example.com"
      When a customer registers with email "existing@example.com"
      Then the response status is 201
      And no new user is created in the database
      And a security notification email is sent to "Existing@Example.com"

    @security
    Scenario: Duplicate response tokens are not functional
      Given a CUSTOMER is already registered with email "existing@example.com"
      When a customer registers with email "existing@example.com"
      Then the response status is 201
      And the returned access_token does not correspond to any real user in the database
      And the returned refresh_token hash is not present in the database

    @security @manual
    # Note: validated manually or in a performance test suite; CI timing is unreliable
    Scenario: Duplicate and fresh registration response times are indistinguishable
      Given a CUSTOMER is already registered with email "existing@example.com"
      When I measure response time for a fresh registration
      And I measure response time for the duplicate registration
      Then both response times differ by less than 500 milliseconds

  Rule: Duplicate phone registrations must not leak existence information

    @security
    Scenario: Duplicate phone returns 201 — phone enumeration is prevented
      Given a CUSTOMER is already registered with phone "+447911000001"
      When a customer registers with email "newuser@example.com" and phone "+447911000001"
      Then the response status is 201
      And no new user is created in the database
      And a DuplicateRegistrationAttemptEvent is published

  Rule: Passwords must never be stored in plaintext

    @security
    Scenario: Password is stored as a bcrypt hash — never plaintext
      When a CUSTOMER registers with password "Password1!"
      Then the stored password_hash starts with "$2a$" or "$2b$"
      And the stored value does not contain "Password1!"

  Rule: Concurrent duplicate registrations are handled safely

    @regression
    Scenario: Concurrent registrations with the same email — only one user is created
      When 5 concurrent POST requests arrive at "/api/v1/auth/register" with email "race@example.com"
      Then exactly 1 user record exists in the database with that email
      And exactly 1 response contains a real token pair
      And all other responses are silent 201s with non-functional tokens
      And a DuplicateRegistrationAttemptEvent is published for each duplicate

    @regression
    Scenario: Concurrent registrations with the same phone — only one user is created
      When 3 concurrent POST requests arrive with phone "+447911999999"
      Then exactly 1 user record exists in the database with that phone

