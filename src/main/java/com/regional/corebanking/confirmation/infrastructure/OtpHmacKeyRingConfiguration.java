package com.regional.corebanking.confirmation.infrastructure;
import java.util.*;
public final class OtpHmacKeyRingConfiguration {
 private OtpHmacKeyRingConfiguration(){}
 public static Map<String,byte[]> parse(String encoded){
  if(encoded==null||encoded.isBlank())throw new IllegalStateException("REGIONAL_OTP_HMAC_KEYS must be configured");
  Map<String,byte[]> keys=new LinkedHashMap<>();
  for(String raw:encoded.split(",")){String e=raw.trim();int i=e.indexOf(':');if(i<=0||i==e.length()-1)throw new IllegalStateException("REGIONAL_OTP_HMAC_KEYS entries must use <version>:<base64-secret>");String v=e.substring(0,i).trim(),secret=e.substring(i+1).trim();if(keys.containsKey(v))throw new IllegalStateException("Duplicate OTP HMAC key version: "+v);try{byte[] k=Base64.getDecoder().decode(secret);if(k.length<32)throw new IllegalStateException("OTP HMAC key must decode to at least 256 bits: "+v);keys.put(v,k);}catch(IllegalArgumentException x){throw new IllegalStateException("Invalid Base64 OTP HMAC key: "+v,x);}}
  return keys;
 }
}
