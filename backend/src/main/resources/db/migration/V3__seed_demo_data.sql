-- Demo data so a fresh install has something to look at immediately. Everything here is
-- local (user_id IS NULL) and completely safe to edit or delete from the UI - it is not
-- special in any way to the application code.

INSERT INTO workspaces (id, user_id, name, description, is_shared) VALUES
(1, NULL, 'Getting Started', 'A few examples to show what CalcForge can do - feel free to edit or delete these.', FALSE);

INSERT INTO variables (workspace_id, name, value, unit, description) VALUES
(1, 'rate', 4.75, '%', 'Sample annual mortgage interest rate'),
(1, 'principal', 350000, 'USD', 'Sample loan principal'),
(1, 'months', 360, NULL, 'Sample loan term in months (30 years)'),
(1, 'radius', 5, 'm', 'Sample circle radius'),
(1, 'tax_rate', 8.25, '%', 'Sample sales tax rate');

INSERT INTO formulas (workspace_id, name, expression, description) VALUES
(1, 'circle_area', 'pi * radius^2', 'Area of a circle given the workspace radius variable'),
(1, 'circle_circumference', '2 * pi * radius', 'Circumference of a circle given the workspace radius variable'),
(1, 'monthly_loan_payment',
    '(principal * (rate / 100 / 12)) / (1 - (1 + rate / 100 / 12)^(-months))',
    'Standard amortizing monthly payment (rate is annual percent, months is the loan term)');

INSERT INTO calculations (workspace_id, label, expression, result, position_index) VALUES
(1, 'Circle area (r = 5m)', 'pi * radius^2', '78.53981633974483096', 0),
(1, 'Monthly mortgage payment', '(principal * (rate / 100 / 12)) / (1 - (1 + rate / 100 / 12)^(-months))', '1825.69', 1),
(1, 'Restaurant tip (18% of $86.40)', '86.40 * 18%', '15.552', 2);

INSERT INTO scenarios (workspace_id, name, variables_json) VALUES
(1, 'If rates rise to 6%', JSON_OBJECT('rate', 6.0)),
(1, 'If rates drop to 3.5%', JSON_OBJECT('rate', 3.5));

INSERT INTO history_entries (user_id, workspace_id, expression, result, tags) VALUES
(NULL, NULL, '12 * (34 + 7)', '492', 'practice'),
(NULL, NULL, 'sqrt(2)', '1.4142135623730951', NULL),
(NULL, NULL, 'sin(30)', '0.5', 'trig'),
(NULL, NULL, '240 * 15%', '36', 'tip');
