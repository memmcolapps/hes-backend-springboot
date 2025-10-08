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

    @Column(name = "total_import_active_energy")
    private Double totalImportActiveEnergy;

    @Column(name = "total_export_active_energy")
    private Double totalExportActiveEnergy;

//    @Column(name = "received_at", insertable = false, updatable = false,
//            columnDefinition = "timestamp default CURRENT_TIMESTAMP")
//    private LocalDateTime receivedAt;

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
