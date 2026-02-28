Feature: EPC price calculation
  As a customer booking an EPC assessment
  I want the price to reflect my property size and floor area
  So that the assessment cost matches the work required

  Background:
    Given the standard pricing seed data is loaded
    And a registered customer "sarah@test.com" exists with a valid token

  # ── Baseline ──

  @regression
  Scenario: Minimum EPC — 1-bed flat, small floor area
    Given Sarah owns a property:
      | property_type | bedrooms | floor_area_sqm | has_gas_supply |
      | FLAT          | 1        | 40             | true           |
    When I calculate the price for "EPC" with urgency "STANDARD"
    Then the price breakdown is:
      | base_price_pence        | 7000 |
      | property_modifier_pence | 0    |
      | total_price_pence       | 7000 |
    And no modifiers are listed in the breakdown

  # ── Bedroom scaling ──

  @regression
  Scenario: 3-bedroom property adds bedroom modifier
    Given Sarah owns a property:
      | property_type | bedrooms | floor_area_sqm | has_gas_supply |
      | FLAT          | 3        | 40             | true           |
    When I calculate the price for "EPC" with urgency "STANDARD"
    Then the breakdown includes a modifier "BEDROOMS" of 1500 pence

  @regression
  Scenario: 4-bedroom property adds higher bedroom modifier
    Given Sarah owns a property:
      | property_type | bedrooms | floor_area_sqm | has_gas_supply |
      | FLAT          | 4        | 40             | true           |
    When I calculate the price for "EPC" with urgency "STANDARD"
    Then the breakdown includes a modifier "BEDROOMS" of 2500 pence

  @regression
  Scenario: 5+ bedroom property hits top bracket
    Given Sarah owns a property:
      | property_type | bedrooms | floor_area_sqm | has_gas_supply |
      | FLAT          | 5        | 40             | true           |
    When I calculate the price for "EPC" with urgency "STANDARD"
    Then the breakdown includes a modifier "BEDROOMS" of 4000 pence

  # ── Floor area scaling ──

  @regression
  Scenario: Floor area 50-80 sqm bracket
    Given Sarah owns a property:
      | property_type | bedrooms | floor_area_sqm | has_gas_supply |
      | FLAT          | 2        | 65             | true           |
    When I calculate the price for "EPC" with urgency "STANDARD"
    Then the breakdown includes a modifier "FLOOR_AREA" of 500 pence

  @regression
  Scenario: Floor area at exact lower boundary — 80 sqm matches 80-120 bracket
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

  @regression
  Scenario: Floor area 80-120 sqm bracket
    Given Sarah owns a property:
      | property_type | bedrooms | floor_area_sqm | has_gas_supply |
      | FLAT          | 2        | 95             | true           |
    When I calculate the price for "EPC" with urgency "STANDARD"
    Then the breakdown includes a modifier "FLOOR_AREA" of 1500 pence

  @regression
  Scenario: Floor area 120-180 sqm bracket
    Given Sarah owns a property:
      | property_type | bedrooms | floor_area_sqm | has_gas_supply |
      | FLAT          | 2        | 150            | true           |
    When I calculate the price for "EPC" with urgency "STANDARD"
    Then the breakdown includes a modifier "FLOOR_AREA" of 2500 pence

  @regression
  Scenario: Floor area 180+ sqm bracket
    Given Sarah owns a property:
      | property_type | bedrooms | floor_area_sqm | has_gas_supply |
      | DETACHED      | 5        | 220            | true           |
    When I calculate the price for "EPC" with urgency "STANDARD"
    Then the breakdown includes a modifier "FLOOR_AREA" of 4000 pence
    And the breakdown includes a modifier "BEDROOMS" of 4000 pence
    And the breakdown includes a modifier "PROPERTY_TYPE_DETACHED" of 1000 pence
    And the price breakdown has property_modifier_pence of 9000

  # ── Cross-cert guard ──

  @regression
  Scenario: Appliance modifiers do NOT apply to EPC
    Given Sarah owns a property:
      | property_type | bedrooms | gas_appliance_count | floor_area_sqm | has_gas_supply |
      | FLAT          | 2        | 5                   | 40             | true           |
    When I calculate the price for "EPC" with urgency "STANDARD"
    Then the breakdown does not include a modifier containing "APPLIANCES"
    And the price breakdown has property_modifier_pence of 0
