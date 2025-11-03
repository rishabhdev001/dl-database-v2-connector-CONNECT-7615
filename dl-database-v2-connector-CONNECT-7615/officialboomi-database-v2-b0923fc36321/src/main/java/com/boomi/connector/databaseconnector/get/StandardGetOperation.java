// © Copyright IBM Corp. 2023, 2024
// Copyright (c) 2020 Boomi, Inc.
package com.boomi.connector.databaseconnector.get;

import static com.boomi.connector.databaseconnector.util.DatabaseConnectorConstants.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Types;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.text.StringEscapeUtils;
import org.json.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.postgresql.util.PGobject;

import com.boomi.connector.api.ConnectorException;
import com.boomi.connector.api.ObjectData;
import com.boomi.connector.api.OperationResponse;
import com.boomi.connector.api.OperationStatus;
import com.boomi.connector.api.ResponseUtil;
import com.boomi.connector.api.UpdateRequest;
import com.boomi.connector.databaseconnector.DatabaseConnectorConnection;
import com.boomi.connector.databaseconnector.util.CustomPayloadUtil;
import com.boomi.connector.databaseconnector.util.CustomResponseUtil;
import com.boomi.connector.databaseconnector.util.DatabaseConnectorConstants;
import com.boomi.connector.databaseconnector.util.MetadataUtil;
import com.boomi.connector.databaseconnector.util.QueryBuilderUtil;
import com.boomi.connector.databaseconnector.util.RequestUtil;
import com.boomi.connector.databaseconnector.util.SchemaBuilderUtil;
import com.boomi.connector.util.SizeLimitedUpdateOperation;
import com.boomi.util.IOUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;

import oracle.sql.json.OracleJsonFactory;
import oracle.sql.json.OracleJsonObject;

/**
 * The Class StandardGetOperation.
 *
 * @author swastik.vn
 */
public class StandardGetOperation extends SizeLimitedUpdateOperation {

	/** The Constant logger. */
	private static final Logger logger = Logger.getLogger(StandardGetOperation.class.getName());

	/**
	 * Instantiates a new standard get operation.
	 *
	 * @param connection the connection
	 */
	public StandardGetOperation(DatabaseConnectorConnection connection) {
		super(connection);
	}

	/**
	 * Execute update.
	 *
	 * @param request  the request
	 * @param response the response
	 */
	@Override
	public void executeSizeLimitedUpdate(UpdateRequest request, OperationResponse response) {
		DatabaseConnectorConnection conn = getConnection();
		String query = getContext().getOperationProperties().getProperty(DatabaseConnectorConstants.QUERY, "");
		Long maxRows = getContext().getOperationProperties().getLongProperty(DatabaseConnectorConstants.MAX_ROWS);
		Long maxFieldSize = getContext().getOperationProperties().getLongProperty("maxFieldSize");
		try (Connection con = conn.getSoloConnection().connect(conn.getUrl(), conn.loadProperties());) {
			this.executeStatements(con, request, response, maxRows, query, maxFieldSize);
		} catch (Exception e) {
			ResponseUtil.addExceptionFailures(response, request, e);
		}
	}

	/**
	 * This method will process the Prepared Statements based on the requests and
	 * query provided in the Sql Query field.
	 *
	 * @param con          the con
	 * @param trackedData  the tracked data
	 * @param response     the response
	 * @param maxRows      the max rows
	 * @param query        the query
	 * @param maxFieldSize the max field size
	 * @throws SQLException the SQL exception
	 */
	private void executeStatements(Connection con, UpdateRequest trackedData, OperationResponse response, Long maxRows,
			String query, Long maxFieldSize) throws SQLException {
		DatabaseMetaData databaseMetaData = con.getMetaData();

		boolean checkInClause = getContext().getOperationProperties().getBooleanProperty("INClause", false);
		for (ObjectData objdata : trackedData) {
			try (InputStream is = objdata.getData();) {
				// Here we are storing the Object data in MAP, Since the input request is not
				// having the fixed number of fields and Keys are unknown to extract the Json
				// Values.
				JsonNode userData = RequestUtil.getJsonData(is);
				if (userData != null) {
					String finalQuery = this.getQuery(query, userData);
					if (finalQuery != null && !finalQuery.trim().isEmpty()) {
						Map<String, String> dataTypes = this.getDataTypes(con, getContext().getObjectTypeId(),
								finalQuery);
						if (checkInClause) {
							executeQueryForINClause(finalQuery, con, objdata, dataTypes, maxFieldSize, maxRows,
									response, query);
						} else if ((!checkInClause && inCheck(finalQuery.toUpperCase()))
								|| (checkInClause && !inCheck(finalQuery.toUpperCase()))) {
							throw new ConnectorException("Kindly select the IN clause check box!");
						} else {
							if (dataTypes.containsKey("NVARCHAR") && databaseMetaData.getDatabaseProductName()
									.equals(DatabaseConnectorConstants.MSSQL)) {
								finalQuery = finalQuery.toUpperCase() + " FOR JSON AUTO";
							}
							try (NamedParameterStatement pstmnt = new NamedParameterStatement(con, finalQuery)) {
								this.prepareStatement(con, userData, dataTypes, pstmnt, query);
								executeQuery(pstmnt, maxRows, objdata, response, maxFieldSize);
							}
						}
					} else {
						throw new ConnectorException("Please enter SQL Statement");
					}
				} else if (query != null) {
					try (NamedParameterStatement pstmnt = new NamedParameterStatement(con, query)) {
						executeQuery(pstmnt, maxRows, objdata, response, maxFieldSize);
					}
				} else {
					throw new ConnectorException("Please enter SQL Statement");
				}
			} catch (SQLException e) {
				CustomResponseUtil.writeSqlErrorResponse(e, objdata, response);
			} catch (IllegalArgumentException e) {
				CustomResponseUtil.writeInvalidInputResponse(e, objdata, response);
			} catch (Exception e) {
				CustomResponseUtil.writeErrorResponse(e, objdata, response);
			}
		}
		logger.info("Statements proccessed Successfully!!");

	}

	/**
	 * Builds the final query based on the EXEC(
	 *
	 * @param query    the query
	 * @param userData the user data
	 * @return the query
	 */
	private String getQuery(String query, JsonNode userData) {
		if (!query.toUpperCase().contains(OPEN_EXEC)) {
			return userData.get(DatabaseConnectorConstants.SQL_QUERY) == null ? query
					: userData.get(DatabaseConnectorConstants.SQL_QUERY).toString().replace(BACKSLASH, "");
		} else {
			return query;
		}

	}

	/**
	 * Returns true if entered Query has IN CLAUSE.
	 *
	 * @param query the query
	 * @return the in check
	 */
	private boolean inCheck(String query) {
		return query.contains(IN_WITHSPACE) || query.contains(IN_WITHOUTSPACE);
	}

	/**
	 * Gets the data types for each columns associated with the tables/table.
	 *
	 * @param con          the con
	 * @param objectTypeId the object type id
	 * @param finalQuery   the final query
	 * @return the data types
	 * @throws SQLException the SQL exception
	 */
	private Map<String, String> getDataTypes(Connection con, String objectTypeId, String finalQuery)
			throws SQLException {
		Map<String, String> dataTypes = new HashMap<>();
		if (objectTypeId.contains(",")) {
			String[] tableNames = SchemaBuilderUtil.getTableNames(objectTypeId);
			for (String tableName : tableNames) {
				dataTypes.putAll(MetadataUtil.getDataTypesWithTable(con, tableName.trim()));
			}
		} else {
			if (!finalQuery.toUpperCase().contains(OPEN_EXEC)) {
				String tableNameInQuery = QueryBuilderUtil.validateTheTableName(finalQuery.toUpperCase());
				if (!tableNameInQuery.equalsIgnoreCase(getContext().getObjectTypeId())) {
					throw new ConnectorException(
							"The table name used in the query does not match with Object Type selected!");
				}
			}
			dataTypes.putAll(MetadataUtil.getDataTypes(con, objectTypeId));

		}
		return dataTypes;
	}

	/**
	 * Execute query for IN clause.
	 *
	 * @param finalINQuery the final IN query
	 * @param con          the con
	 * @param objdata      the objdata
	 * @param dataTypes    the data types
	 * @param maxFieldSize the max field size
	 * @param maxRows      the max rows
	 * @param response     the response
	 * @param query
	 * @throws SQLException
	 * @throws IOException
	 * @throws ParseException
	 * @throws Exception
	 */
	private void executeQueryForINClause(String finalINQuery, Connection con, ObjectData objdata,
			Map<String, String> dataTypes, Long maxFieldSize, Long maxRows, OperationResponse response, String query)
			throws SQLException, IOException, ParseException {
		DatabaseMetaData databaseMetaData = con.getMetaData();
		if (finalINQuery != null) {
			if (dataTypes.containsKey("NVARCHAR")
					&& databaseMetaData.getDatabaseProductName().equals(DatabaseConnectorConstants.MSSQL)) {
				finalINQuery = finalINQuery + " FOR JSON AUTO";
			}
			try (NamedParameterStatement pstmnt = new NamedParameterStatement(con, finalINQuery, objdata)) {
				this.inClausePreparedStatement(objdata, dataTypes, pstmnt, con, query);
				executeQuery(pstmnt, maxRows, objdata, response, maxFieldSize);
			}

		}

	}

	/**
	 * In clause prepared statement.
	 *
	 * @param objdata   the objdata
	 * @param dataTypes the data types
	 * @param bstmnt    the bstmnt
	 * @param con       the con
	 * @param query
	 * @throws IOException    Signals that an I/O exception has occurred.
	 * @throws SQLException   the SQL exception
	 * @throws ParseException the parse exception
	 */
	private void inClausePreparedStatement(ObjectData objdata, Map<String, String> dataTypes,
			NamedParameterStatement bstmnt, Connection con, String query)
			throws IOException, SQLException, ParseException {
		ObjectMapper mapper = new ObjectMapper().disable(MapperFeature.CAN_OVERRIDE_ACCESS_MODIFIERS)
				.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
		try (InputStream is = objdata.getData()) {
			JsonNode json = mapper.readTree(is);
			if (json != null) {
				DatabaseMetaData md = con.getMetaData();
				String databaseName = md.getDatabaseProductName();
				int i = 0;
				Iterator<String> inputKeys = json.fieldNames();
				while (inputKeys.hasNext()) {
					String key = inputKeys.next();
					if (!key.equalsIgnoreCase(SQL_QUERY)) {
						i++;
						this.validateDataType(dataTypes, key);
						JsonNode value = json.get(key);
						switch (dataTypes.get(key)) {
						case INTEGER:
							if (value == null) {
								bstmnt.setNull(i, Types.INTEGER);
							} else {
								if (json.get(key) instanceof ArrayNode) {
									bstmnt.setIntArray(key, (ArrayNode) json.get(key));
								} else {
									int num = Integer.parseInt(json.get(key).toString().replace(BACKSLASH, ""));
									bstmnt.setInt(key, num);
								}
							}

							break;
						case DATE:
							if (value == null) {
								bstmnt.setNull(i, Types.DATE);
							} else {
								if (databaseName.equals(ORACLE) || databaseName.equals(MYSQL)) {
									if (json.get(key) instanceof ArrayNode) {
										bstmnt.setStringArray(key, (ArrayNode) json.get(key));
									} else {
										bstmnt.setString(key, json.get(key).toString().replace(BACKSLASH, ""));
									}
								} else {
									if (json.get(key) instanceof ArrayNode) {
										bstmnt.setDateArray(key, (ArrayNode) json.get(key));
									} else {
										bstmnt.setDate(key,
												Date.valueOf(json.get(key).toString().replace(BACKSLASH, "")));
									}
								}
							}
							break;
						case JSON:
							extractJsonInClause(bstmnt, json, databaseName, i, key);
							break;
						case NVARCHAR:
							if (value == null) {
								bstmnt.setNull(i, Types.NVARCHAR);
							} else {
								if (json.get(key) instanceof ArrayNode) {
									bstmnt.setNvarcharArray(key, (ArrayNode) json.get(key));
								} else {
									bstmnt.setString(key, StringEscapeUtils
											.unescapeJava(json.get(key).toString().replace(BACKSLASH, "")));
								}
							}
							break;
						case STRING:
							if (value == null) {
								bstmnt.setNull(i, Types.VARCHAR);
							} else {
								if (json.get(key) instanceof ArrayNode) {
									bstmnt.setStringArray(key, (ArrayNode) json.get(key));
								} else {
									String varchar = json.get(key).toString().replace(BACKSLASH, "");
									bstmnt.setString(key, varchar);
								}
							}
							break;
						case TIME:
							if (value == null) {
								bstmnt.setNull(i, Types.VARCHAR);
							} else {
								String varchar = json.get(key).toString().replace(BACKSLASH, "");
								if (databaseName.equals(MYSQL)) {
									if (json.get(key) instanceof ArrayNode) {
										bstmnt.setStringArray(key, (ArrayNode) json.get(key));
									} else {
										bstmnt.setString(key, varchar);
									}
								} else {
									if (json.get(key) instanceof ArrayNode) {
										bstmnt.setTimeArray(key, (ArrayNode) json.get(key));
									} else {
										bstmnt.setTime(key, Time.valueOf(varchar));
									}
								}
							}
							break;
						case BOOLEAN:
							boolean flag = Boolean.parseBoolean(json.get(key).toString().replace(BACKSLASH, ""));
							if (value == null) {
								bstmnt.setNull(i, Types.BOOLEAN);
							} else {
								if (json.get(key) instanceof ArrayNode) {
									bstmnt.setBooleanArray(key, (ArrayNode) json.get(key));
								} else {
									bstmnt.setBoolean(key, flag);
								}
							}
							break;
						default:
							break;
						}
					} else if (query.contains(EXEC)) {
						bstmnt.setExec(1, json.get(key).toString().replace(BACKSLASH, ""));
					}
				}
			}
		}

	}

	/**
	 * @param bstmnt
	 * @param json
	 * @param databaseName
	 * @param i
	 * @param key
	 * @throws SQLException
	 * @throws ParseException
	 */
	private void extractJsonInClause(NamedParameterStatement bstmnt, JsonNode json, String databaseName, int i,
			String key) throws SQLException, ParseException {
		if (databaseName.equals(POSTGRESQL)) {
			PGobject jsonObject = new PGobject();
			jsonObject.setType("json");
			ArrayNode node = (ArrayNode) json.get(key);
			jsonObject.setValue(node.toString());
			bstmnt.setObject(i, jsonObject);
		} else if (databaseName.equals(ORACLE)) {
			String finalString = json.get(key).toString().replace("\\\"", BACKSLASH);
			JSONParser parser = new JSONParser();
			JSONObject jsonString = (JSONObject) parser.parse(finalString);
			JSONObject jsonObject = new JSONObject(jsonString);
			Iterator<String> keys = jsonObject.keys();
			while (keys.hasNext()) {
				String jsonKey = keys.next();
				OracleJsonFactory factory = new OracleJsonFactory();
				OracleJsonObject object = factory.createObject();
				object.put(jsonKey, (String) jsonObject.get(jsonKey));
				bstmnt.setObject(i, object);
			}
		} else {
			bstmnt.setString(key, StringEscapeUtils
					.unescapeJava(json.get(key).toString().replace(BACKSLASH, "")));
		}
	}

	/**
	 * This method will validate the Datatypes of the column before setting the
	 * values to Prepared Statement.
	 *
	 * @param dataTypes the data types
	 * @param key       the key
	 */
	private void validateDataType(Map<String, String> dataTypes, String key) {
		if (!dataTypes.containsKey(key)) {
			throw new ConnectorException("Keys Provided in Input Request is not matching the Request Profile");
		}

	}

	/**
	 * This method will set the MaxRows and MaxFieldSize from the operation UI to
	 * the Preparedstatement and it will call processResultSet.
	 *
	 * @param pstmnt       the pstmnt
	 * @param maxRows      the max rows
	 * @param objdata      the objdata
	 * @param response     the response
	 * @param maxFieldSize the max field size
	 * @throws SQLException the SQL exception
	 */
	private void executeQuery(NamedParameterStatement pstmnt, Long maxRows, ObjectData objdata,
			OperationResponse response, Long maxFieldSize) throws SQLException {
		if (maxRows != null && maxRows > 0) {
			pstmnt.setMaxRows(maxRows.intValue());
		}
		if (maxFieldSize != null && maxFieldSize > 0) {
			pstmnt.setMaxFieldSize(maxFieldSize.intValue());
		}
		this.processResultSet(pstmnt, objdata, response);
	}

	/**
	 * This method will add the parameters to the Prepared Statements based on the
	 * incoming requests.
	 *
	 * @param con       the con
	 * @param jsonNode  the json node
	 * @param dataTypes the data types
	 * @param pstmnt    the pstmnt
	 * @param query
	 * @throws SQLException the SQL exception
	 */
	private void prepareStatement(Connection con, JsonNode jsonNode, Map<String, String> dataTypes,
			NamedParameterStatement pstmnt, String query) throws SQLException {
		int i = 0;
		String databasename = con.getMetaData().getDatabaseProductName();
		if (jsonNode != null) {
			for (Iterator<String> fieldName = jsonNode.fieldNames(); fieldName.hasNext();) {
				String key = fieldName.next().trim();
				JsonNode fieldValue = jsonNode.get(key);
				if (!key.equals(SQL_QUERY)) {
					this.validateDataType(dataTypes, key);
					i++;
					if(dataTypes.containsKey(key)) {
						checkDataType(dataTypes, pstmnt, i, databasename, key, fieldValue);
					}
					
				} else if (query.toUpperCase().contains(OPEN_EXEC)) {
					pstmnt.setExec(1, fieldValue.toString().replace(BACKSLASH, ""));
				}

			}
		}

		logger.log(Level.INFO, "Values appeneded for prepared statement");
	}

	/**
	 * @param dataTypes
	 * @param pstmnt
	 * @param i
	 * @param databasename
	 * @param key
	 * @param fieldValue
	 * @throws SQLException
	 */
	private void checkDataType(Map<String, String> dataTypes, NamedParameterStatement pstmnt, int i,
			String databasename, String key, JsonNode fieldValue) throws SQLException {
		
		switch (dataTypes.get(key)) {
		case INTEGER:
			if (fieldValue != null) {
				int num = Integer.parseInt(fieldValue.toString().replace(BACKSLASH, ""));
				pstmnt.setInt(key, num);
			} else {
				pstmnt.setNull(i, Types.INTEGER);
			}
			break;
		case STRING:
			if (fieldValue != null) {
				String varchar = fieldValue.toString().replace(BACKSLASH, "");
				pstmnt.setString(key, varchar);
			} else {
				pstmnt.setNull(i, Types.VARCHAR);
			}
			break;
		case NVARCHAR:
			if (fieldValue != null) {
				pstmnt.setString(key, StringEscapeUtils.unescapeJava(fieldValue.toString()));
			} else {
				pstmnt.setNull(i, Types.NVARCHAR);
			}
			break;
		case JSON:
			if (fieldValue != null) {
				this.extractJson(pstmnt, i, databasename, key, fieldValue);
			} else {
				pstmnt.setNull(i, Types.NULL);
			}
			break;
		case DATE:
			if (fieldValue != null) {
				if (databasename.equals(ORACLE) || databasename.equals(MYSQL)) {
					pstmnt.setString(key, fieldValue.toString().replace(BACKSLASH, ""));
				} else {
					pstmnt.setDate(key, Date.valueOf(fieldValue.toString().replace(BACKSLASH, "")));
				}
			} else {
				pstmnt.setNull(i, Types.DATE);
			}
			break;
		case CLOB:
			if (fieldValue != null && databasename.equals("Oracle")) {
				Reader string = new StringReader(fieldValue.toString());
				pstmnt.setClob(key, string);
			}
			break;
		case TIME:
			if (fieldValue != null) {
				String time = fieldValue.toString().replace(BACKSLASH, "");
				if (databasename.equals(ORACLE) || databasename.equals(MYSQL)) {
					pstmnt.setString(key, time);
				} else {
					pstmnt.setTime(key, Time.valueOf(time));
				}
			} else {
				pstmnt.setNull(i, Types.TIME);
			}
			break;
		case BOOLEAN:
			if (fieldValue != null) {
				boolean flag = Boolean.parseBoolean(fieldValue.toString().replace(BACKSLASH, ""));
				pstmnt.setBoolean(key, flag);
			} else {
				pstmnt.setNull(i, Types.BOOLEAN);
			}
			break;
		default:
			break;
		}
		
	}

	/**
	 * This method will extract the Json from Json field value and sets it to
	 * prepared statement based on the database name
	 * 
	 * @param pstmnt
	 * @param i
	 * @param databasename
	 * @param key
	 * @param fieldValue
	 * @throws SQLException
	 */
	private void extractJson(NamedParameterStatement pstmnt, int i, String databasename, String key,
			JsonNode fieldValue) throws SQLException {
		if (databasename.equals(POSTGRESQL)) {
			PGobject jsonObject = new PGobject();
			jsonObject.setType("json");
			jsonObject.setValue(fieldValue.toString());
			pstmnt.setObject(i, jsonObject);
		} else if (databasename.equals(ORACLE)) {
			OracleJsonFactory factory = new OracleJsonFactory();
			OracleJsonObject object = factory.createObject();
			JSONObject jsonObject = new JSONObject(fieldValue.toString());
			Iterator<String> keys = jsonObject.keys();
			while (keys.hasNext()) {
				String jsonKeys = keys.next();
				object.put(jsonKeys, jsonObject.get(jsonKeys).toString());
			}
			pstmnt.setObject(i, object);
		} else {
			pstmnt.setString(key, StringEscapeUtils.unescapeJava(fieldValue.toString().replace(BACKSLASH, "")));
		}
	}

	/**
	 * This method will process the result set and creates the Payload for the
	 * Operation Response.
	 *
	 * @param pstmnt   the pstmnt
	 * @param objdata  the objdata
	 * @param response the response
	 */
	private void processResultSet(NamedParameterStatement pstmnt, ObjectData objdata, OperationResponse response) {
		CustomPayloadUtil load = null;
		try (ResultSet rs = pstmnt.executeQuery();) {
			while (rs.next()) {
				load = new CustomPayloadUtil(rs);
				response.addPartialResult(objdata, OperationStatus.SUCCESS, SUCCESS_RESPONSE_CODE,
						SUCCESS_RESPONSE_MESSAGE, load);
			}
			response.finishPartialResult(objdata);

		} catch (SQLException e) {
			CustomResponseUtil.writeErrorResponse(e, objdata, response);
		} finally {
			IOUtil.closeQuietly(load);
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
