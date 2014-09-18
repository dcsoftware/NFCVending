package it.blqlabs.android.nfcvending.OTPGenerator;

import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Created by davide on 18/09/14.
 */
public class OTPGenerator {

    private final String secret;
    private final Clock clock;
    private long timestamp;

    public OTPGenerator(String secret) {
        this.secret = secret;
        clock = new Clock();
    }

    public long getCode1() throws NoSuchAlgorithmException, InvalidKeyException {
        timestamp = clock.getCurrentInterval();
        SecretKeySpec signKey = new SecretKeySpec(secret.getBytes(), "HmacSHA1");
        ByteBuffer buffer = ByteBuffer.allocate(8);
        buffer.putLong(timestamp);
        byte[] timeBytes = buffer.array();
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(signKey);
        byte[] hash = mac.doFinal(timeBytes);
        int offset = hash[19] & 0xf;
        long truncatedHash = hash[offset] & 0x7f;
        for (int i = 1; i < 4; i++) {
            truncatedHash <<= 8;
            truncatedHash |= hash[offset + i] & 0xff;
        }
        return (truncatedHash %= 1000000);
    }

    /*public long getCode2() throws NoSuchAlgorithmException, InvalidKeyException, Base32.DecodingException {
        SecretKeySpec signKey = new SecretKeySpec(Base32.decode(secret), "HmacSHA1");
        ByteBuffer buffer = ByteBuffer.allocate(8);
        buffer.putLong(timestamp);
        byte[] timeBytes = buffer.array();
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(signKey);
        byte[] hash = mac.doFinal(timeBytes);
        int offset = hash[19] & 0xf;
        long truncatedHash = hash[offset] & 0x7f;
        for (int i = 1; i < 4; i++) {
            truncatedHash <<= 8;
            truncatedHash |= hash[offset + i] & 0xff;
        }
        return (truncatedHash %= 1000000);
    }*/
}
