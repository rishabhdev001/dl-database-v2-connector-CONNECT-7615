// © Copyright IBM Corp. 2023, 2024
// Copyright (c) 2020 Boomi, Inc.
package com.boomi.connector.databaseconnector;

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

import com.boomi.connector.api.ConnectorException;
import com.boomi.connector.api.JsonPayloadUtil;
import com.boomi.connector.api.ObjectData;
import com.boomi.connector.api.OperationResponse;
import com.boomi.connector.api.OperationStatus;
import com.boomi.connector.api.Payload;
import com.boomi.connector.api.ResponseUtil;
import com.boomi.connector.api.UpdateRequest;
import com.boomi.connector.databaseconnector.model.BatchResponse;
import com.boomi.connector.databaseconnector.model.BatchResponseWithId;
import com.boomi.connector.databaseconnector.model.QueryResponseWithId;
import com.boomi.connector.databaseconnector.util.CustomResponseUtil;
import com.boomi.connector.databaseconnector.util.InsertionIDUtil;
import com.boomi.connector.databaseconnector.util.MetadataUtil;
import com.boomi.connector.databaseconnector.util.QueryBuilderUtil;
import com.boomi.connector.util.SizeLimitedUpdateOperation;
import com.boomi.util.IOUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * The Class DynamicInsertOperation.
 *
 * @author swastik.vn
 */
public class DynamicInsertOperation extends SizeLimitedUpdateOperation {

	/** The Constant logger. */
	private static final Logger logger = Logger.getLogger(DynamicInsertOperation.class.getName());

	/**
	 * Instantiates a new dynamic insert operation.
	 *
	 * @param conn the conn
	 */
	public DynamicInsertOperation(DatabaseConnectorConnection conn) {
		super(conn);
	}

	/**
	 * Overriden method of SizeLimitUpdateOperation.
	 *
	 * @param request  the request
	 * @param response the response
	 */
	@Override
	public void executeSizeLimitedUpdate(UpdateRequest request, OperationResponse response) {
		DatabaseConnectorConnection conn = getConnection();
		Long batchCount = getContext().getOperationProperties().getLongProperty(BATCH_COUNT);
		String commitOption = getContext().getOperationProperties().getProperty(COMMIT_OPTION);
		try (Connection con = conn.getSoloConnection().connect(conn.getUrl(), conn.loadProperties());) {
			con.setAutoCommit(false);
			this.executeStatements(con, request, response, batchCount, commitOption);
		} catch (Exception e) {
			ResponseUtil.addExceptionFailures(response, request, e);
		}
	}

	/**
	 * This method will identify whether batching is required based on the input and
	 * process the statements accordingly.
	 *
	 * @param con          the con
	 * @param trackedData  the tracked data
	 * @param response     the response
	 * @param batchCount   the batch count
	 * @param commitOption the commit option
	 * @throws SQLException the SQL exception
	 * @throws IOException  Signals that an I/O exception has occurred.
	 */
	private void executeStatements(Connection con, UpdateRequest trackedData, OperationResponse response,
			Long batchCount, String commitOption) throws SQLException, IOException {
		// This Map will be getting the datatype of the each column associated with the
		// table.
		Map<String, String> dataTypes = MetadataUtil.getDataTypes(con, getContext().getObjectTypeId());
		StringBuilder query = null;
		List<Integer> ids = new ArrayList<>();
		ResultSet resultSet = null;
		String databaseName = con.getMetaData().getDatabaseProductName();
		if (batchCount != null && batchCount > 0 && commitOption.equals(COMMIT_BY_ROWS)) {
			// We are extending SizeLimitUpdate Operation it loads only single document into
			// memory. Hence we are preparing the list of Object Data which will be required
			// for Statement batching.
			List<ObjectData> batchedData = new ArrayList<>();
			for (ObjectData objdata : trackedData) {
				batchedData.add(objdata);
			}
			for (ObjectData data : batchedData) {
				try {
					query = QueryBuilderUtil.buildInitialInsertQuery(con, data, getContext().getObjectTypeId());
					break;
				} catch (Exception e) {
					logger.log(Level.SEVERE, "Error occured!!! Moving to next record...");
				}
			}

			this.doBatch(con, dataTypes, batchCount, batchedData, response, query);
		} else if (commitOption.equals(COMMIT_BY_PROFILE) || batchCount == null || batchCount == 0) {
			for (ObjectData objdata : trackedData) {
				Payload payload = null;
				try {
					query = QueryBuilderUtil.buildInitialInsertQuery(con, objdata, getContext().getObjectTypeId());
					try (PreparedStatement st = con.prepareStatement(query.toString(),
							PreparedStatement.RETURN_GENERATED_KEYS)) {
						this.buildFinalQuery(con, objdata, dataTypes, st);
						int effectedRowCount = st.executeUpdate();
						if (effectedRowCount > 1) {
							if (databaseName.equals(MYSQL) || databaseName.equals(MSSQL)) {
								InsertionIDUtil.getIdOfInsertedRecords(st, effectedRowCount);
							} else if (databaseName.equals(POSTGRESQL)) {
								ids = InsertionIDUtil.queryLastIdPostgreSQL(con, effectedRowCount);
							}
						} else {
							if (!databaseName.equals("Oracle")) {
								ids = InsertionIDUtil.getInsertIds(st);
							}
						}
						payload = JsonPayloadUtil.toPayload(new QueryResponseWithId(query.toString(), effectedRowCount,
								ids, "Executed Successfully"));
						ResponseUtil.addSuccess(response, objdata, SUCCESS_RESPONSE_CODE, payload);

					}
				} catch (SQLException e) {
					CustomResponseUtil.writeSqlErrorResponse(e, objdata, response);
				} catch (IOException e) {
					CustomResponseUtil.writeErrorResponse(e, objdata, response);
				} catch (ConnectorException e) {
					ResponseUtil.addExceptionFailure(response, objdata, e);
				} finally {
					if (resultSet != null) {
						resultSet.close();
					}
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
	 * This method will batch the jdbc statements according to the batch count
	 * specified by the user.
	 *
	 * @param con         the con
	 * @param dataTypes   the data types
	 * @param batchCount  the batch count
	 * @param batchedData the batched data
	 * @param response    the response
	 * @param query       the query
	 */
	private void doBatch(Connection con, Map<String, String> dataTypes, Long batchCount, List<ObjectData> batchedData,
			OperationResponse response, StringBuilder query) {
		int batchnum = 0;
		int b = 0;
		boolean shouldExecute = true;
		List<Integer> ids = new ArrayList<>();
		try (PreparedStatement bstmnt = con.prepareStatement(query.toString(), PreparedStatement.RETURN_GENERATED_KEYS);) {
			String databaseName = con.getMetaData().getDatabaseProductName();
			for (ObjectData objdata : batchedData) {
				b++;
				Payload payload = null;
				try {
					this.buildFinalQuery(con, objdata, dataTypes, bstmnt);
					bstmnt.addBatch();
					if (b == batchCount) {
						batchnum++;
						if (shouldExecute) {
							int[] res = bstmnt.executeBatch();
							if ((databaseName.equals(MYSQL)) || (databaseName.equals(POSTGRESQL))) {
								ids = InsertionIDUtil.getInsertIds(bstmnt);
							}
							bstmnt.clearParameters();
							con.commit();
							response.getLogger().log(Level.INFO, BATCH_NUM + batchnum);
							response.getLogger().log(Level.INFO, BATCH_RECORDS + res.length);
							if ((databaseName.equals(MSSQL))) {
								payload = JsonPayloadUtil.toPayload(
										new BatchResponse("Batch executed successfully", batchnum, res.length));
							} else {
								payload = JsonPayloadUtil.toPayload(new BatchResponseWithId(
										"Batch executed successfully", batchnum, ids, res.length));
							}
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
						if (batchedData.lastIndexOf(objdata) == batchedData.size() - 1) {
							this.executeRemaining(objdata, bstmnt, response, remainingBatch, con, b, databaseName);
						} else {
							payload = JsonPayloadUtil.toPayload(
									new BatchResponse("Record added to batch successfully", remainingBatch, b));
							ResponseUtil.addSuccess(response, objdata, SUCCESS_RESPONSE_CODE, payload);
						}

					}

				} catch (BatchUpdateException e) {
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
				}
				catch (IOException | IllegalArgumentException e) {
					shouldExecute = this.checkLastRecord(b, batchCount);
					if (shouldExecute || batchedData.lastIndexOf(objdata) == batchedData.size() - 1) {
						bstmnt.clearBatch();
						batchnum++;
						CustomResponseUtil.logFailedBatch(response, batchnum, b);
						b = 0;
					}
					CustomResponseUtil.writeErrorResponse(e, objdata, response);
				} catch (ConnectorException e) {
					ResponseUtil.addExceptionFailure(response, objdata, e);
				} finally {
					IOUtil.closeQuietly(payload);
				}
			}

			logger.info("Batching statements proccessed Successfully!!");
		} catch (SQLException e) {
			throw new ConnectorException(e.getMessage());
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

	/**
	 * This method will execute the remaining statements of the batching.
	 *
	 * @param data           the data
	 * @param execStatement  the exec statement
	 * @param response       the response
	 * @param remainingBatch the remaining batch
	 * @param con            the con
	 * @param b              the b
	 * @param databaseName   the database name
	 */
	private void executeRemaining(ObjectData data, PreparedStatement execStatement, OperationResponse response,
			int remainingBatch, Connection con, int b, String databaseName) {
		List<Integer> ids = new ArrayList<>();
		Payload payload = null;
		try {
			int[] res = execStatement.executeBatch();
			if ((databaseName.equals(MYSQL)) || (databaseName.equals(POSTGRESQL))) {
				ids = InsertionIDUtil.getInsertIds(execStatement);
			}
			response.getLogger().log(Level.INFO, BATCH_NUM + remainingBatch);
			response.getLogger().log(Level.INFO, REMAINING_BATCH_RECORDS + res.length);
			if ((databaseName.equals(MSSQL))) {
				payload = JsonPayloadUtil.toPayload(new BatchResponse(
						"Remaining records added to batch and executed successfully", remainingBatch, res.length));
			} else {
				payload = JsonPayloadUtil.toPayload(new BatchResponseWithId(
						"Remaining records added to batch and executed successfully", remainingBatch, ids, res.length));
			}
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
	 * This method will build the query based on the request parameter and data type
	 * for the incoming requests.
	 *
	 * @param con       the con
	 * @param objdata   the objdata
	 * @param dataTypes the data types
	 * @param bstmnt    the bstmnt
	 * @throws IOException  Signals that an I/O exception has occurred.
	 * @throws SQLException the SQL exception
	 */
	private void buildFinalQuery(Connection con, ObjectData objdata, Map<String, String> dataTypes,
			PreparedStatement bstmnt) throws IOException, SQLException {

		ObjectMapper mapper = new ObjectMapper().disable(MapperFeature.CAN_OVERRIDE_ACCESS_MODIFIERS)
				.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
		JsonNode json = null;
		boolean isValid = true;
		String objectTypeId = getContext().getObjectTypeId();
		String databasename = con.getMetaData().getDatabaseProductName();
		String autoIncrementColumn = QueryBuilderUtil.getAutogeneratedKeys(con, objdata, objectTypeId);
		try (InputStream is = objdata.getData()) {
			// After filtering out the inputs (which are more than 1MB) we are loading the
			// inputstream to memory here.
			json = mapper.readTree(is);
			if (json != null) {
				DatabaseMetaData md = con.getMetaData();
				try (ResultSet resultSet = md.getColumns(null, null, objectTypeId, null);
						ResultSet rs = md.getPrimaryKeys(null, null, objectTypeId)) {
					int i = 0;
					while (resultSet.next()) {
						String key = resultSet.getString(COLUMN_NAME);
						isValid = validateColumnName(con, json);
						if (isValid) {
							JsonNode fieldValue = json.get(key);
							if (dataTypes.containsKey(key) && autoIncrementColumn == null
									|| (autoIncrementColumn != null && !autoIncrementColumn
											.equalsIgnoreCase(resultSet.getString(COLUMN_NAME)))) {
								i++;
								switch (dataTypes.get(key)) {
								case INTEGER:
									if (fieldValue != null) {
										int num = Integer.parseInt(fieldValue.toString().replace("\"", ""));
										bstmnt.setInt(i, num);
									} else {
										bstmnt.setNull(i, Types.INTEGER);
									}
									break;
								case DATE:
									if (fieldValue != null) {
										if (databasename.equals(ORACLE)) {
											bstmnt.setString(i, fieldValue.toString().replace("\"", ""));
										} else {
											bstmnt.setDate(i, Date.valueOf(fieldValue.toString().replace("\"", "")));
										}
									} else {
										bstmnt.setNull(i, Types.DATE);
									}
									break;
								case STRING:
									if (fieldValue != null) {
										bstmnt.setString(i, fieldValue.toString().replace("\"", ""));
									} else {
										bstmnt.setNull(i, Types.VARCHAR);
									}
									break;
								case TIME:
									if (fieldValue != null) {
										String time = fieldValue.toString().replace("\"", "");
										bstmnt.setTime(i, Time.valueOf(time));
									} else {
										bstmnt.setNull(i, Types.TIME);
									}
									break;
								case NVARCHAR:
									if (fieldValue != null && databasename.equals(MSSQL)) {
										bstmnt.setString(i, StringEscapeUtils.unescapeJava(fieldValue.toString()));
									}
									break;
								case JSON:
									if (fieldValue != null) {
										QueryBuilderUtil.extractJson(fieldValue.toString(), bstmnt, i, databasename);
									} else {
										bstmnt.setNull(i, Types.NULL);
									}
									break;
								case BOOLEAN:
									if (fieldValue != null) {
										Boolean flag = Boolean.valueOf(fieldValue.toString().replace("\"", ""));
										bstmnt.setBoolean(i, flag);
									} else {
										bstmnt.setNull(i, Types.BOOLEAN);
									}
									break;
								default:
									break;
								}
							}
						} else {
							throw new ConnectorException("Please check the column names provided in the input");
						}
					}

				}
			} else {
				throw new ConnectorException("Please check the input data!!");
			}

		}

	}

	/**
	 * Validate column name.
	 *
	 * @param con      the con
	 * @param iterator the key
	 * @return true, if successful
	 * @throws SQLException
	 */
	private boolean validateColumnName(Connection con, JsonNode json) throws SQLException {
		boolean flag = true;
		DatabaseMetaData md = con.getMetaData();
		for (Iterator<String> iter = json.fieldNames(); iter.hasNext();) {
			try (ResultSet resultSet1 = md.getColumns(null, null, getContext().getObjectTypeId(), iter.next());) {
				if (resultSet1.next()) {
					flag = true;
				} else {
					flag = false;
				}
			}
		}
		return flag;

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
