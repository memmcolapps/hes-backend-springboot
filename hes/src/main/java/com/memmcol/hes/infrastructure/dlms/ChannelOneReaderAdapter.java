package com.memmcol.hes.infrastructure.dlms;

import com.memmcol.hes.application.port.out.PartialProfileRecoveryPort;
import com.memmcol.hes.application.port.out.ProfileDataReaderPort;
import com.memmcol.hes.application.port.out.ProfileReadException;
import com.memmcol.hes.domain.profile.ChannelOneRow;
import com.memmcol.hes.domain.profile.ProfileMetadataResult;
import com.memmcol.hes.model.ModelProfileMetadata;
import com.memmcol.hes.nettyUtils.SessionManager;
import gurux.dlms.GXDLMSClient;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component("channelOneReaderAdapter")
@AllArgsConstructor
@Slf4j
public class ChannelOneReaderAdapter implements ProfileDataReaderPort<ChannelOneRow>, PartialProfileRecoveryPort<ChannelOneRow> {
    private final SessionManager sessionManager;           // your existing component
    private final DlmsPartialDecoder partialDecoder;       // -> create wrapper for existing parser
    @Override
    public List<ChannelOneRow> recoverPartial(String meterSerial, String profileObis) throws ProfileReadException {
        return List.of();
    }

    @Override
    public List<ChannelOneRow> readRange(String model, String meterSerial, String profileObis, ProfileMetadataResult metadataResult, LocalDateTime from, LocalDateTime to) throws ProfileReadException {
        GXDLMSClient client = null;
        long t0 = System.currentTimeMillis();

        // Clear any stale partial buffer before a fresh read
        partialDecoder.clear(meterSerial, profileObis);

        return List.of();
    }
}
