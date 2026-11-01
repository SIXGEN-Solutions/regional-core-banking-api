package com.regional.corebanking.customer.infrastructure.amplitude;

abstract class AbstractAnsiCustomerSqlDialect implements CustomerSqlDialect {

    private static final String ACTIVE_ACCOUNT_FILTER =
            " bkcom.ife = 'N' and bkcom.cfe = 'N' and bkcom.dev = '001' ";

    @Override
    public String searchByCustomerNumber() {
        return "select distinct bkcli.cli, bkcli.nid, bkcli.nomrest "
                + "from bkcom join bkcli on bkcli.cli = bkcom.cli "
                + "where bkcli.cli = ? and" + ACTIVE_ACCOUNT_FILTER;
    }

    @Override
    public String searchByNiu() {
        return "select distinct bkcli.cli, bkcli.nid, bkcli.nomrest "
                + "from bkcom join bkcli on bkcli.cli = bkcom.cli "
                + "where bkcli.nid = ? and" + ACTIVE_ACCOUNT_FILTER;
    }

    @Override
    public String searchByCustomerNumberAndNiu() {
        return "select distinct bkcli.cli, bkcli.nid, bkcli.nomrest "
                + "from bkcom join bkcli on bkcli.cli = bkcom.cli "
                + "where bkcli.cli = ? and bkcli.nid = ? and" + ACTIVE_ACCOUNT_FILTER;
    }

    @Override
    public String findIdentityByCustomerReference() {
        return "select bkcli.cli, bkcli.nid, bkcli.nomrest "
                + "from bkcli where bkcli.cli = ?";
    }

    @Override
    public String findAccountsByCustomerReference() {
        return "select bkcom.ncp, bkcom.age, bkcom.dev, bkcom.cha, bkcom.clc, bkcom.cli, bkcom.inti, bkcom.ife, bkcom.dou, bkcom.cfe, bkcom.sde, bkcom.sin, bkcom.typ, bkcom.suf "
                + "from bkcom where bkcom.cli = ? and" + ACTIVE_ACCOUNT_FILTER;
    }

    @Override
    public String findAccountByReferenceParts() {
        return "select bkcom.ncp, bkcom.age, bkcom.dev, bkcom.cha, bkcom.clc, bkcom.cli, bkcom.inti, bkcom.ife, bkcom.dou, bkcom.cfe, bkcom.sde, bkcom.sin, bkcom.typ, bkcom.suf "
                + "from bkcom where bkcom.age = ? and bkcom.ncp = ? and bkcom.clc = ? and"
                + ACTIVE_ACCOUNT_FILTER;
    }

    @Override
    public String checkNightMode() {
        return "select mnt4 from bknom where trim(ctab)='098' and trim(cacc)='SITE-CENT'";
    }

    @Override
    public String availableBalanceNormal() {
        return "select a.sin-nvl(a.minds,0)+nvl(maut,0) as solde,cha from bkcom a "
                + "left join bkautc b on a.age=b.age and a.dev=b.dev and a.ncp=b.ncp "
                + "and eta in ('VA','VF','FO') and SYSDATE between b.debut and b.fin "
                + "where a.cfe='N' and a.ife='N' and trim(a.age)= ? "
                + "and trim(a.ncp)= ? and trim(a.dev)= ? ";
    }

    @Override
    public String availableBalanceNight() {
        return "select a.sin-nvl(a.minds,0)+nvl(maut,0) as solde,cha from bkcom a "
                + "left join bkautc b on a.age=b.age and a.dev=b.dev and a.ncp=b.ncp "
                + "and eta in ('VA','VF','FO') and SYSDATE between b.debut and b.fin "
                + "where trim(a.cfe)='N' and trim(a.ife)='N' and trim(a.age)= ? "
                + "and trim(a.ncp)= ? and trim(a.dev)= ? ";
    }

    @Override
    public String findPhonesByCustomerReference() {
        return "select num from bktelcli where cli = ?";
    }

    @Override
    public String findEmailsByCustomerReference() {
        return "select email from bkemacli where cli = ?";
    }
}
