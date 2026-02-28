Feature: Commission calculation and rounding
  As the platform
  I want the commission split to be calculated correctly
  So that both the platform and engineer receive the correct amounts

  Background:
    Given the standard pricing seed data is loaded
    And a registered customer "sarah@test.com" exists with a valid token

  @regression
  Scenario: Commission is 15% of total — exact amount
    Given Sarah owns a property:
      | property_type | bedrooms | gas_appliance_count | has_gas_supply |
      | FLAT          | 2        | 2                   | true           |
    When I calculate the price for "GAS_SAFETY" with urgency "STANDARD"
    Then the price breakdown is:
      | commission_rate       | 0.15 |
      | commission_pence      | 1125 |
      | engineer_payout_pence | 6375 |

  @regression
  Scenario: Commission + payout always equals total
    Given Sarah owns a property:
      | property_type | bedrooms | gas_appliance_count | has_gas_supply |
      | DETACHED      | 4        | 4                   | true           |
    When I calculate the price for "GAS_SAFETY" with urgency "PRIORITY"
    Then commission_pence plus engineer_payout_pence equals total_price_pence

  @regression
  Scenario: Commission rounds HALF_UP — exact pence with no rounding
    Given Sarah owns a property:
      | property_type | bedrooms | gas_appliance_count | has_gas_supply |
      | FLAT          | 3        | 2                   | true           |
    When I calculate the price for "GAS_SAFETY" with urgency "STANDARD"
    Then the price breakdown is:
      | total_price_pence     | 8500 |
      | commission_pence      | 1275 |
      | engineer_payout_pence | 7225 |

  @regression
  Scenario: Commission rounds HALF_UP — non-round result
    Given Sarah owns a property:
      | property_type | bedrooms | gas_appliance_count | has_gas_supply |
      | FLAT          | 2        | 2                   | true           |
    When I calculate the price for "GAS_SAFETY" with urgency "PRIORITY"
    Then the price breakdown is:
      | total_price_pence     | 9375 |
      | commission_pence      | 1406 |
      | engineer_payout_pence | 7969 |
    And commission_pence plus engineer_payout_pence equals total_price_pence

  @wip
  Scenario: Commission rate is driven by application config
    Given the commission rate is configured as 0.20
    And Sarah owns a property:
      | property_type | bedrooms | gas_appliance_count | has_gas_supply |
      | FLAT          | 2        | 2                   | true           |
    When I calculate the price for "GAS_SAFETY" with urgency "STANDARD"
    Then the price breakdown is:
      | commission_rate       | 0.20 |
      | commission_pence      | 1500 |
      | engineer_payout_pence | 6000 |
