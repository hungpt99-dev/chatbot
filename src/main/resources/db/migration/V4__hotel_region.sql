-- Hotel admin CRUD (Phase 1G): add a `region` attribute so a property can be
-- updated with name/location/region. Nullable; existing rows default to NULL.

ALTER TABLE hotel ADD COLUMN region VARCHAR(256);
