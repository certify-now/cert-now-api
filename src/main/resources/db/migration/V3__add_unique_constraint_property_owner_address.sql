-- Prevent a user from registering the same property more than once.
-- Normalise postcode to uppercase and trim whitespace before comparing
-- (handled at service layer too, but enforced here as ground truth).
CREATE UNIQUE INDEX IF NOT EXISTS uq_property_owner_address_postcode
    ON property (owner_id, lower(trim(address_line1)), upper(replace(postcode, ' ', '')))
    WHERE deleted_at IS NULL;
