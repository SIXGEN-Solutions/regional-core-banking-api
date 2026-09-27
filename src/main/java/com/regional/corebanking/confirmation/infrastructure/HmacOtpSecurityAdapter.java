package com.regional.corebanking.confirmation.infrastructure;
import com.regional.corebanking.confirmation.application.port.out.OtpSecurityPort;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Arrays;
import java.util.HexFormat;

public final class HmacOtpSecurityAdapter implements OtpSecurityPort {
    private final SecureRandom random=new SecureRandom();
    private final byte[] key; private final String version;
    public HmacOtpSecurityAdapter(byte[] key,String version){
        if(key==null || key.length<32) throw new IllegalArgumentException("OTP HMAC key must be at least 256 bits");
        this.key=key.clone(); this.version=version;
    }
    public char[] generate(){ return String.format("%06d",random.nextInt(1_000_000)).toCharArray(); }
    public String verifier(String context,char[] otp){
        try {
            Mac mac=Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(key,"HmacSHA256"));
            mac.update(context.getBytes(StandardCharsets.UTF_8)); mac.update((byte)0);
            ByteBuffer encoded=StandardCharsets.UTF_8.encode(CharBuffer.wrap(otp));
            byte[] b=new byte[encoded.remaining()]; encoded.get(b); mac.update(b); Arrays.fill(b,(byte)0);
            return HexFormat.of().formatHex(mac.doFinal());
        } catch(Exception e){ throw new IllegalStateException("Unable to calculate OTP verifier",e); }
    }
    public boolean matches(String context,char[] candidate,String expected){
        return MessageDigest.isEqual(verifier(context,candidate).getBytes(StandardCharsets.US_ASCII),
                expected.getBytes(StandardCharsets.US_ASCII));
    }
    public String keyVersion(){ return version; }
}
