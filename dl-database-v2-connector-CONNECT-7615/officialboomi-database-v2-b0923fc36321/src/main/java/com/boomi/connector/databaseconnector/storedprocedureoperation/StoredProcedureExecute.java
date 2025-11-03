// © Copyright IBM Corp. 2023, 2024
// Copyright (c) 2020 Boomi, Inc.
package com.boomi.connector.databaseconnector.storedprocedureoperation;

import static com.boomi.connector.databaseconnector.util.DatabaseConnectorConstants.BACKSLASH;
import static com.boomi.connector.databaseconnector.util.DatabaseConnectorConstants.MSSQLSERVER;
import static com.boomi.connector.databaseconnector.util.DatabaseConnectorConstants.ORACLE;
import static com.boomi.connector.databaseconnector.util.DatabaseConnectorConstants.POSTGRESQL;

import java.io.IOException;
import java.io.InputStream;
import java.sql.BatchUpdateException;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Date;
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
import com.boomi.connector.databaseconnector.model.ErrorDetails;
import com.boomi.connector.databaseconnector.model.ProcedureResponseNonBatch;
import com.boomi.connector.databaseconnector.util.CustomPayloadUtil;
import com.boomi.connector.databaseconnector.util.CustomResponseUtil;
import com.boomi.connector.databaseconnector.util.DatabaseConnectorConstants;
import com.boomi.connector.databaseconnector.util.ProcedureMetaDataUtil;
import com.boomi.connector.databaseconnector.util.QueryBuilderUtil;
import com.boomi.util.IOUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;

import oracle.jdbc.OracleType;
import oracle.sql.json.OracleJsonFactory;
import oracle.sql.json.OracleJsonObject;

/**
 * The Class StoredProcedureExecute.
 *
 * @author swastik.vn
 */
public class StoredProcedureExecute {

	/**  The List of Parameters present in the procedure. */
	List<String> params;

	/**  The List of only IN Parameters present in the procedure. */
	List<String> inParams;

	/**  The List of only OUT parameters present in the procedure. */
	List<String> outParams;

	/** The data type. */
	Map<String, Integer> dataType;

	/** The tracked data. */
	UpdateRequest trackedData;

	/** The response. */
	OperationResponse response;

	/** The con. */
	Connection con;

	/** The procedure name. */
	String procedureName;

	/** The Constant logger. */
	private static final Logger logger = Logger.getLogger(StoredProcedureExecute.class.getName());

	/**
	 * Instantiates a new stored procedure helper.
	 *
	 * @param con           the con
	 * @param procedureName the table name
	 * @param trackedData   the tracked data
	 * @param response      the response
	 */
	public StoredProcedureExecute(Connection con, String procedureName, UpdateRequest trackedData,
			OperationResponse response) {

		this.params = ProcedureMetaDataUtil.getProcedureParams(con, procedureName);
		this.inParams = ProcedureMetaDataUtil.getInputParams(con, procedureName);
		this.outParams = ProcedureMetaDataUtil.getOutputParams(con, procedureName);
		this.dataType = ProcedureMetaDataUtil.getProcedureMetadata(con, procedureName);
		this.trackedData = trackedData;
		this.response = response;
		this.con = con;
		this.procedureName = procedureName;

	}

	/**
	 * This method will create the Callable statement and provide the neccessary
	 * parameters and execute the statements.
	 *
	 * @param batchCount   the batch count
	 * @param maxFieldSize the max field size
	 * @throws JsonProcessingException the json processing exception
	 * @throws SQLException            the SQL exception
	 */
	public void executeStatements(Long batchCount, Long maxFieldSize) throws JsonProcessingException, SQLException {
		StringBuilder query = new StringBuilder();
		DatabaseMetaData metaData = con.getMetaData();
		if (metaData.getDatabaseProductName().equals(MSSQLSERVER)) {
			query = QueryBuilderUtil.buildInitialQuerySqlDB(params, procedureName);
		} else {
			query = QueryBuilderUtil.buildProcedureQuery(params, procedureName);
		}
		if (batchCount != null && batchCount > 0) {
			if (!inParams.isEmpty() && outParams.isEmpty()) {
				// We are extending SizeLimitUpdate Operation it loads only single document into
				// memory. Hence we are preparing the list of Object Data which will be required
				// for Statement batching and for creating the Query for Prepared Statement.
				List<ObjectData> batchData = new ArrayList<>();
				for (ObjectData objdata : trackedData) {
					batchData.add(objdata);
				}
				this.doBatch(batchCount, query, batchData);
			} else {
				throw new ConnectorException("Batching cannot be applied for non input parameter procedures");
			}

		} else if (batchCount == null || batchCount == 0) {

			for (ObjectData objdata : trackedData) {
				try (InputStream is = objdata.getData(); CallableStatement csmt = con.prepareCall(query.toString())) {
					if (!inParams.isEmpty()) {
						this.prepareStatements(csmt, is, metaData);
					}
					if (!params.isEmpty()) {
						for (int i = 1; i <= params.size(); i++) {
							if (outParams.contains(params.get(i - 1))) {
								if(metaData.getDatabaseProductName().equals(ORACLE) && dataType.get(params.get(i - 1))== Types.OTHER) {
									csmt.registerOutParameter(params.indexOf(params.get(i - 1)) + 1, OracleType.JSON);
								}else {
								csmt.registerOutParameter(params.indexOf(params.get(i - 1)) + 1,
										dataType.get(params.get(i - 1)));
								}
							}
						}
					}

					this.callProcedure(csmt, objdata, maxFieldSize);
				} catch (IOException | IllegalArgumentException e) {
					CustomResponseUtil.writeErrorResponse(e, objdata, response);
				} catch (SQLException e) {
					CustomResponseUtil.writeSqlErrorResponse(e, objdata, response);
				} catch (ConnectorException e) {
					ResponseUtil.addExceptionFailure(response, objdata, e);
				}

			}

			try {
				con.commit();
			} catch (SQLException e) {
				throw new ConnectorException(e.getMessage());
			}

		} else if (batchCount < 0) {
			throw new ConnectorException("Batch count cannot be negative!!");
		}

	}

	/**
	 * This method will batch the jdbc statements according to the batch count
	 * specified by the user.
	 *
	 * @param batchCount the batch count
	 * @param query      the query
	 * @param batchData the batch data
	 * @throws SQLException            the SQL exception
	 */
	private void doBatch(Long batchCount, StringBuilder query, List<ObjectData> batchData) throws SQLException {
		int batchnum = 0;
		int b = 0;
		boolean shouldExecute = true;
		try (CallableStatement csmt = con.prepareCall(query.toString())) {
			for (ObjectData objdata : batchData) {
				Payload payload = null;
				b++;
				try (InputStream is = objdata.getData();) {
					DatabaseMetaData metaData = con.getMetaData();
					this.prepareStatements(csmt, is, metaData);
					csmt.addBatch();
					if (b == batchCount) {
						batchnum++;
						if (shouldExecute) {
							int[] res = csmt.executeBatch();
							csmt.clearBatch();
							con.commit();
							response.getLogger().log(Level.INFO, DatabaseConnectorConstants.BATCH_NUM + batchnum);
							response.getLogger().log(Level.INFO, DatabaseConnectorConstants.BATCH_RECORDS + res.length);
							payload = JsonPayloadUtil
									.toPayload(new BatchResponse("Batch executed successfully", batchnum, res.length));
							ResponseUtil.addSuccess(response, objdata, DatabaseConnectorConstants.SUCCESS_RESPONSE_CODE,
									payload);
						} else {
							csmt.clearBatch();
							shouldExecute = true;
							CustomResponseUtil.logFailedBatch(response, batchnum, b);
							CustomResponseUtil.batchExecuteError(objdata, response, batchnum, b);
						}
						b = 0;
					} else if (b < batchCount) {
						int remainingBatch = batchnum + 1;
						if (batchData.lastIndexOf(objdata) == batchData.size() - 1) {
							this.executeRemaining(objdata, csmt, remainingBatch, b);
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
				} catch (IOException e) {
					shouldExecute = this.checkLastRecord(b, batchCount);
					if (shouldExecute || batchData.lastIndexOf(objdata) == batchData.size() - 1) {
						csmt.clearBatch();
						batchnum++;
						CustomResponseUtil.logFailedBatch(response, batchnum, b);
						b = 0;
					}
					CustomResponseUtil.writeErrorResponse(e, objdata, response);
				} finally {
					IOUtil.closeQuietly(payload);
				}
			}
		}

	}

	/**
	 * This method will provide the necessary parameters required for the Callable
	 * statement based on the incoming requests.
	 *
	 * @param csmt the csmt
	 * @param is   the is
	 * @param metadata the metadata
	 * @throws SQLException the SQL exception
	 * @throws IOException  Signals that an I/O exception has occurred.
	 */
	private void prepareStatements(CallableStatement csmt, InputStream is, DatabaseMetaData metadata)
			throws SQLException, IOException {

		ObjectMapper mapper = new ObjectMapper().disable(MapperFeature.CAN_OVERRIDE_ACCESS_MODIFIERS)
				.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
		String databasename = con.getMetaData().getDatabaseProductName();
		JsonNode json = null;
		if (is.available() != 0) {
			// After filtering out the inputs (which are more than 1MB) we are loading the
			// inputstream to memory here.
			json = mapper.readTree(is);
			int i = 1;
			try (ResultSet rs = metadata.getProcedureColumns(null, null, procedureName, null);) {
				while (rs.next() && i <= inParams.size()) {
						JsonNode node = json.get(inParams.get(i - 1));
						if (node != null) {
							if(dataType.get(inParams.get(i - 1)) == Types.OTHER && databasename.equalsIgnoreCase(ORACLE)) {
								OracleJsonFactory factory = new OracleJsonFactory();
							    OracleJsonObject object = factory.createObject();
							    JSONObject jsonObject = new JSONObject(node.toString());
								Iterator<String> keys = jsonObject.keys();
								while(keys.hasNext()) {
									String jsonKeys = keys.next();
									object.put(jsonKeys, jsonObject.get(jsonKeys).toString());
								}
								csmt.setObject(i, object, OracleType.JSON);
							}
							if (dataType.get(inParams.get(i - 1)) == -1
									&& (rs.getString("TYPE_NAME").equalsIgnoreCase(DatabaseConnectorConstants.JSON))) {
								csmt.setString(params.indexOf(inParams.get(i - 1)) + 1, StringEscapeUtils.unescapeJava(node.toString()));
							} else if (dataType.get(inParams.get(i - 1)) == Types.VARCHAR
									|| dataType.get(inParams.get(i - 1)) == Types.LONGVARCHAR
									|| dataType.get(inParams.get(i - 1)) == Types.CLOB) {
								csmt.setString(params.indexOf(inParams.get(i - 1)) + 1,
										node.toString().replace(BACKSLASH, ""));
							} else if (dataType.get(inParams.get(i - 1)) == Types.INTEGER
									|| dataType.get(inParams.get(i - 1)) == Types.NUMERIC) {
								csmt.setInt(params.indexOf(inParams.get(i - 1)) + 1,
										Integer.valueOf(node.toString().replace(BACKSLASH, "")));
							} else if (dataType.get(inParams.get(i - 1)) == Types.DATE
									|| dataType.get(inParams.get(i - 1)) == Types.TIMESTAMP) {
								csmt.setDate(params.indexOf(inParams.get(i - 1)) + 1,
										Date.valueOf(node.toString().replace(BACKSLASH, "")));
							} else if (dataType.get(inParams.get(i - 1)) == Types.TIME) {
								csmt.setTime(params.indexOf(inParams.get(i - 1)) + 1,
										Time.valueOf(node.toString().replace(BACKSLASH, "")));
							} else if (dataType.get(inParams.get(i - 1)) == Types.NVARCHAR) {
								csmt.setString(params.indexOf(inParams.get(i - 1)) + 1, StringEscapeUtils.unescapeJava(node.toString()));
							}else if (dataType.get(inParams.get(i - 1)) == Types.BOOLEAN
									|| dataType.get(inParams.get(i - 1)) == Types.TINYINT
									|| dataType.get(inParams.get(i - 1)) == Types.BIT) {
								csmt.setBoolean(params.indexOf(inParams.get(i - 1)) + 1,
										Boolean.valueOf(node.toString().replace(BACKSLASH, "")));
							} else if(dataType.get(inParams.get(i - 1)) == Types.OTHER && databasename.equalsIgnoreCase(POSTGRESQL)) {
								PGobject jsonObject = new PGobject();
								jsonObject.setType("json");
								jsonObject.setValue(node.toString());
								csmt.setObject(params.indexOf(inParams.get(i - 1)) + 1, jsonObject);
							}
						} else if (POSTGRESQL.equals(databasename)) {
							csmt.setString(i, null);
						}

					i++;
				}
			}
		}

	}

	/**
	 * Method that will execute remaining records in the batch.
	 *
	 * @param objdata  the objdata
	 * @param csmt     the csmt
	 * @param batchnum the batchnum
	 * @param b        the b
	 */
	private void executeRemaining(ObjectData objdata, CallableStatement csmt, int batchnum, int b) {

		Payload payload = null;
		try {
			int res[] = csmt.executeBatch();
			response.getLogger().log(Level.INFO, DatabaseConnectorConstants.BATCH_NUM + batchnum);
			response.getLogger().log(Level.INFO, DatabaseConnectorConstants.REMAINING_BATCH_RECORDS + res.length);
			payload = JsonPayloadUtil.toPayload(new BatchResponse(
					"Remaining records added to batch and executed successfully", batchnum, res.length));
			ResponseUtil.addSuccess(response, objdata, DatabaseConnectorConstants.SUCCESS_RESPONSE_CODE, payload);
			con.commit();

		} catch (SQLException e) {
			CustomResponseUtil.logFailedBatch(response, batchnum, b);
			CustomResponseUtil.writeSqlErrorResponse(e, objdata, response);
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

	/**
	 * This method will call the procedure and process the resultset based on the
	 * OUT param.
	 *
	 * @param csmt         the csmt
	 * @param objdata      the objdata
	 * @param maxFieldSize the max field size
	 * @throws SQLException the SQL exception
	 */
	private void callProcedure(CallableStatement csmt, ObjectData objdata, Long maxFieldSize) throws SQLException {

		ObjectMapper mapper = new ObjectMapper().disable(MapperFeature.CAN_OVERRIDE_ACCESS_MODIFIERS)
				.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);

		if (maxFieldSize != null && maxFieldSize > 0) {
			csmt.setMaxFieldSize(maxFieldSize.intValue());
		}

		boolean result = csmt.execute();

		Payload payload = null;
		try (ResultSet rs = csmt.getResultSet();) {
			if (outParams != null && !outParams.isEmpty() && !result) {
				ObjectNode node = mapper.createObjectNode();
				for (int i = 0; i <= outParams.size() - 1; i++) {
					if (csmt.getString(params.indexOf(outParams.get(i)) + 1) != null
							|| !csmt.getString(params.indexOf(outParams.get(i)) + 1).isEmpty()) {
						// OUT params returned from the procedure will not exceed more than 300kb in
						// most of the databases. Hence we are copying it to Object Node.
						node.put(outParams.get(i), csmt.getString(params.indexOf(outParams.get(i)) + 1));
					} else {
						payload = JsonPayloadUtil.toPayload(new ErrorDetails(405, "Value from Out parameter is Null"));
						response.addResult(objdata, OperationStatus.APPLICATION_ERROR,
								DatabaseConnectorConstants.SUCCESS_RESPONSE_CODE,
								DatabaseConnectorConstants.SUCCESS_RESPONSE_MESSAGE, payload);
					}

				}
				payload = JsonPayloadUtil.toPayload(node);
				response.addResult(objdata, OperationStatus.SUCCESS, DatabaseConnectorConstants.SUCCESS_RESPONSE_CODE,
						DatabaseConnectorConstants.SUCCESS_RESPONSE_MESSAGE, payload);

			}

			else if (result && rs != null) {
				this.processResultset(objdata, rs);

			} else if (!result && rs == null) {
				payload = JsonPayloadUtil
						.toPayload(new ProcedureResponseNonBatch(200, "Procedure Executed Successfully!!"));
				response.addResult(objdata, OperationStatus.SUCCESS, DatabaseConnectorConstants.SUCCESS_RESPONSE_CODE,
						DatabaseConnectorConstants.SUCCESS_RESPONSE_MESSAGE, payload);
			}

			logger.info("Procedure called Successfully!!!");
		} finally {
			IOUtil.closeQuietly(payload);
		}

	}

	/**
	 * This method will process the resultset and Writes each field from the
	 * resultset to the payload.
	 *
	 * @param objdata the objdata
	 * @param rs      the rs
	 */
	private void processResultset(ObjectData objdata, ResultSet rs) {

		CustomPayloadUtil load = null;
		try {
			while (rs.next()) {
				load = new CustomPayloadUtil(rs);
				response.addPartialResult(objdata, OperationStatus.SUCCESS,
						DatabaseConnectorConstants.SUCCESS_RESPONSE_CODE,
						DatabaseConnectorConstants.SUCCESS_RESPONSE_MESSAGE, load);
			}
			response.finishPartialResult(objdata);
		} catch (SQLException e) {
			ResponseUtil.addExceptionFailure(response, objdata, e);
		} finally {
			IOUtil.closeQuietly(load);
		}

	}

}
