package com.regional.corebanking.payment.infrastructure.amplitude;

import java.util.Set;

abstract class AbstractPaymentExecutionSqlDialect implements PaymentExecutionSqlDialect {
    private static final Set<String> EVENT_TABLES = Set.of("BKEVE", "BKEVE_EOD");

    private static String table(String t) {
        if (!EVENT_TABLES.contains(t)) throw new IllegalArgumentException("Unsupported Core Banking event table");
        return t;
    }

    public String findActiveAccount() {
        return "select bkcom.age, bkcom.ncp, bkcom.clc, bkcom.cli, bkcom.dev from bkcom where bkcom.age = ? and bkcom.ncp = ? and bkcom.clc = ? and bkcom.ife = 'N' and bkcom.cfe = 'N' and bkcom.dev = '001'";
    }

    public String findCustomerOpposition() {
        return "select opp from bkoppcli where ddeb <= ? and trim(cli)=? and trim(eta)='V' and (dfin is null or dfin>SYSDATE) ";
    }

    public String findAccountOpposition() {
        return "select opp from bkoppcom where ddeb <= ? and trim(age)=? and trim(ncp)=? and trim(eta)='V'  and (dfin is null or dfin>SYSDATE) ";
    }

    public String findOperationOpposition() {
        return "select o.*,t070.lib1,mnt1,mnt2 from bkopl o,bknom t070 where trim(ope)=? and trim(copp)='O' and o.age='00099' and t070.ctab='070' and cacc=o.opp";
    }

    public String findAvailableFunds() {
        return "select a.sin-nvl(a.minds,0)+nvl(maut,0) as solde,cha from bkcom a left join bkautc b on a.age=b.age and a.dev=b.dev and a.ncp=b.ncp and eta in ('VA','VF','FO') and SYSDATE between b.debut and b.fin where a.cfe='N' and a.ife='N' and trim(a.age)= ? and trim(a.ncp)= ? and trim(a.dev)= ? ";
    }

    public String findAccountingDatePrimary() {
        return "select mnt2 from bknom where trim(ctab)=? and trim(cacc)=?";
    }

    public String findAccountingDateFallback() {
        return "select mnt1 from bknom where trim(ctab)=? and trim(cacc)=?";
    }

    public String findNightMode() {
        return "select mnt4 from bknom where trim(ctab)='098' and trim(cacc)='SITE-CENT' ";
    }

    public String findMaxEventNumber(String t) {
        return "SELECT MAX(eve) AS eve FROM " + table(t) + " WHERE ope = ?";
    }

    public String countEventNumber(String t) {
        return "SELECT COUNT(*) AS nbr FROM " + table(t) + " WHERE eve = ? AND ope = ?";
    }

    public String findHolidays() {
        return "select jourfer from bkfer where TO_CHAR(jourfer,'%Y')=? ";
    }
}
