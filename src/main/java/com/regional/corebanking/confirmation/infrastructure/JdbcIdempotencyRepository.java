package com.regional.corebanking.confirmation.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.regional.corebanking.confirmation.application.exception.IdempotencyConflictException;
import com.regional.corebanking.confirmation.application.port.out.IdempotencyRepository;
import com.regional.corebanking.confirmation.domain.ConfirmationChallenge;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;
import java.util.function.Supplier;

public final class JdbcIdempotencyRepository implements IdempotencyRepository {
    private final JdbcTemplate jdbc; private final TransactionTemplate tx; private final ObjectMapper json;
    public JdbcIdempotencyRepository(JdbcTemplate jdbc, TransactionTemplate tx, ObjectMapper json){this.jdbc=jdbc;this.tx=tx;this.json=json;}

    public ConfirmationChallenge execute(String institution,String key,String operation,String fingerprint,
                                         Supplier<ConfirmationChallenge> action){
        return tx.execute(status -> {
            int claimed=jdbc.update("""
              INSERT INTO regional_confirmation_idempotency
              (institution_code,idempotency_key,operation,request_fingerprint,created_at)
              VALUES (?,?,?,?,CURRENT_TIMESTAMP) ON CONFLICT (institution_code,idempotency_key) DO NOTHING
              """,
              institution,key,operation,fingerprint);
            if(claimed==0){
                Entry existing=locked(institution,key).orElseThrow();
                validate(existing,operation,fingerprint);
                if(existing.result()==null) throw new IllegalStateException("Idempotency operation has no stable result");
                return existing.result();
            }
            ConfirmationChallenge result=action.get();
            jdbc.update("""
              UPDATE regional_confirmation_idempotency SET result_json=CAST(? AS jsonb), completed_at=CURRENT_TIMESTAMP
              WHERE institution_code=? AND idempotency_key=?
              """,serialize(result),institution,key);
            return result;
        });
    }
    public Optional<Entry> find(String institution,String key){
        return jdbc.query("""
          SELECT institution_code,idempotency_key,operation,request_fingerprint,result_json::text
          FROM regional_confirmation_idempotency WHERE institution_code=? AND idempotency_key=?
          """,
          (rs,row)->new Entry(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),deserialize(rs.getString(5))),
          institution,key).stream().findFirst();
    }
    private Optional<Entry> locked(String institution,String key){
        return jdbc.query("""
          SELECT institution_code,idempotency_key,operation,request_fingerprint,result_json::text
          FROM regional_confirmation_idempotency WHERE institution_code=? AND idempotency_key=? FOR UPDATE
          """,
          (rs,row)->new Entry(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),deserialize(rs.getString(5))),
          institution,key).stream().findFirst();
    }
    private static void validate(Entry e,String op,String fp){
        if(!e.operation().equals(op)||!MessageDigest.isEqual(e.fingerprint().getBytes(StandardCharsets.US_ASCII),fp.getBytes(StandardCharsets.US_ASCII)))
            throw new IdempotencyConflictException();
    }
    private String serialize(ConfirmationChallenge c){try{return json.writeValueAsString(c);}catch(JsonProcessingException e){throw new IllegalStateException("Cannot serialize idempotency result",e);}}
    private ConfirmationChallenge deserialize(String v){if(v==null)return null;try{return json.readValue(v,ConfirmationChallenge.class);}catch(JsonProcessingException e){throw new IllegalStateException("Cannot deserialize idempotency result",e);}}
}
