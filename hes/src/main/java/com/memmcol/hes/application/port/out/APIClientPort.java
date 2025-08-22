package com.memmcol.hes.application.port.out;

public interface APIClientPort {
    Object readInstantaneous(String model, String meterSerial, String obisString) throws Exception;
}
