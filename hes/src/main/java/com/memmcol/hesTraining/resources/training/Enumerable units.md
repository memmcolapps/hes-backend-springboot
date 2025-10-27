
/**
 * Enumerable units.
 */
public enum Unit {
    /**
     * No Unit.
     */
    NONE(0),
    /**
     * Year.
     */
    YEAR(1),

    /**
     * Month.
     */
    MONTH(2),

    /**
     * Week.
     */
    WEEK(3),

    /**
     * Day.
     */
    DAY(4),

    /**
     * Hour.
     */
    HOUR(5),

    /**
     * Minute.
     */
    MINUTE(6),

    /**
     * Second.
     */
    SECOND(7),

    /**
     * Phase angle degree.
     */
    PHASE_ANGLE_DEGREE(8),
    /*
     * Temperature T degree centigrade, rad*180/p.
     */
    TEMPERATURE(9),
    /*
     * Local currency.
     */
    LOCAL_CURRENCY(10),
    /*
     * Length l meter m.
     */
    LENGTH(11),
    /*
     * Speed v m/s.
     */
    SPEED(12),
    /*
     * Volume V m3.
     */
    VOLUME_CUBIC_METER(13),
    /*
     * Corrected volume m3.
     */
    CORRECTED_VOLUME(14),
    /*
     * Volume flux m3/60*60s.
     */
    VOLUME_FLUX_HOUR(15),
    /*
     * Corrected volume flux m3/60*60s.
     */
    CORRECTED_VOLUME_FLUX_HOUR(16),
    /*
     * Volume flux m3/24*60*60s.
     */
    VOLUME_FLUX_DAY(17),
    /*
     * Corrected volume flux m3/24*60*60s.
     */
    CORRECTED_VOLUME_FLUX_DAY(18),
    /*
     * Volume 10-3 m3.
     */
    VOLUME_LITER(19),
    /*
     * Mass m kilogram kg.
     */
    MASS_KG(20),
    /*
     * return "Force F newton N.
     */
    FORCE(21),
    /*
     * Energy newtonmeter J = Nm = Ws.
     */
    ENERGY(22),
    /*
     * Pressure p pascal N/m2.
     */
    PRESSURE_PASCAL(23),
    /*
     * Pressure p bar 10-5 N/m2.
     */
    PRESSURE_BAR(24),
    /*
     * Energy joule J = Nm = Ws.
     */
    ENERGY_JOULE(25),
    /*
     * Thermal power J/60*60s.
     */
    THERMAL_POWER(26),
    /*
     * Active power P watt W = J/s.
     */
    ACTIVE_POWER(27),
    /*
     * Apparent power S.
     */
    APPARENT_POWER(28),
    /*
     * Reactive power Q.
     */
    REACTIVE_POWER(29),
    /*
     * Active energy W*60*60s.
     */
    ACTIVE_ENERGY(30),
    /*
     * Apparent energy VA*60*60s.
     */
    APPARENT_ENERGY(31),
    /*
     * Reactive energy var*60*60s.
     */
    REACTIVE_ENERGY(32),
    /*
     * Current I ampere A.
     */
    CURRENT(33),
    /*
     * Electrical charge Q coulomb C = As.
     */
    ELECTRICAL_CHARGE(34),
    /*
     * Voltage.
     */
    VOLTAGE(35),
    /*
     * Electrical field strength E V/m.
     */
    ELECTRICAL_FIELD_STRENGTH(36),
    /*
     * Capacity C farad C/V = As/V.
     */
    CAPACITY(37),
    /*
     * Resistance R ohm = V/A.
     */
    RESISTANCE(38),
    /*
     * Resistivity.
     */
    RESISTIVITY(39),
    /*
     * Magnetic flux F weber Wb = Vs.
     */
    MAGNETIC_FLUX(40),
    /*
     * Induction T tesla Wb/m2.
     */
    INDUCTION(41),
    /*
     * Magnetic field strength H A/m.
     */
    MAGNETIC(42),
    /*
     * Inductivity L henry H = Wb/A.
     */
    INDUCTIVITY(43),
    /*
     * Frequency f.
     */
    FREQUENCY(44),
    /*
     * Active energy meter constant 1/Wh.
     */
    ACTIVE(45),
    /*
     * Reactive energy meter constant.
     */
    REACTIVE(46),
    /*
     * Apparent energy meter constant.
     */
    APPARENT(47),
    /*
     * V260*60s.
     */
    V260(48),
    /*
     * A260*60s.
     */
    A260(49),
    /*
     * Mass flux kg/s.
     */
    MASS_KG_PER_SECOND(50),
    /*
     * Unit is Conductance siemens 1/ohm.
     */
    CONDUCTANCE(51),
    /*
     * Temperature in Kelvin.
     */
    KELVIN(52),
    /*
     * 1/(V2h) RU2h , volt-squared hour meter constant or pulse value.
     */
    RU2H(53),
    /*
     * 1/(A2h) RI2h , ampere-squared hour meter constant or pulse value.
     */
    RI2H(54),
    /*
     * 1/m3 RV , meter constant or pulse value (volume).
     */
    CUBIC_METER_RV(55),
    /*
     * Percentage.
     */
    PERCENTAGE(56),
    /*
     * Ah ampere hours.
     */
    AMPERE_HOURS(57),
    /*
     * Wh/m3 energy per volume 3,6*103 J/m3.
     */
    ENERGY_PER_VOLUME(60),
    /*
     * J/m3 calorific value, wobbe.
     */
    WOBBE(61),
    /*
     * Mol % molar fraction of gas composition mole percent (Basic gas
     * composition unit).
     */
    MOLE_PERCENT(62),
    /*
     * g/m3 mass density, quantity of material.
     */
    MASS_DENSITY(63),
    /*
     * Dynamic viscosity pascal second (Characteristic of gas stream).
     */
    PASCAL_SECOND(64),
    /*
     * J/kg Specific energy NOTE The amount of energy per unit of mass of a
     * substance Joule / kilogram m2 . kg . s -2 / kg = m2.
     */
    JOULE_KILOGRAM(65),
    /**
     * Pressure, gram per square centimeter.
     */
    PRESSURE_GRAM_PER_SQUARE_CENTIMETER(66),
    /**
     * Pressure, atmosphere.
     */
    PRESSURE_ATMOSPHERE(67),

    /*
     * Signal strength, dB milliwatt (e.g. of GSM radio systems).
     */
    SIGNAL_STRENGTH_MILLI_WATT(70),

    /**
     * Signal strength, dB microvolt.
     */
    SIGNAL_STRENGTH_MICRO_VOLT(71),
    /**
     * Logarithmic unit that expresses the ratio between two values of a
     * physical quantity
     */
    DB(72),
    /**
     * Length in inches.
     */
    INCH(128),
    /**
     * Foot (Length).
     */
    FOOT(129),
    /**
     * Pound (mass).
     */
    POUND(130),
    /**
     * Fahrenheit
     */
    FAHRENHEIT(131),
    /**
     * Rankine
     */
    RANKINE(132),
    /**
     * Square inch.
     */
    SQUARE_INCH(133),
    /**
     * Square foot.
     */
    SQUARE_FOOT(134),
    /**
     * Acre
     */
    ACRE(135),
    /**
     * Cubic inch.
     */
    CUBIC_INCH(136),
    /**
     * Cubic foot.
     */
    CUBIC_FOOT(137),
    /**
     * Acre-foot.
     */
    ACRE_FOOT(138),
    /**
     * Gallon (imperial).
     */
    GALLON_IMPERIAL(139),
    /**
     * Gallon (US).
     */
    GALLON_US(140),
    /**
     * Pound force.
     */
    POUND_FORCE(141),
    /**
     * Pound force per square inch
     */
    POUND_FORCE_PER_SQUARE_INCH(142),
    /**
     * Pound per cubic foot.
     */
    POUND_PER_CUBIC_FOOT(143),
    /**
     * Pound per (foot second)
     */
    POUND_PER_FOOT_SECOND(144),
    /**
     * Square foot per second.
     */
    SQUARE_FOOT_PER_SECOND(145),
    /**
     * British thermal unit.
     */
    BRITISH_THERMAL_UNIT(146),
    /**
     * Therm EU.
     */
    THERM_EU(147),
    /**
     * Therm US.
     */
    THERM_US(148),
    /**
     * British thermal unit per pound.
     */
    BRITISH_THERMAL_UNIT_PER_POUND(149),
    /**
     * British thermal unit per cubic foot.
     */
    BRITISH_THERMAL_UNIT_PER_CUBIC_FOOT(150),
    /**
     * Cubic feet.
     */
    CUBIC_FEET(151),
    /**
     * Foot per second.
     */
    FOOT_PER_SECOND(152),
    /**
     * Cubic foot per second.
     */
    CUBIC_FOOT_PER_SECOND(153),
    /**
     * Cubic foot per min.
     */
    CUBIC_FOOT_PER_MIN(154),
    /**
     * Cubic foot per hour.
     */
    CUBIC_FOOT_PER_HOUR(155),
    /**
     * Cubic foot per day
     */
    CUBIC_FOOT_PER_DAY(156),
    /**
     * Acre foot per second.
     */
    ACRE_FOOT_PER_SECOND(157),
    /**
     * Acre foot per min.
     */
    ACRE_FOOT_PER_MIN(158),
    /**
     * Acre foot per hour.
     */
    ACRE_FOOT_PER_HOUR(159),
    /**
     * Acre foot per day.
     */
    ACRE_FOOT_PER_DAY(160),
    /**
     * Imperial gallon.
     */
    IMPERIAL_GALLON(161),
    /**
     * Imperial gallon per second.
     */
    IMPERIAL_GALLON_PER_SECOND(162),
    /**
     * Imperial gallon per min.
     */
    IMPERIAL_GALLON_PER_MIN(163),
    /**
     * Imperial gallon per hour.
     */
    IMPERIAL_GALLON_PER_HOUR(164),
    /**
     * Imperial gallon per day.
     */
    IMPERIAL_GALLON_PER_DAY(165),
    /**
     * US gallon.
     */
    US_GALLON(166),
    /**
     * US gallon per second.
     */
    US_GALLON_PER_SECOND(167),
    /**
     * US gallon per min.
     */
    US_GALLON_PER_MIN(168),
    /**
     * US gallon per hour.
     */
    US_GALLON_PER_HOUR(169),
    /**
     * US gallon per day.
     */
    US_GALLON_PER_DAY(170),
    /**
     * British thermal unit per second.
     */
    BRITISH_THERMAL_UNIT_PER_SECOND(171),
    /**
     * British thermal unit per minute.
     */
    BRITISH_THERMAL_UNIT_PER_MINUTE(172),
    /**
     * British thermal unit per hour.
     */
    BRITISH_THERMAL_UNIT_PER_HOUR(173),
    /**
     * British thermal unit per day.
     */
    BRITISH_THERMAL_UNIT_PER_DAY(174),
    /*
     * Other Unit.
     */
    OTHER_UNIT(254),
    /*
     * No Unit.
     */
    NO_UNIT(255);

   