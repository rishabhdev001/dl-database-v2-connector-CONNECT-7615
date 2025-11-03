// © Copyright IBM Corp. 2023, 2024
// Copyright (c) 2020 Boomi, Inc.
package com.boomi.connector.databaseconnector.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The Class BatchResponse.
 *
 * @author swastik.vn
 */
public class BatchResponse {

	/** The status. */
	@JsonProperty("Status ")
	private String status;

	/** The batch number. */
	@JsonProperty("Batch Number ")
	private int batchNumber;

	/** The record number. */
	@JsonProperty("No of records in batch ")
	private int recordNumber;
	
	

	

	/**
	 * Instantiates a new batch response.
	 *
	 * @param status       the status
	 * @param batchNumber  the batch number
	 * @param recordNumber the record number
	 */
	public BatchResponse(String status, int batchNumber, int recordNumber) {
		this.status = status;
		this.batchNumber = batchNumber;
		this.recordNumber = recordNumber;
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
	 * Gets the batch number.
	 *
	 * @return the batch number
	 */
	public int getBatchNumber() {
		return batchNumber;
	}

	/**
	 * Sets the batch number.
	 *
	 * @param batchNumber the new batch number
	 */
	public void setBatchNumber(int batchNumber) {
		this.batchNumber = batchNumber;
	}

	/**
	 * Gets the record number.
	 *
	 * @return the record number
	 */
	public int getRecordNumber() {
		return recordNumber;
	}

	/**
	 * Sets the record number.
	 *
	 * @param recordNumber the new record number
	 */
	public void setRecordNumber(int recordNumber) {
		this.recordNumber = recordNumber;
	}
	
	

}
