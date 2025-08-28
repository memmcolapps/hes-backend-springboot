package com.memmcol.hes.application.port.out;

import com.memmcol.hes.domain.profile.ProfileRow;
import com.memmcol.hes.model.ModelProfileMetadata;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 2. Port Interfaces (Application Layer Contracts)
 */
public interface ProfileDataReaderPort {
    List<ProfileRow> readRange(String model, String meterSerial, String profileObis, List<ModelProfileMetadata> metadataList,
                               LocalDateTime from, LocalDateTime to) throws ProfileReadException;

    void sendDisconnectRequest(String meterSerial) throws InvalidAlgorithmParameterException, NoSuchPaddingException, IllegalBlockSizeException, IOException, NoSuchAlgorithmException, BadPaddingException, SignatureException, InvalidKeyException;
}
