// © Copyright IBM Corp. 2023, 2024
// Copyright (c) 2020 Boomi, Inc.
package com.boomi.connector.databaseconnector;

import com.boomi.connector.api.BrowseContext;
import com.boomi.connector.api.Browser;
import com.boomi.connector.api.Operation;
import com.boomi.connector.api.OperationContext;
import com.boomi.connector.databaseconnector.get.DynamicGetOperation;
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
	 * Creates the execute operation.
	 *
	 * @param context the context
	 * @return the operation
	 */
	@Override
	protected Operation createExecuteOperation(OperationContext context) {
		return new DynamicGetOperation(createConnection(context));
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
