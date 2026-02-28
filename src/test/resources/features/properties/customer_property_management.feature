Feature: Customer property management
  As a customer
  I want to add, edit, list, view, and soft delete my properties
  So that ownership, validation, and side effects are enforced correctly

  Background:
    Given the registration service is available
    And the email service is stubbed to accept all sends
    And an active customer is authenticated

  Scenario: Customer creates a property
    When the active customer creates a property with valid data
    Then the API response status is 201
    And the created property is linked to the active customer
    And the created property is marked as active
    And the response includes request_id

  Scenario: Customer lists only their own properties
    Given the active customer has created a property
    And a second active customer is authenticated
    When the active customer lists their properties
    Then the API response status is 200
    And the property list contains only the active customer's properties
    And the property list does not contain properties owned by other customers

  Scenario: Customer gets property detail
    Given the active customer has created a property
    When the active customer requests property detail for that property
    Then the API response status is 200
    And the property detail matches the active customer's property
    And the property owner is the active customer

  Scenario: Customer updates property
    Given the active customer has created a property
    When the active customer updates that property with valid data
    Then the API response status is 200
    And the property update is persisted in the database
    And the property remains linked to the active customer

  Scenario: Customer soft deletes property
    Given the active customer has created a property
    When the active customer soft deletes that property
    Then the API response status is 204
    And the property is soft deleted in the database
    And the property no longer appears in the active customer's property list

  Scenario: Ownership - another customer cannot view property detail
    Given the active customer has created a property
    And a second active customer is authenticated
    When the second customer requests property detail for another customer's property
    Then the API response status is 403
    And the response has an access denied error

  Scenario: Ownership - another customer cannot update property
    Given the active customer has created a property
    And a second active customer is authenticated
    When the second customer updates another user's property
    Then the API response status is 403
    And the response has an access denied error

  Scenario: Ownership - another customer cannot soft delete property
    Given the active customer has created a property
    And a second active customer is authenticated
    When the second customer soft deletes another customer's property
    Then the API response status is 403
    And the response has an access denied error

  Scenario: Property created event increments customer profile total properties
    Given the active customer's total property count baseline is recorded
    When the active customer creates a property with valid data
    Then the API response status is 201
    And the customer total properties count increments by one

  Scenario: Validation rejects invalid UK postcode
    When the active customer creates a property with an invalid UK postcode
    Then the API response status is 422
    And the validation error includes field "postcode"

  Scenario: Validation rejects invalid property type
    When the active customer creates a property with an invalid property type
    Then the API response status is 422
    And the validation error includes field "propertyType"

  Scenario: Validation rejects negative bedrooms
    When the active customer creates a property with negative bedrooms
    Then the API response status is 422
    And the validation error includes field "bedrooms"

  Scenario Outline: Missing auth returns unauthorized for property and profile endpoints
    When an unauthenticated call is made to "<method> <endpoint>"
    Then the API response status is 401
    And the response has an unauthorized error

    Examples:
      | method | endpoint                |
      | GET    | /api/v1/users/me        |
      | PUT    | /api/v1/users/me        |
      | POST   | /api/v1/properties      |
      | GET    | /api/v1/properties      |
      | GET    | /api/v1/properties/{id} |
      | PUT    | /api/v1/properties/{id} |
      | DELETE | /api/v1/properties/{id} |
