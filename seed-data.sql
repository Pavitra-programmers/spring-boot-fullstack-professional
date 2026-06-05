-- Seed data for local testing
-- Run this AFTER the app has started at least once (Hibernate creates the tables via ddl-auto=update)
-- Run in Supabase SQL Editor or: psql $DB_URL -f seed-data.sql

-- Sites
INSERT INTO sites (name, location, active) VALUES
  ('Bandra-Worli Sea Link', 'Bandra, Mumbai', true),
  ('Metro Phase 3', 'Andheri, Mumbai', true),
  ('IT Park Block C', 'Whitefield, Bangalore', true)
ON CONFLICT DO NOTHING;

-- Workers
INSERT INTO workers (name, phone, designation, daily_wage, active) VALUES
  ('Raju Sharma',    '9876543210', 'MASON',        600.00, true),
  ('Suresh Yadav',   '9876543211', 'CARPENTER',    700.00, true),
  ('Mohan Verma',    '9876543212', 'ELECTRICIAN',  800.00, true),
  ('Ramesh Patel',   '9876543213', 'PLUMBER',      750.00, true),
  ('Vikram Singh',   '9876543214', 'FOREMAN',      900.00, true),
  ('Arjun Das',      '9876543215', 'LABORER',      500.00, true)
ON CONFLICT DO NOTHING;
