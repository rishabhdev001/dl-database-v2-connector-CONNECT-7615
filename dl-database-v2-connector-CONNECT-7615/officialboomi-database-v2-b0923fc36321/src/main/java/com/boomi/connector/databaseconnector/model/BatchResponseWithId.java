// © Copyright IBM Corp. 2023, 2024
// Copyright (c) 2021 Boomi, Inc.
package com.boomi.connector.databaseconnector.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;


/**
 * The Class BatchResponseWithId.
 * @author sweta.b.das
 */
public class BatchResponseWithId extends BatchResponse {

	/** The ids. */
	@JsonProperty("Inserted Ids ")
	public List<Integer> ids;
	
	/**
	 * Instantiates a new batch response.
	 *
	 * @param status       the status
	 * @param batchNumber  the batch number
	 * @param ids the ids
	 * @param recordNumber the record number
	 */
	public BatchResponseWithId(String status, int batchNumber, List<Integer> ids, int recordNumber) {
		super(status, batchNumber, recordNumber);
		this.ids = ids;
			
	}

}
