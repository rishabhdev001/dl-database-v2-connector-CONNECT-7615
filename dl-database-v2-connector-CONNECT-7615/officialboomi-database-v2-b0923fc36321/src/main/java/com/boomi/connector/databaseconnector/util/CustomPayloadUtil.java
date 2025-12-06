// © Copyright IBM Corp. 2023, 2024
// Copyright (c) 2020 Boomi, Inc.
package com.boomi.connector.databaseconnector.util;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.ConnectException;
import java.sql.ResultSet;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.sql.Types;
import java.text.DecimalFormat;
import java.util.logging.Logger;

import static com.boomi.connector.databaseconnector.util.DatabaseConnectorConstants.*;

import com.boomi.connector.api.BasePayload;
import com.boomi.connector.api.ConnectorException;
import com.boomi.connector.api.PayloadMetadata;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import oracle.sql.NUMBER;

/**
 * @author swastik.vn
 **/
public class CustomPayloadUtil extends BasePayload {
	private static final Logger logger = Logger.getLogger(CustomPayloadUtil.class.getName());

	/** The Constant JSON_FACTORY. */
	private static final JsonFactory JSON_FACTORY = new JsonFactory();

	/** The Constant for storing total processed records. */
	private long grandTotalProcessed = 0;

	private long batchCount = 0;

	private PayloadMetadata _metadata;

	/** The rs. */
	private ResultSet rs;

	/** The generator. */
	JsonGenerator generator = null;
	
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
		logger.info("inside CustomPayloadutil method batchCount "+ batchCount+ " rs " + rs);
	}

	public CustomPayloadUtil(ResultSet rs, long batchCount, OutputStream outputStream) throws IOException {
		this.rs = rs;
		this.batchCount = batchCount;
		this.generator = JSON_FACTORY.createGenerator(outputStream); // Correct variable
		logger.info("CustomPayloadUtil constructor - batchCount: " + batchCount + ", rs: " + rs);
	}



	/**
	 * This method will write the resultset in Json using JsonGenerator to Output
	 * stream.
	 *
	 * @param out OutputStream
	 * @throws IOException Signals that an I/O exception has occurred.
	 */

	public void writeTo(OutputStream out) throws IOException {
		try {
			if (batchCount > 0) {
				logger.info("calling batchedPayload method from writeTo method");
				batchedPayload(batchCount, out);
			} else {
				generator = JSON_FACTORY.createGenerator(out);
				logger.info("calling nonBatchedPayload method from writeTo method");
				nonBatchedPayload(out);
			}
		} finally {
			generator.close();
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
	 * @param metadata
	 */
	public void setMetadata(PayloadMetadata metadata) {
		_metadata = metadata;
	}

	private void batchedPayload(long batchCount, OutputStream out) throws IOException {
		logger.info("Entered batchedPayload with batchCount: " + batchCount);
		generator = JSON_FACTORY.createGenerator(out);
		try {
			generator.writeStartArray();
			for (long i = 0; i < batchCount; i++) {
				boolean hasNext = (i == 0) ? rs.getRow() != 0 : rs.next(); // For the first record, assume rs is already positioned
				logger.info("Calling rs.next(), hasNext: " + hasNext);

				if (!hasNext) {
					logger.info("No more rows in ResultSet. Breaking loop.");
					break;
				}
				logger.info("Current ResultSet row: " + rs.getRow());
				nonBatchedPayload(out);
				grandTotalProcessed++;
			}
			generator.writeEndArray();
			logger.info("flush called from batchedPayload: ");
			generator.flush();
		} catch (SQLException e) {
			logger.log(java.util.logging.Level.SEVERE, "SQLException in batchedPayload", e);
			throw new ConnectorException(e);
		} catch (Exception e) {
			logger.log(java.util.logging.Level.SEVERE, "Exception coming in batchedPayload", e);
			throw new ConnectorException(e);
		} finally {
			generator.close();
			logger.info("Total records processed in batchedPayload: " +grandTotalProcessed );
			logger.info("JSON generator closed in batchedPayload.");

		}
	}


	private void nonBatchedPayload(OutputStream out) throws IOException {
		try {
			generator.writeStartObject();
			for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
				if (rs.getMetaData().getColumnType(i) == Types.INTEGER || rs.getMetaData().getColumnType(i) == Types.SMALLINT
						|| rs.getMetaData().getColumnType(i) == Types.TINYINT) {
					int value = rs.getInt(rs.getMetaData().getColumnLabel(i));
					if (rs.wasNull()) {
						generator.writeNullField(rs.getMetaData().getColumnLabel(i));
					} else {
						generator.writeNumberField(rs.getMetaData().getColumnLabel(i), value);
					}
					generator.flush();
				}
				else if (rs.getMetaData().getColumnType(i) == Types.VARCHAR || rs.getMetaData().getColumnType(i) == Types.DATE
						|| rs.getMetaData().getColumnType(i) == Types.TIME || rs.getMetaData().getColumnType(i) == Types.LONGVARCHAR
						|| rs.getMetaData().getColumnType(i) == Types.CLOB || rs.getMetaData().getColumnType(i) == Types.TIMESTAMP
						|| rs.getMetaData().getColumnType(i) == Types.NVARCHAR || rs.getMetaData().getColumnTypeName(i).equalsIgnoreCase(JSON)
						||  rs.getMetaData().getColumnType(i) == Types.CHAR ||  rs.getMetaData().getColumnType(i) == Types.NCHAR
						|| rs.getMetaData().getColumnType(i) == Types.LONGNVARCHAR || rs.getMetaData().getColumnType(i) == Types.ROWID) {
					String varchar = rs.getString(rs.getMetaData().getColumnLabel(i));
					generator.writeStringField(rs.getMetaData().getColumnLabel(i), varchar);
					generator.flush();
				} else if (rs.getMetaData().getColumnType(i) == Types.BOOLEAN || rs.getMetaData().getColumnType(i) == Types.BIT) {
					boolean flag = rs.getBoolean(rs.getMetaData().getColumnLabel(i));
					if (rs.wasNull()) {
						generator.writeNullField(rs.getMetaData().getColumnLabel(i));
					} else {
						generator.writeBooleanField(rs.getMetaData().getColumnLabel(i), flag);
					}
					generator.flush();
				}else if(rs.getMetaData().getColumnType(i) == Types.BLOB || rs.getMetaData().getColumnType(i) == Types.LONGVARBINARY
						|| rs.getMetaData().getColumnType(i) == Types.BINARY || rs.getMetaData().getColumnType(i) == Types.VARBINARY) {
					if(rs.getBytes(rs.getMetaData().getColumnLabel(i))!=null) {
						generator.writeStringField(rs.getMetaData().getColumnLabel(i), new String(rs.getBytes(rs.getMetaData().getColumnLabel(i))));
					}
					generator.flush();
				}
				else if(rs.getMetaData().getColumnType(i) == Types.DECIMAL || rs.getMetaData().getColumnType(i) == Types.DOUBLE) {
					double value = rs.getDouble(rs.getMetaData().getColumnLabel(i));
					if (rs.wasNull()) {
						generator.writeNullField(rs.getMetaData().getColumnLabel(i));
					} else {
						generator.writeNumberField(rs.getMetaData().getColumnLabel(i), value);
					}
					generator.flush();
				}
				else if(rs.getMetaData().getColumnType(i) == Types.FLOAT || rs.getMetaData().getColumnType(i) == Types.REAL) {
					float value = rs.getFloat(rs.getMetaData().getColumnLabel(i));
					if (rs.wasNull()) {
						generator.writeNullField(rs.getMetaData().getColumnLabel(i));
					} else {
						generator.writeNumberField(rs.getMetaData().getColumnLabel(i), value);
					}
					generator.flush();
				}
				else if(rs.getMetaData().getColumnType(i) == Types.BIGINT) {
					long value = rs.getLong(rs.getMetaData().getColumnLabel(i));
					if (rs.wasNull()) {
						generator.writeNullField(rs.getMetaData().getColumnLabel(i));
					} else {
						generator.writeNumberField(rs.getMetaData().getColumnLabel(i), value);
					}
					generator.flush();
				}
				else if(rs.getMetaData().getColumnType(i) == Types.NUMERIC) {
					BigDecimal value = rs.getBigDecimal(rs.getMetaData().getColumnLabel(i));
					if (rs.wasNull()) {
						generator.writeNullField(rs.getMetaData().getColumnLabel(i));
					} else {
						generator.writeNumberField(rs.getMetaData().getColumnLabel(i), value);
					}
				}
				generator.flush();
			}
			generator.writeEndObject();
			generator.flush();
		} catch (SQLException e) {
			logger.severe("SQLException in nonBatchedPayload: " + e.getMessage());
			throw new ConnectException(e.getMessage());
		}
	}


	/**
	 * Close.
	 *
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	@Override
	public void close() throws IOException {
		try {
			if (!generator.isClosed()) {
				generator.close();
			}
		} catch (Exception e) {
			throw new ConnectorException(e.getMessage());
		}
	}

}
