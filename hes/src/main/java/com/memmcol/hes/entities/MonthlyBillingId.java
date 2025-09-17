package com.memmcol.hes.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MonthlyBillingId implements Serializable {
  private String meterSerial;
  private LocalDateTime entryTimestamp;
}
