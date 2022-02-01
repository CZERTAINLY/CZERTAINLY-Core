ALTER TABLE acme_profile
    RENAME COLUMN insist_terms_of_service TO require_terms_of_service;

ALTER TABLE acme_profile
    RENAME COLUMN insist_contact TO require_contact;

ALTER TABLE acme_profile
    RENAME COLUMN terms_of_service_change_approval TO disable_new_orders;
