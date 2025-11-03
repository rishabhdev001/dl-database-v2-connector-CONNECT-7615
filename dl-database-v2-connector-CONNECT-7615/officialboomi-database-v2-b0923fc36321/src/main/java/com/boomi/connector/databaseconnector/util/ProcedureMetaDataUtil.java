// © Copyright IBM Corp. 2023, 2024
// Copyright (c) 2020 Boomi, Inc.
package com.boomi.connector.databaseconnector.util;

import static com.boomi.connector.databaseconnector.util.DatabaseConnectorConstants.*;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.boomi.connector.api.ConnectorException;

/**
 * The Class ProcedureMetaDataUtil.
 *
 * @author swastik.vn
 */
public class ProcedureMetaDataUtil {

	/**
	 * Instantiates a new procedure meta data util.
	 */
	private ProcedureMetaDataUtil() {

	}

	/**
	 * This method will get the Input Parameters along with DataType required for
	 * the procedure call.
	 *
	 * @param con the con
	 * @param objectTypeId the object type id
	 * @return the procedure metadata
	 */
	public static Map<String, Integer> getProcedureMetadata(Connection con, String objectTypeId) {
		Map<String, Integer> dataType = new HashMap<>();
		try {
			DatabaseMetaData md = con.getMetaData();
			try (ResultSet rs = md.getProcedureColumns(null, null, objectTypeId, null);) {
				while (rs.next()) {
					if((md.getDatabaseProductName().equalsIgnoreCase(ORACLE)
							&& rs.getString(6).equals(UNKNOWN_DATATYPE)
							&& rs.getString(TYPE_NAME).equalsIgnoreCase(DatabaseConnectorConstants.JSON))) {
						dataType.put(rs.getString(COLUMN_NAME), Types.OTHER);
					}
					if ((md.getDatabaseProductName().equalsIgnoreCase(POSTGRESQL)
							&& rs.getString(6).equals(UNKNOWN_DATATYPE)
							&& rs.getString(TYPE_NAME).equalsIgnoreCase(DatabaseConnectorConstants.JSON))
							|| !rs.getString(6).equals(UNKNOWN_DATATYPE) && !rs.getString(COLUMN_NAME).equals("@RETURN_VALUE")) {
						dataType.put(rs.getString(COLUMN_NAME), Integer.valueOf(rs.getString(6)));
					}

				}

			}
		} catch (SQLException e) {
			throw new ConnectorException(e.getMessage());
		}

		return dataType;

	}

	/**
	 * This method will get the Input Parameters required for the procedure call.
	 *
	 * @param con the con
	 * @param objectTypeId the object type id
	 * @return the procedure params
	 */
	public static List<String> getProcedureParams(Connection con, String objectTypeId) {

		List<String> params = new ArrayList<>();
		try {
			DatabaseMetaData md = con.getMetaData();
			String databaseName = md.getDatabaseProductName();
			try (ResultSet rs = md.getProcedureColumns(null, null, objectTypeId, null);) {
				while (rs.next()) {

					if ((databaseName.equalsIgnoreCase(POSTGRESQL) && rs.getString(6).equals(UNKNOWN_DATATYPE)
							&& rs.getString(TYPE_NAME).equalsIgnoreCase(DatabaseConnectorConstants.JSON))
							|| !rs.getString(6).equals(UNKNOWN_DATATYPE)
									&& !rs.getString(COLUMN_NAME).equals("@RETURN_VALUE")||(md.getDatabaseProductName().equalsIgnoreCase(ORACLE)
											&& rs.getString(6).equals(UNKNOWN_DATATYPE)
											&& rs.getString(TYPE_NAME).equalsIgnoreCase(DatabaseConnectorConstants.JSON))) {
						params.add(rs.getString(COLUMN_NAME));
					}

				}

			}
		} catch (SQLException e) {
			throw new ConnectorException(e.getMessage());
		}

		return params;

	}

	/**
	 * This method will get the IN params of Procedure.
	 *
	 * @param con the con
	 * @param objectTypeId the object type id
	 * @return the input params
	 */
	public static List<String> getInputParams(Connection con, String objectTypeId) {
		List<String> inparams = new ArrayList<>();

		try (ResultSet rs = con.getMetaData().getProcedureColumns(null, null, objectTypeId, null)) {
			while (rs.next()) {
				if (con.getMetaData().getDatabaseProductName().equals(MSSQLSERVER)) {
					if (rs.getShort(5) == 1 || rs.getShort(5) == 2 || rs.getShort(5) == 4) {
						inparams.add(rs.getString(4));
					}
				} else {
					if (rs.getShort(5) == 1 || rs.getShort(5) == 2) {
						inparams.add(rs.getString(4));
					}
				}

			}

		} catch (SQLException e) {
			throw new ConnectorException(e.getMessage());
		}

		return inparams;
	}

	/**
	 * This method will get the OutPut Parameters of the Stored Procedure.
	 *
	 * @param con the con
	 * @param objectTypeId the object type id
	 * @return the output params
	 */
	public static List<String> getOutputParams(Connection con, String objectTypeId) {
		List<String> outParams = new ArrayList<>();

		try (ResultSet rs = con.getMetaData().getProcedureColumns(null, null, objectTypeId, null)) {
			while (rs.next()) {
				if (rs.getShort(5) == 2 || rs.getShort(5) == 4) {
					outParams.add(rs.getString(4));
				}
			}

		} catch (SQLException e) {
			throw new ConnectorException(e.getMessage());
		}

		return outParams;
	}

}
