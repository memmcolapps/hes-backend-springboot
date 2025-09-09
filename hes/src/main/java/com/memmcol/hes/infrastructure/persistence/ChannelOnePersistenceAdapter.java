package com.memmcol.hes.infrastructure.persistence;

import com.memmcol.hes.application.port.out.ProfilePersistencePort;
import com.memmcol.hes.domain.profile.*;

import java.util.List;
import java.util.Map;

public class ChannelOnePersistenceAdapter implements ProfilePersistencePort<ChannelOneRow> {

    @Override
    public int saveBatch(String meterSerial, String profileObis, List<ChannelOneRow> rows) {
        return 0;
    }

    @Override
    public ProfileTimestamp findLatestTimestamp(String meterSerial, String profileObis) {
        return null;
    }

    @Override
    public ProfileSyncResult saveBatchAndAdvanceCursor(String meterSerial, String meterModel, String obis, List<ChannelOneRow> rows, CapturePeriod capturePeriodSeconds, ProfileMetadataResult metadataResult) {
        return null;
    }

    @Override
    public ProfileSyncResult saveBatchAndAdvanceCursor(String meterSerial, String meterModel, String obis, List<ChannelOneRow> rows, CapturePeriod capturePeriodSeconds) {
        return null;
    }
}
