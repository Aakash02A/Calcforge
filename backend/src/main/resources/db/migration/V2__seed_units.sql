-- Offline unit conversion database. Every row's factor/offset is relative to that
-- category's single base unit (is_base_unit = TRUE), so adding a new unit to a category
-- only ever requires one new row - see UnitConversionService for the conversion math.

-- ---------------------------------------------------------------- length (base: meter)
INSERT INTO units (category, name, symbol, to_base_factor, to_base_offset, is_base_unit, sort_order) VALUES
('length', 'Millimeter', 'mm', 0.001, 0, FALSE, 1),
('length', 'Centimeter', 'cm', 0.01, 0, FALSE, 2),
('length', 'Meter', 'm', 1, 0, TRUE, 3),
('length', 'Kilometer', 'km', 1000, 0, FALSE, 4),
('length', 'Inch', 'in', 0.0254, 0, FALSE, 5),
('length', 'Foot', 'ft', 0.3048, 0, FALSE, 6),
('length', 'Yard', 'yd', 0.9144, 0, FALSE, 7),
('length', 'Mile', 'mi', 1609.344, 0, FALSE, 8),
('length', 'Nautical Mile', 'nmi', 1852, 0, FALSE, 9);

-- ---------------------------------------------------------------- mass (base: kilogram)
INSERT INTO units (category, name, symbol, to_base_factor, to_base_offset, is_base_unit, sort_order) VALUES
('mass', 'Milligram', 'mg', 0.000001, 0, FALSE, 1),
('mass', 'Gram', 'g', 0.001, 0, FALSE, 2),
('mass', 'Kilogram', 'kg', 1, 0, TRUE, 3),
('mass', 'Metric Ton', 't', 1000, 0, FALSE, 4),
('mass', 'Ounce', 'oz', 0.028349523125, 0, FALSE, 5),
('mass', 'Pound', 'lb', 0.45359237, 0, FALSE, 6),
('mass', 'Stone', 'st', 6.35029318, 0, FALSE, 7);

-- ---------------------------------------------------------------- volume (base: liter)
INSERT INTO units (category, name, symbol, to_base_factor, to_base_offset, is_base_unit, sort_order) VALUES
('volume', 'Milliliter', 'mL', 0.001, 0, FALSE, 1),
('volume', 'Liter', 'L', 1, 0, TRUE, 2),
('volume', 'Cubic Meter', 'm3', 1000, 0, FALSE, 3),
('volume', 'Teaspoon', 'tsp', 0.0049289216, 0, FALSE, 4),
('volume', 'Tablespoon', 'tbsp', 0.0147867648, 0, FALSE, 5),
('volume', 'Fluid Ounce (US)', 'fl_oz', 0.0295735295625, 0, FALSE, 6),
('volume', 'Cup (US)', 'cup', 0.2365882365, 0, FALSE, 7),
('volume', 'Pint (US)', 'pt', 0.473176473, 0, FALSE, 8),
('volume', 'Quart (US)', 'qt', 0.946352946, 0, FALSE, 9),
('volume', 'Gallon (US)', 'gal', 3.785411784, 0, FALSE, 10);

-- ---------------------------------------------------------------- area (base: square meter)
INSERT INTO units (category, name, symbol, to_base_factor, to_base_offset, is_base_unit, sort_order) VALUES
('area', 'Square Centimeter', 'cm2', 0.0001, 0, FALSE, 1),
('area', 'Square Meter', 'm2', 1, 0, TRUE, 2),
('area', 'Hectare', 'ha', 10000, 0, FALSE, 3),
('area', 'Square Kilometer', 'km2', 1000000, 0, FALSE, 4),
('area', 'Square Foot', 'ft2', 0.09290304, 0, FALSE, 5),
('area', 'Square Yard', 'yd2', 0.83612736, 0, FALSE, 6),
('area', 'Acre', 'acre', 4046.8564224, 0, FALSE, 7),
('area', 'Square Mile', 'mi2', 2589988.110336, 0, FALSE, 8);

-- ---------------------------------------------------------------- speed (base: meter/second)
INSERT INTO units (category, name, symbol, to_base_factor, to_base_offset, is_base_unit, sort_order) VALUES
('speed', 'Meter/Second', 'm/s', 1, 0, TRUE, 1),
('speed', 'Kilometer/Hour', 'km/h', 0.277777778, 0, FALSE, 2),
('speed', 'Mile/Hour', 'mph', 0.44704, 0, FALSE, 3),
('speed', 'Knot', 'kn', 0.514444444, 0, FALSE, 4),
('speed', 'Foot/Second', 'ft/s', 0.3048, 0, FALSE, 5);

-- ---------------------------------------------------------------- time (base: second)
INSERT INTO units (category, name, symbol, to_base_factor, to_base_offset, is_base_unit, sort_order) VALUES
('time', 'Millisecond', 'ms', 0.001, 0, FALSE, 1),
('time', 'Second', 's', 1, 0, TRUE, 2),
('time', 'Minute', 'min', 60, 0, FALSE, 3),
('time', 'Hour', 'h', 3600, 0, FALSE, 4),
('time', 'Day', 'd', 86400, 0, FALSE, 5),
('time', 'Week', 'wk', 604800, 0, FALSE, 6);

-- ---------------------------------------------------------------- data (base: byte)
INSERT INTO units (category, name, symbol, to_base_factor, to_base_offset, is_base_unit, sort_order) VALUES
('data', 'Bit', 'bit', 0.125, 0, FALSE, 1),
('data', 'Byte', 'B', 1, 0, TRUE, 2),
('data', 'Kilobyte', 'KB', 1000, 0, FALSE, 3),
('data', 'Megabyte', 'MB', 1000000, 0, FALSE, 4),
('data', 'Gigabyte', 'GB', 1000000000, 0, FALSE, 5),
('data', 'Terabyte', 'TB', 1000000000000, 0, FALSE, 6),
('data', 'Kibibyte', 'KiB', 1024, 0, FALSE, 7),
('data', 'Mebibyte', 'MiB', 1048576, 0, FALSE, 8),
('data', 'Gibibyte', 'GiB', 1073741824, 0, FALSE, 9);

-- ---------------------------------------------------------------- pressure (base: pascal)
INSERT INTO units (category, name, symbol, to_base_factor, to_base_offset, is_base_unit, sort_order) VALUES
('pressure', 'Pascal', 'Pa', 1, 0, TRUE, 1),
('pressure', 'Kilopascal', 'kPa', 1000, 0, FALSE, 2),
('pressure', 'Bar', 'bar', 100000, 0, FALSE, 3),
('pressure', 'Atmosphere', 'atm', 101325, 0, FALSE, 4),
('pressure', 'PSI', 'psi', 6894.757293168, 0, FALSE, 5),
('pressure', 'Torr', 'torr', 133.322368421, 0, FALSE, 6);

-- ---------------------------------------------------------------- energy (base: joule)
INSERT INTO units (category, name, symbol, to_base_factor, to_base_offset, is_base_unit, sort_order) VALUES
('energy', 'Joule', 'J', 1, 0, TRUE, 1),
('energy', 'Kilojoule', 'kJ', 1000, 0, FALSE, 2),
('energy', 'Calorie', 'cal', 4.184, 0, FALSE, 3),
('energy', 'Kilocalorie', 'kcal', 4184, 0, FALSE, 4),
('energy', 'Watt-hour', 'Wh', 3600, 0, FALSE, 5),
('energy', 'Kilowatt-hour', 'kWh', 3600000, 0, FALSE, 6),
('energy', 'BTU', 'BTU', 1055.05585262, 0, FALSE, 7);

-- ---------------------------------------------------------------- angle (base: radian)
INSERT INTO units (category, name, symbol, to_base_factor, to_base_offset, is_base_unit, sort_order) VALUES
('angle', 'Radian', 'rad', 1, 0, TRUE, 1),
('angle', 'Degree', 'deg', 0.017453292519943295, 0, FALSE, 2),
('angle', 'Gradian', 'grad', 0.015707963267948967, 0, FALSE, 3);

-- ---------------------------------------------------------------- temperature (base: kelvin, affine)
INSERT INTO units (category, name, symbol, to_base_factor, to_base_offset, is_base_unit, sort_order) VALUES
('temperature', 'Kelvin', 'K', 1, 0, TRUE, 1),
('temperature', 'Celsius', 'C', 1, 273.15, FALSE, 2),
('temperature', 'Fahrenheit', 'F', 0.5555555555555556, 255.3722222222222, FALSE, 3);

-- ---------------------------------------------------------------- currency (base: USD)
-- NOTE: these are static, illustrative approximate rates seeded for offline demo use,
-- NOT live financial data. GET /api/v1/cloud/currency/rates reports `live: false` unless
-- calcforge.cloud.live-currency-enabled is on and a real rate provider is configured -
-- the frontend must show these as approximate/stale whenever `live` is false.
INSERT INTO units (category, name, symbol, to_base_factor, to_base_offset, is_base_unit, sort_order) VALUES
('currency', 'US Dollar', 'USD', 1, 0, TRUE, 1),
('currency', 'Euro', 'EUR', 0.92, 0, FALSE, 2),
('currency', 'British Pound', 'GBP', 0.79, 0, FALSE, 3),
('currency', 'Japanese Yen', 'JPY', 151.0, 0, FALSE, 4),
('currency', 'Canadian Dollar', 'CAD', 1.37, 0, FALSE, 5),
('currency', 'Australian Dollar', 'AUD', 1.52, 0, FALSE, 6),
('currency', 'Swiss Franc', 'CHF', 0.90, 0, FALSE, 7),
('currency', 'Chinese Yuan', 'CNY', 7.24, 0, FALSE, 8),
('currency', 'Indian Rupee', 'INR', 83.4, 0, FALSE, 9),
('currency', 'Mexican Peso', 'MXN', 17.0, 0, FALSE, 10);
