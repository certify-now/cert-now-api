Feature: Admin management of pricing rules and modifiers
  As an admin
  I want to create, update, and manage pricing rules and modifiers
  So that I can adjust prices without a code deploy

  Background:
    Given the standard pricing seed data is loaded
    And a registered admin "admin@certifynow.com" exists with a valid token

  # ── Read ──

  @regression
  Scenario: Admin lists all active pricing rules with modifiers
    When the admin calls GET /api/v1/admin/pricing/rules
    Then the response status is 200
    And the response contains 3 pricing rules
    And the "GAS_SAFETY" rule has base_price_pence of 7500
    And the "GAS_SAFETY" rule has at least 8 modifiers
    And the "EPC" rule has base_price_pence of 7000
    And the "EICR" rule has base_price_pence of 15000

  @regression
  Scenario: active_only=true excludes deactivated rules
    Given the EPC pricing rule is deactivated
    When the admin calls GET /api/v1/admin/pricing/rules with active_only true
    Then the response status is 200
    And the response contains 2 pricing rules

  @regression
  Scenario: active_only=false includes deactivated rules
    Given the EPC pricing rule is deactivated
    When the admin calls GET /api/v1/admin/pricing/rules with active_only false
    Then the response status is 200
    And the response contains 3 pricing rules
    And the EPC rule has is_active of false

  # ── Create ──

  @regression
  Scenario: Admin creates a national pricing rule with future effective_from
    When the admin creates a pricing rule:
      | certificate_type | region | base_price_pence | effective_from |
      | GAS_SAFETY       |        | 8500             | 2030-06-01     |
    Then the response status is 201
    And the rule has base_price_pence of 8500 and no modifiers

  @regression
  Scenario: Admin creates a regional LONDON rule
    When the admin creates a pricing rule:
      | certificate_type | region | base_price_pence | effective_from |
      | GAS_SAFETY       | LONDON | 9000             | 2030-06-01     |
    Then the response status is 201
    And the rule has region "LONDON" and base_price_pence 9000

  @regression
  Scenario: Cannot create rule with base_price_pence of 0
    When the admin creates a pricing rule with base_price_pence 0
    Then the response status is 422

  @regression
  Scenario: Cannot create rule with effective_from in the past
    When the admin creates a pricing rule with effective_from "2020-01-01"
    Then the response status is 422

  @regression
  Scenario: Cannot create duplicate rule for same type+region with overlapping dates
    Given a GAS_SAFETY rule for region LONDON already exists effective 2030-06-01 with no end date
    When the admin creates another GAS_SAFETY rule for LONDON effective 2030-06-01
    Then the response status is 409

  # ── Update ──

  @regression
  Scenario: Admin updates base price on existing rule
    When the admin updates the GAS_SAFETY national rule with base_price_pence 8000
    Then the response status is 200
    And the rule now has base_price_pence of 8000

  @regression
  Scenario: Admin sets an effective_to date to expire a rule
    When the admin updates the GAS_SAFETY rule with effective_to "2030-12-31"
    Then the response status is 200
    And the rule now has effective_to of "2030-12-31"

  # ── Modifiers ──

  @regression
  Scenario: Admin adds a new BEDROOMS modifier to a rule
    When the admin adds a modifier to the GAS_SAFETY rule:
      | modifier_type | condition_min | condition_max | modifier_pence |
      | BEDROOMS      | 7             |               | 5000           |
    Then the response status is 201
    And the GAS_SAFETY rule now has a BEDROOMS modifier for 7+ bedrooms of 5000 pence

  @regression
  Scenario: Admin adds a PROPERTY_TYPE modifier to a rule
    When the admin adds a modifier to the GAS_SAFETY rule:
      | modifier_type            | condition_min | condition_max | modifier_pence |
      | PROPERTY_TYPE_COMMERCIAL |               |               | 5000           |
    Then the response status is 201

  @regression
  Scenario: Cannot add modifier with overlapping bracket
    Given the GAS_SAFETY rule already has a BEDROOMS modifier for condition_min=3 condition_max=4
    When the admin adds a BEDROOMS modifier with condition_min=3 and condition_max=4
    Then the response status is 400
    And the error code is "INVALID_MODIFIER_OVERLAP"

  @regression
  Scenario: Cannot add modifier with invalid modifier_type
    When the admin adds a modifier with modifier_type "NONSENSE"
    Then the response status is 422

  @regression
  Scenario: Cannot add modifier with modifier_pence of 0
    When the admin adds a modifier with modifier_pence 0
    Then the response status is 422

  @regression
  Scenario: Admin removes a modifier
    Given the GAS_SAFETY rule has a BEDROOMS modifier for 3-4 bedrooms
    When the admin deletes that modifier
    Then the response status is 204
    And the GAS_SAFETY rule no longer contains a BEDROOMS modifier for 3-4 bedrooms

  @regression
  Scenario: Delete non-existent modifier returns 404
    When the admin deletes modifier "00000000-0000-0000-0000-000000000000" from the GAS_SAFETY rule
    Then the response status is 404

  # ── Price change propagation ──

  @regression
  Scenario: Price calculation reflects updated base price after cache eviction
    Given a registered customer "sarah@test.com" exists with a valid token
    And Sarah owns a property:
      | property_type | bedrooms | gas_appliance_count | has_gas_supply |
      | FLAT          | 1        | 1                   | true           |
    When I calculate the price for "GAS_SAFETY" with urgency "STANDARD"
    Then the total_price_pence is 7500
    When the admin updates the GAS_SAFETY base price to 8500
    And I calculate the price for "GAS_SAFETY" with urgency "STANDARD"
    Then the total_price_pence is 8500

  @regression
  Scenario: Price calculation reflects removed modifier after cache eviction
    Given a registered customer "sarah@test.com" exists with a valid token
    And Sarah owns a property:
      | property_type | bedrooms | gas_appliance_count | has_gas_supply |
      | FLAT          | 3        | 2                   | true           |
    When I calculate the price for "GAS_SAFETY" with urgency "STANDARD"
    Then the breakdown includes a modifier "BEDROOMS" of 1000 pence
    When the admin removes the BEDROOMS 3-4 modifier from the GAS_SAFETY rule
    And I calculate the price for "GAS_SAFETY" with urgency "STANDARD"
    Then the breakdown does not include a modifier containing "BEDROOMS"
