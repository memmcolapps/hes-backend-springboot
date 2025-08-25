package com.memmcol.hes.infrastructure.dlms;

import com.memmcol.hes.domain.profile.ProfileMetadataResult;
import com.memmcol.hes.model.ModelProfileMetadata;
import com.memmcol.hes.service.ProfileMetadataService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

//This service loads metadata and always ensures it’s available (from cache or meter), then returns the result.
@Service
public class ProfileMetadataProvider {

    private final ProfileMetadataService profileMetadataService;

    private final Map<String, String> ADAPTER_KEY_MAP = Map.of(
            "1.0.99.1.0.255", "channelOneReaderAdapter",
            "1.0.99.2.0.255", "channelTwoReaderAdapter"
            // add more as needed
    );

    public String resolveAdapterKey(String profileObis) {
        return ADAPTER_KEY_MAP.get(profileObis);
    }

    public ProfileMetadataProvider(ProfileMetadataService profileMetadataService) {
        this.profileMetadataService = profileMetadataService;
    }

    public ProfileMetadataResult resolve(String meterSerial, String profileObis, String model) {
        List<ModelProfileMetadata> metadataList = profileMetadataService.getOrLoadMetadata(model, profileObis, meterSerial);
        return new ProfileMetadataResult(metadataList);
    }


}
