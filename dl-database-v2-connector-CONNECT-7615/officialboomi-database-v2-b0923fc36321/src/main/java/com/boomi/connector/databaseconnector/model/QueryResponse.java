// © Copyright IBM Corp. 2023, 2024
// Copyright (c) 2020 Boomi, Inc.
package com.boomi.connector.databaseconnector.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The Class QueryResponse.
 *
 * @author swastik.vn
 */
public class QueryResponse {
	
	/** The query. */
	@JsonProperty("Query")
	private String query;
	
	/** The rows effected. */
	@JsonProperty("Rows Effected")
	private int rowsEffected;
	

	/** The status. */
	@JsonProperty("Status")
	private String status;
	
	
	/**
	 * Instantiates a new query response.
	 *
	 * @param query the query
	 * @param rowsEffected the rows effected
	 * @param id the id
	 * @param status the status
	 */
	public QueryResponse(String query, int rowsEffected, List<Integer> id, String status) {
		this.query = query;
		this.rowsEffected = rowsEffected;
		this.status = status;
		
	}
	
	
	
	public QueryResponse(String query, int rowsEffected, String status) {
		this.query = query;
		this.rowsEffected = rowsEffected;
		this.status = status;
		
	}

	/**
	 * Gets the query.
	 *
	 * @return the query
	 */
	public String getQuery() {
		return query;
	}

	/**
	 * Sets the query.
	 *
	 * @param query the new query
	 */
	public void setQuery(String query) {
		this.query = query;
	}
	
	/**
	 * Gets the status.
	 *
	 * @return the status
	 */
	public String getStatus() {
		return status;
	}

	/**
	 * Sets the status.
	 *
	 * @param status the new status
	 */
	public void setStatus(String status) {
		this.status = status;
	}
	
	/**
	 * Gets the rows effected.
	 *
	 * @return the rows effected
	 */
	public int getRowsEffected() {
		return rowsEffected;
	}

	/**
	 * Sets the rows effected.
	 *
	 * @param rowsEffected the new rows effected
	 */
	public void setRowsEffected(int rowsEffected) {
		this.rowsEffected = rowsEffected;
	}

}
