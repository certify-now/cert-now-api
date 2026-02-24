Feature: User registration happy path
  As a new user
  I want to register an account
  So that I am immediately signed in after registration

  Background:
    Given the registration service is available
    And the email service is stubbed to accept all sends

  Rule: A customer can register successfully

    @smoke
    Scenario: A customer registers successfully with all fields
      When I POST to "/api/v1/auth/register" with:
        | email     | jane@example.com |
        | password  | Password1!       |
        | full_name | Jane Smith       |
        | phone     | +447911123456    |
        | role      | CUSTOMER         |
      Then the response status is 201
      And the response contains a valid JWT access_token
      And the response contains an opaque refresh_token
      And token_type is "Bearer"
      And expires_in is 900
      And the user.email is "jane@example.com"
      And the user.role is "CUSTOMER"
      And the user.status is "PENDING_VERIFICATION"
      And the user.email_verified is false
      And the response contains a request_id UUID
      And a CustomerProfile exists in the database for that user

    @smoke
    Scenario: An engineer registers successfully
      When a customer registers with email "bob@example.com" and role "ENGINEER"
      Then the response status is 201
      And the user.status is "PENDING_VERIFICATION"
      And the user.role is "ENGINEER"
      And an EngineerProfile exists in the database for that user

    @regression
    Scenario: Registration without optional phone field succeeds
      When a customer registers with email "nophone@example.com" and no phone
      Then the response status is 201
      And the user is saved in the database with a null phone

    @regression
    Scenario: Two users can register with no phone — partial unique index allows multiple nulls
      Given a CUSTOMER is registered with email "user1@example.com" and no phone
      When a customer registers with email "user2@example.com" and no phone
      Then the response status is 201

    @regression
    Scenario: Registration triggers an email verification email
      When a customer registers with email "jane@example.com"
      Then a verification email is sent to "jane@example.com"
      And the user's email_verified is false

