// © Copyright IBM Corp. 2023, 2024
// Copyright (c) 2020 Boomi, Inc.
package com.boomi.connector.databaseconnector.upsert;

import java.sql.Connection;

import com.boomi.connector.api.OperationResponse;
import com.boomi.connector.api.ResponseUtil;
import com.boomi.connector.api.UpdateRequest;
import com.boomi.connector.databaseconnector.DatabaseConnectorConnection;
import com.boomi.connector.databaseconnector.util.DatabaseConnectorConstants;
import com.boomi.connector.util.SizeLimitedUpdateOperation;


/**
 * The Class UpsertOperation.
 *
 * @author swastik.vn
 */
public class UpsertOperation extends SizeLimitedUpdateOperation {

	/**
	 * Instantiates a new upsert operation.
	 *
	 * @param conn the conn
	 */
	public UpsertOperation(DatabaseConnectorConnection conn) {
		super(conn);
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
		Long batchCount = getContext().getOperationProperties().getLongProperty(DatabaseConnectorConstants.BATCH_COUNT);
		String commitOption = getContext().getOperationProperties()
				.getProperty(DatabaseConnectorConstants.COMMIT_OPTION);
		try (Connection con = conn.getSoloConnection().connect(conn.getUrl(), conn.loadProperties());) {
			con.setAutoCommit(false);
			String databaseName = con.getMetaData().getDatabaseProductName();
			if ("MySQL".equals(databaseName)) {
				MysqlUpsert upsert = new MysqlUpsert(con, batchCount, getContext().getObjectTypeId(), commitOption);
				upsert.executeStatements(request, response);
			} else if ("PostgreSQL".equals(databaseName)) {
				PostgresUpsert upsert = new PostgresUpsert(con, batchCount, getContext().getObjectTypeId(),
						commitOption);
				upsert.executeStatements(request, response);
			} else {
				CommonUpsert upsert = new CommonUpsert(con, batchCount, getContext().getObjectTypeId(), commitOption);
				upsert.executeStatements(request, response);
			}

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
