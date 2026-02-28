Feature: Pricing cache behaviour and eviction
  As the system
  I want pricing calculations to be cached
  So that repeated lookups do not hit the database on every request

  Background:
    Given the standard pricing seed data is loaded
    And a registered customer "sarah@test.com" exists with a valid token
    And a registered admin "admin@certifynow.com" exists with a valid token

  # ── Cache eviction on admin writes ──

  @regression
  Scenario: Admin base price update evicts cache and new price is returned
    Given Sarah owns a property:
      | property_type | bedrooms | gas_appliance_count | has_gas_supply |
      | FLAT          | 2        | 2                   | true           |
    When I calculate the price for "GAS_SAFETY" with urgency "STANDARD"
    Then the total_price_pence is 7500
    When the admin updates the GAS_SAFETY base price to 9000
    And I calculate the price for "GAS_SAFETY" with urgency "STANDARD"
    Then the total_price_pence is 9000

  @regression
  Scenario: Admin adds a modifier — evicts cache and modifier appears in next calculation
    Given Sarah owns a property:
      | property_type | bedrooms | gas_appliance_count | has_gas_supply |
      | FLAT          | 3        | 2                   | true           |
    When the admin adds a BEDROOMS modifier for 6-7 bedrooms at 2500 pence to GAS_SAFETY
    And I calculate the price for "GAS_SAFETY" with urgency "STANDARD"
    Then the breakdown includes a modifier "BEDROOMS" of 1000 pence

  @regression
  Scenario: Admin removes a modifier — evicts cache and modifier disappears
    Given Sarah owns a property:
      | property_type | bedrooms | gas_appliance_count | has_gas_supply |
      | FLAT          | 3        | 2                   | true           |
    When I calculate the price for "GAS_SAFETY" with urgency "STANDARD"
    Then the breakdown includes a modifier "BEDROOMS" of 1000 pence
    When the admin removes the BEDROOMS 3-4 modifier from the GAS_SAFETY rule
    And I calculate the price for "GAS_SAFETY" with urgency "STANDARD"
    Then the breakdown does not include a modifier containing "BEDROOMS"

  @regression
  Scenario: Admin urgency multiplier update evicts cache and new multiplier is used
    Given Sarah owns a property:
      | property_type | bedrooms | gas_appliance_count | has_gas_supply |
      | FLAT          | 2        | 2                   | true           |
    When I calculate the price for "GAS_SAFETY" with urgency "PRIORITY"
    Then the urgency_modifier_pence is 1875
    When the admin updates the PRIORITY multiplier to 1.500
    And I calculate the price for "GAS_SAFETY" with urgency "PRIORITY"
    Then the urgency_modifier_pence is 3750

  @wip
  Scenario: Cache entries expire after 1 hour TTL
    Given Sarah owns a property:
      | property_type | bedrooms | gas_appliance_count | has_gas_supply |
      | FLAT          | 2        | 2                   | true           |
    When I calculate the price for "GAS_SAFETY" with urgency "STANDARD"
    And 1 hour passes
    And the GAS_SAFETY base price has been updated to 8000 directly in the database
    And I calculate the price for "GAS_SAFETY" with urgency "STANDARD"
    Then the total_price_pence is 8000
