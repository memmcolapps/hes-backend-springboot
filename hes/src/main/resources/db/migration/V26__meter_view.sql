CREATE OR REPLACE VIEW vw_meter_summary AS
SELECT
    vt.id AS meter_id,
    vt.org_id,
    vt.customer_id,
    vt.account_number AS meter_account_number,
    vt.meter_number,
    vt.cin AS meter_cin,
    vt.meter_class,
    vt.meter_category,
    vt.type,
    vt.old_sgc,
    vt.new_sgc,
    vt.old_krn,
    vt.new_krn,
    vt.old_tariff_index,
    vt.new_tariff_index,
    t.id AS tariff_id,
    t.name AS tariff_name,
    t.tariff_rate AS tariff_rate,
    b.name AS band_name,
    b.hour AS band_hour,
    md.ct_ratio_num AS md_ct_ratio_num,
    md.ct_ratio_denom AS md_ct_ratio_denom,
    md.volt_ratio_num AS md_volt_ratio_num,
    md.volt_ratio_denom AS md_volt_ratio_denom,
    md.multiplier AS md_multiplier,
    md.meter_rating AS md_meter_ration,
    md.initial_reading AS md_initial_reading,
    md.dial AS md_dial,
    ms.meter_model AS smart_meter_model,
    ms.protocol AS smart_meter_protocol,
    cda.debit AS debit_amount,
    cda.balance AS balance_after_adjustment,
    cda.type AS adjustment_type,
    cdp.credit AS credit_amount,
    lc.name AS liability_name,
    lc.code AS liability_code,
    CONCAT(c.firstname, ' ', c.lastname) AS customer_fullname,
    CONCAT(lo.house_no, ',', lo.street_name, ',', lo.city, ',', lo.state) AS address,
    mr.name AS manufacturer_name,
    vt.created_at,
    vt.updated_at
FROM meters vt
         LEFT JOIN customers c ON vt.customer_id = c.customer_id
         LEFT JOIN meter_assign_locations lo ON vt.id = lo.meter_id
         LEFT JOIN payment_mode pm ON vt.id = pm.meter_id
         LEFT JOIN md_meters_info md ON vt.id = md.meter_id
         LEFT JOIN smart_meter_info ms ON vt.id = ms.meter_id
         LEFT JOIN manufacturers mr ON vt.meter_manufacturer = mr.id
         LEFT JOIN tariffs t ON vt.tariff = t.id
         LEFT JOIN bands b ON t.band_id = b.id
         LEFT JOIN credit_debit_adjustment cda ON cda.meter_id = vt.id
         LEFT JOIN credit_debit_payment cdp ON cdp.credit_debit_adj_id = cda.id
         LEFT JOIN liability_cause lc ON lc.id = cda.liability_cause_id;