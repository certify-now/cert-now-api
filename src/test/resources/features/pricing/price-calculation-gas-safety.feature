Feature: Gas Safety price calculation
  As a customer booking a gas safety inspection
  I want the price to reflect my property size and appliance count
  So that I pay a fair price based on the work involved

  Background:
    Given the standard pricing seed data is loaded
    And a registered customer "sarah@test.com" exists with a valid token

  # ── Baseline ──

  @regression
  Scenario: Minimum price — 1-bed flat with 1 appliance
    Given Sarah owns a property:
      | property_type | bedrooms | gas_appliance_count | has_gas_supply |
      | FLAT          | 1        | 1                   | true           |
    When I calculate the price for "GAS_SAFETY" with urgency "STANDARD"
    Then the response status is 200
    And the price breakdown is:
      | base_price_pence        | 7500 |
      | property_modifier_pence | 0    |
      | urgency_modifier_pence  | 0    |
      | total_price_pence       | 7500 |
      | commission_pence        | 1125 |
      | engineer_payout_pence   | 6375 |
    And no modifiers are listed in the breakdown

  # ── Bedroom scaling ──

  @regression
  Scenario: 3-bedroom property adds bedroom modifier
    Given Sarah owns a property:
      | property_type | bedrooms | gas_appliance_count | has_gas_supply |
      | FLAT          | 3        | 2                   | true           |
    When I calculate the price for "GAS_SAFETY" with urgency "STANDARD"
    Then the price breakdown is:
      | base_price_pence        | 7500 |
      | property_modifier_pence | 1000 |
      | total_price_pence       | 8500 |
    And the breakdown includes a modifier "BEDROOMS" of 1000 pence

  @regression
  Scenario: 4-bedroom property adds higher bedroom modifier
    Given Sarah owns a property:
      | property_type | bedrooms | gas_appliance_count | has_gas_supply |
      | FLAT          | 4        | 2                   | true           |
    When I calculate the price for "GAS_SAFETY" with urgency "STANDARD"
    Then the price breakdown has property_modifier_pence of 2000
    And the breakdown includes a modifier "BEDROOMS" of 2000 pence

  @regression
  Scenario: 6-bedroom property hits the 5+ bracket
    Given Sarah owns a property:
      | property_type | bedrooms | gas_appliance_count | has_gas_supply |
      | FLAT          | 6        | 2                   | true           |
    When I calculate the price for "GAS_SAFETY" with urgency "STANDARD"
    Then the breakdown includes a modifier "BEDROOMS" of 3000 pence

  @regression
  Scenario: 2 bedrooms — below first bracket, no bedroom modifier applied
    Given Sarah owns a property:
      | property_type | bedrooms | gas_appliance_count | has_gas_supply |
      | FLAT          | 2        | 2                   | true           |
    When I calculate the price for "GAS_SAFETY" with urgency "STANDARD"
    Then the breakdown does not include a modifier containing "BEDROOMS"

  # ── Bracket boundary values ──

  @regression
  Scenario: Bedroom count at exact lower boundary — 3 is inclusive
    Given Sarah owns a property:
      | property_type | bedrooms | gas_appliance_count | has_gas_supply |
      | FLAT          | 3        | 2                   | true           |
    When I calculate the price for "GAS_SAFETY" with urgency "STANDARD"
    Then the breakdown includes a modifier "BEDROOMS" of 1000 pence

  @regression
  Scenario: Bedroom count at upper boundary — 4 is exclusive from 3-4 bracket
    Given Sarah owns a property:
      | property_type | bedrooms | gas_appliance_count | has_gas_supply |
      | FLAT          | 4        | 2                   | true           |
    When I calculate the price for "GAS_SAFETY" with urgency "STANDARD"
    Then the breakdown includes a modifier "BEDROOMS" of 2000 pence

  # ── Appliance scaling ──

  @regression
  Scenario: 3 gas appliances adds appliance modifier
    Given Sarah owns a property:
      | property_type | bedrooms | gas_appliance_count | has_gas_supply |
      | FLAT          | 2        | 3                   | true           |
    When I calculate the price for "GAS_SAFETY" with urgency "STANDARD"
    Then the price breakdown is:
      | base_price_pence        | 7500 |
      | property_modifier_pence | 1000 |
      | total_price_pence       | 8500 |
    And the breakdown includes a modifier "APPLIANCES" of 1000 pence

  @regression
  Scenario: 5 gas appliances hits the 5+ bracket
    Given Sarah owns a property:
      | property_type | bedrooms | gas_appliance_count | has_gas_supply |
      | FLAT          | 2        | 5                   | true           |
    When I calculate the price for "GAS_SAFETY" with urgency "STANDARD"
    Then the breakdown includes a modifier "APPLIANCES" of 3000 pence

  # ── Property type modifiers ──

  @regression
  Scenario: Semi-detached house adds property type modifier
    Given Sarah owns a property:
      | property_type  | bedrooms | gas_appliance_count | has_gas_supply |
      | SEMI_DETACHED  | 2        | 2                   | true           |
    When I calculate the price for "GAS_SAFETY" with urgency "STANDARD"
    Then the breakdown includes a modifier "PROPERTY_TYPE_SEMI_DETACHED" of 500 pence

  @regression
  Scenario: Detached house adds property type modifier
    Given Sarah owns a property:
      | property_type | bedrooms | gas_appliance_count | has_gas_supply |
      | DETACHED      | 2        | 2                   | true           |
    When I calculate the price for "GAS_SAFETY" with urgency "STANDARD"
    Then the breakdown includes a modifier "PROPERTY_TYPE_DETACHED" of 1000 pence

  @regression
  Scenario: HMO adds the highest property type surcharge
    Given Sarah owns a property:
      | property_type | bedrooms | gas_appliance_count | has_gas_supply |
      | HMO           | 2        | 2                   | true           |
    When I calculate the price for "GAS_SAFETY" with urgency "STANDARD"
    Then the breakdown includes a modifier "PROPERTY_TYPE_HMO" of 3000 pence

  @regression
  Scenario: Terraced house has no property type modifier
    Given Sarah owns a property:
      | property_type | bedrooms | gas_appliance_count | has_gas_supply |
      | TERRACED      | 2        | 2                   | true           |
    When I calculate the price for "GAS_SAFETY" with urgency "STANDARD"
    Then the price breakdown has property_modifier_pence of 0

  @regression
  Scenario: Flat has no property type modifier
    Given Sarah owns a property:
      | property_type | bedrooms | gas_appliance_count | has_gas_supply |
      | FLAT          | 2        | 2                   | true           |
    When I calculate the price for "GAS_SAFETY" with urgency "STANDARD"
    Then the breakdown does not include a modifier containing "PROPERTY_TYPE"

  # ── Combined modifiers ──

  @regression
  Scenario: All modifiers stack — 4-bed detached with 4 appliances
    Given Sarah owns a property:
      | property_type | bedrooms | gas_appliance_count | has_gas_supply |
      | DETACHED      | 4        | 4                   | true           |
    When I calculate the price for "GAS_SAFETY" with urgency "STANDARD"
    Then the price breakdown is:
      | base_price_pence        | 7500  |
      | property_modifier_pence | 5000  |
      | total_price_pence       | 12500 |
    And the breakdown includes a modifier "BEDROOMS" of 2000 pence
    And the breakdown includes a modifier "APPLIANCES" of 2000 pence
    And the breakdown includes a modifier "PROPERTY_TYPE_DETACHED" of 1000 pence

  @regression
  Scenario: 3-bed detached with 3 appliances — full verified breakdown
    Given Sarah owns a property:
      | property_type | bedrooms | gas_appliance_count | has_gas_supply |
      | DETACHED      | 3        | 3                   | true           |
    When I calculate the price for "GAS_SAFETY" with urgency "STANDARD"
    Then the price breakdown is:
      | base_price_pence        | 7500  |
      | property_modifier_pence | 3000  |
      | urgency_modifier_pence  | 0     |
      | total_price_pence       | 10500 |
      | commission_pence        | 1575  |
      | engineer_payout_pence   | 8925  |
    And the breakdown includes a modifier "BEDROOMS" of 1000 pence
    And the breakdown includes a modifier "APPLIANCES" of 1000 pence
    And the breakdown includes a modifier "PROPERTY_TYPE_DETACHED" of 1000 pence

  @regression
  Scenario: Very large HMO — max brackets across bedrooms and appliances
    Given Sarah owns a property:
      | property_type | bedrooms | gas_appliance_count | has_gas_supply |
      | HMO           | 10       | 8                   | true           |
    When I calculate the price for "GAS_SAFETY" with urgency "STANDARD"
    Then the price breakdown is:
      | base_price_pence        | 7500  |
      | property_modifier_pence | 9000  |
      | total_price_pence       | 16500 |
    And the breakdown includes a modifier "BEDROOMS" of 3000 pence
    And the breakdown includes a modifier "APPLIANCES" of 3000 pence
    And the breakdown includes a modifier "PROPERTY_TYPE_HMO" of 3000 pence
