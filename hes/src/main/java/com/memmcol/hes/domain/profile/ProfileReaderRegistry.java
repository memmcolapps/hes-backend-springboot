package com.memmcol.hes.domain.profile;

import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class ProfileReaderRegistry {
    private static final Map<String, Class<?>> READER_MAP = new HashMap<>();

    static {
        // Register all supported readers here
        READER_MAP.put("MMX-313-CT::1.0.99.1.0.255", MetersLockService.class);
        READER_MAP.put("MMX-313-CT::1.0.99.2.0.255", ChannelTwoService.class);
        // Example for other models:
        // READER_MAP.put("MODEL2::1.0.99.1.0.255", new ProfileChannelOneReaderV2());
    }
    /**
     * Allows dynamic registration of new readers at runtime.
     */
    public static void registerReader(String model, String profileOBIS, Object reader) {
        READER_MAP.put(model + "::" + profileOBIS, (Class<?>) reader);
    }

    /**
     * Retrieves a reader for the given meter model and OBIS code.
     *
     * @param model       The meter model (e.g., MODEL1)
     * @param profileOBIS The OBIS code (e.g., 1.0.99.1.0.255)
     * @return ProfileReader instance, or null if no mapping found.
     */
    public static Object getReaderInstance(String model, String profileOBIS) {
        Class<?> clazz = READER_MAP.get(model + "::" + profileOBIS);
        if (clazz == null) {
            throw new IllegalArgumentException("No reader class found for: " + model + "-" + profileOBIS);
        }
        try {
            return clazz.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Unable to create instance for " + clazz.getName(), e);
        }
    }

}
