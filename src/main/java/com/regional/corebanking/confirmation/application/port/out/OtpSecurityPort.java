package com.regional.corebanking.confirmation.application.port.out;
public interface OtpSecurityPort {
 char[] generate();
 String verifier(String context,char[] otp);
 String verifier(String context,char[] otp,String keyVersion);
 boolean matches(String context,char[] candidate,String expectedVerifier,String keyVersion);
 String keyVersion();
}
