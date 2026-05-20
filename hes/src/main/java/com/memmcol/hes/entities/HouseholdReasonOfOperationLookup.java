package com.memmcol.hes.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

@Entity
@Table(name = "household_reason_of_operation_lookup")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HouseholdReasonOfOperationLookup {

    @Id
    private Integer code;

    @Column(nullable = false, length = 128)
    private String description;
}
