package com.regional.corebanking.confirmation.infrastructure;
import com.regional.corebanking.confirmation.application.port.out.OtpSecurityPort;
import javax.crypto.Mac; import javax.crypto.spec.SecretKeySpec;
import java.nio.*; import java.nio.charset.StandardCharsets; import java.security.*; import java.util.*;
public final class HmacOtpSecurityAdapter implements OtpSecurityPort {
 private final SecureRandom random=new SecureRandom(); private final Map<String,byte[]> keys; private final String activeVersion;
 public HmacOtpSecurityAdapter(byte[] key,String version){this(Map.of(version,key),version);}
 public HmacOtpSecurityAdapter(Map<String,byte[]> source,String activeVersion){
  if(activeVersion==null||activeVersion.isBlank()) throw new IllegalArgumentException("Active OTP HMAC key version is required");
  if(source==null||!source.containsKey(activeVersion)) throw new IllegalArgumentException("Active OTP HMAC key version is not present in key ring");
  Map<String,byte[]> copy=new LinkedHashMap<>();
  source.forEach((v,k)->{if(v==null||v.isBlank())throw new IllegalArgumentException("OTP HMAC key version is required"); if(k==null||k.length<32)throw new IllegalArgumentException("OTP HMAC key must be at least 256 bits"); copy.put(v,k.clone());});
  this.keys=Map.copyOf(copy); this.activeVersion=activeVersion;
 }
 public char[] generate(){return String.format("%06d",random.nextInt(1_000_000)).toCharArray();}
 public String verifier(String context,char[] otp){return verifier(context,otp,activeVersion);}
 public String verifier(String context,char[] otp,String version){
  byte[] key=keys.get(version); if(key==null)throw new IllegalStateException("OTP HMAC key version is unavailable: "+version);
  try{Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(key,"HmacSHA256"));mac.update(Objects.requireNonNull(context).getBytes(StandardCharsets.UTF_8));mac.update((byte)0);ByteBuffer e=StandardCharsets.UTF_8.encode(CharBuffer.wrap(otp));byte[] b=new byte[e.remaining()];e.get(b);try{mac.update(b);return HexFormat.of().formatHex(mac.doFinal());}finally{Arrays.fill(b,(byte)0);}}catch(Exception x){throw new IllegalStateException("Unable to calculate OTP verifier",x);}
 }
 public boolean matches(String context,char[] candidate,String expected,String version){return MessageDigest.isEqual(verifier(context,candidate,version).getBytes(StandardCharsets.US_ASCII),expected.getBytes(StandardCharsets.US_ASCII));}
 public String keyVersion(){return activeVersion;}
}
