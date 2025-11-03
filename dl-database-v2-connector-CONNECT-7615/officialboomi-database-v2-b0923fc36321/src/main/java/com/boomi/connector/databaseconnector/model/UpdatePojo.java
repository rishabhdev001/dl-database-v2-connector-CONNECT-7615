// © Copyright IBM Corp. 2023, 2024
// Copyright (c) 2020 Boomi, Inc.
package com.boomi.connector.databaseconnector.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;


/**
 * The Class UpdatePojo.
 *
 * @author swastik.vn
 */
public class UpdatePojo {

	/** The set. */
	private List<Set> set;
	
	/** The where. */
	private List<Where> where;

	/**
	 *  The where.
	 *
	 * @return the sets the
	 */

	/**
	 * Gets the sets the.
	 *
	 * @return the sets the
	 */
	public List<Set> getSet() {
		return set;
	}

	/**
	 * Sets the sets the.
	 *
	 * @param set the new sets the
	 */
	@JsonProperty("SET")
	public void setSet(List<Set> set) {
		this.set = set;
	}

	/**
	 * Gets the where.
	 *
	 * @return the where
	 */
	

	/**
	 * Gets the where.
	 *
	 * @return the where
	 */
	public List<Where> getWhere() {
		return where;
	}

	/**
	 * Sets the where.
	 *
	 * @param where the new where
	 */
	@JsonProperty("WHERE")
	public void setWhere(List<Where> where) {
		this.where = where;
	}
	
	/**
	 * The Class Set.
	 */
	public static class Set {

		/** The column. */
		private String column;

		/** The value. */
		private String value;

		/**
		 * Gets the value.
		 *
		 * @return the value
		 */
		public String getValue() {
			return value;
		}

		/**
		 * Sets the value.
		 *
		 * @param value the new value
		 */
		public void setValue(String value) {
			this.value = value;
		}

		/**
		 * Gets the column.
		 *
		 * @return the column
		 */
		public String getColumn() {
			return column;
		}

		/**
		 * Sets the column.
		 *
		 * @param column the new column
		 */
		public void setColumn(String column) {
			this.column = column;
		}

	}

	

}
