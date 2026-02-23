Feature: User registration input validation
  As the system
  I want to reject invalid registration requests
  So that only well-formed data enters the system

  Background:
    Given the registration service is available

  Rule: Email must be a valid format

    Scenario Outline: Invalid email formats are rejected
      When I POST to "/api/v1/auth/register" with email "<email>", password "Password1!", full_name "Jane Smith", role "CUSTOMER"
      Then the response status is 400
      And the error references the "email" field

      Examples:
        | email           |
        | notanemail      |
        | missing@        |
        | @nodomain.com   |
        | jane @email.com |
        |                 |

  Rule: Password must meet complexity requirements

    Scenario Outline: Weak passwords are rejected
      When I POST to "/api/v1/auth/register" with email "jane@example.com", password "<password>", full_name "Jane Smith", role "CUSTOMER"
      Then the response status is 400
      And the error references the "password" field

      # The 'reason' column is documentation-only and is not used in step definitions
      Examples:
        | password   | reason              |
        | short1!    | less than 8 chars   |
        | password1! | no uppercase        |
        | PASSWORD1! | no lowercase        |
        | Password!  | no digit            |
        | Password1  | no special char     |
        |            | blank               |

  Rule: Full name must be between 2 and 100 characters

    Scenario Outline: Invalid full_name values are rejected
      When I POST to "/api/v1/auth/register" with full_name "<name>", email "jane@example.com", password "Password1!", role "CUSTOMER"
      Then the response status is 400
      And the error references the "full_name" field

      # The 'reason' column is documentation-only and is not used in step definitions
      Examples:
        | name   | reason             |
        | A      | too short (1 char) |
        |        | blank              |

    Scenario: A full_name of exactly 2 characters is accepted
      When a customer registers with full_name "AB"
      Then the response status is 201

    Scenario: A full_name exceeding 100 characters is rejected
      Given a full_name of 101 characters
      When I POST to "/api/v1/auth/register" with that full_name
      Then the response status is 400
      And the error references the "full_name" field

  Rule: Phone number must be a valid UK format when provided

    Scenario Outline: Invalid UK phone numbers are rejected
      When I POST to "/api/v1/auth/register" with phone "<phone>", email "jane@example.com", password "Password1!", full_name "Jane Smith", role "CUSTOMER"
      Then the response status is 400
      And the error references the "phone" field

      # The 'reason' column is documentation-only and is not used in step definitions
      Examples:
        | phone            | reason                     |
        | 07911123456      | missing +44 prefix         |
        | +14155552671     | non-UK country code        |
        | +44791112345     | too short                  |
        | notaphone        | not numeric                |
        | +44 7911 123456  | spaces not allowed         |

  Rule: Role must be a valid registrable value

    Scenario Outline: Required fields cannot be omitted
      When a customer attempts to register without the "<field>" field
      Then the response status is 400
      And the error references the "<field>" field

      Examples:
        | field     |
        | email     |
        | password  |
        | full_name |
        | role      |

    Scenario: An unknown role value is rejected
      When I POST to "/api/v1/auth/register" with role "SUPERUSER"
      Then the response status is 400

    Scenario: An ADMIN role cannot be self-registered
      When a customer attempts to register with role "ADMIN"
      Then the response status is 400
      And the error references the "role" field

