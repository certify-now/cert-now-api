Feature: EICR price calculation
  As a customer booking an electrical inspection
  I want the price to reflect the property complexity
  So that the price matches the number of circuits to test

  Background:
    Given the standard pricing seed data is loaded
    And a registered customer "sarah@test.com" exists with a valid token

  # ── Baseline ──

  @regression
  Scenario: Minimum EICR — 1-bed flat
    Given Sarah owns a property:
      | property_type | bedrooms | has_gas_supply |
      | FLAT          | 1        | true           |
    When I calculate the price for "EICR" with urgency "STANDARD"
    Then the price breakdown is:
      | base_price_pence        | 15000 |
      | property_modifier_pence | 0     |
      | total_price_pence       | 15000 |
    And no modifiers are listed in the breakdown

  # ── Bedroom scaling (steep) ──

  @regression
  Scenario: 3-bedroom EICR adds bedroom modifier
    Given Sarah owns a property:
      | property_type | bedrooms | has_gas_supply |
      | FLAT          | 3        | true           |
    When I calculate the price for "EICR" with urgency "STANDARD"
    Then the breakdown includes a modifier "BEDROOMS" of 3000 pence

  @regression
  Scenario: 4-bedroom EICR — steep scaling
    Given Sarah owns a property:
      | property_type | bedrooms | has_gas_supply |
      | FLAT          | 4        | true           |
    When I calculate the price for "EICR" with urgency "STANDARD"
    Then the breakdown includes a modifier "BEDROOMS" of 6000 pence

  @regression
  Scenario: 5+ bedroom EICR — highest bracket
    Given Sarah owns a property:
      | property_type | bedrooms | has_gas_supply |
      | FLAT          | 5        | true           |
    When I calculate the price for "EICR" with urgency "STANDARD"
    Then the breakdown includes a modifier "BEDROOMS" of 10000 pence

  # ── Combined — HMO ──

  @regression
  Scenario: 5-bed HMO — bedroom and property type modifiers stack
    Given Sarah owns a property:
      | property_type | bedrooms | has_gas_supply |
      | HMO           | 6        | true           |
    When I calculate the price for "EICR" with urgency "STANDARD"
    Then the price breakdown is:
      | base_price_pence        | 15000 |
      | property_modifier_pence | 13000 |
      | total_price_pence       | 28000 |
    And the breakdown includes a modifier "BEDROOMS" of 10000 pence
    And the breakdown includes a modifier "PROPERTY_TYPE_HMO" of 3000 pence

  # ── Cross-cert guards ──

  @regression
  Scenario: Appliance modifiers do NOT apply to EICR
    Given Sarah owns a property:
      | property_type | bedrooms | gas_appliance_count | floor_area_sqm | has_gas_supply |
      | FLAT          | 2        | 5                   | 200            | true           |
    When I calculate the price for "EICR" with urgency "STANDARD"
    Then the breakdown does not include a modifier containing "APPLIANCES"
    And the breakdown does not include a modifier containing "FLOOR_AREA"
    And the price breakdown has property_modifier_pence of 0
