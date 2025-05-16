package com.memmcol.hes.dto;

public class LoginFrameData {
    private String meterNumber;
    private int status;
    private String command;
    private String crc;

    // Constructor
    public LoginFrameData(String meterNumber, int status, String command, String crc) {
        this.meterNumber = meterNumber;
        this.status = status;
        this.command = command;
        this.crc = crc;
    }

    // Getters and Setters

    public String getMeterNumber() {
        return meterNumber;
    }

    public void setMeterNumber(String meterNumber) {
        this.meterNumber = meterNumber;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }

    public String getCrc() {
        return crc;
    }

    public void setCrc(String crc) {
        this.crc = crc;
    }
}