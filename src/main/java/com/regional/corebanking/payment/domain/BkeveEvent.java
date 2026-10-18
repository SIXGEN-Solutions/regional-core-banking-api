package com.regional.corebanking.payment.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Regional banking event received from authorized consumers. */
public record BkeveEvent(Map<String, Object> values) {
    public static final List<String> COLUMNS = List.of(
            "AGSA", "AGE", "OPE", "EVE", "TYP", "NDOS", "AGE1", "DEV1",
            "NCP1", "SUF1", "CLC1", "CLI1", "NOM1", "GES1", "SEN1", "MHT1",
            "MON1", "DVA1", "EXO1", "SOL1", "INDH1", "INDS1", "DESA1", "DESA2",
            "DESA3", "DESA4", "DESA5", "AGE2", "DEV2", "NCP2", "SUF2", "CLC2",
            "CLI2", "NOM2", "GES2", "SEN2", "MHT2", "MON2", "DVA2", "DIN",
            "EXO2", "SOL2", "INDH2", "INDS2", "DESC1", "DESC2", "DESC3", "DESC4",
            "DESC5", "ETAB", "GUIB", "NOME", "DOMI", "ADB1", "ADB2", "ADB3",
            "VILB", "CPOB", "CPAY", "ETABR", "GUIBR", "COMB", "CLEB", "NOMB",
            "MBAN", "DVAB", "CAI1", "TYC1", "DCAI1", "SCAI1", "MCAI1", "ARRC1",
            "CAI2", "TYC2", "DCAI2", "SCAI2", "MCAI2", "ARRC2", "DEV", "MHT",
            "MNAT", "MBOR", "NBOR", "NBLIG", "TCAI2", "TCAI3", "NAT", "NATO",
            "OPEO", "EVEO", "PIEO", "DOU", "DCO", "ETA", "ETAP", "NBV",
            "NVAL", "UTI", "UTF", "UTA", "MOA", "MOF", "LIB1", "LIB2",
            "LIB3", "LIB4", "LIB5", "LIB6", "LIB7", "LIB8", "LIB9", "LIB10",
            "AGEC", "AGEP", "INTR", "ORIG", "RLET", "CATR", "CEB", "PLB",
            "CCO", "DRET", "NATP", "NUMP", "DATP", "NOMP", "AD1P", "AD2P",
            "DELP", "SERIE", "NCHE", "CHQL", "CHQC", "CAB", "NCFF", "CSA",
            "DECH", "TIRE", "AGTI", "AGRE", "NBJI", "PTFC", "EFAV", "NAVL",
            "EDOM", "EOPP", "EFAC", "MOTI", "ENVACC", "ENOM", "VICL", "TECO",
            "TENV", "EXJO", "ORG", "CAI3", "MCAI3", "FORC", "OCAI3", "NCAI3",
            "CSP1", "CSP2", "CSP3", "CSP4", "CSP5", "NDOM", "CMOD", "DEVF",
            "NCPF", "SUFF", "MONF", "DVAF", "EXOF", "AGEE", "DEVE", "NCPE",
            "SUFE", "CLCE", "NCPI", "SUFI", "MIMP", "DVAI", "NCPP", "SUFP",
            "PRGA", "MRGA", "TERM", "TVAR", "INTP", "CAP", "PRLL", "ANO",
            "ETAB1", "GUIB1", "COM1B", "ETAB2", "GUIB2", "COM2B", "TCOM1", "MCOM1",
            "TCOM2", "MCOM2", "TCOM3", "MCOM3", "FRAI1", "FRAI2", "FRAI3", "TTAX1",
            "MTAX1", "TTAX2", "MTAX2", "TTAX3", "MTAX3", "MNT1", "MNT2", "MNT3",
            "MNT4", "MNT5", "TYC3", "DCAI3", "SCAI3", "ARRC3", "MHTD", "TCAI4",
            "TOPE", "IMG", "DSAI", "HSAI", "PAYSP", "PDELP", "MANDA", "REFDOS",
            "TCHFR"
    );
    private static final Set<String> ALLOWED = Set.copyOf(COLUMNS);
    private static final Set<String> SERVER_OWNED = Set.of("EVE", "DCO", "DVA1", "DVA2");

    public BkeveEvent {
        Objects.requireNonNull(values, "values");
        if (!ALLOWED.containsAll(values.keySet())) {
            throw new IllegalArgumentException("Unknown BKEVE properties");
        }
        for (String owned : SERVER_OWNED) {
            if (values.containsKey(owned)) {
                throw new IllegalArgumentException("BKEVE property is managed by Regional: " + owned);
            }
        }
        // Copy retains explicit nulls for internal use, rather than Map.copyOf.
        values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    public static BkeveEvent empty() {
        return new BkeveEvent(Map.of());
    }
}
