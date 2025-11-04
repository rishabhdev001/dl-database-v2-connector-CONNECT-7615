// © Copyright IBM Corp. 2023, 2024
// Copyright (c) 2020 Boomi, Inc.
package com.boomi.connector.databaseconnector.util;

import static com.boomi.connector.databaseconnector.util.DatabaseConnectorConstants.*;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import com.boomi.connector.databaseconnector.util.MetadataUtil;

import com.boomi.connector.api.ConnectorException;
import com.boomi.util.json.JSONUtil;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * This Util Class will Generate the JSON Schema by taking each column name from
 * the table and associated DataTypes of the column. Based on the column
 * datatype the Schema Type will be either String, Boolean or Number.
 * 
 * @author swastik.vn
 *
 */
public class SchemaBuilderUtil {

	private static final String BACKSLASH_OBJECT = "\": \"object\",";

	private static final String BACKSLASH_BRACKET = "\": {";

	/**
	 * Instantiates a new schema builder util.
	 */
	private SchemaBuilderUtil() {

	}

	/**
	 * Build Json Schema Based on the Database Column names and column types.
	 *
	 * @param con          the con
	 * @param objectTypeId the object type id
	 * @param enableQuery  the enable query
	 * @param output       the output
	 * @return the json schema
	 */
	public static String getJsonSchema(Connection con, String objectTypeId, boolean enableQuery, boolean output, boolean isBatching) {

		String jsonSchema = null;
		StringBuilder sbSchema = new StringBuilder();
		String json = null;
		Map<String, String> dataTypes = new HashMap<>();
		String[] tableNames = getTableNames(objectTypeId);
		try {
			if (objectTypeId.contains(",")) {
				for (String tableName : tableNames) {
					dataTypes.putAll(MetadataUtil.getDataTypesWithTable(con, tableName.trim()));
				}
			} else {
				dataTypes.putAll(MetadataUtil.getDataTypes(con, objectTypeId));
			}
			DatabaseMetaData md = con.getMetaData();
			if (isBatching) {
				sbSchema.append("{").append(JSON_DRAFT4_DEFINITION).append(" \"").append(
								JSONUtil.SCHEMA_TYPE).append("\": \"array\",").append(" \"").append("items\":{")
						.append(" \"").append(JSONUtil.SCHEMA_TYPE).append(BACKSLASH_OBJECT).append(" \"")
						.append(JSONUtil.SCHEMA_PROPERTIES).append(BACKSLASH_BRACKET);
			}
			else{
				sbSchema.append("{").append(JSON_DRAFT4_DEFINITION).append(" \"").append(
								JSONUtil.SCHEMA_TYPE).append(BACKSLASH_OBJECT).append(" \"")
						.append(JSONUtil.SCHEMA_PROPERTIES).append(BACKSLASH_BRACKET);
			}
			for (String tableName : tableNames) {
				try (ResultSet resultSet = md.getColumns(null, null, tableName.trim(), null);) {
					while (resultSet.next()) {
						String param = "";
						if (objectTypeId.contains(",")) {
							param = tableName.trim() + DOT + resultSet.getString(COLUMN_NAME);
						} else {
							param = resultSet.getString(COLUMN_NAME);
						}
						if (output) {
							sbSchema.append(BACKSLASH).append(resultSet.getString(COLUMN_NAME)).append("\": {");
						} else {
							sbSchema.append(BACKSLASH).append(param).append("\": {");
						}
						if (dataTypes.get(param) == null) {
							throw new SQLException("The data type " + resultSet.getString(TYPE_NAME)
									+ " is not supported in the connector!");
						} else if (dataTypes.get(param).equals(STRING) || dataTypes.get(param).equals(TIME)
								|| dataTypes.get(param).equals(DATE) || dataTypes.get(param).equals(JSON)
								|| dataTypes.get(param).equals(NVARCHAR) || dataTypes.get(param).equals(CHAR)) {
							sbSchema.append(BACKSLASH).append(JSONUtil.SCHEMA_TYPE).append("\": \"").append(STRING).append(BACKSLASH);
						} else if (dataTypes.get(param).equals(INTEGER)) {
							sbSchema.append(BACKSLASH).append(JSONUtil.SCHEMA_TYPE).append("\": \"").append(INTEGER).append(BACKSLASH);
						} else if (dataTypes.get(param).equals(DOUBLE) || dataTypes.get(param).equals(DECIMAL) || dataTypes.get(param).equals(FLOAT)) {
							sbSchema.append(BACKSLASH).append(JSONUtil.SCHEMA_TYPE).append("\": \"").append(DOUBLE).append(BACKSLASH);
						} else if (dataTypes.get(param).equals(BIGDECIMAL)) {
							sbSchema.append(BACKSLASH).append(JSONUtil.SCHEMA_TYPE).append("\": \"").append(BIGDECIMAL).append(BACKSLASH);
						} else if (dataTypes.get(param).equals(BIGINT)) {
							sbSchema.append(BACKSLASH).append(JSONUtil.SCHEMA_TYPE).append("\": \"").append(BIGINT).append(BACKSLASH);
						}else if (dataTypes.get(param).equals(BOOLEAN)) {
							sbSchema.append(BACKSLASH).append(JSONUtil.SCHEMA_TYPE).append("\": \"").append(BOOLEAN).append(BACKSLASH);
						}
						sbSchema.append("},");
					}
					if (enableQuery) {
						sbSchema.append(BACKSLASH).append(SQL_QUERY).append("\": {");
						sbSchema.append(BACKSLASH).append(JSONUtil.SCHEMA_TYPE).append("\": \"").append(STRING).append(BACKSLASH);
						sbSchema.append("},");
					}
				}
			}

		} catch (SQLException e) {
			throw new ConnectorException(e.getMessage());
		}

		sbSchema.deleteCharAt(sbSchema.length() - 1);
		sbSchema.append("}}");

		if (isBatching) {
			sbSchema.append("}");
		}

		json = sbSchema.toString();
		JsonNode rootNode = null;

		try {

			rootNode = JSONUtil.getDefaultObjectMapper().readTree(json);
			if (rootNode != null) {
				jsonSchema = JSONUtil.prettyPrintJSON(rootNode);

			}
		} catch (Exception e) {
			throw new ConnectorException(SCHEMA_BUILDER_EXCEPTION, e.getMessage());
		}

		return jsonSchema;
	}

	/**
	 * Gets the table names.
	 *
	 * @param objectTypeId the object type id
	 * @return the table names
	 */
	public static String[] getTableNames(String objectTypeId) {
		if (objectTypeId.contains(",")) {
			return objectTypeId.split("[,]", 0);
		} else {
			return new String[] { objectTypeId };
		}
	}
}
