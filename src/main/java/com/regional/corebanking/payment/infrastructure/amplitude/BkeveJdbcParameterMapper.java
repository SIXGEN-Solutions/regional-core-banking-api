package com.regional.corebanking.payment.infrastructure.amplitude;

import com.regional.corebanking.payment.domain.PaymentProviderEvent;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * R6.6.3: complete positional mapping, not an executable Amplitude posting adapter.
 * Values not supported by the approved client contract must come from a separate
 * bank-authoritative resolver and MUST be explicitly present, including SQL NULLs.
 * Legacy defaults are intentionally NOT inferred or applied.
 */
public final class BkeveJdbcParameterMapper {
    private static final Set<String> CLIENT_COLUMNS = Set.of(
            "AGSA", "AGE", "OPE", "MHT1", "MON1", "MHT2", "MON2", "LIB1", "CSP1");
    private static final Set<String> ALL_COLUMNS = Set.copyOf(BkeveJdbcColumnOrder.COLUMNS);

    public record Parameters(List<String> columns, List<Object> values) {
        public Parameters {
            Objects.requireNonNull(columns, "columns");
            Objects.requireNonNull(values, "values");
            if (columns.size() != 233 || values.size() != 233) {
                throw new IllegalArgumentException("Exactly 233 BKEVE columns and values required");
            }
            if (!columns.equals(BkeveJdbcColumnOrder.COLUMNS)) {
                throw new IllegalArgumentException("BKEVE column ordering mismatch");
            }
            columns = List.copyOf(columns);
            values = Collections.unmodifiableList(new ArrayList<>(values)); // permits explicit SQL NULL
        }

        public Object valueAt(int oneBasedPosition) {
            if (oneBasedPosition < 1 || oneBasedPosition > 233) {
                throw new IndexOutOfBoundsException("BKEVE position: " + oneBasedPosition);
            }
            return values.get(oneBasedPosition - 1);
        }
    }

    public Parameters map(PaymentProviderEvent event, Map<String, ?> approvedBankValues) {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(approvedBankValues, "approvedBankValues");
        requireText(event.operationCode(), "operationCode");
        requirePositive(event.debitAmount(), "debitAmount");
        requirePositive(event.creditAmount(), "creditAmount");

        if (!ALL_COLUMNS.containsAll(approvedBankValues.keySet())) {
            throw new IllegalArgumentException("Unknown BKEVE bank-resolved columns");
        }
        for (String key : CLIENT_COLUMNS) {
            if (approvedBankValues.containsKey(key)) {
                throw new IllegalArgumentException("Bank resolver must not override caller column: " + key);
            }
        }
        Map<String, Object> values = new HashMap<>(event.bkeveEvent().values());
        for (var entry : approvedBankValues.entrySet()) {
            if (values.containsKey(entry.getKey()) && !Objects.equals(values.get(entry.getKey()), entry.getValue())) {
                throw new IllegalArgumentException("Conflicting bank-resolved BKEVE value: " + entry.getKey());
            }
            values.put(entry.getKey(), entry.getValue());
        }
        // Approved R6.6.2 semantic equivalences. No payment-domain amount is used.
        if (event.agsa() != null) {
            if (values.containsKey("AGSA") && !sameBankValue(values.get("AGSA"), event.agsa()))
                throw new IllegalArgumentException("Conflicting BKEVE AGSA value");
            values.put("AGSA", event.agsa());
        }
        if (event.age() != null) {
            if (values.containsKey("AGE") && !sameBankValue(values.get("AGE"), event.age()))
                throw new IllegalArgumentException("Conflicting BKEVE AGE value");
            values.put("AGE", event.age());
        }
        if (event.operationCode() != null) {
            if (values.containsKey("OPE") && !sameBankValue(values.get("OPE"), event.operationCode()))
                throw new IllegalArgumentException("Conflicting BKEVE OPE value");
            values.put("OPE", event.operationCode());
        }
        if (!values.containsKey("MHT1") && event.debitAmount() != null) values.put("MHT1", event.debitAmount());
        if (event.debitAmount() != null) {
            if (values.containsKey("MON1") && !sameBankValue(values.get("MON1"), event.debitAmount()))
                throw new IllegalArgumentException("Conflicting BKEVE MON1 value");
            values.put("MON1", event.debitAmount());
        }
        if (!values.containsKey("MHT2") && event.creditAmount() != null) values.put("MHT2", event.creditAmount());
        if (event.creditAmount() != null) {
            if (values.containsKey("MON2") && !sameBankValue(values.get("MON2"), event.creditAmount()))
                throw new IllegalArgumentException("Conflicting BKEVE MON2 value");
            values.put("MON2", event.creditAmount());
        }
        if (event.label() != null) {
            if (values.containsKey("LIB1") && !sameBankValue(values.get("LIB1"), event.label()))
                throw new IllegalArgumentException("Conflicting BKEVE LIB1 value");
            values.put("LIB1", event.label());
        }
        if (event.csp1() != null) {
            if (values.containsKey("CSP1") && !sameBankValue(values.get("CSP1"), event.csp1()))
                throw new IllegalArgumentException("Conflicting BKEVE CSP1 value");
            values.put("CSP1", event.csp1());
        }

        // Regional rule: all missing BKEVE fields are explicitly SQL NULL.
        // Preserve values provided by the caller or resolved from the bank.
        for (String column : BkeveJdbcColumnOrder.COLUMNS) {
            values.putIfAbsent(column, null);
        }
        List<Object> ordered = new ArrayList<>(233);
        for (String column : BkeveJdbcColumnOrder.COLUMNS) {
            ordered.add(values.get(column));
        }
        return new Parameters(BkeveJdbcColumnOrder.COLUMNS, ordered);
    }

    /** Parameter binding only. Does not prepare, execute, or commit an INSERT. */
    public void bind(PreparedStatement statement, Parameters parameters) throws SQLException {
        Objects.requireNonNull(statement, "statement");
        Objects.requireNonNull(parameters, "parameters");
        for (int pos = 1; pos <= 233; pos++) {
            Object v = parameters.valueAt(pos);
            if (v == null) statement.setNull(pos, sqlType(pos));
            else if (v instanceof java.math.BigDecimal n) statement.setBigDecimal(pos, n);
            else if (v instanceof Double n) statement.setBigDecimal(pos, java.math.BigDecimal.valueOf(n));
            else if (v instanceof Integer n) statement.setInt(pos, n);
            else if (v instanceof java.time.LocalDate d) statement.setDate(pos, java.sql.Date.valueOf(d));
            else if (v instanceof java.util.Date d) statement.setDate(pos, new java.sql.Date(d.getTime()));
            else if (v instanceof String s) statement.setString(pos, s);
            else throw new SQLException("Unsupported BKEVE type at position " + pos + ": " + v.getClass());
        }
    }
    private static int sqlType(int pos) {
        return switch(pos) {
            case 1 -> java.sql.Types.CHAR; // AGSA
            case 2 -> java.sql.Types.CHAR; // AGE
            case 3 -> java.sql.Types.CHAR; // OPE
            case 4 -> java.sql.Types.CHAR; // EVE
            case 5 -> java.sql.Types.CHAR; // TYP
            case 6 -> java.sql.Types.CHAR; // NDOS
            case 7 -> java.sql.Types.CHAR; // AGE1
            case 8 -> java.sql.Types.CHAR; // DEV1
            case 9 -> java.sql.Types.CHAR; // NCP1
            case 10 -> java.sql.Types.CHAR; // SUF1
            case 11 -> java.sql.Types.CHAR; // CLC1
            case 12 -> java.sql.Types.CHAR; // CLI1
            case 13 -> java.sql.Types.CHAR; // NOM1
            case 14 -> java.sql.Types.CHAR; // GES1
            case 15 -> java.sql.Types.CHAR; // SEN1
            case 16 -> java.sql.Types.DECIMAL; // MHT1
            case 17 -> java.sql.Types.DECIMAL; // MON1
            case 18 -> java.sql.Types.DATE; // DVA1
            case 19 -> java.sql.Types.CHAR; // EXO1
            case 20 -> java.sql.Types.DECIMAL; // SOL1
            case 21 -> java.sql.Types.DECIMAL; // INDH1
            case 22 -> java.sql.Types.DECIMAL; // INDS1
            case 23 -> java.sql.Types.CHAR; // DESA1
            case 24 -> java.sql.Types.CHAR; // DESA2
            case 25 -> java.sql.Types.CHAR; // DESA3
            case 26 -> java.sql.Types.CHAR; // DESA4
            case 27 -> java.sql.Types.CHAR; // DESA5
            case 28 -> java.sql.Types.CHAR; // AGE2
            case 29 -> java.sql.Types.CHAR; // DEV2
            case 30 -> java.sql.Types.CHAR; // NCP2
            case 31 -> java.sql.Types.CHAR; // SUF2
            case 32 -> java.sql.Types.CHAR; // CLC2
            case 33 -> java.sql.Types.CHAR; // CLI2
            case 34 -> java.sql.Types.CHAR; // NOM2
            case 35 -> java.sql.Types.CHAR; // GES2
            case 36 -> java.sql.Types.CHAR; // SEN2
            case 37 -> java.sql.Types.DECIMAL; // MHT2
            case 38 -> java.sql.Types.DECIMAL; // MON2
            case 39 -> java.sql.Types.DATE; // DVA2
            case 40 -> java.sql.Types.DATE; // DIN
            case 41 -> java.sql.Types.CHAR; // EXO2
            case 42 -> java.sql.Types.DECIMAL; // SOL2
            case 43 -> java.sql.Types.DECIMAL; // INDH2
            case 44 -> java.sql.Types.DECIMAL; // INDS2
            case 45 -> java.sql.Types.CHAR; // DESC1
            case 46 -> java.sql.Types.CHAR; // DESC2
            case 47 -> java.sql.Types.CHAR; // DESC3
            case 48 -> java.sql.Types.CHAR; // DESC4
            case 49 -> java.sql.Types.CHAR; // DESC5
            case 50 -> java.sql.Types.CHAR; // ETAB
            case 51 -> java.sql.Types.CHAR; // GUIB
            case 52 -> java.sql.Types.CHAR; // NOME
            case 53 -> java.sql.Types.CHAR; // DOMI
            case 54 -> java.sql.Types.CHAR; // ADB1
            case 55 -> java.sql.Types.CHAR; // ADB2
            case 56 -> java.sql.Types.CHAR; // ADB3
            case 57 -> java.sql.Types.CHAR; // VILB
            case 58 -> java.sql.Types.CHAR; // CPOB
            case 59 -> java.sql.Types.CHAR; // CPAY
            case 60 -> java.sql.Types.CHAR; // ETABR
            case 61 -> java.sql.Types.CHAR; // GUIBR
            case 62 -> java.sql.Types.CHAR; // COMB
            case 63 -> java.sql.Types.CHAR; // CLEB
            case 64 -> java.sql.Types.CHAR; // NOMB
            case 65 -> java.sql.Types.DECIMAL; // MBAN
            case 66 -> java.sql.Types.DATE; // DVAB
            case 67 -> java.sql.Types.CHAR; // CAI1
            case 68 -> java.sql.Types.CHAR; // TYC1
            case 69 -> java.sql.Types.CHAR; // DCAI1
            case 70 -> java.sql.Types.CHAR; // SCAI1
            case 71 -> java.sql.Types.DECIMAL; // MCAI1
            case 72 -> java.sql.Types.DECIMAL; // ARRC1
            case 73 -> java.sql.Types.CHAR; // CAI2
            case 74 -> java.sql.Types.CHAR; // TYC2
            case 75 -> java.sql.Types.CHAR; // DCAI2
            case 76 -> java.sql.Types.CHAR; // SCAI2
            case 77 -> java.sql.Types.DECIMAL; // MCAI2
            case 78 -> java.sql.Types.DECIMAL; // ARRC2
            case 79 -> java.sql.Types.CHAR; // DEV
            case 80 -> java.sql.Types.DECIMAL; // MHT
            case 81 -> java.sql.Types.DECIMAL; // MNAT
            case 82 -> java.sql.Types.DECIMAL; // MBOR
            case 83 -> java.sql.Types.CHAR; // NBOR
            case 84 -> java.sql.Types.INTEGER; // NBLIG
            case 85 -> java.sql.Types.DECIMAL; // TCAI2
            case 86 -> java.sql.Types.DECIMAL; // TCAI3
            case 87 -> java.sql.Types.CHAR; // NAT
            case 88 -> java.sql.Types.CHAR; // NATO
            case 89 -> java.sql.Types.CHAR; // OPEO
            case 90 -> java.sql.Types.CHAR; // EVEO
            case 91 -> java.sql.Types.CHAR; // PIEO
            case 92 -> java.sql.Types.DATE; // DOU
            case 93 -> java.sql.Types.DATE; // DCO
            case 94 -> java.sql.Types.CHAR; // ETA
            case 95 -> java.sql.Types.CHAR; // ETAP
            case 96 -> java.sql.Types.INTEGER; // NBV
            case 97 -> java.sql.Types.INTEGER; // NVAL
            case 98 -> java.sql.Types.CHAR; // UTI
            case 99 -> java.sql.Types.CHAR; // UTF
            case 100 -> java.sql.Types.CHAR; // UTA
            case 101 -> java.sql.Types.DECIMAL; // MOA
            case 102 -> java.sql.Types.DECIMAL; // MOF
            case 103 -> java.sql.Types.CHAR; // LIB1
            case 104 -> java.sql.Types.CHAR; // LIB2
            case 105 -> java.sql.Types.CHAR; // LIB3
            case 106 -> java.sql.Types.CHAR; // LIB4
            case 107 -> java.sql.Types.CHAR; // LIB5
            case 108 -> java.sql.Types.CHAR; // LIB6
            case 109 -> java.sql.Types.CHAR; // LIB7
            case 110 -> java.sql.Types.CHAR; // LIB8
            case 111 -> java.sql.Types.CHAR; // LIB9
            case 112 -> java.sql.Types.CHAR; // LIB10
            case 113 -> java.sql.Types.CHAR; // AGEC
            case 114 -> java.sql.Types.CHAR; // AGEP
            case 115 -> java.sql.Types.CHAR; // INTR
            case 116 -> java.sql.Types.CHAR; // ORIG
            case 117 -> java.sql.Types.CHAR; // RLET
            case 118 -> java.sql.Types.CHAR; // CATR
            case 119 -> java.sql.Types.CHAR; // CEB
            case 120 -> java.sql.Types.CHAR; // PLB
            case 121 -> java.sql.Types.CHAR; // CCO
            case 122 -> java.sql.Types.DATE; // DRET
            case 123 -> java.sql.Types.CHAR; // NATP
            case 124 -> java.sql.Types.CHAR; // NUMP
            case 125 -> java.sql.Types.CHAR; // DATP
            case 126 -> java.sql.Types.CHAR; // NOMP
            case 127 -> java.sql.Types.CHAR; // AD1P
            case 128 -> java.sql.Types.CHAR; // AD2P
            case 129 -> java.sql.Types.CHAR; // DELP
            case 130 -> java.sql.Types.CHAR; // SERIE
            case 131 -> java.sql.Types.CHAR; // NCHE
            case 132 -> java.sql.Types.CHAR; // CHQL
            case 133 -> java.sql.Types.CHAR; // CHQC
            case 134 -> java.sql.Types.CHAR; // CAB
            case 135 -> java.sql.Types.CHAR; // NCFF
            case 136 -> java.sql.Types.CHAR; // CSA
            case 137 -> java.sql.Types.DATE; // DECH
            case 138 -> java.sql.Types.CHAR; // TIRE
            case 139 -> java.sql.Types.CHAR; // AGTI
            case 140 -> java.sql.Types.CHAR; // AGRE
            case 141 -> java.sql.Types.INTEGER; // NBJI
            case 142 -> java.sql.Types.CHAR; // PTFC
            case 143 -> java.sql.Types.CHAR; // EFAV
            case 144 -> java.sql.Types.CHAR; // NAVL
            case 145 -> java.sql.Types.CHAR; // EDOM
            case 146 -> java.sql.Types.CHAR; // EOPP
            case 147 -> java.sql.Types.CHAR; // EFAC
            case 148 -> java.sql.Types.CHAR; // MOTI
            case 149 -> java.sql.Types.CHAR; // ENVACC
            case 150 -> java.sql.Types.CHAR; // ENOM
            case 151 -> java.sql.Types.CHAR; // VICL
            case 152 -> java.sql.Types.CHAR; // TECO
            case 153 -> java.sql.Types.CHAR; // TENV
            case 154 -> java.sql.Types.CHAR; // EXJO
            case 155 -> java.sql.Types.CHAR; // ORG
            case 156 -> java.sql.Types.CHAR; // CAI3
            case 157 -> java.sql.Types.DECIMAL; // MCAI3
            case 158 -> java.sql.Types.CHAR; // FORC
            case 159 -> java.sql.Types.CHAR; // OCAI3
            case 160 -> java.sql.Types.INTEGER; // NCAI3
            case 161 -> java.sql.Types.CHAR; // CSP1
            case 162 -> java.sql.Types.CHAR; // CSP2
            case 163 -> java.sql.Types.CHAR; // CSP3
            case 164 -> java.sql.Types.CHAR; // CSP4
            case 165 -> java.sql.Types.CHAR; // CSP5
            case 166 -> java.sql.Types.CHAR; // NDOM
            case 167 -> java.sql.Types.CHAR; // CMOD
            case 168 -> java.sql.Types.CHAR; // DEVF
            case 169 -> java.sql.Types.CHAR; // NCPF
            case 170 -> java.sql.Types.CHAR; // SUFF
            case 171 -> java.sql.Types.DECIMAL; // MONF
            case 172 -> java.sql.Types.DATE; // DVAF
            case 173 -> java.sql.Types.CHAR; // EXOF
            case 174 -> java.sql.Types.CHAR; // AGEE
            case 175 -> java.sql.Types.CHAR; // DEVE
            case 176 -> java.sql.Types.CHAR; // NCPE
            case 177 -> java.sql.Types.CHAR; // SUFE
            case 178 -> java.sql.Types.CHAR; // CLCE
            case 179 -> java.sql.Types.CHAR; // NCPI
            case 180 -> java.sql.Types.CHAR; // SUFI
            case 181 -> java.sql.Types.DECIMAL; // MIMP
            case 182 -> java.sql.Types.DATE; // DVAI
            case 183 -> java.sql.Types.CHAR; // NCPP
            case 184 -> java.sql.Types.CHAR; // SUFP
            case 185 -> java.sql.Types.DECIMAL; // PRGA
            case 186 -> java.sql.Types.DECIMAL; // MRGA
            case 187 -> java.sql.Types.CHAR; // TERM
            case 188 -> java.sql.Types.CHAR; // TVAR
            case 189 -> java.sql.Types.CHAR; // INTP
            case 190 -> java.sql.Types.CHAR; // CAP
            case 191 -> java.sql.Types.CHAR; // PRLL
            case 192 -> java.sql.Types.CHAR; // ANO
            case 193 -> java.sql.Types.CHAR; // ETAB1
            case 194 -> java.sql.Types.CHAR; // GUIB1
            case 195 -> java.sql.Types.CHAR; // COM1B
            case 196 -> java.sql.Types.CHAR; // ETAB2
            case 197 -> java.sql.Types.CHAR; // GUIB2
            case 198 -> java.sql.Types.CHAR; // COM2B
            case 199 -> java.sql.Types.DECIMAL; // TCOM1
            case 200 -> java.sql.Types.DECIMAL; // MCOM1
            case 201 -> java.sql.Types.DECIMAL; // TCOM2
            case 202 -> java.sql.Types.DECIMAL; // MCOM2
            case 203 -> java.sql.Types.DECIMAL; // TCOM3
            case 204 -> java.sql.Types.DECIMAL; // MCOM3
            case 205 -> java.sql.Types.DECIMAL; // FRAI1
            case 206 -> java.sql.Types.DECIMAL; // FRAI2
            case 207 -> java.sql.Types.DECIMAL; // FRAI3
            case 208 -> java.sql.Types.DECIMAL; // TTAX1
            case 209 -> java.sql.Types.DECIMAL; // MTAX1
            case 210 -> java.sql.Types.DECIMAL; // TTAX2
            case 211 -> java.sql.Types.DECIMAL; // MTAX2
            case 212 -> java.sql.Types.DECIMAL; // TTAX3
            case 213 -> java.sql.Types.DECIMAL; // MTAX3
            case 214 -> java.sql.Types.DECIMAL; // MNT1
            case 215 -> java.sql.Types.DECIMAL; // MNT2
            case 216 -> java.sql.Types.DECIMAL; // MNT3
            case 217 -> java.sql.Types.DECIMAL; // MNT4
            case 218 -> java.sql.Types.DECIMAL; // MNT5
            case 219 -> java.sql.Types.CHAR; // TYC3
            case 220 -> java.sql.Types.CHAR; // DCAI3
            case 221 -> java.sql.Types.CHAR; // SCAI3
            case 222 -> java.sql.Types.DECIMAL; // ARRC3
            case 223 -> java.sql.Types.DECIMAL; // MHTD
            case 224 -> java.sql.Types.DECIMAL; // TCAI4
            case 225 -> java.sql.Types.CHAR; // TOPE
            case 226 -> java.sql.Types.CHAR; // IMG
            case 227 -> java.sql.Types.DATE; // DSAI
            case 228 -> java.sql.Types.CHAR; // HSAI
            case 229 -> java.sql.Types.CHAR; // PAYSP
            case 230 -> java.sql.Types.CHAR; // PDELP
            case 231 -> java.sql.Types.CHAR; // MANDA
            case 232 -> java.sql.Types.CHAR; // REFDOS
            case 233 -> java.sql.Types.DECIMAL; // TCHFR
            default -> throw new IllegalArgumentException("Invalid BKEVE position: " + pos);
        };
    }

    private static boolean sameBankValue(Object a, Object b) {
        if (a instanceof BigDecimal x && b instanceof BigDecimal y) return x.compareTo(y) == 0;
        return Objects.equals(a, b);
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
    }

    private static void requirePositive(BigDecimal value, String name) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
