Feature: Admin management of urgency multipliers
  As an admin
  I want to adjust urgency multipliers
  So that I can tune priority and emergency surcharges

  Background:
    Given the standard pricing seed data is loaded
    And a registered admin "admin@certifynow.com" exists with a valid token

  @regression
  Scenario: Admin lists all urgency multipliers
    When the admin calls GET /api/v1/admin/pricing/urgency-multipliers
    Then the response status is 200
    And the response contains 3 multipliers:
      | urgency   | multiplier | is_active |
      | STANDARD  | 1.000      | true      |
      | PRIORITY  | 1.250      | true      |
      | EMERGENCY | 1.500      | true      |

  @regression
  Scenario: Admin updates the PRIORITY multiplier
    When the admin updates the PRIORITY multiplier to 1.350
    Then the response status is 200
    And the multiplier for PRIORITY is now 1.350

  @regression
  Scenario: Admin updates the EMERGENCY multiplier
    When the admin updates the EMERGENCY multiplier to 2.000
    Then the response status is 200
    And the multiplier for EMERGENCY is now 2.000

  @regression
  Scenario: Updated multiplier is reflected in price calculations
    Given a registered customer "sarah@test.com" exists with a valid token
    And Sarah owns a property:
      | property_type | bedrooms | gas_appliance_count | has_gas_supply |
      | FLAT          | 2        | 2                   | true           |
    When the admin updates the PRIORITY multiplier to 1.500
    And I calculate the price for "GAS_SAFETY" with urgency "PRIORITY"
    Then the total_price_pence is 11250

  @regression
  Scenario: Cannot set multiplier below minimum 1.000
    When the admin updates the PRIORITY multiplier to 0.999
    Then the response status is 422

  @regression
  Scenario: Cannot set multiplier above maximum 3.000
    When the admin updates the EMERGENCY multiplier to 3.001
    Then the response status is 422

  @regression
  Scenario: Multiplier at exact minimum boundary is accepted
    When the admin updates the PRIORITY multiplier to 1.000
    Then the response status is 200

  @regression
  Scenario: Multiplier at exact maximum boundary is accepted
    When the admin updates the EMERGENCY multiplier to 3.000
    Then the response status is 200
