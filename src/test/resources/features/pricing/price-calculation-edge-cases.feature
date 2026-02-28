Feature: Pricing edge cases and error handling
  As a developer
  I want the pricing engine to handle missing data and invalid requests gracefully
  So that the system never crashes or returns incorrect prices

  Background:
    Given the standard pricing seed data is loaded
    And a registered customer "sarah@test.com" exists with a valid token

  # ── Null property fields ──

  @regression
  Scenario: Property with null bedrooms — bedroom modifiers skipped silently
    Given Sarah owns a property:
      | property_type | bedrooms | gas_appliance_count | has_gas_supply |
      | FLAT          | null     | 2                   | true           |
    When I calculate the price for "GAS_SAFETY" with urgency "STANDARD"
    Then the response status is 200
    And the price breakdown has property_modifier_pence of 0
    And the breakdown does not include a modifier containing "BEDROOMS"

  @regression
  Scenario: Property with null floor area — floor area modifiers skipped silently
    Given Sarah owns a property:
      | property_type | bedrooms | floor_area_sqm | has_gas_supply |
      | FLAT          | 2        | null           | true           |
    When I calculate the price for "EPC" with urgency "STANDARD"
    Then the response status is 200
    And the breakdown does not include a modifier containing "FLOOR_AREA"

  @regression
  Scenario: Property with null gas appliance count — appliance modifiers skipped silently
    Given Sarah owns a property:
      | property_type | bedrooms | gas_appliance_count | has_gas_supply |
      | FLAT          | 2        | null                | true           |
    When I calculate the price for "GAS_SAFETY" with urgency "STANDARD"
    Then the response status is 200
    And the breakdown does not include a modifier containing "APPLIANCES"

  @regression
  Scenario: All nullable fields are null — only base price applies
    Given Sarah owns a property:
      | property_type | bedrooms | gas_appliance_count | floor_area_sqm | has_gas_supply |
      | FLAT          | null     | null                | null           | true           |
    When I calculate the price for "GAS_SAFETY" with urgency "STANDARD"
    Then the response status is 200
    And the price breakdown is:
      | base_price_pence        | 7500 |
      | property_modifier_pence | 0    |
      | total_price_pence       | 7500 |

  # ── Validation errors ──

  @regression
  Scenario: Gas safety on property with no gas supply
    Given Sarah owns a property:
      | property_type | bedrooms | has_gas_supply |
      | FLAT          | 2        | false          |
    When I calculate the price for "GAS_SAFETY" with urgency "STANDARD"
    Then the response status is 400
    And the error code is "NO_GAS_SUPPLY"

  @regression
  Scenario: Property does not exist
    When I calculate the price for property "00000000-0000-0000-0000-000000000000" with "GAS_SAFETY" and "STANDARD"
    Then the response status is 404

  @regression
  Scenario: Invalid certificate type
    Given Sarah owns a valid property
    When I calculate the price with certificate_type "INVALID_TYPE"
    Then the response status is 400

  @regression
  Scenario: Invalid urgency value
    Given Sarah owns a valid property
    When I calculate the price with urgency "VERY_FAST"
    Then the response status is 400

  @regression
  Scenario: Missing certificate_type query parameter
    Given Sarah owns a valid property
    When I call GET /api/v1/pricing/calculate without the certificate_type parameter
    Then the response status is 400

  @regression
  Scenario: Missing urgency query parameter
    Given Sarah owns a valid property
    When I call GET /api/v1/pricing/calculate without the urgency parameter
    Then the response status is 400

  @regression
  Scenario: Missing property_id query parameter
    When I call GET /api/v1/pricing/calculate without the property_id parameter
    Then the response status is 400

  # ── No active pricing rule ──

  @regression
  Scenario: All pricing rules for cert type are deactivated
    Given all pricing rules for "GAS_SAFETY" are deactivated
    And Sarah owns a valid property with gas supply
    When I calculate the price for "GAS_SAFETY" with urgency "STANDARD"
    Then the response status is 400
    And the error code is "NO_PRICING_RULE"

  @regression
  Scenario: Rule with future effective_from is not used
    Given the only GAS_SAFETY rule has effective_from of tomorrow
    And Sarah owns a valid property with gas supply
    When I calculate the price for "GAS_SAFETY" with urgency "STANDARD"
    Then the response status is 400
    And the error code is "NO_PRICING_RULE"

  @regression
  Scenario: Rule with past effective_to is not used
    Given the only GAS_SAFETY rule has effective_to of yesterday
    And Sarah owns a valid property with gas supply
    When I calculate the price for "GAS_SAFETY" with urgency "STANDARD"
    Then the response status is 400
    And the error code is "NO_PRICING_RULE"

  # ── Bracket boundary values ──

  @regression
  Scenario: Floor area at exact lower boundary — 80 sqm matches 80-120 not 50-80
    Given Sarah owns a property:
      | property_type | bedrooms | floor_area_sqm | has_gas_supply |
      | FLAT          | 2        | 80.00          | true           |
    When I calculate the price for "EPC" with urgency "STANDARD"
    Then the breakdown includes a modifier "FLOOR_AREA" of 1500 pence

  @regression
  Scenario: Floor area just below boundary — 79.99 sqm matches 50-80 bracket
    Given Sarah owns a property:
      | property_type | bedrooms | floor_area_sqm | has_gas_supply |
      | FLAT          | 2        | 79.99          | true           |
    When I calculate the price for "EPC" with urgency "STANDARD"
    Then the breakdown includes a modifier "FLOOR_AREA" of 500 pence

  # ── Large property max brackets ──

  @regression
  Scenario: Very large HMO — max brackets, EMERGENCY urgency
    Given Sarah owns a property:
      | property_type | bedrooms | gas_appliance_count | has_gas_supply |
      | HMO           | 10       | 8                   | true           |
    When I calculate the price for "GAS_SAFETY" with urgency "EMERGENCY"
    Then the price breakdown is:
      | base_price_pence        | 7500  |
      | property_modifier_pence | 9000  |
      | urgency_modifier_pence  | 8250  |
      | total_price_pence       | 24750 |
