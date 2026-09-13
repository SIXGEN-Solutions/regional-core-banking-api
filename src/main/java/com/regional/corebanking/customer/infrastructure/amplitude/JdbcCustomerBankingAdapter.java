package com.regional.corebanking.customer.infrastructure.amplitude;

import com.regional.corebanking.customer.application.exception.BankingAccessException;
import com.regional.corebanking.customer.application.exception.BankingErrorCode;
import com.regional.corebanking.customer.application.port.out.CustomerBankingPort;
import com.regional.corebanking.customer.domain.BankAccount;
import com.regional.corebanking.customer.domain.CustomerIdentity;
import com.regional.corebanking.customer.domain.CustomerSummary;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class JdbcCustomerBankingAdapter implements CustomerBankingPort {

    private final DataSource dataSource;
    private final CustomerSqlDialect dialect;
    private final String financialInstitutionCode;

    public JdbcCustomerBankingAdapter(
            DataSource dataSource,
            CustomerSqlDialect dialect,
            String financialInstitutionCode
    ) {
        this.dataSource = dataSource;
        this.dialect = dialect;
        if (financialInstitutionCode == null || financialInstitutionCode.isBlank()) {
            throw new IllegalArgumentException("financialInstitutionCode is required");
        }
        this.financialInstitutionCode = financialInstitutionCode.strip();
    }

    @Override
    public List<CustomerSummary> searchCustomers(
            String requestedInstitution,
            String niu,
            String customerNumber
    ) {
        validateInstitution(requestedInstitution);

        boolean hasNiu = notBlank(niu);
        boolean hasCustomerNumber = notBlank(customerNumber);

        if (!hasNiu && !hasCustomerNumber) {
            throw new IllegalArgumentException("At least one search criterion is required");
        }

        if (hasNiu && hasCustomerNumber) {
            return queryCustomers(
                    dialect.searchByCustomerNumberAndNiu(),
                    List.of(customerNumber.strip(), niu.strip())
            );
        }
        if (hasCustomerNumber) {
            return queryCustomers(
                    dialect.searchByCustomerNumber(),
                    List.of(customerNumber.strip())
            );
        }
        return queryCustomers(
                dialect.searchByNiu(),
                List.of(niu.strip())
        );
    }

    @Override
    public CustomerIdentity getCustomer(
            String requestedInstitution,
            String customerReference
    ) {
        validateInstitution(requestedInstitution);

        try (Connection connection = openConnection();
             PreparedStatement statement =
                     connection.prepareStatement(dialect.findIdentityByCustomerReference())) {

            statement.setString(1, customerReference);

            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }

                String reference = trim(rs.getString("cli"));
                String niu = trimToNull(rs.getString("nid"));
                String legalName = trimToNull(rs.getString("nomrest"));

                String phone = firstPhone(connection, reference);
                String email = firstEmail(connection, reference);

                List<CustomerIdentity.KycField> fields = List.of(
                        field("niu", niu),
                        field("legalName", legalName),
                        field("phoneNumber", phone),
                        field("email", email)
                );

                return new CustomerIdentity(
                        reference,
                        reference,
                        financialInstitutionCode,
                        niu,
                        legalName,
                        phone,
                        email,
                        CustomerIdentity.KycStatus.UNKNOWN,
                        fields,
                        null,
                        Instant.now()
                );
            }
        } catch (SQLException exception) {
            throw bankError("Unable to load customer identity", exception);
        }
    }

    @Override
    public List<BankAccount> getCustomerAccounts(
            String requestedInstitution,
            String customerReference,
            String rib,
            String iban
    ) {
        validateInstitution(requestedInstitution);

        if (notBlank(rib) || notBlank(iban)) {
            throw new IllegalArgumentException(
                    "RIB/IBAN filtering is not implemented because no authoritative mapping "
                            + "to Amplitude columns has been supplied"
            );
        }

        try (Connection connection = openConnection();
             PreparedStatement statement =
                     connection.prepareStatement(dialect.findAccountsByCustomerReference())) {

            statement.setString(1, customerReference);

            try (ResultSet rs = statement.executeQuery()) {
                List<BankAccount> accounts = new ArrayList<>();
                while (rs.next()) {
                    accounts.add(mapAccount(rs));
                }
                return List.copyOf(accounts);
            }
        } catch (SQLException exception) {
            throw bankError("Unable to load customer accounts", exception);
        }
    }

    @Override
    public BankAccount findAccountByReference(String accountReference) {
        AccountReferenceParts parts = AccountReferenceParts.parse(accountReference);

        try (Connection connection = openConnection();
             PreparedStatement statement =
                     connection.prepareStatement(dialect.findAccountByReferenceParts())) {

            statement.setString(1, parts.age());
            statement.setString(2, parts.ncp());
            statement.setString(3, parts.clc());

            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? mapAccount(rs) : null;
            }
        } catch (SQLException exception) {
            throw bankError("Unable to load bank account", exception);
        }
    }

    private List<CustomerSummary> queryCustomers(String sql, List<String> parameters) {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            for (int i = 0; i < parameters.size(); i++) {
                statement.setString(i + 1, parameters.get(i));
            }

            try (ResultSet rs = statement.executeQuery()) {
                List<CustomerSummary> customers = new ArrayList<>();
                if (rs.next()) {
                    String reference = trim(rs.getString("cli"));
                    customers.add(new CustomerSummary(
                            reference,
                            reference,
                            financialInstitutionCode,
                            trimToNull(rs.getString("nid")),
                            trimToNull(rs.getString("nomrest"))
                    ));
                }
                return List.copyOf(customers);
            }
        } catch (SQLException exception) {
            throw bankError("Unable to search customer", exception);
        }
    }

    private Connection openConnection() throws SQLException {
        Connection connection = dataSource.getConnection();
        boolean prepared = false;
        try {
            connection.setReadOnly(true);
            dialect.prepareConnection(connection);
            prepared = true;
            return connection;
        } finally {
            if (!prepared) {
                connection.close();
            }
        }
    }

    private String firstPhone(Connection connection, String customerReference) throws SQLException {
        try (PreparedStatement statement =
                     connection.prepareStatement(dialect.findPhonesByCustomerReference())) {
            statement.setString(1, customerReference);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? trimToNull(rs.getString("num")) : null;
            }
        } catch (SQLException exception) {
            throw new BankingAccessException(
                    BankingErrorCode.BANK_PHONE_ERROR,
                    "Unable to load customer phone",
                    exception
            );
        }
    }

    private String firstEmail(Connection connection, String customerReference) throws SQLException {
        try (PreparedStatement statement =
                     connection.prepareStatement(dialect.findEmailsByCustomerReference())) {
            statement.setString(1, customerReference);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? trimToNull(rs.getString("email")) : null;
            }
        }
    }

    private BankAccount mapAccount(ResultSet rs) throws SQLException {
        String accountReference =
                trim(rs.getString("age")) + "-"
                        + trim(rs.getString("ncp")) + "-"
                        + trim(rs.getString("clc"));

        return new BankAccount(
                accountReference,
                trim(rs.getString("cli")),
                financialInstitutionCode,
                mask(accountReference),
                trim(rs.getString("dev")),
                BankAccount.AccountType.UNKNOWN,
                BankAccount.AccountStatus.ACTIVE,
                Set.of(),
                Instant.now()
        );
    }

    private CustomerIdentity.KycField field(String code, String value) {
        return new CustomerIdentity.KycField(
                code,
                value,
                notBlank(value),
                null,
                null
        );
    }

    private void validateInstitution(String requestedInstitution) {
        if (!financialInstitutionCode.equals(requestedInstitution)) {
            throw new IllegalArgumentException("Unsupported financialInstitutionCode");
        }
    }

    private BankingAccessException bankError(String message, SQLException exception) {
        return new BankingAccessException(BankingErrorCode.BANK_ERROR, message, exception);
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    private static String trimToNull(String value) {
        String trimmed = trim(value);
        return notBlank(trimmed) ? trimmed : null;
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static String mask(String value) {
        if (value == null || value.length() <= 4) {
            return value;
        }
        return "*".repeat(value.length() - 4)
                + value.substring(value.length() - 4);
    }

    private record AccountReferenceParts(String age, String ncp, String clc) {
        static AccountReferenceParts parse(String reference) {
            if (reference == null || reference.isBlank()) {
                throw new IllegalArgumentException("accountReference is required");
            }
            String[] parts = reference.strip().split("-", -1);
            if (parts.length != 3
                    || parts[0].isBlank()
                    || parts[1].isBlank()
                    || parts[2].isBlank()) {
                throw new IllegalArgumentException(
                        "Unsupported accountReference format; expected age-ncp-clc"
                );
            }
            return new AccountReferenceParts(parts[0], parts[1], parts[2]);
        }
    }
}
