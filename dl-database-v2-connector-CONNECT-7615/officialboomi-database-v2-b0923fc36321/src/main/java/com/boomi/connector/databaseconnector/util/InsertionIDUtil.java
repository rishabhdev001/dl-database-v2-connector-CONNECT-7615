// © Copyright IBM Corp. 2023, 2024
// Copyright (c) 2021 Boomi, Inc.
package com.boomi.connector.databaseconnector.util;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * The Class InsertionIDUtil.
 * 
 * @author sweta.b.das
 */
public class InsertionIDUtil {

	/**
	 * Instantiates a new query builder util.
	 */
	private InsertionIDUtil() {

	}

	/**
	 * Gets the insert ids.
	 *
	 * @param statement the statement
	 * @return the insert ids
	 * @throws SQLException the SQL exception
	 */
	public static List<Integer> getInsertIds(PreparedStatement statement) throws SQLException {
		int id = 0;
		List<Integer> listIds = new ArrayList<>();
		try (ResultSet resultSet = statement.getGeneratedKeys();) {
			while (resultSet.next()) {
				try {
					id = resultSet.getInt(1);
				} catch (Exception e) {
					break;
				}
				listIds.add(id);
			}
		}
		return listIds;
	}

	/**
	 * Insert ids for oracle.
	 *
	 * @param statement the statement
	 * @return the list
	 * @throws SQLException the SQL exception
	 */
	public static List<Integer> insertIdsForOracle(PreparedStatement statement) throws SQLException {
		int generatedKey = 0;
		List<Integer> listIds = new ArrayList<>();
		try (ResultSet resultSet = statement.getGeneratedKeys();) {
			while (resultSet.next()) {
				generatedKey = (int) resultSet.getLong(1);
				listIds.add(generatedKey);
			}
		}
		return listIds;
	}

	/**
	 * Gets the id of inserted records.
	 *
	 * @param statement        the statement
	 * @param effectedRowCount the effected row count
	 * @return the id of inserted records
	 */
	public static List<Integer> getIdOfInsertedRecords(PreparedStatement statement, int effectedRowCount)
			throws SQLException {
		int id = 0;
		List<Integer> listIds = new ArrayList<>();
		try (ResultSet resultSet = statement.getGeneratedKeys();) {
			for (int i = 0; i < effectedRowCount; i++) {
				while (resultSet.next()) {
					try {
						id = resultSet.getInt(i);
						listIds.add(id);
					} catch (Exception e) {
						break;
					}
				}
				id++;
				listIds.add(id);
			}
		}
		return listIds;

	}

	/**
	 * Query last id ms SQL.
	 *
	 * @param conn             the conn
	 * @param effectedRowCount the effected row count
	 * @return the list
	 */
	public static List<Integer> queryLastIdMsSQL(Connection conn, int effectedRowCount) throws SQLException {
		String cmd = "SELECT SCOPE_IDENTITY()";
		int id = 0;
		List<Integer> listIds = new ArrayList<>();
		try (PreparedStatement stmt = conn.prepareStatement(cmd); ResultSet resultSet = stmt.executeQuery();) {
			for (int i = 1; i < effectedRowCount + 1; i++) {
				while (resultSet.next()) {
					try {
						id = (int) resultSet.getLong(i);
						listIds.add(id);
					} catch (Exception e) {
						break;
					}
				}
			}
		}
		return listIds;

	}

	/**
	 * Query last id postgre SQL.
	 *
	 * @param conn             the conn
	 * @param effectedRowCount the effected row count
	 * @return the list
	 */
	public static List<Integer> queryLastIdPostgreSQL(Connection conn, int effectedRowCount) throws SQLException {
		String cmd = "select LASTVAL()";
		int id = 0;
		List<Integer> listIds = new ArrayList<>();
		try (PreparedStatement stmt = conn.prepareStatement(cmd); ResultSet resultSet = stmt.executeQuery();) {
			for (int i = 1; i < effectedRowCount + 1; i++) {
				while (resultSet.next()) {
					try {
					id = resultSet.getInt(i);
					listIds.add(id);
					}catch(Exception e) {
						break;
					}
				}
				id++;
				listIds.add(id);
			}
		}
		return listIds;

	}

}
