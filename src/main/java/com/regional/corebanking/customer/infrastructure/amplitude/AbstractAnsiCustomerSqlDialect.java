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
        return "select bkcom.age, bkcom.ncp, bkcom.clc, bkcom.cli, bkcom.dev "
                + "from bkcom where bkcom.cli = ? and" + ACTIVE_ACCOUNT_FILTER;
    }

    @Override
    public String findAccountByReferenceParts() {
        return "select bkcom.age, bkcom.ncp, bkcom.clc, bkcom.cli, bkcom.dev "
                + "from bkcom where bkcom.age = ? and bkcom.ncp = ? and bkcom.clc = ? and"
                + ACTIVE_ACCOUNT_FILTER;
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
