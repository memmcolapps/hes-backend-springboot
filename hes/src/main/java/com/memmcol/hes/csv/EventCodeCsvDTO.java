package com.memmcol.hes.csv;

import com.opencsv.bean.CsvBindByName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EventCodeCsvDTO {

    @CsvBindByName(column = "Event ID")
    private Long eventTypeId;

    @CsvBindByName(column = "Event Code")
    private Integer code;

    @CsvBindByName(column = "Event Name")
    private String eventName;

    @CsvBindByName(column = "Event Description")
    private String description;

    // getters and setters
}