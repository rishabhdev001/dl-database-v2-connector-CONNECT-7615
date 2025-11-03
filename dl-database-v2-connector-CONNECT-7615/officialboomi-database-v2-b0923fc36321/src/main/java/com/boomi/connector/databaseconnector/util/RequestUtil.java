// © Copyright IBM Corp. 2023, 2024
// Copyright (c) 2020 Boomi, Inc.
package com.boomi.connector.databaseconnector.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import com.boomi.connector.api.ConnectorException;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * The Class RequestUtil.
 *
 * @author swastik.vn
 */
public class RequestUtil {

	/**
	 * Instantiates a new request util.
	 */
	private RequestUtil() {

	}

	/**
	 * This method will get the userData from the input stream and enters it into
	 * Map as Key value pairs and will be used only for Standard Get Operation.
	 * Since the key is unknown we will be needing key and value from the input
	 * object data.
	 *
	 * @param is the is
	 * @return userData
	 */
	@SuppressWarnings("unchecked")
	public static Map<String, Object> getUserData(InputStream is) {

		ObjectMapper mapper = new ObjectMapper().disable(MapperFeature.CAN_OVERRIDE_ACCESS_MODIFIERS)
				.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
		Map<String, Object> userData = null;
		try (JsonParser jp = new JsonFactory().createParser(is)) {

			if (jp.nextToken() != null) {
				userData = mapper.readValue(jp, Map.class);
			}
		} catch (IOException e) {
			throw new ConnectorException(e.getMessage());
		}
		return userData;

	}

	/**
	 * Gets the json data.
	 *
	 * @param is the is
	 * @return the json data
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	public static JsonNode getJsonData(InputStream is) throws IOException {
		JsonNode jsonData = null;
		ObjectMapper mapper = new ObjectMapper().disable(MapperFeature.CAN_OVERRIDE_ACCESS_MODIFIERS)
				.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
		if (is.available() != 0) {
			jsonData = mapper.readTree(is);
			return jsonData;
		} else {
			return null;
		}
	}

}
