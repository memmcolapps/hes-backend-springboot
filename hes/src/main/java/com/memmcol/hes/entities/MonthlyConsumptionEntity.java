package com.memmcol.hes.entities;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.proxy.HibernateProxy;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Objects;

@Entity
@Table(name = "monthly_consumption")
@IdClass(MonthlyConsumptionId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MonthlyConsumptionEntity {

    @Id
    @Column(name = "meter_serial", nullable = false, length = 64)
    private String meterSerial;

    @Id
    @Column(name = "month_start", nullable = false)
    private LocalDate monthStart; // first day of month (e.g. 2024-09-01)

    @Column(name = "meter_model", length = 64)
    private String meterModel;

    @Column(name = "prev_value_kwh")
    private Double prevValueKwh;

    @Column(name = "curr_value_kwh")
    private Double currValueKwh;

    @Column(name = "consumption_kwh")
    private Double consumptionKwh;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    void prePersist() {
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oClass = (o instanceof HibernateProxy)
                ? ((HibernateProxy) o).getHibernateLazyInitializer().getPersistentClass()
                : o.getClass();
        Class<?> thisClass = (this instanceof HibernateProxy)
                ? ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass()
                : this.getClass();
        if (thisClass != oClass) return false;
        MonthlyConsumptionEntity that = (MonthlyConsumptionEntity) o;
        return Objects.equals(meterSerial, that.meterSerial)
                && Objects.equals(monthStart, that.monthStart);
    }

    @Override
    public final int hashCode() {
        return Objects.hash(meterSerial, monthStart);
    }
}
