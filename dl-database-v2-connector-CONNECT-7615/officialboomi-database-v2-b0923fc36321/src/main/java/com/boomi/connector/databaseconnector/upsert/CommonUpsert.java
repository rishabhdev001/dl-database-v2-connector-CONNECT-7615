// © Copyright IBM Corp. 2023, 2024
// Copyright (c) 2020 Boomi, Inc.
package com.boomi.connector.databaseconnector.upsert;

import static com.boomi.connector.databaseconnector.util.DatabaseConnectorConstants.*;

import java.io.IOException;
import java.io.InputStream;
import java.sql.BatchUpdateException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.text.StringEscapeUtils;
import org.json.JSONObject;

import com.boomi.connector.api.ConnectorException;
import com.boomi.connector.api.JsonPayloadUtil;
import com.boomi.connector.api.ObjectData;
import com.boomi.connector.api.OperationResponse;
import com.boomi.connector.api.OperationStatus;
import com.boomi.connector.api.Payload;
import com.boomi.connector.api.ResponseUtil;
import com.boomi.connector.api.UpdateRequest;
import com.boomi.connector.databaseconnector.model.BatchResponse;
import com.boomi.connector.databaseconnector.model.QueryResponse;
import com.boomi.connector.databaseconnector.util.CustomResponseUtil;
import com.boomi.connector.databaseconnector.util.DatabaseConnectorConstants;
import com.boomi.connector.databaseconnector.util.MetadataUtil;
import com.boomi.connector.databaseconnector.util.QueryBuilderUtil;
import com.boomi.util.IOUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import oracle.jdbc.OracleType;
import oracle.sql.json.OracleJsonFactory;
import oracle.sql.json.OracleJsonObject;

/**
 * The Class CommonUpsert.
 *
 * @author swastik.vn
 */
public class CommonUpsert {

	/** The con. */
	Connection con;

	/** The batch count. */
	Long batchCount;

	/** The table name. */
	String tableName;

	/** The commit option. */
	String commitOption;

	/**
	 * Instantiates a new common upsert.
	 *
	 * @param con          the con
	 * @param batchCount   the batch count
	 * @param string       the string
	 * @param commitOption the commit option
	 */
	public CommonUpsert(Connection con, Long batchCount, String string, String commitOption) {
		this.con = con;
		this.batchCount = batchCount;
		this.commitOption = commitOption;
		this.tableName = string;
	}

	/** The mapper. */
	ObjectMapper mapper = new ObjectMapper().disable(MapperFeature.CAN_OVERRIDE_ACCESS_MODIFIERS)
			.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);

	/** The Constant logger. */
	private static final Logger logger = Logger.getLogger(CommonUpsert.class.getName());

	/**
	 * This method is the entry point for the Upsert Logic. This method will take
	 * the UpdateRequest and builds the SQL Statements and Executes them based on
	 * the Commit Options.
	 *
	 * @param trackedData the tracked data
	 * @param response    the response
	 * @throws SQLException            the SQL exception
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	public void executeStatements(UpdateRequest trackedData, OperationResponse response)
			throws SQLException, IOException {

		// This Map will be getting the data type of the each column associated with the
		// table.
		Map<String, String> dataTypes = MetadataUtil.getDataTypes(con, tableName);

		// We are extending SizeLimitUpdate Operation it loads only single document into
		// memory. Hence we are preparing the list of Object Data which will be required
		// for Statement batching and for creating the Query for Prepared Statement.
		List<ObjectData> batchData = new ArrayList<>();
		for (ObjectData objdata : trackedData) {
			batchData.add(objdata);
		}
		StringBuilder query = this.buildPreparedStatement(batchData, dataTypes);
		if (batchCount != null && batchCount > 0 && commitOption.equals(DatabaseConnectorConstants.COMMIT_BY_ROWS)) {
			this.doBatch(dataTypes, response, batchData, query);
		} else if (batchCount == null || batchCount == 0
				|| commitOption.equals(DatabaseConnectorConstants.COMMIT_BY_PROFILE)) {
			for (ObjectData objdata : batchData) {
				Payload payload = null;
				String queryNonBatch = this.buildStatements(objdata, dataTypes).toString();
				try (PreparedStatement pstmnt = con.prepareStatement(queryNonBatch)) {
					this.appendParams(objdata, dataTypes, pstmnt);
					int effectedRowCount = pstmnt.executeUpdate();
					payload = JsonPayloadUtil
							.toPayload(new QueryResponse(queryNonBatch, effectedRowCount, "Executed Successfully"));
					ResponseUtil.addSuccess(response, objdata, SUCCESS_RESPONSE_CODE, payload);
					con.commit();
				} catch (SQLException e) {
					CustomResponseUtil.writeSqlErrorResponse(e, objdata, response);
				} catch (IOException e) {
					CustomResponseUtil.writeErrorResponse(e, objdata, response);
				} catch (ConnectorException e) {
					ResponseUtil.addExceptionFailure(response, objdata, e);
				} finally {
					IOUtil.closeQuietly(payload);
				}

			}
			try {
				con.commit();
			} catch (SQLException e) {
				throw new ConnectorException(e.getMessage());
			}

		} else if (batchCount < 0) {
			throw new ConnectorException("Batch count cannot be negative");
		}

	}

	/**
	 * This method will build the SQL Statements based on the conflict. If conflict
	 * is present it will build Insert statement orelse Update
	 *
	 * @param objdata   the objdata
	 * @param dataTypes the data types
	 * @return the string builder
	 * @throws SQLException the SQL exception
	 * @throws IOException  Signals that an I/O exception has occurred.
	 */
	private StringBuilder buildStatements(ObjectData objdata, Map<String, String> dataTypes)
			throws SQLException, IOException {
		StringBuilder query = new StringBuilder();
		// This List will be holding the names of the columns which will satisfy the
		// Primary Key and Unique Key constraints if any.
		List<String> conflict = this.checkForVoilation(objdata, getPrimaryKeys(), dataTypes);

		if (conflict.isEmpty()) {
			query = QueryBuilderUtil.buildInitialQuery(con, tableName);
			this.buildInsertQuery(query, objdata);

		} else {
			this.buildUpdateSyntax(query, objdata, conflict);

		}
		return query;
	}

	/**
	 * Does the JDBC Statement batching if the batch count is greater than zero and
	 * commit option is commit by rows. This method will take the input request and
	 * Builds the SQL Statements and does the batching.
	 *
	 * @param dataTypes the data types
	 * @param response  the response
	 * @param batchData the batch data
	 * @param query     the query
	 * @throws SQLException the SQL exception
	 */
	private void doBatch(Map<String, String> dataTypes, OperationResponse response, List<ObjectData> batchData,
			StringBuilder query) throws SQLException {

		int batchnum = 0;
		int b = 0;
		boolean shouldExecute = true;

		try (PreparedStatement bstmnt = con.prepareStatement(query.toString())) {
			for (ObjectData objdata : batchData) {
				b++;
				Payload payload = null;
				try {
					this.appendParams(objdata, dataTypes, bstmnt);
					bstmnt.addBatch();
					if (b == batchCount) {
						batchnum++;
						if (shouldExecute) {
							int[] res = bstmnt.executeBatch();
							bstmnt.clearParameters();
							con.commit();
							response.getLogger().log(Level.INFO, DatabaseConnectorConstants.BATCH_NUM + batchnum);
							response.getLogger().log(Level.INFO, DatabaseConnectorConstants.BATCH_RECORDS + res.length);
							payload = JsonPayloadUtil
									.toPayload(new BatchResponse("Batch executed successfully", batchnum, res.length));
							response.addResult(objdata, OperationStatus.SUCCESS, SUCCESS_RESPONSE_CODE,
									SUCCESS_RESPONSE_MESSAGE, payload);
						} else {
							bstmnt.clearBatch();
							shouldExecute = true;
							CustomResponseUtil.logFailedBatch(response, batchnum, b);
							CustomResponseUtil.batchExecuteError(objdata, response, batchnum, b);
						}
						b = 0;
					} else if (b < batchCount) {
						int remainingBatch = batchnum + 1;
						if (batchData.lastIndexOf(objdata) == batchData.size() - 1) {
							this.executeRemaining(objdata, bstmnt, response, remainingBatch, con, b);
						} else {
							payload = JsonPayloadUtil.toPayload(
									new BatchResponse("Record added to batch successfully", remainingBatch, b));
							ResponseUtil.addSuccess(response, objdata, DatabaseConnectorConstants.SUCCESS_RESPONSE_CODE,
									payload);
						}

					}

				} catch (BatchUpdateException e) {
					CustomResponseUtil.logFailedBatch(response, batchnum, b);
					CustomResponseUtil.batchExecuteError(objdata, response, batchnum, b);
					b = 0;
				} catch (SQLException e) {
					CustomResponseUtil.logFailedBatch(response, batchnum, b);
					shouldExecute = this.checkLastRecord(b, batchCount);
					if (shouldExecute) {
						b = 0;
					}
					CustomResponseUtil.writeSqlErrorResponse(e, objdata, response);
				} catch (IOException | IllegalArgumentException e) {
					shouldExecute = this.checkLastRecord(b, batchCount);
					if (shouldExecute || batchData.lastIndexOf(objdata) == batchData.size() - 1) {
						bstmnt.clearBatch();
						batchnum++;
						CustomResponseUtil.logFailedBatch(response, batchnum, b);
						b = 0;
					}
					CustomResponseUtil.writeErrorResponse(e, objdata, response);
				} catch (ConnectorException e) {
					ResponseUtil.addExceptionFailure(response, objdata, e);
				}

				finally {
					IOUtil.closeQuietly(payload);
				}
			}

		} catch (SQLException e) {
			throw new ConnectorException(e.getMessage());
		}

	}

	/**
	 * This method will check if any violation exists in the table and decides
	 * whether to form insert statement or update.
	 *
	 * @param objdata   the objdata
	 * @param dataTypes the data types
	 * @param bstmnt    the bstmnt
	 * @throws SQLException the SQL exception
	 * @throws IOException  Signals that an I/O exception has occurred.
	 */
	private void appendParams(ObjectData objdata, Map<String, String> dataTypes, PreparedStatement bstmnt)
			throws SQLException, IOException {
		List<String> conflict = this.checkForVoilation(objdata, getPrimaryKeys(), dataTypes);

		if (conflict.isEmpty()) {
			this.appendInsertParams(bstmnt, objdata, dataTypes);
		} else {
			this.appendUpdateParams(bstmnt, objdata, dataTypes, conflict);
		}

	}

	/**
	 * This method will append the parameters for the Update Query formed in the
	 * Prepared Statement.
	 *
	 * @param bstmnt       the bstmnt
	 * @param objdata      the objdata
	 * @param dataTypes    the data types
	 * @param conflict     the conflict
	 * @throws IOException  Signals that an I/O exception has occurred.
	 * @throws SQLException the SQL exception
	 */
	private void appendUpdateParams(PreparedStatement bstmnt, ObjectData objdata, Map<String, String> dataTypes,
			List<String> conflict) throws IOException, SQLException {

		int i = 0;
		JsonNode json = null;
		try (InputStream is = objdata.getData()) {
			json = mapper.readTree(is);
			if (json != null) {
				DatabaseMetaData md = con.getMetaData();
				try (ResultSet resultSet = md.getColumns(null, null, tableName, null);) {
					while (resultSet.next()) {
						String key = resultSet.getString(COLUMN_NAME);
						if (!conflict.contains(key)) {
							JsonNode fieldName = json.get(key);
							if (fieldName != null) {
								i++;
								this.checkDatatype(bstmnt, dataTypes, key, fieldName, i);
							}
						}
					}
				}

			} else {
				throw new ConnectorException(INPUT_ERROR);
			}
		}
		for (int j = 0; j <= conflict.size() - 1; j++) {

			String key = conflict.get(j);

			JsonNode jsonNode = null;
			try (InputStream is = objdata.getData()) {
				jsonNode = mapper.readTree(is);
				if (jsonNode != null) {
					JsonNode fieldName = jsonNode.get(key);
					if (fieldName != null) {
						i++;
						this.checkDatatype(bstmnt, dataTypes, key, fieldName, i);
					}
				}

			}

		}
	}

	/**
	 * This method will append the values to the Insert Query formed for the
	 * prepared Statements.
	 *
	 * @param bstmnt       the bstmnt
	 * @param objdata      the objdata
	 * @param dataTypes    the data types
	 * @throws SQLException the SQL exception
	 * @throws IOException  Signals that an I/O exception has occurred.
	 */
	private void appendInsertParams(PreparedStatement bstmnt, ObjectData objdata, Map<String, String> dataTypes) throws SQLException, IOException {
		int i = 0;
		JsonNode json = null;
		try (InputStream is = objdata.getData()) {
			json = mapper.readTree(is);
			if (json != null) {
				DatabaseMetaData md = con.getMetaData();
				try (ResultSet resultSet = md.getColumns(null, null, tableName, null);) {
					while (resultSet.next()) {
						String key = resultSet.getString(COLUMN_NAME);
						JsonNode fieldName = json.get(key);
						i++;
						this.checkDatatype(bstmnt, dataTypes, key, fieldName, i);

					}
				}
			} else {
				throw new ConnectorException(INPUT_ERROR);
			}

		}

	}

	/**
	 * This method will check the Datatype of the key and append the values to the
	 * prepared statement accordingly.
	 *
	 * @param bstmnt    the bstmnt
	 * @param dataTypes the data types
	 * @param key       the key
	 * @param fieldName the field name
	 * @param i         the i
	 * @throws SQLException the SQL exception
	 */
	private void checkDatatype(PreparedStatement bstmnt, Map<String, String> dataTypes, String key, JsonNode fieldName,
			int i) throws SQLException {
		String databaseName = con.getMetaData().getDatabaseProductName();
		switch (dataTypes.get(key)) {
		case INTEGER:
			if (fieldName != null) {
				int num = Integer.parseInt(fieldName.toString().replace(BACKSLASH, ""));
				bstmnt.setInt(i, num);
			} else {
				bstmnt.setNull(i, Types.INTEGER);
			}
			break;
		case DATE:
			if (fieldName != null) {
				if (databaseName.equals(ORACLE)) {
					bstmnt.setString(i, fieldName.toString().replace(BACKSLASH, ""));
				} else {
					bstmnt.setDate(i, Date.valueOf(fieldName.toString().replace(BACKSLASH, "")));
				}
			} else {
				bstmnt.setNull(i, Types.DATE);
			}
			break;
		case STRING:
			if (fieldName != null) {
				bstmnt.setString(i, fieldName.toString().replace(BACKSLASH, ""));
			} else {
				bstmnt.setNull(i, Types.VARCHAR);
			}
			break;
		case JSON:
			this.processJson(databaseName, bstmnt, fieldName, i);
			break;
		case NVARCHAR:
			bstmnt.setString(i, StringEscapeUtils.unescapeJava(fieldName.toString()));
			break;
		case TIME:
			if (fieldName != null) {
				bstmnt.setTime(i, Time.valueOf(fieldName.toString().replace(BACKSLASH, "")));
			} else {
				bstmnt.setNull(i, Types.TIME);
			}
			break;
		case BOOLEAN:
			if (fieldName != null) {
				boolean flag = Boolean.parseBoolean(fieldName.toString().replace(BACKSLASH, ""));
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
	 * Processes the Json field and sets the value to prepared statement based on the database name.
	 *
	 * @param databaseName the database name
	 * @param bstmnt the bstmnt
	 * @param fieldName the field name
	 * @param i the i
	 * @throws SQLException the SQL exception
	 */
	private void processJson(String databaseName, PreparedStatement bstmnt, JsonNode fieldName, int i) throws SQLException {
		if (databaseName.equals(ORACLE)) {
			OracleJsonFactory factory = new OracleJsonFactory();
		    OracleJsonObject object = factory.createObject();
		    JSONObject jsonObject = new JSONObject(fieldName.toString());
			Iterator<String> keys = jsonObject.keys();
			while(keys.hasNext()) {
				String jsonKeys = keys.next();
				object.put(jsonKeys, jsonObject.get(jsonKeys).toString());
			}
			bstmnt.setObject(i, object, OracleType.JSON);
		} else {
			bstmnt.setString(i, StringEscapeUtils.unescapeJava(fieldName.toString()));
		}
	}

	/**
	 * Builds the prepared statement by taking the 1st request of the tracked data.
	 * if 1st request is not proper or if it throws any exception it will move to
	 * subsequent requests until the query is formed.
	 *
	 * @param batchData the batch data
	 * @param dataTypes the data types
	 * @return the string builder
	 * @throws SQLException the SQL exception
	 */
	private StringBuilder buildPreparedStatement(List<ObjectData> batchData, Map<String, String> dataTypes)
			throws SQLException {
		StringBuilder query = new StringBuilder();
		for (ObjectData objdata : batchData) {
			try {
				query = this.buildStatements(objdata, dataTypes);
			} catch (IOException e) {
				// moving to next request
				logger.log(Level.SEVERE, e.toString());
			}
			if (query.length() != 0) {
				break;
			}
		}
		return query;
	}

	/**
	 * This method will build the Update Syntax required for prepared statements.
	 *
	 * @param query        the query
	 * @param objdata      the objdata
	 * @param conflict     the conflict
	 * @throws SQLException the SQL exception
	 * @throws IOException  Signals that an I/O exception has occurred.
	 */
	private void buildUpdateSyntax(StringBuilder query, ObjectData objdata, List<String> conflict)
			throws SQLException, IOException {

		JsonNode json = null;
		query.append("UPDATE ").append(tableName).append(" SET ");
		try (InputStream is = objdata.getData()) {
			json = mapper.readTree(is);
			if (json != null) {
				DatabaseMetaData md = con.getMetaData();
				try (ResultSet resultSet = md.getColumns(null, null, tableName, null);) {
					while (resultSet.next()) {
						String key = resultSet.getString(COLUMN_NAME);
						if (!conflict.contains(key)) {
							JsonNode fieldName = json.get(key);
							if (fieldName != null) {
								query.append(key).append("=");
								query.append(PARAM);
							}
						}
					}
				}
			} else {
				throw new ConnectorException(INPUT_ERROR);
			}
			query.deleteCharAt(query.length() - 1);
		}
		query.append(" WHERE ");
		for (int i = 0; i <= conflict.size() - 1; i++) {
			if (i > 0) {
				query.append(" AND ");
			}
			String key = conflict.get(i);
			query.append(key).append(" = ");
			query.append("?");

		}

	}

	/**
	 * This method will take the current request and check for any Primary key and
	 * Unique Key Violation.
	 *
	 * @param objdata     the objdata
	 * @param primaryKeys the primary keys
	 * @param dataTypes   the data types
	 * @return the list
	 * @throws SQLException the SQL exception
	 * @throws IOException  Signals that an I/O exception has occurred.
	 */
	public List<String> checkForVoilation(ObjectData objdata, List<String> primaryKeys, Map<String, String> dataTypes)
			throws SQLException, IOException {
		List<String> conflict = new ArrayList<>();
		JsonNode json = null;
		try (InputStream is = objdata.getData();) {
			json = mapper.readTree(is);
			if (json != null) {
				for (int i = 0; i <= primaryKeys.size() - 1; i++) {
					StringBuilder query = new StringBuilder("Select " + primaryKeys.get(i) + " from " + tableName
							+ " WHERE " + primaryKeys.get(i) + " = ");
					String key = primaryKeys.get(i);
					JsonNode fieldName = json.get(key);
					if (fieldName != null) {
						String value = fieldName.toString().replace(BACKSLASH, "");
						this.checkDatatype(dataTypes, key, value, query);
						try (ResultSet set = con.prepareStatement(query.toString()).executeQuery()) {
							if (set.isBeforeFirst()) {
								conflict.add(primaryKeys.get(i));
							}
						}

					}

				}
			} else {
				throw new ConnectorException(INPUT_ERROR);
			}

		}
		return conflict;

	}

	/**
	 * This method will Check for the datatype and append the query with values
	 * based on the datatype.
	 *
	 * @param dataTypes the data types
	 * @param key       the key
	 * @param value     the value
	 * @param query     the query
	 */
	private void checkDatatype(Map<String, String> dataTypes, String key, String value, StringBuilder query) {
		if (dataTypes.containsKey(key) && dataTypes.get(key).equals(INTEGER)) {
			Integer num = Integer.valueOf(value);
			query.append(num);
		} else if (dataTypes.containsKey(key) && (dataTypes.get(key).equals(STRING) || dataTypes.get(key).equals(DATE)
				|| dataTypes.get(key).equals(TIME) || dataTypes.get(key).equals(JSON))) {
			query.append("'");
			query.append(value);
			query.append("'");
		} else if (dataTypes.containsKey(key) && dataTypes.get(key).equals(BOOLEAN)) {
			Boolean flag = Boolean.valueOf(value);
			query.append(flag);
		}

	}

	/**
	 * This method will build the Insert query based on the Input Request.
	 *
	 * @param query        the query
	 * @param objdata      the objdata
	 * @throws SQLException the SQL exception
	 * @throws IOException  Signals that an I/O exception has occurred.
	 */
	private void buildInsertQuery(StringBuilder query, ObjectData objdata)
			throws SQLException, IOException {
		JsonNode json = null;
		try (InputStream is = objdata.getData()) {
			json = mapper.readTree(is);
			if (json != null) {
				DatabaseMetaData md = con.getMetaData();
				try (ResultSet resultSet = md.getColumns(null, null, tableName, null);) {
					while (resultSet.next()) {
						query.append(PARAM);
					}
				}

			} else {
				throw new ConnectorException(INPUT_ERROR);
			}
			query.deleteCharAt(query.length() - 1);
			query.append(")");
		}

	}

	/**
	 * This method will return the primary keys and the Unique Keys in the table.
	 *
	 * @return the primary keys
	 * @throws SQLException the SQL exception
	 */
	private List<String> getPrimaryKeys() throws SQLException {
		List<String> pk = new ArrayList<>();
		try (ResultSet resultSet = con.getMetaData().getIndexInfo(null, null, tableName, true, false)) {
			while (resultSet.next()) {
				if (null != resultSet.getString(NON_UNIQUE)
						&& (resultSet.getString(NON_UNIQUE).equals("0") || resultSet.getString(NON_UNIQUE).equals("f"))
						&& resultSet.getString(COLUMN_NAME) != null && !pk.contains(resultSet.getString(COLUMN_NAME)))
					pk.add(resultSet.getString(COLUMN_NAME));
			}

		}
		return pk;

	}

	/**
	 * This method will execute the remaining statements of the batch.
	 *
	 * @param data           the data
	 * @param execStatement  the exec statement
	 * @param response       the response
	 * @param remainingBatch the remaining batch
	 * @param con            the con
	 * @param b              the b
	 */
	private void executeRemaining(ObjectData data, PreparedStatement execStatement, OperationResponse response,
			int remainingBatch, Connection con, int b) {
		Payload payload = null;
		try {
			int[] res = execStatement.executeBatch();
			response.getLogger().log(Level.INFO, DatabaseConnectorConstants.BATCH_NUM + remainingBatch);
			response.getLogger().log(Level.INFO, DatabaseConnectorConstants.REMAINING_BATCH_RECORDS + res.length);
			payload = JsonPayloadUtil.toPayload(new BatchResponse(
					"Remaining records added to batch and executed successfully", remainingBatch, res.length));
			response.addResult(data, OperationStatus.SUCCESS, SUCCESS_RESPONSE_CODE, SUCCESS_RESPONSE_MESSAGE, payload);
			con.commit();
		} catch (SQLException e) {
			CustomResponseUtil.logFailedBatch(response, remainingBatch, b);
			CustomResponseUtil.writeSqlErrorResponse(e, data, response);
		} finally {
			IOUtil.closeQuietly(payload);
		}
	}

	/**
	 * This method will check whether the input is the last object data of the batch
	 * or not.
	 *
	 * @param b          the b
	 * @param batchCount the batch count
	 * @return if yes returns true or else return false
	 */

	private boolean checkLastRecord(int b, Long batchCount) {
		return b == batchCount;
	}

}
