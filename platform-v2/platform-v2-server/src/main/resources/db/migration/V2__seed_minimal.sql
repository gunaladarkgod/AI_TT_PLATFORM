-- Minimal reference data (safe to re-run on empty DB only; Flyway tracks version)
INSERT INTO usr_role (code, name) VALUES ('ADMIN', 'Administrator'), ('USER', 'Regular user');
