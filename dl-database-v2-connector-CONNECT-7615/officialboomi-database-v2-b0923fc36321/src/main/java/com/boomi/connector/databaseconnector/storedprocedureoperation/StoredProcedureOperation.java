// © Copyright IBM Corp. 2023, 2024
// Copyright (c) 2020 Boomi, Inc.
package com.boomi.connector.databaseconnector.storedprocedureoperation;

import static com.boomi.connector.databaseconnector.util.DatabaseConnectorConstants.BATCH_COUNT;

import java.sql.Connection;

import com.boomi.connector.api.OperationResponse;
import com.boomi.connector.api.ResponseUtil;
import com.boomi.connector.api.UpdateRequest;
import com.boomi.connector.databaseconnector.DatabaseConnectorConnection;
import com.boomi.connector.util.SizeLimitedUpdateOperation;


/**
 * The Class StoredProcedureOperation.
 *
 * @author swastik.vn
 */
public class StoredProcedureOperation extends SizeLimitedUpdateOperation {

	/**
	 * Instantiates a new stored procedure operation.
	 *
	 * @param conn the conn
	 */
	public StoredProcedureOperation(DatabaseConnectorConnection conn) {
		super(conn);
	}

	/**
	 * Execute size limited update.
	 *
	 * @param request the request
	 * @param response the response
	 */
	@Override
	public void executeSizeLimitedUpdate(UpdateRequest request, OperationResponse response) {
		DatabaseConnectorConnection conn = getConnection();
		String procedureName = getContext().getObjectTypeId();
		try (Connection con = conn.getSoloConnection().connect(conn.getUrl(), conn.loadProperties());) {
			con.setAutoCommit(false);
			Long batchCount = getContext().getOperationProperties().getLongProperty(BATCH_COUNT);
			Long maxFieldSize = getContext().getOperationProperties().getLongProperty("maxFieldSize");
			StoredProcedureExecute execute = new StoredProcedureExecute(con, procedureName, request, response);
			execute.executeStatements(batchCount, maxFieldSize);
		} catch (Exception e) {
			ResponseUtil.addExceptionFailures(response, request, e);
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