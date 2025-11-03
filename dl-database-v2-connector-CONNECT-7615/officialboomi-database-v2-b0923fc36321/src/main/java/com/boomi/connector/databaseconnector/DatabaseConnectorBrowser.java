// © Copyright IBM Corp. 2023, 2024
// Copyright (c) 2020 Boomi, Inc.
package com.boomi.connector.databaseconnector;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.boomi.connector.api.ConnectionTester;
import com.boomi.connector.api.ConnectorException;
import com.boomi.connector.api.ContentType;
import com.boomi.connector.api.ObjectDefinition;
import com.boomi.connector.api.ObjectDefinitionRole;
import com.boomi.connector.api.ObjectDefinitions;
import com.boomi.connector.api.ObjectType;
import com.boomi.connector.api.ObjectTypes;
import com.boomi.connector.api.OperationType;
import com.boomi.connector.databaseconnector.util.DatabaseConnectorConstants;
import com.boomi.connector.databaseconnector.util.ProcedureMetaDataUtil;
import com.boomi.connector.databaseconnector.util.SchemaBuilderUtil;
import com.boomi.connector.util.BaseBrowser;
import org.json.JSONObject;

/**
 * The Class DatabaseConnectorBrowser.
 *
 * @author swastik.vn
 */
public class DatabaseConnectorBrowser extends BaseBrowser implements ConnectionTester {

	/**
	 * Instantiates a new database connector browser.
	 *
	 * @param conn the conn
	 */
	public DatabaseConnectorBrowser(DatabaseConnectorConnection conn) {
		super(conn);
	}

	/** The Constant DATABASE_BATCHING. */
	private static final String DATABASE_BATCHING = "documentBatching";

	/** The Constant logger. */
	private static final Logger logger = Logger.getLogger(DatabaseConnectorBrowser.class.getName());

	/**
	 * Gets the object definitions.
	 *
	 * @param objectTypeId the object type id
	 * @param roles        the roles
	 * @return the object definitions
	 */
	@Override
	public ObjectDefinitions getObjectDefinitions(String objectTypeId, Collection<ObjectDefinitionRole> roles) {
		String customOpsType = getContext().getCustomOperationType();
		OperationType opsType = getContext().getOperationType();
		String getType = (String) getContext().getOperationProperties().get(DatabaseConnectorConstants.GET_TYPE);
		String updateType = (String) getContext().getOperationProperties().get(DatabaseConnectorConstants.TYPE);
		String deleteType = (String) getContext().getOperationProperties().get(DatabaseConnectorConstants.DELETE_TYPE);
		String insertType = (String) getContext().getOperationProperties().get(DatabaseConnectorConstants.INSERTION_TYPE);
		boolean enableQuery = getContext().getOperationProperties().getBooleanProperty("enableQuery", false);
		boolean isBatching = true;
		ObjectDefinitions objdefs = new ObjectDefinitions();
		DatabaseConnectorConnection conn = getConnection();
		try (Connection con = conn.getSoloConnection().connect(conn.getUrl(), conn.loadProperties())) {
			for (ObjectDefinitionRole role : roles) {
				ObjectDefinition objdef = new ObjectDefinition();
				String jsonSchema = null;
				switch (role) {
				case OUTPUT:
					if (DatabaseConnectorConstants.STOREDPROCEDUREWRITE.equals(customOpsType)) {
						List<String> outParams = ProcedureMetaDataUtil.getOutputParams(con, objectTypeId);
						jsonSchema = SchemaBuilderUtil.getProcedureSchema(con, objectTypeId, outParams);

					} else if (DatabaseConnectorConstants.GET.equals(customOpsType)) {
						if (getContext().getOperationProperties() != null
								&& !getContext().getOperationProperties().isEmpty()) {
							JSONObject jsonCookie = new JSONObject();
							isBatching = getContext().getOperationProperties().getBooleanProperty(DATABASE_BATCHING,
									false);
							jsonCookie.put(DATABASE_BATCHING, isBatching);
							objdef.withCookie(jsonCookie.toString());
						}
						jsonSchema = SchemaBuilderUtil.getJsonSchema(con, objectTypeId, false, true, isBatching);

					} else {
						jsonSchema = SchemaBuilderUtil.getQueryJsonSchema("");
					}
					if (jsonSchema == null) {
						objdefs = this.getUnstructuredSchema(objdef, objdefs);
					} else {
						objdefs = this.getJsonStructure(jsonSchema, objdef, objdefs);
					}

					break;

				case INPUT:
					if (DatabaseConnectorConstants.STOREDPROCEDUREWRITE.equals(customOpsType)) {
						List<String> inParams = ProcedureMetaDataUtil.getInputParams(con, objectTypeId);
						jsonSchema = SchemaBuilderUtil.getProcedureSchema(con, objectTypeId, inParams);
					} else if (DatabaseConnectorConstants.DYNAMIC_UPDATE.equals(updateType)) {
						jsonSchema = SchemaBuilderUtil.getQueryJsonSchema(updateType);
					} else if (DatabaseConnectorConstants.DYNAMIC_DELETE.equals(deleteType)) {
						jsonSchema = SchemaBuilderUtil.getQueryJsonSchema(deleteType);
					} else if (DatabaseConnectorConstants.DYNAMIC_INSERT.equals(insertType)
							|| DatabaseConnectorConstants.DYNAMIC_GET.equals(getType)
							|| OperationType.UPSERT.equals(opsType)) {
						jsonSchema = SchemaBuilderUtil.getJsonSchema(con, objectTypeId, false, false, false);
					}

					else {
					
						jsonSchema = SchemaBuilderUtil.getJsonSchema(con, objectTypeId, enableQuery, false, false);
					}
					if (jsonSchema != null) {
						objdefs = this.getJsonStructure(jsonSchema, objdef, objdefs);
					} else {
						objdefs = this.getUnstructuredSchema(objdef, objdefs);
					}
					break;
				default:
					break;
				}
			}
		} catch (SQLException e) {
			throw new ConnectorException(e.getMessage());
		}
		return objdefs;
	}

	/**
	 * Method that will take {@link ObjectDefinition} and build the unstructured
	 * Schema for request and response profile.
	 *
	 * @param objdef  the objdef
	 * @param objdefs the objdefs
	 * @return objdefs
	 */
	private ObjectDefinitions getUnstructuredSchema(ObjectDefinition objdef, ObjectDefinitions objdefs) {
		objdef.setElementName("");
		objdef.setOutputType(ContentType.BINARY);
		objdef.setInputType(ContentType.NONE);
		objdefs.getDefinitions().add(objdef);
		return objdefs;
	}

	/**
	 * This method will take {@link ObjectDefinition} and jsonSchema and it will
	 * build the structured Schema for request and response profile.
	 *
	 * @param jsonSchema the json schema
	 * @param objdef     the objdef
	 * @param objdefs    the objdefs
	 * @return objdefs
	 */
	private ObjectDefinitions getJsonStructure(String jsonSchema, ObjectDefinition objdef, ObjectDefinitions objdefs) {
		objdef.setElementName("");
		objdef.setJsonSchema(jsonSchema);
		objdef.setOutputType(ContentType.JSON);
		objdef.setInputType(ContentType.JSON);
		objdefs.getDefinitions().add(objdef);
		return objdefs;
	}

	/**
	 * This method will add the table names or the procedure names to the object
	 * type list based on the operation selected.
	 *
	 * @return the object types
	 */
	@Override
	public ObjectTypes getObjectTypes() {

		ObjectTypes objtypes = new ObjectTypes();
		List<ObjectType> objTypeList = new ArrayList<>();
		DatabaseConnectorConnection conn = getConnection();
		ResultSet resultSet = null;
		try (Connection con = conn.getSoloConnection().connect(conn.getUrl(), conn.loadProperties());) {
			DatabaseMetaData md = con.getMetaData();
			String opsType = getContext().getCustomOperationType();
			if (opsType != null && opsType.equals(DatabaseConnectorConstants.STOREDPROCEDUREWRITE)) {
				resultSet = md.getProcedures(null, con.getSchema(), "%");
				while (resultSet.next()) {
					String procedureName = resultSet.getString(DatabaseConnectorConstants.PROCEDURE_NAME);
					if (md.getDatabaseProductName().equals(DatabaseConnectorConstants.MSSQLSERVER)) {
						ObjectType objtype = new ObjectType();
						objtype.setId(procedureName.substring(0, procedureName.length() - 2));
						objTypeList.add(objtype);
					} else {
						ObjectType objtype = new ObjectType();
						objtype.setId(procedureName);
						objTypeList.add(objtype);
					}
				}
			} else {
				String tableNames = getContext().getOperationProperties().getProperty("tableNames", null);
				if (tableNames != null) {
					ObjectType objType = this.validate(tableNames, con);
					objTypeList.add(objType);
				} else {
					resultSet = md.getTables(null, con.getSchema(), null,
							new String[] { DatabaseConnectorConstants.TABLE });
					while (resultSet.next()) {
						ObjectType objtype = new ObjectType();
						objtype.setId(resultSet.getString(DatabaseConnectorConstants.TABLE_NAME));
						objTypeList.add(objtype);
					}
				}
			}
		} catch (Exception e) {
			logger.log(Level.SEVERE, "Unable to get the table names from the database {0}", e.getMessage());
		} finally {
			if (resultSet != null) {
				try {
					resultSet.close();
				} catch (SQLException e) {
					logger.log(Level.SEVERE, "Result set not closed properly!!");
				}
			}
		}
		objtypes.getTypes().addAll(objTypeList);
		return objtypes;
	}

	/**
	 * This method is to validate whether the table names provided by user exists in
	 * the database by Querying the particular table and fetching the 1st row if
	 * record exists.
	 *
	 * @param tableNames the table names
	 * @param con        the con
	 * @return the object type
	 * @throws SQLException
	 */
	private ObjectType validate(String tableNames, Connection con) throws SQLException {
		String[] tableName = tableNames.split("[,]", 0);
		ObjectType objectType = new ObjectType();
		for (String table : tableName) {
			try (PreparedStatement pstmnt = con.prepareStatement("SELECT count(*) FROM " + table.trim())) {
				pstmnt.setMaxRows(1);
				try (ResultSet rs = pstmnt.executeQuery()) {
					logger.fine(table + "exists!!");
				}
			} catch (SQLException e) {
				throw new ConnectorException(e.getMessage());
			}
		}
		if (con.getMetaData().getDatabaseProductName().equals("Oracle")) {
			objectType.setId(tableNames.toUpperCase());
		} else {
			objectType.setId(tableNames);
		}

		return objectType;

	}

	/**
	 * Gets Connection Object.
	 *
	 * @return the connection
	 */
	@Override
	public DatabaseConnectorConnection getConnection() {
		return (DatabaseConnectorConnection) super.getConnection();
	}

	/**
	 * Method to test the database Connection by taking connection parameters.
	 */
	@Override
	public void testConnection() {
		getConnection().test();
	}
}