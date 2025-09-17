package com.memmcol.hes.entities;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.proxy.HibernateProxy;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "monthly_billing_profile")
@IdClass(MonthlyBillingId.class)
@Getter
@Setter
@ToString
@RequiredArgsConstructor
//@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MonthlyBillingEntity {

    @Id
    @Column(name = "meter_serial")
    private String meterSerial;

    @Id
    @Column(name = "entry_timestamp")
    private LocalDateTime entryTimestamp;

    @Column(name = "meter_model")
    private String meterModel;

    @Column(name = "received_at")
    private LocalDateTime receivedAt;

    // Billing fields
    private Double totalAbsoluteActiveEnergy;
    private Double exportActiveEnergy;
    private Double importActiveEnergy;
    private Double importReactiveEnergy;
    private Double exportReactiveEnergy;
    private Double remainingCreditAmount;
    private Double importActiveMd;

    private LocalDateTime importActiveMdTime;

    @Column(name = "t1_active_energy")
    private Double t1ActiveEnergy;

    @Column(name = "t2_active_energy")
    private Double t2ActiveEnergy;

    @Column(name = "t3_active_energy")
    private Double t3ActiveEnergy;

    @Column(name = "t4_active_energy")
    private Double t4ActiveEnergy;

    private Double totalActiveEnergy;
    private Double totalApparentEnergy;

    @Column(name = "t1_total_apparent_energy")
    private Double t1TotalApparentEnergy;

    @Column(name = "t2_total_apparent_energy")
    private Double t2TotalApparentEnergy;

    @Column(name = "t3_total_apparent_energy")
    private Double t3TotalApparentEnergy;

    @Column(name = "t4_total_apparent_energy")
    private Double t4TotalApparentEnergy;

    private Double activeMaximumDemand;
    private Double totalApparentDemand;

    private LocalDateTime totalApparentDemandTime;

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffectiveClass = o instanceof HibernateProxy ? ((HibernateProxy) o).getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> thisEffectiveClass = this instanceof HibernateProxy ? ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (thisEffectiveClass != oEffectiveClass) return false;
        MonthlyBillingEntity that = (MonthlyBillingEntity) o;
        return getMeterSerial() != null && Objects.equals(getMeterSerial(), that.getMeterSerial())
                && getEntryTimestamp() != null && Objects.equals(getEntryTimestamp(), that.getEntryTimestamp());
    }

    @Override
    public final int hashCode() {
        return Objects.hash(meterSerial, entryTimestamp);
    }
}