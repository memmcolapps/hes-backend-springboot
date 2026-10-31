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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

//    public Map<String, List<ProfileChannel2ReadingDTO>> processProfiless(
//            ProfileProcessRequest request) throws Exception {
//
//        if (request.getMeterSerial() == null
//                || request.getMeterSerial().isEmpty()) {
//            throw new IllegalArgumentException(
//                    "At least one meter serial is required.");
//        }
//
//        Map<String, List<ProfileChannel2ReadingDTO>> results =
//                new LinkedHashMap<>();
//
//        for (String serial : request.getMeterSerial()) {
//
//            if (serial == null || serial.isBlank()) {
//                throw new IllegalArgumentException(
//                        "Meter serial must not be blank.");
//            }
//
//            log.info("Processing profile for meter {}", serial);
//
//            ProfileMetadataResult metadataResult =
//                    metadataProvider.resolve(
//                            serial,
//                            request.getProfileObis(),
//                            request.getModel());
//
//            List<ProfileRowGeneric> rawRows =
//                    dlmsReaderUtils.readRange(
//                            request.getModel(),
//                            serial,
//                            request.getProfileObis(),
//                            metadataResult,
//                            request.getFrom(),
//                            request.getTo(),
//                            request.isMdMeter());
//
//            List<ProfileChannel2ReadingDTO> profileDTOs =
//                    channelTwoMapper.toDTO(
//                            rawRows,
//                            serial,
//                            request.getModel(),
//                            request.isMdMeter(),
//                            metadataResult);
//
//            List<ProfileChannel2ReadingDTO> newReadings =
//                    profileDTOs.isEmpty()
//                            ? List.of()
//                            : deduplicationTimestamp.deduplicate(
//                            profileDTOs, serial);
//
//            results.put(serial, newReadings);
//
//            log.info(
//                    "Finished meter {}. Raw rows: {}, mapped readings: {}, new readings: {}",
//                    serial,
//                    rawRows.size(),
//                    profileDTOs.size(),
//                    newReadings.size());
//        }
//
//        return results;
//    }
}
