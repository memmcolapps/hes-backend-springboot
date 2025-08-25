package com.memmcol.hes.domain.profile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.memmcol.hes.infrastructure.dlms.DlmsReaderUtils;
import com.memmcol.hes.infrastructure.dlms.ProfileMetadataProvider;
import com.memmcol.hes.model.ProfileChannel2Reading;
import com.memmcol.hes.model.ProfileChannel2ReadingDTO;
import com.memmcol.hes.service.ProfileChannel2ReadingMapper;
import com.memmcol.hes.trackByTimestamp.MeterProfileTimestampProgressRepository;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
@Setter
public class ProfileProcessor {

    private final DlmsReaderUtils dlmsReaderUtils;
    private final ChannelTwoMapper channelTwoMapper;
    private final ProfileMetadataProvider metadataProvider;
    private final deduplicationTimestamp deduplicationTimestamp;
    private String model;
    private String meterSerial;
    private String profileObis;
    private LocalDateTime from;
    private LocalDateTime to;
    private boolean mdMeter;

    public ProfileProcessor(DlmsReaderUtils dlmsReaderUtils, ChannelTwoMapper channelTwoMapper, ProfileMetadataProvider metadataProvider, MeterProfileTimestampProgressRepository meterProfileTimestampProgressRepository, com.memmcol.hes.domain.profile.deduplicationTimestamp deduplicationTimestamp) {
        this.dlmsReaderUtils = dlmsReaderUtils;
        this.channelTwoMapper = channelTwoMapper;
        this.metadataProvider = metadataProvider;
        this.deduplicationTimestamp = deduplicationTimestamp;
    }

    public void processProfiles() throws Exception {
        ProfileMetadataResult metadataResult = metadataProvider.resolve(meterSerial, profileObis, model);
        List<ProfileRowGeneric> rawRows = dlmsReaderUtils.readRange(model, meterSerial, profileObis, metadataResult, from, to, mdMeter);

        // 2. Map profile generic rows to DTO and apply scaler and multiplers

        List<ProfileChannel2ReadingDTO> profileDTOs = channelTwoMapper.toDTO(rawRows, meterSerial, model, mdMeter, metadataResult);

        //log as JSON
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())                          // ✅ Enables Java 8 date/time
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)       // ✅ Use ISO-8601, not numeric timestamps
                .enable(SerializationFeature.INDENT_OUTPUT);
        try {
            String json = mapper.writeValueAsString(profileDTOs);
            log.info("📦 profileDTOs:\n{}", json);
        } catch (Exception e) {
            log.error("❌ Failed to convert OBIS DTOs to JSON", e);
        }

        // 📌 Deduplication by timestamp only
        List<ProfileChannel2ReadingDTO> filtered = deduplicationTimestamp.deduplicate(profileDTOs, meterSerial);

        // 3. Save to DB
        if (!filtered.isEmpty()) {
            profileDTOs = filtered;
        }
    }
}
