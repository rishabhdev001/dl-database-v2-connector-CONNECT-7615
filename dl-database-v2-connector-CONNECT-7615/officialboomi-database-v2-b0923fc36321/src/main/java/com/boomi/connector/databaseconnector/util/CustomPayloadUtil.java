// © Copyright IBM Corp. 2023, 2024
// Copyright (c) 2020 Boomi, Inc.
package com.boomi.connector.databaseconnector.util;

import com.boomi.connector.api.BasePayload;
import com.boomi.connector.api.ConnectorException;
import com.boomi.connector.api.PayloadMetadata;
import com.opencsv.CSVWriter;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.ConnectException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * @author swastik.vn
 **/
public class CustomPayloadUtil extends BasePayload {
	private static final Logger logger = Logger.getLogger(CustomPayloadUtil.class.getName());

	/** The Constant for storing total processed records. */
	private long grandTotalProcessed = 0;

	private long batchCount = 0;

	private PayloadMetadata _metadata;

	/** The rs. */
	private ResultSet rs;

	/** The writer. */
	CSVWriter writer = null;

	/**
	 * Creates a new instance. Closing the payload will close the Resultset
	 *
	 * @param resultset the resultset
	 */
	public CustomPayloadUtil(ResultSet resultset) {
		this.rs = resultset;
	}

	public CustomPayloadUtil(ResultSet resultset, long batchCount) {
		this.rs = resultset;
		this.batchCount = batchCount;
		logger.info("inside CustomPayloadutil method batchCount " + batchCount + " rs " + rs);
	}

	public CustomPayloadUtil(ResultSet rs, long batchCount, OutputStream outputStream) throws IOException {
		this.rs = rs;
		this.batchCount = batchCount;
		this.writer = new CSVWriter(new OutputStreamWriter(outputStream));
		logger.info("CustomPayloadUtil constructor - batchCount: " + batchCount + ", rs: " + rs);
	}

	/**
	 * This method will write the resultset in CSV using CSVWriter to Output
	 * stream.
	 *
	 * @param out OutputStream
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	@Override
	public void writeTo(OutputStream out) throws IOException {
		try {
			if (writer == null) {
				writer = new CSVWriter(new OutputStreamWriter(out));
			}
			// Write header row
			try {
				writer.writeNext(getColumnNames());
			} catch (SQLException e) {
				throw new IOException("Error getting column names", e);
			}

			if (batchCount > 0) {
				logger.info("calling batchedPayload method from writeTo method");
				batchedPayload(batchCount, out);
			} else {
				logger.info("calling nonBatchedPayload method from writeTo method");
				nonBatchedPayload(out);
			}
		} finally {
			if (writer != null) {
				writer.close();
			}
		}
	}

	/**
	 * Get {@link PayloadMetadata}.
	 *
	 * @return
	 */
	@Override
	public PayloadMetadata getMetadata() {
		return _metadata;
	}

	/**
	 * Set {@link PayloadMetadata}
	 *
	 * @param metadata
	 */
	public void setMetadata(PayloadMetadata metadata) {
		_metadata = metadata;
	}

	private void batchedPayload(long batchCount, OutputStream out) throws IOException {
		logger.info("Entered batchedPayload with batchCount: " + batchCount);
		try {
			List<String[]> rows = new ArrayList<>();
			while (rs.next()) {
				rows.add(getRowData());
				grandTotalProcessed++;
				if (rows.size() >= batchCount) {
					writer.writeAll(rows);
					rows.clear();
				}
			}
			if (!rows.isEmpty()) {
				writer.writeAll(rows);
			}
			writer.flush();
		} catch (SQLException e) {
			logger.severe("SQLException in batchedPayload: " + e.getMessage());
			throw new ConnectorException(e.getMessage());
		} catch (Exception e) {
			logger.severe("Exception coming in batchedPayload: " + e.getMessage());
			throw new ConnectorException(e.getMessage());
		} finally {
			logger.info("Total records processed in batchedPayload: " + grandTotalProcessed);
		}
	}

	private void nonBatchedPayload(OutputStream out) throws IOException {
		try {
            while(rs.next()){
			    writer.writeNext(getRowData());
            }
		} catch (SQLException e) {
			logger.severe("SQLException in nonBatchedPayload: " + e.getMessage());
			throw new ConnectException(e.getMessage());
		}
	}

	private String[] getColumnNames() throws SQLException {
		int columnCount = rs.getMetaData().getColumnCount();
		String[] columnNames = new String[columnCount];
		for (int i = 1; i <= columnCount; i++) {
			columnNames[i - 1] = rs.getMetaData().getColumnLabel(i);
		}
		return columnNames;
	}

	private String[] getRowData() throws SQLException {
		int columnCount = rs.getMetaData().getColumnCount();
		String[] rowData = new String[columnCount];
		for (int i = 1; i <= columnCount; i++) {
			rowData[i - 1] = rs.getString(i);
		}
		return rowData;
	}

	/**
	 * Close.
	 *
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	@Override
	public void close() throws IOException {
		if (writer != null) {
			writer.close();
		}
	}
}
