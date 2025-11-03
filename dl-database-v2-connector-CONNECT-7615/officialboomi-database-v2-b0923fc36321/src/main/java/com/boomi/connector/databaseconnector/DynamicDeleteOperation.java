// © Copyright IBM Corp. 2023, 2024
// Copyright (c) 2020 Boomi, Inc.
package com.boomi.connector.databaseconnector;

import static com.boomi.connector.databaseconnector.util.DatabaseConnectorConstants.*;

import java.io.IOException;
import java.io.InputStream;
import java.sql.BatchUpdateException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.boomi.connector.api.ConnectorException;
import com.boomi.connector.api.JsonPayloadUtil;
import com.boomi.connector.api.ObjectData;
import com.boomi.connector.api.OperationResponse;
import com.boomi.connector.api.OperationStatus;
import com.boomi.connector.api.Payload;
import com.boomi.connector.api.ResponseUtil;
import com.boomi.connector.api.UpdateRequest;
import com.boomi.connector.databaseconnector.model.BatchResponse;
import com.boomi.connector.databaseconnector.model.DeletePojo;
import com.boomi.connector.databaseconnector.model.QueryResponse;
import com.boomi.connector.databaseconnector.model.Where;
import com.boomi.connector.databaseconnector.util.CustomResponseUtil;
import com.boomi.connector.databaseconnector.util.MetadataUtil;
import com.boomi.connector.databaseconnector.util.QueryBuilderUtil;
import com.boomi.connector.util.SizeLimitedUpdateOperation;
import com.boomi.util.IOUtil;
import com.boomi.util.json.JSONUtil;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;


/**
 * The Class DynamicDeleteOperation.
 *
 * @author swastik.vn
 */
public class DynamicDeleteOperation extends SizeLimitedUpdateOperation {

	/**
	 * Instantiates a new dynamic delete operation.
	 *
	 * @param con the con
	 */
	public DynamicDeleteOperation(DatabaseConnectorConnection con) {
		super(con);
	}

	/** The Constant logger. */
	private static final Logger logger = Logger.getLogger(DynamicDeleteOperation.class.getName());

	/**
	 * Execute update.
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
			logger.log(Level.INFO, "Statements Executed!!");
		} catch (Exception e) {
			ResponseUtil.addExceptionFailures(response, request, e);
		}
	}

	/**
	 * Execute statements. This Method will take the input params and process the
	 * statements according to the batch count provided
	 *
	 * @param con          the con
	 * @param request      the request
	 * @param response     the response
	 * @param batchCount   the batch count
	 * @param commitOption the commit option
	 * @throws SQLException the SQL exception
	 * @throws IOException  Signals that an I/O exception has occurred.
	 */
	private void executeStatements(Connection con, UpdateRequest request, OperationResponse response, Long batchCount,
			String commitOption) throws SQLException, IOException {

		// We are extending SizeLimitUpdate Operation it loads only single document into
		// memory. Hence we are preparing the list of Object Data which will be required
		// for Statement batching and for creating the Query for Prepared Statement.

		List<ObjectData> trackedData = new ArrayList<>();
		for (ObjectData objdata : request) {
			trackedData.add(objdata);
		}
		StringBuilder query = new StringBuilder(DELETE_QUERY + getContext().getObjectTypeId());
		this.appendKeys(trackedData, query);
		Map<String, String> dataTypes = MetadataUtil.getDataTypes(con, getContext().getObjectTypeId());
		if (batchCount != null && batchCount > 0 && commitOption.equals(COMMIT_BY_ROWS)) {
			this.doBatch(con, dataTypes, batchCount, trackedData, response);
		} else if (COMMIT_BY_PROFILE.equals(commitOption) || batchCount == null || batchCount == 0) {
			for (ObjectData objdata : trackedData) {
				Payload payload = null;
				try (PreparedStatement bstmnt = con.prepareStatement(query.toString());) {
					this.appendValues(objdata, bstmnt, dataTypes, con, response);
					int rowsEffected = bstmnt.executeUpdate();
					payload = JsonPayloadUtil
							.toPayload(new QueryResponse(query.toString(), rowsEffected, "Executed Successfully"));
					response.addResult(objdata, OperationStatus.SUCCESS, SUCCESS_RESPONSE_CODE,
							SUCCESS_RESPONSE_MESSAGE, payload);

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
	 * This method will batch the jdbc statements according to the batch count
	 * specified by the user.
	 *
	 * @param con         the con
	 * @param dataTypes   the data types
	 * @param batchCount  the batch count
	 * @param trackedData the tracked data
	 * @param response    the response
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	private void doBatch(Connection con, Map<String, String> dataTypes, Long batchCount, List<ObjectData> trackedData,
			OperationResponse response) throws IOException {

		int batchnum = 0;
		int b = 0;
		boolean shouldExecute = true;
		StringBuilder query = new StringBuilder(DELETE_QUERY + getContext().getObjectTypeId());
		this.appendKeys(trackedData, query);
		try (PreparedStatement bstmnt = con.prepareStatement(query.toString());) {
			for (ObjectData objdata : trackedData) {
				b++;
				Payload payload = null;
				try {
					this.appendValues(objdata, bstmnt, dataTypes, con, response);
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
							shouldExecute = true;
							CustomResponseUtil.logFailedBatch(response, batchnum, b);
							CustomResponseUtil.batchExecuteError(objdata, response, batchnum, b);
						}

						b = 0;

					} else if (b < batchCount) {
						int remainingBatch = batchnum + 1;
						if (trackedData.lastIndexOf(objdata) == trackedData.size() - 1) {
							this.executeRemaining(objdata, bstmnt, response, remainingBatch, con, b);
						} else {
							payload = JsonPayloadUtil.toPayload(
									new BatchResponse("Record added to batch successfully", remainingBatch, b));
							ResponseUtil.addSuccess(response, objdata, SUCCESS_RESPONSE_CODE, payload);
						}
					}
				} catch (BatchUpdateException e) {

					CustomResponseUtil.logFailedBatch(response, batchnum, b);
					CustomResponseUtil.writeSqlErrorResponse(e, objdata, response);
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
					if (shouldExecute || trackedData.lastIndexOf(objdata) == trackedData.size() - 1) {
						bstmnt.clearBatch();
						batchnum++;
						CustomResponseUtil.logFailedBatch(response, batchnum, b);
						b = 0;
					}
					CustomResponseUtil.writeErrorResponse(e, objdata, response);
				} finally {
					IOUtil.closeQuietly(payload);
				}
			}

		} catch (SQLException e) {
			throw new ConnectorException("Creating the statement got failed from Connection");
		}

	}

	/**
	 * This method will append the question mark place holders to the query based on
	 * the column names.
	 *
	 * @param batchData the batch data
	 * @param query     the query
	 */
	public void appendKeys(List<ObjectData> batchData, StringBuilder query) {

		for (ObjectData data : batchData) {
			boolean dataConsistent = false;
			JsonFactory factory = new JsonFactory();
			ObjectMapper mapper = new ObjectMapper(factory).disable(MapperFeature.CAN_OVERRIDE_ACCESS_MODIFIERS);
			try (InputStream is = data.getData()) {
				DeletePojo deletePojo = mapper.readValue(is, DeletePojo.class);
				boolean comma = false;
				if (deletePojo.getWhere() != null) {
					for (Where where : deletePojo.getWhere()) {
						this.whereInitial(comma, query);
						String column = where.getColumn();
						query.append(column);
						String operator = where.getOperator();
						query.append(operator);
						String value = where.getValue();
						if (value != null) {
							query.append("?");
						}
						comma = true;
						dataConsistent = true;
					}
				}
			} catch (IOException e) {
				// moving to next request
				logger.log(Level.SEVERE, e.toString());
			}
			if (dataConsistent) {
				break;
			}

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
	 * This method will append the values to the Prepared Statement Parameters.
	 *
	 * @param data          the data
	 * @param execStatement the exec statement
	 * @param dataType      the data type
	 * @param con           the con
	 * @param response      the response
	 * @throws IOException          Signals that an I/O exception has occurred.
	 */
	public void appendValues(ObjectData data, PreparedStatement execStatement, Map<String, String> dataType,
			Connection con, OperationResponse response) throws IOException {

		try (InputStream is = data.getData()) {
			DeletePojo deletePojo = JSONUtil.getDefaultObjectMapper().readValue(is, DeletePojo.class);
			int i = 0;
			if (deletePojo.getWhere() != null) {
				for (Where where : deletePojo.getWhere()) {
					i++;
					String column = where.getColumn();
					String value = where.getValue();
					QueryBuilderUtil.checkDataType(dataType, column, value, execStatement, i, con);
				}
			}
		} catch (SQLException e) {
			ResponseUtil.addExceptionFailure(response, data, e);
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
		} catch (BatchUpdateException e) {
			CustomResponseUtil.logFailedBatch(response, remainingBatch, b);
			CustomResponseUtil.writeSqlErrorResponse(e, data, response);
		} catch (SQLException e) {
			CustomResponseUtil.writeSqlErrorResponse(e, data, response);
		} finally {
			IOUtil.closeQuietly(payload);
		}

	}

	/**
	 * This method will append the Where clause to the query if any values present
	 * in the Where parameters. Also it will check whether parameter is first one to
	 * append AND for multiple Where values.
	 *
	 * @param comma the comma
	 * @param query the query
	 */
	private void whereInitial(boolean comma, StringBuilder query) {
		if (comma) {
			query.append(" AND ");
		} else {
			query.append(" WHERE ");
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
