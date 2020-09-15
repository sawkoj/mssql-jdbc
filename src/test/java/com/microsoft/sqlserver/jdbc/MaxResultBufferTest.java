/*
 * Microsoft JDBC Driver for SQL Server Copyright(c) Microsoft Corporation All rights reserved. This program is made
 * available under the terms of the MIT License. See the LICENSE file in the project root for more information.
 */

package com.microsoft.sqlserver.jdbc;

import com.microsoft.sqlserver.testframework.AbstractSQLGenerator;
import com.microsoft.sqlserver.testframework.AbstractTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Random;


/**
 * Class for testing maxResultBuffer property, all tests were performed on default connection settings
 * (defaultPacketLength = 8000), only changed were ResponsiveBuffering and MaxResultBuffer
 */
@RunWith(JUnitPlatform.class)
@DisplayName("maxResultBuffer Tests")
public class MaxResultBufferTest extends AbstractTest {

    @SuppressWarnings("SqlResolve")
    private static final String TEST_TABLE_NAME = "maxResultBufferTestTable";

    /**
     * This sets value of maxResultBuffer for each test
     */
    @BeforeEach
    void prepareMaxResultBuffer() {
        setMaxResultBuffer("10k");
    }

    /**
     * Create TEST_TABLE with 1 column nchar(precision) with numberOfRows. Let's calculate payload on example:
     * numberOfRows = 800 precision = 10
     *
     * Payload (in Bytes) = 49 (Header plus column metadata) + numberOfRows * (precision * 2 + 1 + 2) (3 extra bytes are
     * for column length and end of line character)
     *
     * So payload generated by this method = 49 + 800 * (10 * 2 + 2 + 1) = 49 + 800 * 23 = 18449
     *
     * Default packetLength = 8000, so payload is sent in 3 packets
     *
     * @throws SQLException
     *         Signalizes error when creating TEST_TABLE
     */
    @BeforeAll
    static void createAndPopulateNCharTestTable() throws SQLException {
        String insertSQL = "INSERT INTO " + AbstractSQLGenerator.escapeIdentifier(TEST_TABLE_NAME) + " VALUES (?)";
        int numberOfRows = 800;
        int precision = 10;

        try (Connection connection = DriverManager.getConnection(connectionString);
                Statement statement = connection.createStatement();
                PreparedStatement preparedStatement = connection.prepareStatement(insertSQL)) {

            // drop Table if exists and then create new one
            TestUtils.dropTableIfExists(AbstractSQLGenerator.escapeIdentifier(TEST_TABLE_NAME), statement);
            statement.execute("CREATE TABLE " + AbstractSQLGenerator.escapeIdentifier(TEST_TABLE_NAME)
                    + " ( col1 nchar(" + precision + "))");

            // insert into Table
            for (int i = 0; i < numberOfRows; i++) {
                preparedStatement.setString(1, generateRandomString(precision));
                preparedStatement.addBatch();
            }
            preparedStatement.executeBatch();
        }
    }

    @AfterAll
    static void teardownTestTable() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            TestUtils.dropTableIfExists(AbstractSQLGenerator.escapeIdentifier(TEST_TABLE_NAME), statement);
        }
    }

    /**
     * This method tests if ResultSets behave correctly when maxResultBuffer is set to 10000, they all should pass
     * (assuming default packetLength = 8000 and responsiveBuffering = adaptive)
     *
     * @throws SQLException
     *         Exception is thrown when maxResultBuffer is exceeded
     */
    @Test
    public void testResultSetLinearWithAdaptiveResponsiveBuffering() throws SQLException {
        setResponseBufferingAdaptive(true);

        testResultSet(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
        testResultSet(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_UPDATABLE);
        testResultSet(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
        testResultSet(ResultSet.TYPE_SCROLL_SENSITIVE, ResultSet.CONCUR_READ_ONLY);
        testResultSet(ResultSet.TYPE_SCROLL_SENSITIVE, ResultSet.CONCUR_UPDATABLE);
    }

    /**
     * This method tests if ResultSets behave correctly when maxResultBuffer is set to 10000, they all should pass
     * (assuming default packetLength = 8000 and responsiveBuffering = full)
     * 
     * @throws SQLException
     *         Exception is thrown when maxResultBuffer is exceeded
     */
    @Test
    public void testResultSetLinearWithFullResponsiveBuffering() throws SQLException {
        setResponseBufferingAdaptive(false);

        testResultSet(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_UPDATABLE);
        testResultSet(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
        testResultSet(ResultSet.TYPE_SCROLL_SENSITIVE, ResultSet.CONCUR_READ_ONLY);
        testResultSet(ResultSet.TYPE_SCROLL_SENSITIVE, ResultSet.CONCUR_UPDATABLE);
    }

    /**
     * This method tests if ResultSet behave correctly when maxResultBuffer is set to 10000, it should throw Exception
     * (assuming default packetLength = 8000 and responsiveBuffering = full)
     */
    @Test
    public void testResultSetLinearWithFullResponsiveBufferingException() {
        setResponseBufferingAdaptive(false);

        Assertions.assertThrows(SQLServerException.class,
                () -> testResultSet(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY));
    }

    /**
     * This method tests if when calling multiple PreparedStatements with their own ResultSet, JDBC driver would behave
     * appropriately, assuming maxResultBuffer is set to 10000, it should throw Exception (assuming default packetLength
     * = 8000 and responsiveBuffering = adaptive)
     */
    @Test
    public void testPreparedStatementMultipleResultSetsWithAdaptiveResponseBuffering() {
        setResponseBufferingAdaptive(true);

        Assertions.assertThrows(SQLServerException.class, this::testPreparedStatementWithMultipleResultSets);
    }

    /**
     * This method tests if when calling multiple PreparedStatements with their own ResultSet, JDBC driver would behave
     * appropriately, assuming maxResultBuffer is set to 10000, it should throw Exception (assuming default packetLength
     * = 8000 and responsiveBuffering = full)
     */
    @Test
    public void testPreparedStatementMultipleResultSetsWithFullResponseBuffering() {
        setResponseBufferingAdaptive(false);

        Assertions.assertThrows(SQLServerException.class, this::testPreparedStatementWithMultipleResultSets);
    }

    /**
     * This method tests if calling multiple Queries in one Statement works properly. When maxResultBuffer is set to
     * 10000, it should work properly (assuming default packetLength = 8000 and responsiveBuffering = adaptive)
     * 
     * @throws SQLException
     *         Exception is thrown when maxResultBuffer is exceeded
     */
    @Test
    public void testTwoQueriesInOneStatementWithAdaptiveResponseBuffering() throws SQLException {
        setResponseBufferingAdaptive(true);

        testTwoQueriesInOneStatement();
    }

    /**
     * This method tests if calling multiple Queries in one Statement works properly. When maxResultBuffer is set to
     * 10000, it should throw Exception (assuming default packetLength = 8000 and responsiveBuffering = full)
     */
    @Test
    public void testTwoQueriesInOneStatementWithFullResponseBuffering() {
        setResponseBufferingAdaptive(false);

        Assertions.assertThrows(SQLServerException.class, this::testTwoQueriesInOneStatement);
    }

    /**
     * This method tests if all packets from ResultSet are correctly retrieved
     *
     * @param resultSetType
     *        a result set type; one of <code>ResultSet.TYPE_FORWARD_ONLY</code>,
     *        <code>ResultSet.TYPE_SCROLL_INSENSITIVE</code>, or <code>ResultSet.TYPE_SCROLL_SENSITIVE</code>
     * @param concurrencyMode
     *        a concurrency type; one of <code>ResultSet.CONCUR_READ_ONLY</code> or
     *        <code>ResultSet.CONCUR_UPDATABLE</code>
     *
     * @throws SQLException
     *         Exception is thrown when maxResultBuffer is exceeded
     */
    private void testResultSet(int resultSetType, int concurrencyMode) throws SQLException {
        try (Connection connection = DriverManager.getConnection(connectionString);
                Statement statement = connection.createStatement(resultSetType, concurrencyMode)) {
            statement.execute("SELECT * FROM " + TEST_TABLE_NAME);
            try (ResultSet resultSet = statement.getResultSet()) {
                while (resultSet.next()) {}
            }
        }
    }

    /**
     * This method tests if Statements are detached properly, when first one hasn't been completely retrieved and second
     * one have been executed
     *
     * @throws SQLException
     *         Exception is thrown when maxResultBuffer is exceeded
     */
    private void testPreparedStatementWithMultipleResultSets() throws SQLException {
        String selectSQL = "SELECT * FROM " + TEST_TABLE_NAME;

        try (Connection connection = DriverManager.getConnection(connectionString);
                PreparedStatement statement = connection.prepareStatement(selectSQL);
                ResultSet resultSet = statement.executeQuery()) {

            try (PreparedStatement secondStatement = connection.prepareStatement(selectSQL);
                    ResultSet secondResultSet = secondStatement.executeQuery()) {
                while (resultSet.next()) {}

                try (PreparedStatement thirdStatement = connection.prepareStatement(selectSQL);
                        ResultSet thirdResultSet = thirdStatement.executeQuery()) {
                    while (thirdResultSet.next()) {}
                    while (secondResultSet.next()) {}
                }
            }
        }
    }

    /**
     * This method tests if ResultSet's are retrieved correctly, when more than one Query is executed inside single
     * statement
     * 
     * @throws SQLException
     *         Exception is thrown when maxResultBuffer is exceeded
     */
    private void testTwoQueriesInOneStatement() throws SQLException {
        try (Connection connection = DriverManager.getConnection(connectionString);
                Statement statement = connection.createStatement()) {
            statement.execute("SELECT * FROM " + TEST_TABLE_NAME + ";SELECT * FROM " + TEST_TABLE_NAME);

            try (ResultSet resultSet = statement.getResultSet()) {
                while (resultSet.next()) {}
            }

            if (statement.getMoreResults()) {
                try (ResultSet totallyNewResultSet = statement.getResultSet()) {
                    while (totallyNewResultSet.next()) {}
                }
            }
        }
    }

    private static String generateRandomString(int precision) {
        int leftLimit = 33;
        int rightLimit = 126;
        Random random = new Random();
        return random.ints(leftLimit, rightLimit).limit(precision)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append).toString();
    }

    private static void setResponseBufferingAdaptive(boolean adaptive) {
        String value = adaptive ? "adaptive" : "full";
        connectionString = TestUtils.addOrOverrideProperty(connectionString, "responseBuffering", value);
        AbstractTest.updateDataSource(connectionString, ds);
    }

    private static void setMaxResultBuffer(String maxResultBuffer) {
        connectionString = TestUtils.addOrOverrideProperty(connectionString, "maxResultBuffer", maxResultBuffer);
        AbstractTest.updateDataSource(connectionString, ds);
    }
}
