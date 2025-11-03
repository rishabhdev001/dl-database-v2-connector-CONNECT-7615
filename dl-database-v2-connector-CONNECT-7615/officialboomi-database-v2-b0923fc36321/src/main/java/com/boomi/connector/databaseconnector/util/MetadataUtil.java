// © Copyright IBM Corp. 2023, 2024
// Copyright (c) 2020 Boomi, Inc.
package com.boomi.connector.databaseconnector.util;
import com.boomi.connector.databaseconnector.DatabaseConnectorBrowser;
import static com.boomi.connector.databaseconnector.util.DatabaseConnectorConstants.*;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * The Class MetadataUtil.
 *
 * @author swastik.vn
 */
public class MetadataUtil {

	/**
	 * Instantiates a new metadata util.
	 */
	private MetadataUtil() {

	}

	/** The Constant VARCHAR. */
	public static final String VARCHAR = "12";

	/** The Constant CLOB. */
	public static final String CLOB = "2005";

	/** The Constant LONGVARCHAR. */
	public static final String LONGVARCHAR = "-1";

	/** The Constant INTEGER. */
	public static final String INTEGER = "4";

	/** The Constant NUMERIC. */
	public static final String NUMERIC = "2";

	/** The Constant TIMESTAMP. */
	public static final String TIMESTAMP = "93";

	/** The Constant DATE. */
	public static final String DATE = "91";

	/** The Constant TINYINT. */
	public static final String TINYINT = "-6";

	/** The Constant BOOLEAN. */
	public static final String BOOLEAN = "16";

	/** The Constant BIT. */
	public static final String BIT = "-7";

	/** The Constant TIME. */
	public static final String TIME = "92";

	/** The Constant NVARCHAR. */
	public static final String NVARCHAR = "-9";

	/** The Constant CHAR. */
	public static final String CHAR = "1";
	/** The Constant FLOAT. */
	public static final String FLOAT = "6";

	/** The Constant DOUBLE. */
	public static final String DOUBLE = "8";

	/** The Constant DECIMAL. */
	public static final String DECIMAL = "3";


	/** The Constant BIGINT. */
	public static final String BIGINT = "-5";


	/**
	 * This method will fetch the dataTypes of the columns and stores it in a Map.
	 *
	 * @param con          the con
	 * @param objectTypeId the object type id
	 * @return type
	 * @throws SQLException the SQL exception
	 */
	public static Map<String, String> getDataTypesWithTable(Connection con, String objectTypeId) throws SQLException {

		Map<String, String> type = new HashMap<>();
		DatabaseMetaData md = con.getMetaData();

		try (ResultSet resultSet1 = md.getColumns(null, null, objectTypeId, null);) {
			while (resultSet1.next()) {
				if (resultSet1.getString(TYPE_NAME).equalsIgnoreCase(DatabaseConnectorConstants.JSON)) {
						type.put(objectTypeId+DOT+resultSet1.getString(COLUMN_NAME), DatabaseConnectorConstants.JSON);
				} else if (resultSet1.getString(TYPE_NAME).equalsIgnoreCase(DatabaseConnectorConstants.NVARCHAR)) {
					type.put(objectTypeId+DOT+resultSet1.getString(COLUMN_NAME), DatabaseConnectorConstants.NVARCHAR);
				} else if (resultSet1.getString(DATA_TYPE).equals(VARCHAR)
						|| resultSet1.getString(DATA_TYPE).equals(LONGVARCHAR)
						|| resultSet1.getString(DATA_TYPE).equals(NVARCHAR)
						|| resultSet1.getString(DATA_TYPE).equals(CHAR)
						|| resultSet1.getString(TYPE_NAME).equalsIgnoreCase(DatabaseConnectorConstants.CLOB)) {
					type.put(objectTypeId+DOT+resultSet1.getString(COLUMN_NAME), DatabaseConnectorConstants.STRING);
				} else if (resultSet1.getString(DATA_TYPE).equals(INTEGER)
						|| resultSet1.getString(DATA_TYPE).equals(NUMERIC) || resultSet1.getString(DATA_TYPE).equals(NUMBER) || resultSet1.getString(TYPE_NAME).equalsIgnoreCase(DatabaseConnectorConstants.BINARY_DOUBLE)) {
					type.put(objectTypeId+DOT+resultSet1.getString(COLUMN_NAME), DatabaseConnectorConstants.INTEGER);
				} else if (resultSet1.getString(DATA_TYPE).equals(DATE)
						|| resultSet1.getString(DATA_TYPE).equals(TIMESTAMP)) {
					type.put(objectTypeId+DOT+resultSet1.getString(COLUMN_NAME), DatabaseConnectorConstants.DATE);
				} else if (resultSet1.getString(DATA_TYPE).equals(TINYINT)
						|| resultSet1.getString(DATA_TYPE).equals(BOOLEAN)
						|| resultSet1.getString(DATA_TYPE).equals(BIT)) {
					type.put(objectTypeId+DOT+resultSet1.getString(COLUMN_NAME), DatabaseConnectorConstants.BOOLEAN);
				} else if (resultSet1.getString(DATA_TYPE).equals(TIME)) {
					type.put(objectTypeId+DOT+resultSet1.getString(COLUMN_NAME), DatabaseConnectorConstants.TIME);
				} else if (resultSet1.getString(DATA_TYPE).equals(DOUBLE) || resultSet1.getString(DATA_TYPE).equals(DECIMAL) || resultSet1.getString(DATA_TYPE).equals(FLOAT)) {
					type.put(objectTypeId+DOT+resultSet1.getString(COLUMN_NAME), DatabaseConnectorConstants.DOUBLE);
				} else if (resultSet1.getString(DATA_TYPE).equals(BIGDECIMAL)) {
					type.put(objectTypeId+DOT+resultSet1.getString(COLUMN_NAME), DatabaseConnectorConstants.DECIMAL);
				} else if (resultSet1.getString(DATA_TYPE).equals(BIGINT)) {
					type.put(objectTypeId+DOT+resultSet1.getString(COLUMN_NAME), DatabaseConnectorConstants.BIGINT);
				}else {
					type.put(objectTypeId+DOT+resultSet1.getString(COLUMN_NAME), null);
				}
			}
		}

		return type;

	}

	public static Map<String, String> getDataTypes(Connection con, String objectTypeId) throws SQLException {

		Map<String, String> type = new HashMap<>();
		DatabaseMetaData md = con.getMetaData();

		try (ResultSet resultSet1 = md.getColumns(null, null, objectTypeId, null);) {
			while (resultSet1.next()) {
				if (resultSet1.getString(TYPE_NAME).equalsIgnoreCase(DatabaseConnectorConstants.JSON)) {
						type.put(resultSet1.getString(COLUMN_NAME), DatabaseConnectorConstants.JSON);
				} else if (resultSet1.getString(TYPE_NAME).equalsIgnoreCase(DatabaseConnectorConstants.NVARCHAR)) {
					type.put(resultSet1.getString(COLUMN_NAME), DatabaseConnectorConstants.NVARCHAR);
				} else if (resultSet1.getString(DATA_TYPE).equals(VARCHAR)
						|| resultSet1.getString(DATA_TYPE).equals(LONGVARCHAR)
						|| resultSet1.getString(DATA_TYPE).equals(NVARCHAR)
						|| resultSet1.getString(DATA_TYPE).equals(CHAR)
						|| resultSet1.getString(TYPE_NAME).equalsIgnoreCase(DatabaseConnectorConstants.CLOB)) {
					type.put(resultSet1.getString(COLUMN_NAME), DatabaseConnectorConstants.STRING);
				} else if (resultSet1.getString(DATA_TYPE).equals(INTEGER)
						|| resultSet1.getString(DATA_TYPE).equals(NUMERIC) || resultSet1.getString(DATA_TYPE).equals(NUMBER) || resultSet1.getString(TYPE_NAME).equalsIgnoreCase(DatabaseConnectorConstants.BINARY_DOUBLE)) {
					type.put(resultSet1.getString(COLUMN_NAME), DatabaseConnectorConstants.INTEGER);
				} else if (resultSet1.getString(DATA_TYPE).equals(DATE)
						|| resultSet1.getString(DATA_TYPE).equals(TIMESTAMP)) {
					type.put(resultSet1.getString(COLUMN_NAME), DatabaseConnectorConstants.DATE);
				} else if (resultSet1.getString(DATA_TYPE).equals(TINYINT)
						|| resultSet1.getString(DATA_TYPE).equals(BOOLEAN)
						|| resultSet1.getString(DATA_TYPE).equals(BIT)) {
					type.put(resultSet1.getString(COLUMN_NAME), DatabaseConnectorConstants.BOOLEAN);
				} else if (resultSet1.getString(DATA_TYPE).equals(TIME)) {
					type.put(resultSet1.getString(COLUMN_NAME), DatabaseConnectorConstants.TIME);
				} else if (resultSet1.getString(DATA_TYPE).equals(DOUBLE) || resultSet1.getString(DATA_TYPE).equals(DECIMAL) || resultSet1.getString(DATA_TYPE).equals(FLOAT)) {
					type.put(objectTypeId+DOT+resultSet1.getString(COLUMN_NAME), DatabaseConnectorConstants.DOUBLE);
				} else if (resultSet1.getString(DATA_TYPE).equals(BIGDECIMAL)) {
					type.put(objectTypeId+DOT+resultSet1.getString(COLUMN_NAME), DatabaseConnectorConstants.DECIMAL);
				} else if (resultSet1.getString(DATA_TYPE).equals(BIGINT)) {
					type.put(objectTypeId+DOT+resultSet1.getString(COLUMN_NAME), DatabaseConnectorConstants.BIGINT);
				}else {
					type.put(resultSet1.getString(COLUMN_NAME), null);
				}
			}
		}

		return type;

	}

}