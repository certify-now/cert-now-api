Feature: Pricing endpoint access control
  As the system
  I want to enforce authentication and role checks on all pricing endpoints
  So that only authorised users can access pricing data

  Background:
    Given the standard pricing seed data is loaded

  # ── Calculate endpoint ──

  @regression
  Scenario: Unauthenticated user cannot calculate price
    When I call GET /api/v1/pricing/calculate without authentication
    Then the response status is 401

  @regression
  Scenario: Customer can calculate price for their own property
    Given a registered customer "sarah@test.com" exists with a valid token
    And Sarah owns a property:
      | property_type | bedrooms | gas_appliance_count | has_gas_supply |
      | FLAT          | 2        | 2                   | true           |
    When I calculate the price for "GAS_SAFETY" with urgency "STANDARD"
    Then the response status is 200

  @regression
  Scenario: Customer cannot calculate price for another customer's property
    Given a registered customer "sarah@test.com" exists with a valid token
    And a registered customer "bob@test.com" exists with a valid token
    And Bob owns a property
    When Sarah tries to calculate the price for Bob's property
    Then the response status is 403
    And the error code is "ACCESS_DENIED"

  @regression
  Scenario: Admin can calculate price for any customer's property
    Given a registered admin "admin@certifynow.com" exists with a valid token
    And a registered customer "bob@test.com" exists with a valid token
    And Bob owns a property
    When the admin calculates the price for Bob's property
    Then the response status is 200

  @regression
  Scenario: Engineer cannot calculate price for a property they do not own
    Given a registered customer "sarah@test.com" exists with a valid token
    And a registered engineer "mike@test.com" exists with a valid token
    And Sarah owns a property:
      | property_type | bedrooms | gas_appliance_count | has_gas_supply |
      | FLAT          | 2        | 2                   | true           |
    When Mike tries to calculate the price for Sarah's property
    Then the response status is 403
    And the error code is "ACCESS_DENIED"

  # ── Admin endpoints ──

  @regression
  Scenario Outline: Non-admin roles cannot access admin pricing endpoints
    Given a registered "<role>" user with token exists
    When that user calls "<method>" "<endpoint>"
    Then the response status is 403

    Examples:
      | role     | method | endpoint                                    |
      | CUSTOMER | GET    | /api/v1/admin/pricing/rules                 |
      | CUSTOMER | POST   | /api/v1/admin/pricing/rules                 |
      | ENGINEER | GET    | /api/v1/admin/pricing/rules                 |
      | ENGINEER | GET    | /api/v1/admin/pricing/urgency-multipliers   |

  @regression
  Scenario: Admin can access all admin pricing endpoints
    Given a registered admin "admin@certifynow.com" exists with a valid token
    When the admin calls GET /api/v1/admin/pricing/rules
    Then the response status is 200
    When the admin calls GET /api/v1/admin/pricing/urgency-multipliers
    Then the response status is 200
