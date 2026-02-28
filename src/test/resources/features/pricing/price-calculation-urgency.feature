Feature: Urgency multiplier applied to all certificate types
  As a customer choosing urgency level
  I want the price to increase proportionally for faster service
  So that I understand the premium for priority and emergency bookings

  Background:
    Given the standard pricing seed data is loaded
    And a registered customer "sarah@test.com" exists with a valid token

  # ── Base urgency — 1-bed flat, no modifiers ──

  @regression
  Scenario Outline: Urgency multipliers applied to GAS_SAFETY base price
    Given Sarah owns a property:
      | property_type | bedrooms | gas_appliance_count | has_gas_supply |
      | FLAT          | 2        | 2                   | true           |
    When I calculate the price for "GAS_SAFETY" with urgency "<urgency>"
    Then the price breakdown is:
      | urgency_modifier_pence | <urgency_modifier> |
      | total_price_pence      | <total>            |

    Examples:
      | urgency   | urgency_modifier | total |
      | STANDARD  | 0                | 7500  |
      | PRIORITY  | 1875             | 9375  |
      | EMERGENCY | 3750             | 11250 |

  @regression
  Scenario Outline: Urgency multipliers applied to EPC base price
    Given Sarah owns a property:
      | property_type | bedrooms | floor_area_sqm | has_gas_supply |
      | FLAT          | 2        | 40             | true           |
    When I calculate the price for "EPC" with urgency "<urgency>"
    Then the price breakdown is:
      | urgency_modifier_pence | <urgency_modifier> |
      | total_price_pence      | <total>            |

    Examples:
      | urgency   | urgency_modifier | total |
      | STANDARD  | 0                | 7000  |
      | PRIORITY  | 1750             | 8750  |
      | EMERGENCY | 3500             | 10500 |

  @regression
  Scenario Outline: Urgency multipliers applied to EICR base price
    Given Sarah owns a property:
      | property_type | bedrooms | has_gas_supply |
      | FLAT          | 2        | true           |
    When I calculate the price for "EICR" with urgency "<urgency>"
    Then the price breakdown is:
      | urgency_modifier_pence | <urgency_modifier> |
      | total_price_pence      | <total>            |

    Examples:
      | urgency   | urgency_modifier | total |
      | STANDARD  | 0                | 15000 |
      | PRIORITY  | 3750             | 18750 |
      | EMERGENCY | 7500             | 22500 |

  # ── Urgency applies AFTER property modifiers ──

  @regression
  Scenario: Urgency multiplier applies to subtotal including modifiers
    Given Sarah owns a property:
      | property_type | bedrooms | gas_appliance_count | has_gas_supply |
      | DETACHED      | 4        | 4                   | true           |
    When I calculate the price for "GAS_SAFETY" with urgency "EMERGENCY"
    Then the price breakdown is:
      | base_price_pence        | 7500  |
      | property_modifier_pence | 5000  |
      | urgency_modifier_pence  | 6250  |
      | total_price_pence       | 18750 |

  # ── Missing multiplier fallback ──

  @regression
  Scenario: Missing active urgency multiplier defaults to 1.000
    Given the PRIORITY urgency multiplier is deactivated
    Given Sarah owns a property:
      | property_type | bedrooms | gas_appliance_count | has_gas_supply |
      | FLAT          | 2        | 2                   | true           |
    When I calculate the price for "GAS_SAFETY" with urgency "PRIORITY"
    Then the response status is 200
    And the urgency_modifier_pence is 0
    And the total_price_pence is 7500
