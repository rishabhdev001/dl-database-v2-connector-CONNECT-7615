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
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.postgresql.util.PGobject;

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
import com.boomi.connector.databaseconnector.util.MetadataUtil;
import com.boomi.connector.databaseconnector.util.QueryBuilderUtil;
import com.boomi.util.IOUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * The Class PostgresUpsert.
 *
 * @author swastik.vn
 */
public class PostgresUpsert {

	/** The con. */
	Connection con;

	/** The batch count. */
	Long batchCount;

	/** The table name. */
	String tableName;

	/** The commit option. */
	String commitOption;

	/** The mapper. */
	ObjectMapper mapper = new ObjectMapper().disable(MapperFeature.CAN_OVERRIDE_ACCESS_MODIFIERS)
			.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);

	/** The Constant logger. */
	private static final Logger logger = Logger.getLogger(PostgresUpsert.class.getName());

	/**
	 * Instantiates a new postgres upsert.
	 *
	 * @param con          the con
	 * @param batchCount   the batch count
	 * @param objectTypeId the object type id
	 * @param commitOption the commit option
	 */
	public PostgresUpsert(Connection con, Long batchCount, String objectTypeId, String commitOption) {
		this.con = con;
		this.batchCount = batchCount;
		this.tableName = objectTypeId;
		this.commitOption = commitOption;
	}

	/**
	 * Builds the statements.
	 *
	 * @param trackedData the tracked data
	 * @param response    the response
	 * @throws SQLException the SQL exception
	 */
	public void executeStatements(UpdateRequest trackedData, OperationResponse response) throws SQLException {

		Map<String, String> dataTypes = MetadataUtil.getDataTypes(con, tableName);
		// We are extending SizeLimitUpdate Operation it loads only single document into
		// memory. Hence we are preparing the list of Object Data which will be required
		// for Statement batching and for creating the Query for Prepared Statement.
		List<ObjectData> batchData = new ArrayList<>();
		for (ObjectData objdata : trackedData) {
			batchData.add(objdata);
		}
		StringBuilder query = QueryBuilderUtil.buildInitialQuery(con, tableName);
		this.buildInsertQueryStatement(query, batchData, response);
		boolean primaryKeys = this.buildOnConflict(query);
		if (primaryKeys) {
			this.buildUpdateStatement(query, batchData, response);
		}
		if (batchCount != null && batchCount > 0 && commitOption.equals(COMMIT_BY_ROWS)) {
			this.doBatch(batchData, response, dataTypes, query, primaryKeys);
		} else if (batchCount == null || batchCount == 0 || commitOption.equals(COMMIT_BY_PROFILE)) {
			for (ObjectData objdata : batchData) {
				Payload payload = null;
				try (PreparedStatement pstmnt = con.prepareStatement(query.toString())) {
					int pos = this.appendInsertPreapreStatement(pstmnt, objdata, dataTypes);
					if (primaryKeys) {
						this.appendUpdateStatement(pstmnt, objdata, dataTypes, response, pos);
					}
					int effectedRowCount = pstmnt.executeUpdate();
					payload = JsonPayloadUtil
							.toPayload(new QueryResponse(query.toString(), effectedRowCount, "Executed Successfully"));
					ResponseUtil.addSuccess(response, objdata, SUCCESS_RESPONSE_CODE, payload);
				} catch (SQLException e) {
					CustomResponseUtil.writeSqlErrorResponse(e, objdata, response);
				} catch (IOException e) {
					CustomResponseUtil.writeErrorResponse(e, objdata, response);
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
	 * Does the JDBC Statement batching if the batch count is greater than zero and
	 * commit option is commit by rows. This method will take the input request and
	 * Builds the SQL Statements and does the batching.
	 *
	 * @param batchData   the tracked data
	 * @param response    the response
	 * @param dataTypes   the data types
	 * @param query       the query
	 * @param primaryKeys the primary keys
	 * @throws SQLException the SQL exception
	 */
	private void doBatch(List<ObjectData> batchData, OperationResponse response, Map<String, String> dataTypes,
			StringBuilder query, boolean primaryKeys) throws SQLException {

		int batchnum = 0;
		int b = 0;
		boolean shouldExecute = true;

		try (PreparedStatement bstmnt = con.prepareStatement(query.toString())) {
			for (ObjectData objdata : batchData) {
				Payload payload = null;

				b++;
				try {
					int pos = this.appendInsertPreapreStatement(bstmnt, objdata, dataTypes);
					if (primaryKeys) {
						this.appendUpdateStatement(bstmnt, objdata, dataTypes, response, pos);
					}
					bstmnt.addBatch();
					if (b == batchCount) {
						batchnum++;
						if (shouldExecute) {
							int[] res = bstmnt.executeBatch();
							bstmnt.clearParameters();
							con.commit();
							response.getLogger().log(Level.INFO, BATCH_NUM + batchnum);
							response.getLogger().log(Level.INFO, BATCH_RECORDS + res.length);
							payload = JsonPayloadUtil
									.toPayload(new BatchResponse("Batch executed successfully", batchnum, res.length));
							response.addResult(objdata, OperationStatus.SUCCESS, SUCCESS_RESPONSE_CODE,
									SUCCESS_RESPONSE_MESSAGE, payload);
						} else {
							bstmnt.clearBatch();
							bstmnt.clearParameters();
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
							ResponseUtil.addSuccess(response, objdata, SUCCESS_RESPONSE_CODE, payload);
						}

					}

				} catch (BatchUpdateException e) {
					con.commit();
					CustomResponseUtil.logFailedBatch(response, batchnum, b);
					CustomResponseUtil.batchExecuteError(objdata, response, batchnum, b);
					b = 0;
				}

				catch (SQLException e) {
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

		} catch (Exception e) {
			throw new ConnectorException(e.getMessage());
		}

	}

	/**
	 * This method will build the Update Prepared Statement followed by ON CONFLICT
	 * keyword.
	 *
	 * @param query        the query
	 * @param batchData    the batch data
	 * @param response     the response
	 * @throws SQLException the SQL exception
	 */
	private void buildUpdateStatement(StringBuilder query, List<ObjectData> batchData, OperationResponse response) throws SQLException {
		boolean dataConsistent = false;
		JsonNode json = null;
		query.append(" DO UPDATE SET ");
		for (ObjectData objdata : batchData) {
			try (InputStream is = objdata.getData()) {
				json = mapper.readTree(is);
				if (json != null) {
					DatabaseMetaData md = con.getMetaData();
					try (ResultSet resultSet = md.getColumns(null, null, tableName, null);) {
						while (resultSet.next()) {
							String key = resultSet.getString(COLUMN_NAME);
							JsonNode fieldName = json.get(key);
							if (fieldName != null) {
								dataConsistent = true;
								query.append(key).append(" = ?,");
							}
						}
					}
				} else {
					throw new ConnectorException(INPUT_ERROR);
				}
				query.deleteCharAt(query.length() - 1);
			} catch (IOException e) {
				// moving to next request
				logger.log(Level.SEVERE, e.toString());
			} catch (SQLException e) {
				CustomResponseUtil.writeSqlErrorResponse(e, objdata, response);
			} catch (ConnectorException e) {
				ResponseUtil.addExceptionFailure(response, objdata, e);
			}
			if (dataConsistent) {
				break;
			}

		}

	}

	/**
	 * Builds the insert query required for Prepared Statement by taking the values
	 * from the 1st request. If the 1st request is improper then the loop will
	 * continue until it gets the correct request to form the insert Query.
	 *
	 * @param query        the query
	 * @param batchData    the batch data
	 * @param response     the response
	 * @param objectTypeId the object type id
	 * @throws SQLException the SQL exception
	 */
	private void buildInsertQueryStatement(StringBuilder query, List<ObjectData> batchData, OperationResponse response) throws SQLException {
		JsonNode json = null;
		for (ObjectData objdata : batchData) {
			boolean dataConsistent = false;
			try (InputStream is = objdata.getData()) {
				json = mapper.readTree(is);
				if (json != null) {
					DatabaseMetaData md = con.getMetaData();
					try (ResultSet resultSet = md.getColumns(null, null, tableName, null);) {
						while (resultSet.next()) {
							String key = resultSet.getString(COLUMN_NAME);
							JsonNode fieldName = json.get(key);
							if (fieldName != null) {
								dataConsistent = true;
								query.append(PARAM);
							}
						}
						query.deleteCharAt(query.length() - 1);
						query.append(")");
					}

				} else {
					throw new ConnectorException(INPUT_ERROR);
				}

			} catch (IOException e) {
				// moving to next request
				logger.log(Level.SEVERE, e.toString());
			} catch (SQLException e) {
				CustomResponseUtil.writeSqlErrorResponse(e, objdata, response);
			}
			if (dataConsistent) {
				break;
			}
		}

	}

	/**
	 * Appends the Parameters to the Update Statements followed by On Conflict
	 * Keyword. Position for appending the parameter will be continued from the
	 * Insert Statement.
	 *
	 * @param pstmnt       the pstmnt
	 * @param objdata      the objdata
	 * @param dataTypes    the data types
	 * @param response     the response
	 * @param i            the i
	 * @param objectTypeId the object type id
	 * @throws SQLException the SQL exception
	 */
	private void appendUpdateStatement(PreparedStatement pstmnt, ObjectData objdata, Map<String, String> dataTypes,
			OperationResponse response, int i) throws SQLException {

		JsonNode json = null;
		try (InputStream is = objdata.getData()) {
			json = mapper.readTree(is);
			if (json != null) {
				DatabaseMetaData md = con.getMetaData();
				try (ResultSet resultSet = md.getColumns(null, null, tableName, null);) {
					while (resultSet.next()) {
						String key = resultSet.getString(COLUMN_NAME);
						JsonNode fieldName = json.get(key);
						if (fieldName != null) {
							i++;
							this.checkDataType(pstmnt, key, fieldName, dataTypes, i);
						}
					}
				}
			} else {
				throw new ConnectorException(INPUT_ERROR);
			}
		} catch (IOException e) {
			CustomResponseUtil.writeErrorResponse(e, objdata, response);
		} catch (SQLException e) {
			CustomResponseUtil.writeSqlErrorResponse(e, objdata, response);
		} catch (ConnectorException e) {
			ResponseUtil.addExceptionFailure(response, objdata, e);
		}

	}

	/**
	 * Builds the on conflict Keyword if there is any conflicting primary keys in
	 * the table.
	 *
	 * @param query the query
	 * @return true, if successful
	 */
	private boolean buildOnConflict(StringBuilder query) {
		boolean hasPK = false;
		try (ResultSet pk = con.getMetaData().getPrimaryKeys(null, null, tableName)) {
			if (pk.isBeforeFirst()) {
				query.append(" ON CONFLICT(");
				hasPK = true;
				while (pk.next()) {
					query.append(pk.getString(COLUMN_NAME));
					query.append(COMMA);
				}
				query.deleteCharAt(query.length() - 1);
				query.append(")");
			}
		} catch (SQLException e) {
			throw new ConnectorException(e.toString());
		}
		return hasPK;

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

	/**
	 * Appends the Values to the insert prepared statement based on the Datatypes.
	 *
	 * @param stmnt        the stmnt
	 * @param objdata      the objdata
	 * @param dataTypes    the data types
	 * @param response     the response
	 * @param objectTypeId the object type id
	 * @return the int
	 * @throws IOException  Signals that an I/O exception has occurred.
	 * @throws SQLException the SQL exception
	 */
	private int appendInsertPreapreStatement(PreparedStatement stmnt, ObjectData objdata, Map<String, String> dataTypes) throws IOException, SQLException {
		JsonNode json = null;
		int i = 0;
		try (InputStream is = objdata.getData()) {
			json = mapper.readTree(is);

			if (json != null) {
				DatabaseMetaData md = con.getMetaData();
				try (ResultSet resultSet = md.getColumns(null, null, tableName, null);) {
					while (resultSet.next()) {
						String key = resultSet.getString(COLUMN_NAME);
						JsonNode fieldName = json.get(key);
						if (fieldName != null) {
							i++;
							this.checkDataType(stmnt, key, fieldName, dataTypes, i);
						}
					}

				}
			} else {
				throw new ConnectorException(INPUT_ERROR);
			}

		}
		return i;

	}

	/**
	 * This method will Check for the datatype and append the query with values
	 * based on the datatype.
	 *
	 * @param bstmnt    the bstmnt
	 * @param key       the key
	 * @param fieldName the field name
	 * @param dataTypes the data types
	 * @param i         the i
	 * @throws SQLException the SQL exception
	 */
	private void checkDataType(PreparedStatement bstmnt, String key, JsonNode fieldName, Map<String, String> dataTypes,
			int i) throws SQLException {
		if (dataTypes.containsKey(key)) {
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
					if (con.getMetaData().getDatabaseProductName().equals("Oracle")) {
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
					String varchar = fieldName.toString().replace(BACKSLASH, "");
					bstmnt.setString(i, varchar);
				} else {
					bstmnt.setNull(i, Types.VARCHAR);
				}
				break;
			case JSON:
				PGobject jsonObject = new PGobject();
				jsonObject.setType("json");
				jsonObject.setValue(fieldName.toString());
				bstmnt.setObject(i, jsonObject);
				break;
			case TIME:
				if (fieldName != null) {
					String time = fieldName.toString().replace(BACKSLASH, "");
					bstmnt.setTime(i, Time.valueOf(time));
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
	}

	/**
	 * This method will execute the remaining statements of the batching.
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
			response.getLogger().log(Level.INFO, BATCH_NUM + remainingBatch);
			response.getLogger().log(Level.INFO, REMAINING_BATCH_RECORDS + res.length);
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

}
