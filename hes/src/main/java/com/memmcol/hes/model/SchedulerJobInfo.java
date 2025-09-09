package com.memmcol.hes.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Column;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@ToString
@Getter
@Setter
@Entity
@Table(name = "scheduler_job_info")
public class SchedulerJobInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "job_id")
    private Long jobId;

    @Column(name = "job_name")
    private String jobName;

    @Column(name = "job_group")
    private String jobGroup;

    @Column(name = "job_status")
    private String jobStatus;

    @Column(name = "job_class")
    private String jobClass;

    @Column(name = "cron_expression")
    private String cronExpression;

    @Column(name = "description")
    private String description;

    @Column(name = "interface_name")
    private String interfaceName;

    @Column(name = "repeat_time")
    private Long repeatTime;

    @Column(name = "cron_job")
    private Boolean cronJob;
}