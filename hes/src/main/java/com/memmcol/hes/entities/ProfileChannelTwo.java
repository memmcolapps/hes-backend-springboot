package com.memmcol.hes.entities;


import jakarta.persistence.*;
import lombok.*;
import org.hibernate.proxy.HibernateProxy;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "profile_channel_two")
@IdClass(ProfileChannelTwoId.class)  // composite PK: meterSerial + entryTimestamp
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProfileChannelTwo {

    @Id
    @Column(name = "meter_serial", nullable = false, length = 50)
    private String meterSerial;

    @Id
    @Column(name = "entry_timestamp", nullable = false, columnDefinition = "timestamp(0)")
    private LocalDateTime entryTimestamp;

    @Column(name = "model_number", nullable = false, length = 50)
    private String modelNumber;

    @Column(name = "meter_health_indicator")
    private Integer meterHealthIndicator;

    @Column(name = "active_energy_import")
    private Double activeEnergyImport;

    @Column(name = "active_energy_import_rate1")
    private Double activeEnergyImportRate1;

    @Column(name = "active_energy_import_rate2")
    private Double activeEnergyImportRate2;

    @Column(name = "active_energy_import_rate3")
    private Double activeEnergyImportRate3;

    @Column(name = "active_energy_import_rate4")
    private Double activeEnergyImportRate4;

    @Column(name = "active_energy_combined_total")
    private Double activeEnergyCombinedTotal;

    @Column(name = "active_energy_export")
    private Double activeEnergyExport;

    @Column(name = "reactive_energy_import")
    private Double reactiveEnergyImport;

    @Column(name = "reactive_energy_export")
    private Double reactiveEnergyExport;

    @Column(name = "apparent_energy_import")
    private Double apparentEnergyImport;

    @Column(name = "apparent_energy_export")
    private Double apparentEnergyExport;

    @Column(name = "received_at")
    private LocalDateTime receivedAt;

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffectiveClass = o instanceof HibernateProxy ? ((HibernateProxy) o).getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> thisEffectiveClass = this instanceof HibernateProxy ? ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (thisEffectiveClass != oEffectiveClass) return false;
        ProfileChannelTwo that = (ProfileChannelTwo) o;
        return getMeterSerial() != null && Objects.equals(getMeterSerial(), that.getMeterSerial())
                && getEntryTimestamp() != null && Objects.equals(getEntryTimestamp(), that.getEntryTimestamp());
    }

    @Override
    public final int hashCode() {
        return Objects.hash(meterSerial, entryTimestamp);
    }
}