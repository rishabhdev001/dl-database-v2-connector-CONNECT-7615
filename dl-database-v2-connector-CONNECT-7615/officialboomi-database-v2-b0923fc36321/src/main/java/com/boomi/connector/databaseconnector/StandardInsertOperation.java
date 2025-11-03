// © Copyright IBM Corp. 2023, 2024
//Copyright (c) 2020 Boomi, Inc.
package com.boomi.connector.databaseconnector;

import static com.boomi.connector.databaseconnector.util.DatabaseConnectorConstants.*;

import java.io.IOException;
import java.io.InputStream;
import java.sql.BatchUpdateException;
import java.sql.Connection;
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
import com.boomi.connector.databaseconnector.model.QueryResponse;
import com.boomi.connector.databaseconnector.model.QueryResponseWithId;
import com.boomi.connector.databaseconnector.util.CustomResponseUtil;
import com.boomi.connector.databaseconnector.util.DatabaseConnectorConstants;
import com.boomi.connector.databaseconnector.util.InsertionIDUtil;
import com.boomi.connector.databaseconnector.util.MetadataUtil;
import com.boomi.connector.databaseconnector.util.QueryBuilderUtil;
import com.boomi.connector.databaseconnector.util.RequestUtil;
import com.boomi.connector.util.SizeLimitedUpdateOperation;
import com.boomi.util.IOUtil;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * The Class StandardInsertOperation.
 *
 * @author sweta.b.das
 */
public class StandardInsertOperation extends SizeLimitedUpdateOperation {

	/** The Constant logger. */
	private static final Logger logger = Logger.getLogger(StandardInsertOperation.class.getName());

	/**
	 * Instantiates a new standard insert operation.
	 *
	 * @param connection the connection
	 */
	public StandardInsertOperation(DatabaseConnectorConnection connection) {
		super(connection);
	}

	/**
	 * Execute size limited update.
	 *
	 * @param request  the request
	 * @param response the response
	 */
	@Override
	protected void executeSizeLimitedUpdate(UpdateRequest request, OperationResponse response) {

		DatabaseConnectorConnection conn = getConnection();
		Long batchCount = getContext().getOperationProperties().getLongProperty(BATCH_COUNT);
		String commitOption = getContext().getOperationProperties().getProperty(COMMIT_OPTION);
		String query = getContext().getOperationProperties().getProperty(QUERY, "");

		if (commitOption.equals(COMMIT_BY_ROWS) && batchCount != null && batchCount > 0) {
			try (Connection con = conn.getSoloConnection().connect(conn.getUrl(), conn.loadProperties());
					PreparedStatement pstmnt = con.prepareStatement(query, PreparedStatement.RETURN_GENERATED_KEYS)) {
				Map<String, String> dataTypes = MetadataUtil.getDataTypes(con, getContext().getObjectTypeId());
				con.setAutoCommit(false);
				// We are extending SizeLimitUpdate Operation it loads only single document into
				// memory. Hence we are preparing the list of Object Data which will be required
				// for Statement batching.
				List<ObjectData> batchData = new ArrayList<>();
				for (ObjectData objdata : request) {
					batchData.add(objdata);
				}
				this.executeBatch(con, batchData, response, batchCount, pstmnt, dataTypes);

			} catch (Exception e) {
				ResponseUtil.addExceptionFailures(response, request, e);
			}
		} else if (commitOption.equals(COMMIT_BY_PROFILE) || batchCount == null || batchCount <= 0) {
			try (Connection con = conn.getSoloConnection().connect(conn.getUrl(), conn.loadProperties())) {
				Map<String, String> dataTypes = MetadataUtil.getDataTypes(con, getContext().getObjectTypeId());
				con.setAutoCommit(false);
				this.executeNonBatch(con, request, response, query, dataTypes);
			} catch (Exception e) {
				ResponseUtil.addExceptionFailures(response, request, e);
			}
		}

	}

	/**
	 * This method will form the statements by taking query as input parameter and
	 * executes the statement.
	 *
	 * @param con         the con
	 * @param trackedData the tracked data
	 * @param response    the response
	 * @param query       the query
	 * @param dataTypes   the data types
	 * @throws SQLException the SQL exception
	 */
	private void executeNonBatch(Connection con, UpdateRequest trackedData, OperationResponse response, String query,
			Map<String, String> dataTypes) throws SQLException {
		ResultSet resultSet = null;
		String databaseName = con.getMetaData().getDatabaseProductName();
		List<Integer> ids = new ArrayList<>();
		
		for (ObjectData objdata : trackedData) {
			Payload payload = null;
			try (InputStream is = objdata.getData();) {
				JsonNode jsonData = RequestUtil.getJsonData(is);

				if (jsonData != null) {
					String finalQuery = null;
					if (!query.toUpperCase().contains("EXEC(")) {
						finalQuery = jsonData.get(SQL_QUERY) == null ? query
								: jsonData.get(SQL_QUERY).toString().replace("\"", "");
					}else {
						finalQuery = query;
					}
						try (PreparedStatement stmnt = con.prepareStatement(finalQuery,
								PreparedStatement.RETURN_GENERATED_KEYS)) {
							this.prepareStatement(con, jsonData, dataTypes, stmnt, query);
							int updatedRowCount = stmnt.executeUpdate();
							if (updatedRowCount > 1) {
								if (databaseName.equals(MYSQL) || databaseName.equals(MSSQL)) {
									InsertionIDUtil.getIdOfInsertedRecords(stmnt, updatedRowCount);
								} else if (databaseName.equals(POSTGRESQL)) {
									ids = InsertionIDUtil.queryLastIdPostgreSQL(con, updatedRowCount);
								}
							} else if (!databaseName.equals(ORACLE)) {
								ids = InsertionIDUtil.getInsertIds(stmnt);
							}
							payload = JsonPayloadUtil.toPayload(
									new QueryResponseWithId(finalQuery, updatedRowCount, ids, "Executed Successfully"));
							response.addResult(objdata, OperationStatus.SUCCESS, SUCCESS_RESPONSE_CODE,
									SUCCESS_RESPONSE_MESSAGE, payload);
						} catch (IllegalArgumentException e) {
							CustomResponseUtil.writeErrorResponse(e, objdata, response);
						} catch (SQLException e) {
							CustomResponseUtil.writeSqlErrorResponse(e, objdata, response);
						}
				} else if (!"".equals(query)) {
					if (databaseName.equals(ORACLE)) {
						preparedStatementforOracle(con, query, response, objdata);
					} else {
						try (PreparedStatement stmnt = con.prepareStatement(query,
								PreparedStatement.RETURN_GENERATED_KEYS)) {
							int updatedRowCount = stmnt.executeUpdate();
							ids = InsertionIDUtil.getInsertIds(stmnt);
							payload = JsonPayloadUtil.toPayload(
									new QueryResponseWithId(query, updatedRowCount, ids, "Executed Successfully"));
							response.addResult(objdata, OperationStatus.SUCCESS, SUCCESS_RESPONSE_CODE,
									SUCCESS_RESPONSE_MESSAGE, payload);
						} catch (IllegalArgumentException e) {
							CustomResponseUtil.writeErrorResponse(e, objdata, response);
						} catch (SQLException e) {
							CustomResponseUtil.writeSqlErrorResponse(e, objdata, response);
						}
					}
				} else {
					throw new ConnectorException("Please enter SQLQuery");
				}

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
		logger.log(Level.FINE, "Non Batching statements proccessed Successfully!!");
	}

	/**
	 * Gets the indexes.
	 *
	 * @param con          the con
	 * @param objectTypeId the object type id
	 * @return the indexes
	 * @throws SQLException the SQL exception
	 */
	private String[] getIndexes(Connection con, String objectTypeId) throws SQLException {
		String[] indexes = null;
		if (con.getMetaData().getDatabaseProductName().equalsIgnoreCase(ORACLE)) {
			try (ResultSet rs = con.prepareStatement(
					IDENTITY_QUERY
							+ objectTypeId+"'")
					.executeQuery()) {
				if(rs.isBeforeFirst()) {
					indexes = new String[rs.getMetaData().getColumnCount()];
				int i = 0;
				while (rs.next()) {
					indexes[i] = rs.getString(COLUMN_NAME);
					i++;
				}
				}

			}
		}
		return indexes;
	}

	/**
	 * Prepared statement for oracle.
	 *
	 * @param con      the con
	 * @param query    the query
	 * @param indexes 
	 * @param payload  the payload
	 * @param response the response
	 * @param objdata  the objdata
	 * @throws SQLException the SQL exception
	 */
	private void preparedStatementforOracle(Connection con, String query, OperationResponse response,
			ObjectData objdata) throws SQLException {
		List<Integer> ids = new ArrayList<>();
		String[] indexes = this.getIndexes(con, getContext().getObjectTypeId());
			if(indexes !=null) {
				try (PreparedStatement stmnt = con.prepareStatement(query, indexes)) {
					int updatedRowCount = stmnt.executeUpdate();
					ids = InsertionIDUtil.insertIdsForOracle(stmnt);
					response.addResult(objdata, OperationStatus.SUCCESS, SUCCESS_RESPONSE_CODE,
							SUCCESS_RESPONSE_MESSAGE, JsonPayloadUtil
							.toPayload(new QueryResponseWithId(query, updatedRowCount, ids, "Executed Successfully")));
				} catch (IllegalArgumentException e) {
					CustomResponseUtil.writeErrorResponse(e, objdata, response);
				} catch (SQLException e) {
					CustomResponseUtil.writeSqlErrorResponse(e, objdata, response);
				}
			}else {
				try (PreparedStatement stmnt = con.prepareStatement(query)) {
					int updatedRowCount = stmnt.executeUpdate();
					response.addResult(objdata, OperationStatus.SUCCESS, SUCCESS_RESPONSE_CODE,
							SUCCESS_RESPONSE_MESSAGE, JsonPayloadUtil
							.toPayload(new QueryResponse(query, updatedRowCount, "Executed Successfully")));
				} catch (IllegalArgumentException e) {
					CustomResponseUtil.writeErrorResponse(e, objdata, response);
				} catch (SQLException e) {
					CustomResponseUtil.writeSqlErrorResponse(e, objdata, response);
				}
			}
			

	}

	/**
	 * This method will batch the jdbc statements according to the batch count
	 * specified by the user.
	 *
	 * @param con        the con
	 * @param batchData  the tracked data
	 * @param response   the response
	 * @param batchCount the batch count
	 * @param pstmnt     the pstmnt
	 * @param dataTypes  the data types
	 * @throws SQLException the SQL exception
	 */
	private void executeBatch(Connection con, List<ObjectData> batchData, OperationResponse response, Long batchCount,
			PreparedStatement pstmnt, Map<String, String> dataTypes) throws SQLException {
		int b = 0;
		int batchnum = 0;
		boolean shouldExecute = true;
		List<Integer> ids = new ArrayList<>();
		for (ObjectData objdata : batchData) {
			Payload payload = null;
			b++;
			try (InputStream is = objdata.getData();) {
				String databaseName = con.getMetaData().getDatabaseProductName();
				// Here we are storing the Object data in MAP, Since the input request is not
				// having the fixed number of fields and Keys are unknown to extract the Json
				// Values.
				JsonNode jsonNode = RequestUtil.getJsonData(is);
				if (jsonNode != null) {
					if (jsonNode.get(SQL_QUERY) != null) {
						throw new ConnectorException("Commit by rows doesnt support SQLQuery field in request profile");
					} else {
						this.prepareStatement(con, jsonNode, dataTypes, pstmnt,"");
						pstmnt.addBatch();
						if (b == batchCount) {
							batchnum++;
							if (shouldExecute) {
								int[] res = pstmnt.executeBatch();
								if ((databaseName.equals(MYSQL)) || (databaseName.equals(POSTGRESQL))) {
									ids = InsertionIDUtil.getInsertIds(pstmnt);
								}
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
								ResponseUtil.addSuccess(response, objdata, SUCCESS_RESPONSE_CODE, payload);
							} else {
								pstmnt.clearBatch();
								shouldExecute = true;
								CustomResponseUtil.logFailedBatch(response, batchnum, b);
								CustomResponseUtil.batchExecuteError(objdata, response, batchnum, b);
							}

							b = 0;
						} else if (b < batchCount) {
							int remainingBatch = batchnum + 1;
							if (batchData.lastIndexOf(objdata) == batchData.size() - 1) {
								this.executeRemaining(objdata, pstmnt, response, remainingBatch, con, b, databaseName);
							} else {
								payload = JsonPayloadUtil.toPayload(
										new BatchResponse("Record added to batch successfully", remainingBatch, b));
								ResponseUtil.addSuccess(response, objdata, SUCCESS_RESPONSE_CODE, payload);
							}
						}
					}
				} else {
					pstmnt.execute();
					con.commit();
					response.addResult(objdata, OperationStatus.SUCCESS,
							DatabaseConnectorConstants.SUCCESS_RESPONSE_CODE,
							DatabaseConnectorConstants.SUCCESS_RESPONSE_MESSAGE, null);

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
					pstmnt.clearBatch();
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
		logger.log(Level.FINE, "Batching statements proccessed Successfully!!");

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
	 * @param objdata        the objdata
	 * @param pstmnt         the pstmnt
	 * @param response       the response
	 * @param remainingBatch the remaining batch
	 * @param con            the con
	 * @param b              the b
	 * @param databaseName   the database name
	 */
	private void executeRemaining(ObjectData objdata, PreparedStatement pstmnt, OperationResponse response,
			int remainingBatch, Connection con, int b, String databaseName) {

		Payload payload = null;
		List<Integer> ids = new ArrayList<>();
		try {
			int res[] = pstmnt.executeBatch();
			if ((databaseName.equals(MYSQL)) || (databaseName.equals(POSTGRESQL))) {
				ids = InsertionIDUtil.getInsertIds(pstmnt);
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
			ResponseUtil.addSuccess(response, objdata, SUCCESS_RESPONSE_CODE, payload);
			con.commit();
		} catch (SQLException e) {
			CustomResponseUtil.logFailedBatch(response, remainingBatch, b);
			CustomResponseUtil.writeSqlErrorResponse(e, objdata, response);
		} finally {
			IOUtil.closeQuietly(payload);
		}

	}

	/**
	 * This method will take the input requests and set the values to the Prepared
	 * statement provided by the user.
	 *
	 * @param con       the con
	 * @param jsonNode  the json node
	 * @param dataTypes the data types
	 * @param pstmnt    the pstmnt
	 * @param query the query
	 * @return true if the input request exists or else false.
	 * @throws SQLException the SQL exception
	 */
	private void prepareStatement(Connection con, JsonNode jsonNode, Map<String, String> dataTypes,
			PreparedStatement pstmnt, String query) throws SQLException {

		int i = 0;
		String databasename = con.getMetaData().getDatabaseProductName();
		Iterator<String> fieldName = jsonNode.fieldNames();
		while (fieldName.hasNext()) {
			String key = fieldName.next();
			JsonNode fieldValue = jsonNode.get(key);
			if (!key.equals(SQL_QUERY)) {
				i++;
				if (dataTypes.containsKey(key) && dataTypes.get(key).equals(INTEGER)) {
					if (fieldValue != null) {
						int num = Integer.parseInt(fieldValue.toString().replace("\"", ""));
						pstmnt.setInt(i, num);
					} else {
						pstmnt.setNull(i, Types.INTEGER);
					}
				}
				if (dataTypes.containsKey(key) && dataTypes.get(key).equals(STRING)) {
					if (fieldValue != null) {
						String varchar = fieldValue.toString().replace("\"", "");
						pstmnt.setString(i, varchar);
					} else {
						pstmnt.setNull(i, Types.VARCHAR);
					}
				}
				if (dataTypes.containsKey(key) && dataTypes.get(key).equals(NVARCHAR)) {
					if (fieldValue != null) {
						pstmnt.setString(i, StringEscapeUtils.unescapeJava(fieldValue.toString()));
					}else {
						pstmnt.setNull(i, Types.NVARCHAR);
					}
				}
				if (dataTypes.containsKey(key) && dataTypes.get(key).equals(JSON)) {
					if (fieldValue != null) {
						QueryBuilderUtil.extractUnescapeJson(pstmnt, i, databasename, fieldValue);
					}else {
						pstmnt.setNull(i, Types.NULL);
					}
				}
				if (dataTypes.containsKey(key) && dataTypes.get(key).equals(DATE)) {
					if (fieldValue != null) {
						if (databasename.equals("Oracle")) {
							pstmnt.setString(i, fieldValue.toString().replace("\"", ""));
						} else {
							pstmnt.setDate(i, Date.valueOf(fieldValue.toString().replace("\"", "")));
						}
					} else {
						pstmnt.setNull(i, Types.DATE);
					}
				}
				if (dataTypes.containsKey(key) && dataTypes.get(key).equals(TIME)) {
					if (fieldValue != null) {
						String time = fieldValue.toString().replace("\"", "");
						pstmnt.setTime(i, Time.valueOf(time));
					} else {
						pstmnt.setNull(i, Types.TIME);
					}
				}
				if (dataTypes.containsKey(key) && dataTypes.get(key).equals(BOOLEAN)) {
					if (fieldValue != null) {
						Boolean flag = Boolean.valueOf(fieldValue.toString().replace("\"", ""));
						pstmnt.setBoolean(i, flag);
					} else {
						pstmnt.setNull(i, Types.BOOLEAN);
					}
				}
			}else if(key.equals(SQL_QUERY) && query.toUpperCase().contains("EXEC(")) {
				pstmnt.setString(1, fieldValue.toString().replace("\"", ""));
			}
		}
		logger.log(Level.INFO, "Values appeneded for prepared statement");
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
