package com.regional.corebanking.confirmation.application.port.out;
public interface OtpSecurityPort {
    char[] generate();
    String verifier(String context,char[] otp);
    boolean matches(String context,char[] candidate,String expectedVerifier);
    String keyVersion();
}
