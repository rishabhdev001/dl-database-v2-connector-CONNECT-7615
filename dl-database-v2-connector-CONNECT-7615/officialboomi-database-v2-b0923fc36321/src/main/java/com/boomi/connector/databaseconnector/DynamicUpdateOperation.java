// © Copyright IBM Corp. 2023, 2024
// Copyright (c) 2020 Boomi, Inc.
package com.boomi.connector.databaseconnector;

import static com.boomi.connector.databaseconnector.util.DatabaseConnectorConstants.*;

import java.io.IOException;
import java.io.InputStream;
import java.sql.BatchUpdateException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
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
import com.boomi.connector.databaseconnector.model.QueryResponse;
import com.boomi.connector.databaseconnector.model.UpdatePojo;
import com.boomi.connector.databaseconnector.model.UpdatePojo.Set;
import com.boomi.connector.databaseconnector.model.Where;
import com.boomi.connector.databaseconnector.util.CustomResponseUtil;
import com.boomi.connector.databaseconnector.util.MetadataUtil;
import com.boomi.connector.databaseconnector.util.QueryBuilderUtil;
import com.boomi.connector.util.SizeLimitedUpdateOperation;
import com.boomi.util.IOUtil;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The Class DynamicUpdateOperation.
 *
 * @author swastik.vn
 */
public class DynamicUpdateOperation extends SizeLimitedUpdateOperation {

	/**
	 * Instantiates a new dynamic update operation.
	 *
	 * @param connection the connection
	 */
	public DynamicUpdateOperation(DatabaseConnectorConnection connection) {
		super(connection);
	}

	/** The Constant logger. */
	private static final Logger logger = Logger.getLogger(DynamicUpdateOperation.class.getName());

	/**
	 * Execute update.
	 *
	 * @param request  the request
	 * @param response the response
	 */
	@Override
	protected void executeSizeLimitedUpdate(UpdateRequest request, OperationResponse response) {

		DatabaseConnectorConnection conn = getConnection();
		try (Connection con = conn.getSoloConnection().connect(conn.getUrl(), conn.loadProperties());) {
			con.setAutoCommit(false);
			this.executeUpdateOperation(request, response, con);
		} catch (Exception e) {
			ResponseUtil.addExceptionFailures(response, request, e);
		}

	}

	/**
	 * Update Operation where it will take the List of ObjectData and
	 * OperationResponse and Process the Requests.
	 *
	 * @param trackedData the tracked data
	 * @param response    the response
	 * @param con         the con
	 * @throws SQLException the SQL exception
	 * @throws IOException  Signals that an I/O exception has occurred.
	 */
	public void executeUpdateOperation(UpdateRequest trackedData, OperationResponse response, Connection con)
			throws SQLException, IOException {

		Long batchCount = getContext().getOperationProperties().getLongProperty(BATCH_COUNT);
		String commitOption = getContext().getOperationProperties().getProperty(COMMIT_OPTION);
		// This Map will be getting the datatype of the each column associated with the
		// table.
		Map<String, String> dataType = MetadataUtil.getDataTypes(con, getContext().getObjectTypeId());

		// We are extending SizeLimitUpdate Operation it loads only single document into
		// memory. Hence we are preparing the list of Object Data which will be required
		// for Statement batching and for creating the Query for Prepared Statement.
		List<ObjectData> batchData = new ArrayList<>();
		for (ObjectData objdata : trackedData) {
			batchData.add(objdata);
		}
		StringBuilder query = this.getInitialQuery(getContext().getObjectTypeId());
		this.appendKeys(batchData, query, response);
		if (batchCount != null && batchCount > 0 && commitOption.equals(COMMIT_BY_ROWS)) {
			this.doBatch(con, dataType, batchCount, batchData, response, query);
		} else if (commitOption.equals(COMMIT_BY_PROFILE) || batchCount == null || batchCount == 0) {

			for (ObjectData data : batchData) {
				Payload payload = null;
				try (PreparedStatement execStatement = con.prepareStatement(query.toString())) {
					this.appendValues(data, execStatement, dataType, con, response);
					int updatedRowCount = execStatement.executeUpdate();
					con.commit();
					payload = JsonPayloadUtil
							.toPayload(new QueryResponse(query.toString(), updatedRowCount, "Executed Successfully"));
					response.addResult(data, OperationStatus.SUCCESS, SUCCESS_RESPONSE_CODE, SUCCESS_RESPONSE_MESSAGE,
							payload);

				} catch (SQLException e) {
					CustomResponseUtil.writeSqlErrorResponse(e, data, response);
				} catch (IOException e) {
					CustomResponseUtil.writeErrorResponse(e, data, response);
				} catch (ConnectorException e) {
					ResponseUtil.addExceptionFailure(response, data, e);
				} finally {
					IOUtil.closeQuietly(payload);
				}
			}

			try {
				con.commit();
			} catch (SQLException e) {
				throw new ConnectorException(e.getMessage());
			}
			logger.log(Level.FINE, "Non Batching statements proccessed Successfully!!");
		} else if (batchCount < 0) {
			throw new ConnectorException("Batch Count Cannot be negative!!!");
		}

	}

	/**
	 * Append values to the non batching Statements.
	 *
	 * @param data     the data
	 * @param query    the query
	 * @param dataType the data type
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	public void appendValuesNonBatch(ObjectData data, StringBuilder query, Map<String, String> dataType)
			throws IOException {

		JsonFactory factory = new JsonFactory();
		ObjectMapper mapper = new ObjectMapper(factory).disable(MapperFeature.CAN_OVERRIDE_ACCESS_MODIFIERS)
				.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
		try (InputStream is = data.getData()) {
			UpdatePojo updatePojo = mapper.readValue(is, UpdatePojo.class);
			boolean comma = false;
			for (Set set : updatePojo.getSet()) {
				this.setInitial(comma, query);
				String key = set.getColumn();
				query.append(key);
				query.append("=");
				String value = set.getValue();
				this.checkDataType(dataType, key, value, query);
				comma = true;
			}

			comma = false;
			if (updatePojo.getWhere() != null) {
				for (Where where : updatePojo.getWhere()) {
					this.whereInitial(comma, query);
					String column = where.getColumn();
					query.append(column);
					String operator = where.getOperator();
					query.append(operator);
					String value = where.getValue();
					this.checkDataType(dataType, column, value, query);
					comma = true;
				}

			}

		}

	}

	/**
	 * This method will check the datatype of the column and append the values to
	 * the query according to the datatype.
	 *
	 * @param dataType the data type
	 * @param key      the key
	 * @param value    the value
	 * @param query    the query
	 */
	public void checkDataType(Map<String, String> dataType, String key, String value, StringBuilder query) {

		if (null != dataType.get(key) && (dataType.get(key).equals(STRING) || dataType.get(key).equals(DATE)
				|| dataType.get(key).equals(TIME))) {
			query.append(SINGLE_QUOTE);
			query.append(value);
			query.append(SINGLE_QUOTE);
		} else if (null != dataType.get(key)
				&& (dataType.get(key).equals(INTEGER) || dataType.get(key).equals(BOOLEAN))) {
			query.append(value);
		} else {
			throw new ConnectorException("Invalid Column names");
		}

	}

	/**
	 * This method will batch the jdbc statements according to the batch count
	 * specified by the user.
	 *
	 * @param con        the con
	 * @param dataType   the data type
	 * @param batchCount the batch count
	 * @param batchData  the tracked data
	 * @param response   the response
	 * @param query      the query
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	private void doBatch(Connection con, Map<String, String> dataType, Long batchCount, List<ObjectData> batchData,
			OperationResponse response, StringBuilder query) throws IOException {
		int b = 0;
		int batchnum = 0;
		boolean shouldExecute = true;

		try (PreparedStatement execStatement = con.prepareStatement(query.toString());) {
			for (ObjectData data : batchData) {

				Payload payload = null;
				try {
					b++;
					this.appendValues(data, execStatement, dataType, con, response);
					execStatement.addBatch();
					if (b == batchCount) {
						batchnum++;
						if (shouldExecute) {
							int[] res = execStatement.executeBatch();
							con.commit();
							response.getLogger().log(Level.INFO, BATCH_NUM + batchnum);
							response.getLogger().log(Level.INFO, BATCH_RECORDS + res.length);
							payload = JsonPayloadUtil
									.toPayload(new BatchResponse("Batch executed successfully", batchnum, res.length));
							response.addResult(data, OperationStatus.SUCCESS, SUCCESS_RESPONSE_CODE,
									SUCCESS_RESPONSE_MESSAGE, payload);
						} else {
							execStatement.clearBatch();
							execStatement.clearParameters();
							shouldExecute = true;
							CustomResponseUtil.logFailedBatch(response, batchnum, b);
							CustomResponseUtil.batchExecuteError(data, response, batchnum, b);
						}
						b = 0;
					} else if (b < batchCount) {
						int remainingBatch = batchnum + 1;
						if (batchData.lastIndexOf(data) == batchData.size() - 1) {
							this.executeRemaining(data, execStatement, response, remainingBatch, con, b);
						} else {
							payload = JsonPayloadUtil.toPayload(
									new BatchResponse("Record added to batch successfully", remainingBatch, b));
							ResponseUtil.addSuccess(response, data, SUCCESS_RESPONSE_CODE, payload);
						}
					}

				} catch (BatchUpdateException e) {

					CustomResponseUtil.logFailedBatch(response, batchnum, b);
					CustomResponseUtil.batchExecuteError(data, response, batchnum, b);
					b = 0;
				} catch (SQLException e) {
					CustomResponseUtil.logFailedBatch(response, batchnum, b);
					shouldExecute = this.checkLastRecord(b, batchCount);
					if (shouldExecute) {
						b = 0;
					}
					CustomResponseUtil.writeSqlErrorResponse(e, data, response);
				} catch (IOException | IllegalArgumentException e) {
					shouldExecute = this.checkLastRecord(b, batchCount);
					if (shouldExecute || batchData.lastIndexOf(data) == batchData.size() - 1) {
						execStatement.clearBatch();
						batchnum++;
						CustomResponseUtil.logFailedBatch(response, batchnum, b);
						b = 0;
					}
					CustomResponseUtil.writeErrorResponse(e, data, response);
				} catch (ConnectorException e) {
					ResponseUtil.addExceptionFailure(response, data, e);
				} finally {
					IOUtil.closeQuietly(payload);
				}
			}

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

	/**
	 * This Method will Build the Initial String required for the Update Statement.
	 *
	 * @param objectTypeId the object type id
	 * @return new StringBuilder with initial string required for Update Operation
	 */
	private StringBuilder getInitialQuery(String objectTypeId) {
		return new StringBuilder("UPDATE " + objectTypeId + " SET ");
	}

	/**
	 * This method will build the prepared statement query required for Update
	 * Operation based on the SET and WHERE parameters in the Requests.
	 *
	 * @param batchData the data
	 * @param query     the query
	 * @param response  the response
	 */
	public void appendKeys(List<ObjectData> batchData, StringBuilder query, OperationResponse response) {

		for (ObjectData data : batchData) {
			boolean dataConsistent = false;
			JsonFactory factory = new JsonFactory();
			ObjectMapper mapper = new ObjectMapper(factory).disable(MapperFeature.CAN_OVERRIDE_ACCESS_MODIFIERS);
			mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
			try (InputStream is = data.getData()) {
				UpdatePojo updatePojo = mapper.readValue(is, UpdatePojo.class);
				boolean comma = false;
				for (Set set : updatePojo.getSet()) {
					this.setInitial(comma, query);
					String key = set.getColumn();
					query.append(key);
					query.append("=");
					String value = set.getValue();
					if (value != null) {
						dataConsistent = true;
						query.append("?");
					}
					comma = true;
				}

				comma = false;
				if (updatePojo.getWhere() != null) {
					for (Where where : updatePojo.getWhere()) {
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
	 * This method will append the values to the prepared statements built for
	 * Update Query.
	 *
	 * @param data          the data
	 * @param execStatement the exec statement
	 * @param dataType      the data type
	 * @param con           the con
	 * @param response      the response
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	public void appendValues(ObjectData data, PreparedStatement execStatement, Map<String, String> dataType,
			Connection con, OperationResponse response) throws IOException {
		boolean flag = true;
		try (InputStream is = data.getData()) {
			JsonFactory factory = new JsonFactory();
			ObjectMapper mapper = new ObjectMapper(factory).disable(MapperFeature.CAN_OVERRIDE_ACCESS_MODIFIERS)
					.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
			UpdatePojo updatePojo = mapper.readValue(is, UpdatePojo.class);
			int i = 0;
			for (Set set : updatePojo.getSet()) {
				i++;
				String key = set.getColumn();
				String value = set.getValue();
				flag = validateColumnName(con, key);
				if (flag) {
					QueryBuilderUtil.checkDataType(dataType, key, value, execStatement, i, con);
				}else {
					throw new ConnectorException("The column name " +key+ " does not exists in the database.");
				}
			}

			if (updatePojo.getWhere() != null) {
				i++;
				for (Where where : updatePojo.getWhere()) {
					String column = where.getColumn();
					String value = where.getValue();
					QueryBuilderUtil.checkDataType(dataType, column, value, execStatement, i, con);
					i++;
				}

			}

		} catch (SQLException e) {
			ResponseUtil.addExceptionFailure(response, data, e);
		}

	}

	/**
	 * Validate column name.
	 *
	 * @param con the con
	 * @param key the key
	 * @return true, if successful
	 * @throws SQLException the SQL exception
	 */
	private boolean validateColumnName(Connection con, String key) throws SQLException {
		boolean flag = true;
		DatabaseMetaData md = con.getMetaData();
		try (ResultSet resultSet1 = md.getColumns(null, null, getContext().getObjectTypeId(), key);)  {
			if(resultSet1.next()) {
				flag = true;
			}else {
				flag = false;
			}
		}
		return flag;
		
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
	 * This method will append the ',' after each parameter in the SET clause are
	 * set.
	 *
	 * @param comma the comma
	 * @param query the query
	 */
	private void setInitial(boolean comma, StringBuilder query) {
		if (comma) {
			query.append(COMMA);
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
