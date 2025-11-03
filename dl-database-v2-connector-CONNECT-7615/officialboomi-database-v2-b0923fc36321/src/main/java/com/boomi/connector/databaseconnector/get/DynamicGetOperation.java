// © Copyright IBM Corp. 2023, 2024
// Copyright (c) 2020 Boomi, Inc.
package com.boomi.connector.databaseconnector.get;

import static com.boomi.connector.databaseconnector.util.DatabaseConnectorConstants.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Types;
import java.util.Iterator;
import java.util.Map;
import java.util.logging.Logger;

import com.boomi.connector.api.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.*;
import org.apache.commons.text.StringEscapeUtils;
import org.json.JSONObject;
import org.postgresql.util.PGobject;

import com.boomi.connector.databaseconnector.DatabaseConnectorConnection;
import com.boomi.connector.databaseconnector.util.CustomPayloadUtil;
import com.boomi.connector.databaseconnector.util.CustomResponseUtil;
import com.boomi.connector.databaseconnector.util.DatabaseConnectorConstants;
import com.boomi.connector.databaseconnector.util.MetadataUtil;
import com.boomi.connector.util.SizeLimitedUpdateOperation;
import com.boomi.util.IOUtil;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import oracle.jdbc.OracleType;
import oracle.sql.json.OracleJsonFactory;
import oracle.sql.json.OracleJsonObject;

/**
 * The Class DynamicGetOperation.
 *
 * @author swastik.vn
 */
public class DynamicGetOperation extends SizeLimitedUpdateOperation {

	/**
	 * Instantiates a new dynamic get operation.
	 *
	 * @param con the con
	 */
	public DynamicGetOperation(DatabaseConnectorConnection con) {
		super(con);
	}

	/** The Constant logger. */
	private static final Logger logger = Logger.getLogger(DynamicGetOperation.class.getName());

	public static final Long DEFAULT_BATCH_COUNT = 10_000L;


	/**
	 * Execute size limited update.
	 *
	 * @param request  the request
	 * @param response the response
	 */
	@Override
	public void executeSizeLimitedUpdate(UpdateRequest request, OperationResponse response) {
		logger.info("Entered executeSizeLimitedUpdate method ");
		logger.info("This is the UpdatedDatabase code version 27");
		DatabaseConnectorConnection conn = getConnection();
		Long maxRows = getContext().getOperationProperties().getLongProperty(DatabaseConnectorConstants.MAX_ROWS);
		String linkElement = getContext().getOperationProperties().getProperty(DatabaseConnectorConstants.LINK_ELEMENT,
				"");
		Long maxFieldSize = getContext().getOperationProperties().getLongProperty("maxFieldSize");
		try (Connection con = conn.getSoloConnection().connect(conn.getUrl(), conn.loadProperties())) {
			logger.info("execute size limited update calling executeStatements " + " maxFieldSize " + maxFieldSize +" maxRows "+ maxRows);
			this.executeStatements(con, request, response, maxRows, linkElement, maxFieldSize);
		} catch (Exception e) {
			ResponseUtil.addExceptionFailures(response, request, e);
		}
	}

	/**
	 * Method which will fetch the maxRows linkElement from Operation UI and
	 * dynamically creates the Select Statements.
	 *
	 * @param con          the con
	 * @param trackedData  the tracked data
	 * @param response     the response
	 * @param maxRows      the max rows
	 * @param linkElement  the link element
	 * @param maxFieldSize the max field size
	 * @throws SQLException the SQL exception
	 */
	private void executeStatements(Connection con, UpdateRequest trackedData, OperationResponse response, Long maxRows,
								   String linkElement, Long maxFieldSize) throws SQLException {
		boolean inClause = getContext().getOperationProperties().getBooleanProperty("INClause", false);
		Map<String, String> dataTypes = MetadataUtil.getDataTypes(con, getContext().getObjectTypeId());
		logger.info("inside executeStatements method");
		for (ObjectData objdata : trackedData) {
			PreparedStatement st = null;
			String query = objdata.getDynamicProperties().get("sqlQuery");
			//String query = "select * from test LIMIT 82";
			logger.info("Query being executed from executeStatements is: "+ query);
			try {
				//StringBuilder query = this.buildQuery(objdata, dataTypes, con);
				//String query = getContext().getOperationProperties().getProperty(DatabaseConnectorConstants.QUERY, "Select * from Regions");
				//if (linkElement != null && !linkElement.equalsIgnoreCase("")) {
				//	this.addLinkElement(query, linkElement);
				//}
				st = con.prepareStatement(query);
				//if (inClause && inCheck(query)) {
				//	this.inClausePreparedStatement(objdata, dataTypes, st, con);
				//} else if ((!inClause && inCheck(query)) || (inClause && !inCheck(query))) {
			//		throw new ConnectorException("Kindly select the IN clause check box !");
			//	} else {
			//		this.prepareStatement(objdata, dataTypes, st, con);
			//	}
				this.prepareStatement(objdata, dataTypes, st, con);
				if (maxRows != null && maxRows > 0) {
					st.setMaxRows(maxRows.intValue());
				}
				if (maxFieldSize != null && maxFieldSize > 0) {
					st.setMaxFieldSize(maxFieldSize.intValue());
				}
				this.processResultSet(st, objdata, response);
			} catch (SQLException e) {
				logger.severe("SQL exception coming from executeStatements "+ e.getMessage() );
				CustomResponseUtil.writeSqlErrorResponse(e, objdata, response);
			} catch (Exception e) {
				logger.severe("Exception coming in executeStatements method"+ e.getMessage() );
				CustomResponseUtil.writeErrorResponse(e, objdata, response);
			} finally {
				if (st != null) {
					st.close();
				}
			}
		}
		logger.info("Statements proccessed Successfully!!");
	}

	/**
	 * Returns true if entered Query has IN CLAUSE.
	 *
	 * @param query the query
	 * @return the in check
	 */
	private boolean inCheck(StringBuilder query) {
		return query.toString().contains(IN_WITHSPACE) || query.toString().contains(IN_WITHOUTSPACE);
	}

	/**
	 * In clause prepared statement.
	 *
	 * @param objdata   the objdata
	 * @param dataTypes the data types
	 * @param bstmnt    the bstmnt
	 * @param con       the con
	 * @throws IOException  Signals that an I/O exception has occurred.
	 * @throws SQLException the SQL exception
	 */
	private void inClausePreparedStatement(ObjectData objdata, Map<String, String> dataTypes, PreparedStatement bstmnt,
			Connection con) throws IOException, SQLException {
		ObjectMapper mapper = new ObjectMapper().disable(MapperFeature.CAN_OVERRIDE_ACCESS_MODIFIERS)
				.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
		try (InputStream is = objdata.getData()) {
			JsonNode json = mapper.readTree(is);
			if (json != null) {
				DatabaseMetaData md = con.getMetaData();
				String databaseName = md.getDatabaseProductName();
				try (ResultSet resultSet = md.getColumns(null, null, getContext().getObjectTypeId(), null);) {
					int i = 0;
					int j = 0;
					int keyCount = 0;
					while (resultSet.next()) {
						String key = resultSet.getString(DatabaseConnectorConstants.COLUMN_NAME);
						if (json.get(key) != null) {
							keyCount++;
							ArrayNode arrayNode = json.get(key) instanceof ArrayNode ? (ArrayNode) json.get(key)
									: new ArrayNode(JsonNodeFactory.instance).add(json.get(key));
							Iterator<JsonNode> slaidsIterator = arrayNode.elements();
							while (slaidsIterator.hasNext() || j < arrayNode.size()) {
								i++;
								JsonNode fieldValue = slaidsIterator.next();
								checkInClauseDataType(dataTypes, bstmnt, databaseName, i, key, arrayNode, fieldValue);
								j++;
							}
						}
					}
					if (keyCount != json.size()) {
						throw new ConnectorException("Kindly check the input provided!!!");
					}
				}
			}
		}

	}

	/**
	 * 
	 * This method will check the data type for each key in the Json Request and
	 * sets the value to the prepared statement accordingly
	 * 
	 * @param dataTypes
	 * @param bstmnt
	 * @param databaseName
	 * @param i
	 * @param key
	 * @param arrayNode
	 * @param fieldValue
	 * @throws SQLException
	 */
	private void checkInClauseDataType(Map<String, String> dataTypes, PreparedStatement bstmnt, String databaseName,
			int i, String key, ArrayNode arrayNode, JsonNode fieldValue) throws SQLException {
		switch (dataTypes.get(key)) {
		case INTEGER:
			if (!fieldValue.isNull()) {
				int num = Integer.parseInt(fieldValue.toString().replace(BACKSLASH, ""));
				bstmnt.setInt(i, num);
			} else {
				bstmnt.setNull(i, Types.INTEGER);
			}
			break;
		case DATE:
			if (!fieldValue.isNull()) {
				if (databaseName.equals(ORACLE) || databaseName.equals(MYSQL)) {
					bstmnt.setString(i, fieldValue.toString().replace(BACKSLASH, ""));
				} else {
					bstmnt.setDate(i, Date.valueOf(fieldValue.toString().replace(BACKSLASH, "")));
				}
			} else {
				bstmnt.setNull(i, Types.DATE);
			}
			break;
		case JSON:
			if (!fieldValue.isNull()) {
				extractInClauseJson(bstmnt, databaseName, i, arrayNode, fieldValue);
			} else {
				bstmnt.setNull(i, Types.NULL);
			}
			break;
		case NVARCHAR:
			if (!fieldValue.isNull()) {
				bstmnt.setString(i, StringEscapeUtils.unescapeJava(fieldValue.toString().replace(BACKSLASH, "")));
			} else {
				bstmnt.setNull(i, Types.NVARCHAR);
			}
			break;
		case STRING:
			if (!fieldValue.isNull()) {
				bstmnt.setString(i, fieldValue.toString().replace(BACKSLASH, ""));
			} else {
				bstmnt.setNull(i, Types.VARCHAR);
			}
			break;
		case TIME:
			if (!fieldValue.isNull()) {
				String time = fieldValue.toString().replace(BACKSLASH, "");
				if (databaseName.equals(MYSQL)) {
					bstmnt.setString(i, time);
				} else {
					bstmnt.setTime(i, Time.valueOf(time));
				}
			} else {
				bstmnt.setNull(i, Types.TIME);
			}
			break;
		case BOOLEAN:
			if (!fieldValue.isNull()) {
				boolean flag = Boolean.parseBoolean(fieldValue.toString().replace(BACKSLASH, ""));
				bstmnt.setBoolean(i, flag);
			} else {
				bstmnt.setNull(i, Types.BOOLEAN);
			}
			break;
		default:
			break;
		}
	}

	/**
	 * 
	 * Method to extract JSON Value from the input request and set it to prepared
	 * statement based on the database names
	 * 
	 * @param bstmnt
	 * @param databaseName
	 * @param i
	 * @param arrayNode
	 * @param fieldValue
	 * @throws SQLException
	 */
	private void extractInClauseJson(PreparedStatement bstmnt, String databaseName, int i, ArrayNode arrayNode,
			JsonNode fieldValue) throws SQLException {
		if (databaseName.equals(POSTGRESQL)) {
			PGobject jsonObject = new PGobject();
			jsonObject.setType("json");
			jsonObject.setValue(arrayNode.toString());
			bstmnt.setObject(i, jsonObject);
		} else if (databaseName.equals(ORACLE)) {
			OracleJsonFactory factory = new OracleJsonFactory();
			OracleJsonObject object = factory.createObject();
			JSONObject jsonObject = new JSONObject(arrayNode.toString());
			Iterator<String> keys = jsonObject.keys();
			while (keys.hasNext()) {
				String jsonKeys = keys.next();
				object.put(jsonKeys, jsonObject.get(jsonKeys).toString());
			}
			bstmnt.setObject(i, object, OracleType.JSON);
		} else {
			bstmnt.setString(i, StringEscapeUtils.unescapeJava(fieldValue.toString().replace(BACKSLASH, "")));
		}
	}

	/**
	 * This method will add the parameters to the Prepared Statements based on the
	 * incoming requests.
	 *
	 * @param objdata   the objdata
	 * @param dataTypes the data types
	 * @param bstmnt    the bstmnt
	 * @param con       the con
	 * @throws IOException  Signals that an I/O exception has occurred.
	 * @throws SQLException the SQL exception
	 */
	private void prepareStatement(ObjectData objdata, Map<String, String> dataTypes, PreparedStatement bstmnt,
			Connection con) throws IOException, SQLException {

		ObjectMapper mapper = new ObjectMapper().disable(MapperFeature.CAN_OVERRIDE_ACCESS_MODIFIERS)
				.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
		try (InputStream is = objdata.getData()) {
			if (is.available() != 0) {
				JsonNode json = mapper.readTree(is);
				if (json != null) {
					DatabaseMetaData md = con.getMetaData();
					String databaseName = md.getDatabaseProductName();
					try (ResultSet resultSet = md.getColumns(null, null, getContext().getObjectTypeId(), null);) {
						int i = 0;
						while (resultSet.next()) {
							String key = resultSet.getString(COLUMN_NAME);
							JsonNode fieldName = json.get(key);
							if (dataTypes.containsKey(key) && fieldName != null) {
								i++;
								switch (dataTypes.get(key)) {
								case INTEGER:
									int num = Integer.parseInt(fieldName.toString().replace(BACKSLASH, ""));
									bstmnt.setInt(i, num);
									break;
									case DOUBLE:
										float numf = Float.parseFloat(fieldName.toString().replace(BACKSLASH, ""));
										bstmnt.setDouble(i, numf);
										break;
								case DATE:
									if (databaseName.equals(ORACLE) || databaseName.equals(MYSQL)) {
										bstmnt.setString(i, fieldName.toString().replace(BACKSLASH, ""));
									} else {
										bstmnt.setDate(i, Date.valueOf(fieldName.toString().replace(BACKSLASH, "")));
									}
									break;
								case JSON:
									this.extractJson(bstmnt, databaseName, i, fieldName);
									break;
								case NVARCHAR:
									bstmnt.setString(i, StringEscapeUtils
											.unescapeJava(fieldName.toString().replace(BACKSLASH, "")));
									break;

								case STRING:
									String varchar = fieldName.toString().replace(BACKSLASH, "");
									bstmnt.setString(i, varchar);
									break;
								case TIME:
									String time = fieldName.toString().replace(BACKSLASH, "");
									if (databaseName.equals(ORACLE) || databaseName.equals(MYSQL)) {
										bstmnt.setString(i, time);
									} else {
										bstmnt.setTime(i, Time.valueOf(time));
									}
									break;
								case BOOLEAN:
									boolean flag = Boolean.parseBoolean(fieldName.toString().replace(BACKSLASH, ""));
									bstmnt.setBoolean(i, flag);
									break;
								default:
									break;
								}
							}
						}
					}
				} else {
					throw new ConnectorException("Please check the input data!!");
				}
			}
		}

	}

	/**
	 * Method to extract JSON Value from the input request and set it to prepared
	 * statement based on the database names
	 * 
	 * @param bstmnt
	 * @param databaseName
	 * @param i
	 * @param fieldName
	 * @throws SQLException
	 */
	private void extractJson(PreparedStatement bstmnt, String databaseName, int i, JsonNode fieldName)
			throws SQLException {
		if (databaseName.equals(POSTGRESQL)) {
			PGobject jsonObject = new PGobject();
			jsonObject.setType("json");
			jsonObject.setValue(fieldName.toString());
			bstmnt.setObject(i, jsonObject);
		} else if (databaseName.equals(ORACLE)) {
			OracleJsonFactory factory = new OracleJsonFactory();
			OracleJsonObject object = factory.createObject();
			JSONObject jsonObject = new JSONObject(fieldName.toString());
			Iterator<String> keys = jsonObject.keys();
			while (keys.hasNext()) {
				String jsonKeys = keys.next();
				object.put(jsonKeys, jsonObject.get(jsonKeys).toString());
			}
			bstmnt.setObject(i, object, OracleType.JSON);
		} else {
			bstmnt.setString(i, StringEscapeUtils.unescapeJava(fieldName.toString().replace(BACKSLASH, "")));
		}
	}

	/**
	 * Builds the Prepared Statement by taking the request.
	 *
	 * @param objdata   the is
	 * @param dataTypes the data types
	 * @param con       the con
	 * @return the string builder
	 * @throws IOException  Signals that an I/O exception has occurred.
	 * @throws SQLException the SQL exception
	 */
	private StringBuilder buildQuery(ObjectData objdata, Map<String, String> dataTypes, Connection con)
			throws IOException, SQLException {
		StringBuilder query = new StringBuilder(
				DatabaseConnectorConstants.SELECT_INITIAL + getContext().getObjectTypeId());
		boolean inClause = getContext().getOperationProperties().getBooleanProperty("INClause", false);
		if (inClause) {
			this.buildFinalQueryForINClause(con, query, objdata);
		} else {
			this.buildFinalQuery(con, query, objdata, dataTypes);
		}
		return query;
	}

	/**
	 * Builds the final query for IN clause.
	 *
	 * @param con     the con
	 * @param query   the query
	 * @param objdata the objdata
	 * @throws IOException  Signals that an I/O exception has occurred.
	 * @throws SQLException the SQL exception
	 */
	private void buildFinalQueryForINClause(Connection con, StringBuilder query, ObjectData objdata)
			throws IOException, SQLException {
		ObjectMapper mapper = new ObjectMapper().disable(MapperFeature.CAN_OVERRIDE_ACCESS_MODIFIERS);
		try (InputStream is = objdata.getData();) {
			JsonNode json = null;
			if (is.available() == 0 || null == (json = mapper.readTree(is))) {
				throw new ConnectorException("Please check the Input Request!!!");
			}
			// After filtering out the inputs (which are more than 1MB) we are loading the
			// inputstream to memory here.
			DatabaseMetaData md = con.getMetaData();
			try (ResultSet resultSet = md.getColumns(null, null, getContext().getObjectTypeId(), null);) {
				int keyCount = 0;
				query.append(DatabaseConnectorConstants.WHERE);
				while (resultSet.next()) {
					String key = resultSet.getString(DatabaseConnectorConstants.COLUMN_NAME);
					if (json.get(key) != null) {
						keyCount++;
						query.append(key).append(" IN (");
						int arraySize = json.get(key) instanceof ArrayNode ? json.get(key).size() : 1;
						for (int i = 0; i < arraySize; i++) {
							query.append("?");
							if (i < arraySize - 1) {
								query.append(",");
							}
						}
						query.append(")");
						if (json.size() > 1 && keyCount < json.size()) {
							query.append(" AND ");
						}
					}
				}
				if (keyCount != json.size()) {
					throw new ConnectorException("Column name doesnot exist!!!");
				}
			}
		}

	}

	/**
	 * Adds the link element field values to the GROUPBY Clause of the query.
	 *
	 * @param query       the query
	 * @param linkElement the link element
	 */
	private void addLinkElement(StringBuilder query, String linkElement) {
		query.append(DatabaseConnectorConstants.GROUP_BY).append(linkElement);
	}

	/**
	 * This method will process the result set and creates the Payload for the
	 * Operation Response.
	 *
	 * @param pstmnt   the pstmnt
	 * @param objdata  the objdata
	 * @param response the response
	 */
	private void processResultSet(PreparedStatement pstmnt, ObjectData objdata, OperationResponse response) throws JsonProcessingException {
		logger.info("inside processResultSet method");
		CustomPayloadUtil load = null;
		try (ResultSet rs = pstmnt.executeQuery();) {

			Long batchCount = getContext().getOperationProperties().getLongProperty(BATCH_COUNT);
			if(batchCount == null){
				batchCount = DEFAULT_BATCH_COUNT;
			}
			while (rs.next()) {
				if (batchCount == null || batchCount == 0) {
					logger.info("CustomPayloadUtil has been called from processResultSet method without batchCount");
					load = new CustomPayloadUtil(rs);
				} else if (batchCount != null && batchCount>0) {
					logger.info("CustomPayloadUtil has been called from processResultSet method with batchCount");
					load = new CustomPayloadUtil(rs, batchCount , new ByteArrayOutputStream());
				}
				else {
					throw new ConnectorException("Kindly check the profile details!!");
				}
				logger.info("calling addPartialResult ");
				response.addPartialResult(objdata, OperationStatus.SUCCESS, SUCCESS_RESPONSE_CODE,
						SUCCESS_RESPONSE_MESSAGE, load);
				try {
					if (rs.isClosed()) {
						logger.info("Result set is closed already as complete data has been processed ");
						break;
					}
				} catch (SQLException e) {
					logger.severe("RS already closed: " + e.getMessage());
					break;
				}
			}
			logger.info("calling finishPartialResult ");
			response.finishPartialResult(objdata);
		} catch (SQLException e) {
			logger.severe("SQLException in processResultSet: " + e.getMessage());
			CustomResponseUtil.writeErrorResponse(e, objdata, response);
		} catch (IOException e) {
			throw new RuntimeException(e);
		} finally {
			IOUtil.closeQuietly(load);
		}
	}

	/**
	 * This Method will build the Sql query based on the request parameters.
	 *
	 * @param con       the con
	 * @param query     the query
	 * @param objdata   the objdata
	 * @param dataTypes the data types
	 * @throws IOException  Signals that an I/O exception has occurred.
	 * @throws SQLException the SQL exception
	 */
	private void buildFinalQuery(Connection con, StringBuilder query, ObjectData objdata, Map<String, String> dataTypes)
			throws IOException, SQLException {
		ObjectMapper mapper = new ObjectMapper().disable(MapperFeature.CAN_OVERRIDE_ACCESS_MODIFIERS);
		try (InputStream is = objdata.getData();) {
			if (is.available() != 0) {
				// After filtering out the inputs (which are more than 1MB) we are loading the
				// inputstream to memory here.
				JsonNode json = mapper.readTree(is);
				if (json != null) {
					query.append(DatabaseConnectorConstants.WHERE);
					boolean and = false;
					DatabaseMetaData md = con.getMetaData();
					try (ResultSet resultSet = md.getColumns(null, null, getContext().getObjectTypeId(), null);) {
						while (resultSet.next()) {
							String key = resultSet.getString(DatabaseConnectorConstants.COLUMN_NAME);
							JsonNode node = json.get(key);
							if (node != null) {
								this.checkforAnd(and, query);
								query.append(key).append("=");
								if (dataTypes.containsKey(key)) {
									query.append("?");
								}
								and = true;
							}
						}
					}
				}

				else {
					throw new ConnectorException("Please check the Input Request!!!");
				}

			}
		}

	}

	/**
	 * This method will check whether the incoming request parameter is first one
	 * and append the AND character to the query accordingly.
	 *
	 * @param and   the and
	 * @param query the query
	 */
	private void checkforAnd(boolean and, StringBuilder query) {
		if (and) {
			query.append(" AND ");
		}

	}

	/**
	 * Gets the Connection instance.
	 *
	 * @return the connection
	 */
	@Override
	public DatabaseConnectorConnection getConnection() {
		return (DatabaseConnectorConnection) super.getConnection();
	}

}
