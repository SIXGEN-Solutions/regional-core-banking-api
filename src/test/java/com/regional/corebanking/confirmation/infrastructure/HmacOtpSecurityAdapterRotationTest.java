package com.regional.corebanking.confirmation.infrastructure;
import org.junit.jupiter.api.Test; import java.util.*; import static org.assertj.core.api.Assertions.*;
class HmacOtpSecurityAdapterRotationTest {
 private static byte[] key(int v){byte[] k=new byte[32];Arrays.fill(k,(byte)v);return k;}
 @Test void activeKeyCreatesNewVerifier(){var a=new HmacOtpSecurityAdapter(Map.of("v1",key(1),"v2",key(2)),"v2");char[] otp="123456".toCharArray();String verifier=a.verifier("CHL-1",otp);assertThat(a.keyVersion()).isEqualTo("v2");assertThat(a.matches("CHL-1",otp,verifier,"v2")).isTrue();assertThat(a.matches("CHL-1",otp,verifier,"v1")).isFalse();}
 @Test void previousKeyVerifiesExistingChallengeAfterRotation(){char[] otp="654321".toCharArray();var before=new HmacOtpSecurityAdapter(Map.of("v1",key(1)),"v1");String verifier=before.verifier("CHL-OLD",otp);var after=new HmacOtpSecurityAdapter(Map.of("v1",key(1),"v2",key(2)),"v2");assertThat(after.matches("CHL-OLD",otp,verifier,"v1")).isTrue();}
 @Test void retiredVersionFailsClosed(){var a=new HmacOtpSecurityAdapter(Map.of("v2",key(2)),"v2");assertThatThrownBy(()->a.verifier("CHL-OLD","123456".toCharArray(),"v1")).isInstanceOf(IllegalStateException.class).hasMessageContaining("v1");}
}
