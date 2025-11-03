// © Copyright IBM Corp. 2023, 2024
// Copyright (c) 2020 Boomi, Inc.
package com.boomi.connector.databaseconnector;

import com.boomi.connector.api.BrowseContext;
import com.boomi.connector.api.Browser;
import com.boomi.connector.api.Operation;
import com.boomi.connector.api.OperationContext;
import com.boomi.connector.databaseconnector.get.DynamicGetOperation;
import com.boomi.connector.databaseconnector.get.StandardGetOperation;
import com.boomi.connector.databaseconnector.storedprocedureoperation.StoredProcedureOperation;
import com.boomi.connector.databaseconnector.upsert.UpsertOperation;
import com.boomi.connector.databaseconnector.util.DatabaseConnectorConstants;
import com.boomi.connector.util.BaseConnector;



/**
 * The Class DatabaseConnectorConnector.
 *
 * @author swastik.vn
 */
public class DatabaseConnectorConnector extends BaseConnector {

	/**
	 * Creates the browser.
	 *
	 * @param context the context
	 * @return the browser
	 */
	@Override
	public Browser createBrowser(BrowseContext context) {
		return new DatabaseConnectorBrowser(createConnection(context));
	}

	/**
	 * Creates the create operation.
	 *
	 * @param context the context
	 * @return the operation
	 */
	@Override
	protected Operation createCreateOperation(OperationContext context) {
		String insertionType = (String) context.getOperationProperties().get(DatabaseConnectorConstants.INSERTION_TYPE);
		switch (insertionType) {
		case DatabaseConnectorConstants.DYNAMIC_INSERT:
			return new DynamicInsertOperation(createConnection(context));
		case DatabaseConnectorConstants.STANDARD_INSERT:
			return new StandardInsertOperation(createConnection(context));
		default:
			return null;
		}
	}

	/**
	 * Creates the update operation.
	 *
	 * @param context the context
	 * @return the operation
	 */
	@Override
	protected Operation createUpdateOperation(OperationContext context) {
		String updateType = (String) context.getOperationProperties().get(DatabaseConnectorConstants.TYPE);
		switch (updateType) {
		case DatabaseConnectorConstants.DYNAMIC_UPDATE:
			return new DynamicUpdateOperation(createConnection(context));
		case DatabaseConnectorConstants.STANDARD_UPDATE:
			return new StandardOperation(createConnection(context));
		default:
			return null;
		}
	}

	/**
	 * Creates the execute operation.
	 *
	 * @param context the context
	 * @return the operation
	 */
	@Override
	protected Operation createExecuteOperation(OperationContext context) {
		String opsType = context.getCustomOperationType();
		String getType = (String) context.getOperationProperties().get(DatabaseConnectorConstants.GET_TYPE);
		String deleteType = (String) context.getOperationProperties().get(DatabaseConnectorConstants.DELETE_TYPE);
		switch (opsType) {
		case DatabaseConnectorConstants.GET:
			if (getType.equals(DatabaseConnectorConstants.DYNAMIC_GET)) {
				return new DynamicGetOperation(createConnection(context));
			} else {
				return new StandardGetOperation(createConnection(context));
			}

		case DatabaseConnectorConstants.STOREDPROCEDUREWRITE:
			return new StoredProcedureOperation(createConnection(context));

		case DatabaseConnectorConstants.DELETE:
			if (deleteType.equals(DatabaseConnectorConstants.DYNAMIC_DELETE)) {
				return new DynamicDeleteOperation(createConnection(context));
			} else {
				return new StandardOperation(createConnection(context));
			}
		default:
			return null;
		}

	}

	/**
	 * Creates the upsert operation.
	 *
	 * @param context the context
	 * @return the operation
	 */
	@Override
	protected Operation createUpsertOperation(OperationContext context) {
		return new UpsertOperation(createConnection(context));
	}

	/**
	 * Creates the connection.
	 *
	 * @param context the context
	 * @return the database connector connection
	 */
	private DatabaseConnectorConnection createConnection(BrowseContext context) {
		return new DatabaseConnectorConnection(context);
	}
}