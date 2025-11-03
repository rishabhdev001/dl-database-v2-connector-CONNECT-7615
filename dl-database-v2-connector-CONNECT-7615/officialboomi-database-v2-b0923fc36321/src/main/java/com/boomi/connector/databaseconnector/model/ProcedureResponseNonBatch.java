// © Copyright IBM Corp. 2023, 2024
// Copyright (c) 2020 Boomi, Inc.
package com.boomi.connector.databaseconnector.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The Class ProcedureResponseNonBatch.
 *
 * @author swastik.vn
 */
public class ProcedureResponseNonBatch {

	/** The status code. */
	@JsonProperty("Status Code")
	private int statusCode;

	/** The status message. */
	@JsonProperty("Message")
	private String statusMessage;

	/**
	 * Instantiates a new procedure response non batch.
	 *
	 * @param code the code
	 * @param message the message
	 */
	public ProcedureResponseNonBatch(int code, String message) {

		this.statusCode = code;
		this.statusMessage = message;

	}

	/**
	 * Gets the status code.
	 *
	 * @return the status code
	 */
	public int getStatusCode() {
		return statusCode;
	}

	/**
	 * Sets the status code.
	 *
	 * @param statusCode the new status code
	 */
	public void setStatusCode(int statusCode) {
		this.statusCode = statusCode;
	}

	/**
	 * Gets the status message.
	 *
	 * @return the status message
	 */
	public String getStatusMessage() {
		return statusMessage;
	}

	/**
	 * Sets the status message.
	 *
	 * @param statusMessage the new status message
	 */
	public void setStatusMessage(String statusMessage) {
		this.statusMessage = statusMessage;
	}

}
