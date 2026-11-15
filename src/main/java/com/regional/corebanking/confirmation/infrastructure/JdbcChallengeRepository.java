package com.regional.corebanking.confirmation.infrastructure;

import com.regional.corebanking.confirmation.application.exception.ChallengeNotFoundException;
import com.regional.corebanking.confirmation.application.port.out.ChallengeRepository;
import com.regional.corebanking.confirmation.domain.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

public final class JdbcChallengeRepository implements ChallengeRepository {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    public JdbcChallengeRepository(JdbcTemplate jdbc, TransactionTemplate tx) { this.jdbc=jdbc; this.tx=tx; }

    public ConfirmationChallenge save(String institution, ConfirmationChallenge c) {
        jdbc.update("""
          INSERT INTO regional_confirmation_challenge
          (institution_code, challenge_reference, payment_reference, customer_reference,
           debtor_account_reference, amount, currency, otp_verifier, otp_key_version,
           challenge_status, business_code, failed_attempts, replacement_count,
           delivery_channels, created_at, expires_at, verified_at, replaced_at, revoked_at,
           delivery_status, delivery_requested_at, sent_at)
          VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
          """,
          institution,c.challengeReference(),c.paymentReference(),c.customerReference(),c.debtorAccountReference(),
          c.amount(),c.currency(),c.otpVerifier(),c.otpKeyVersion(),c.status().name(),c.businessCode().name(),
          c.failedAttempts(),c.replacementCount(),channels(c.deliveryChannels()),Timestamp.from(c.createdAt()),
          Timestamp.from(c.expiresAt()),ts(c.verifiedAt()),ts(c.replacedAt()),ts(c.revokedAt()),
          c.deliveryStatus().name(),ts(c.deliveryRequestedAt()),ts(c.sentAt()));
        return c;
    }
    public Optional<ConfirmationChallenge> find(String institution, String reference) {
        return jdbc.query("SELECT * FROM regional_confirmation_challenge WHERE institution_code=? AND challenge_reference=?",
                this::map,institution,reference).stream().findFirst();
    }
    public ConfirmationChallenge update(String institution, String reference,
                                        UnaryOperator<ConfirmationChallenge> mutation) {
        return tx.execute(status -> {
            ConfirmationChallenge current = jdbc.query(
                "SELECT * FROM regional_confirmation_challenge WHERE institution_code=? AND challenge_reference=? FOR UPDATE",
                this::map,institution,reference).stream().findFirst()
                .orElseThrow(() -> new ChallengeNotFoundException(reference));
            ConfirmationChallenge next = mutation.apply(current);
            jdbc.update("""
              UPDATE regional_confirmation_challenge SET challenge_status=?, business_code=?,
              failed_attempts=?, replacement_count=?, delivery_channels=?, verified_at=?, replaced_at=?, revoked_at=?,
              delivery_status=?, delivery_requested_at=?, sent_at=?
              WHERE institution_code=? AND challenge_reference=?
              """,
              next.status().name(),next.businessCode().name(),next.failedAttempts(),next.replacementCount(),
              channels(next.deliveryChannels()),ts(next.verifiedAt()),ts(next.replacedAt()),ts(next.revokedAt()),
              next.deliveryStatus().name(),ts(next.deliveryRequestedAt()),ts(next.sentAt()),institution,reference);
            return next;
        });
    }
    private ConfirmationChallenge map(ResultSet rs,int row) throws SQLException {
        return new ConfirmationChallenge(rs.getString("challenge_reference"),rs.getString("payment_reference"),
          rs.getString("customer_reference"),rs.getString("debtor_account_reference"),rs.getBigDecimal("amount"),
          rs.getString("currency"),rs.getString("otp_verifier"),rs.getString("otp_key_version"),
          ChallengeStatus.valueOf(rs.getString("challenge_status")),ConfirmationBusinessCode.valueOf(rs.getString("business_code")),
          rs.getInt("failed_attempts"),rs.getInt("replacement_count"),parseChannels(rs.getString("delivery_channels")),
          instant(rs,"created_at"),instant(rs,"expires_at"),nullableInstant(rs,"verified_at"),
          nullableInstant(rs,"replaced_at"),nullableInstant(rs,"revoked_at"),
          DeliveryStatus.valueOf(rs.getString("delivery_status")),nullableInstant(rs,"delivery_requested_at"),nullableInstant(rs,"sent_at"));
    }
    private static String channels(Set<DeliveryChannel> c){return c.stream().map(Enum::name).sorted().collect(Collectors.joining(","));}
    private static Set<DeliveryChannel> parseChannels(String v){if(v==null||v.isBlank())return Set.of(); Set<DeliveryChannel>s=EnumSet.noneOf(DeliveryChannel.class);for(String x:v.split(","))s.add(DeliveryChannel.valueOf(x));return s;}
    private static Timestamp ts(Instant v){return v==null?null:Timestamp.from(v);}
    private static Instant instant(ResultSet r,String n)throws SQLException{return r.getTimestamp(n).toInstant();}
    private static Instant nullableInstant(ResultSet r,String n)throws SQLException{Timestamp t=r.getTimestamp(n);return t==null?null:t.toInstant();}
}
