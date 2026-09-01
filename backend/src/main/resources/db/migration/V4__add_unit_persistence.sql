-- Flyway Migration: Add unit persistence column to calculations and history tables
ALTER TABLE calculations
    ADD COLUMN unit_dimension_string VARCHAR(255) NULL AFTER result;

ALTER TABLE history_entries
    ADD COLUMN unit_dimension_string VARCHAR(255) NULL AFTER result;
