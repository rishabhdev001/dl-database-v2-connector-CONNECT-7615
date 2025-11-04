// © Copyright IBM Corp. 2023, 2024
// Copyright (c) 2020 Boomi, Inc.
package com.boomi.connector.databaseconnector;

import java.io.IOException;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.boomi.connector.api.BrowseContext;
import com.boomi.connector.api.ConnectorException;
import com.boomi.connector.databaseconnector.util.DatabaseConnectorConstants;
import com.boomi.connector.util.BaseConnection;
import com.boomi.util.StringUtil;


/**
 * The Class DatabaseConnectorConnection.
 *
 * @author swastik.vn
 */
public class DatabaseConnectorConnection extends BaseConnection {

	/** The class name. */
	private String className;

	/** The username. */
	private String username;

	/** The password. */
	private String password;

	/** The url. */
	private String url;

	/** The classpath. */
	private String classpath;

	/** The custom property. */
	private Map<String, String> customProperty;

	/** The Constant logger. */
	private static final Logger logger = Logger.getLogger(DatabaseConnectorConnection.class.getName());


	/**
	 * Instantiates a new database connector connection.
	 *
	 * @param context the context
	 */
	public DatabaseConnectorConnection(BrowseContext context) {
		super(context);
		this.className = getContext().getConnectionProperties().getProperty(DatabaseConnectorConstants.CLASSNAME,"");
		this.username = getContext().getConnectionProperties().getProperty(DatabaseConnectorConstants.USERNAME,"");
		this.password = getContext().getConnectionProperties().getProperty(DatabaseConnectorConstants.PASS,"");
		this.url = getContext().getConnectionProperties().getProperty(DatabaseConnectorConstants.URL,"");
		this.classpath = getContext().getConnectionProperties().getProperty(DatabaseConnectorConstants.CLASSPATH,"");
		this.customProperty = getContext().getConnectionProperties().getCustomProperties("CustomProperties");
	}
	/**
	 * Gets the class path.
	 *
	 * @return the class path
	 */
	public String getClassPath() {
		return classpath;
	}

	/**
	 * Sets the class name.
	 *
	 * @param classpath the new class path
	 */
	public void setClassPath(String classpath) {
		this.classpath = classpath;
	}
	/**
	 * Gets the class name.
	 *
	 * @return the class name
	 */
	public String getClassName() {
		return className;
	}

	/**
	 * Sets the class name.
	 *
	 * @param className the new class name
	 */
	public void setClassName(String className) {
		this.className = className;
	}

	/**
	 * Gets the username.
	 *
	 * @return the username
	 */
	public String getUsername() {
		return username;
	}

	/**
	 * Sets the username.
	 *
	 * @param username the new username
	 */
	public void setUsername(String username) {
		this.username = username;
	}

	/**
	 * Gets the password.
	 *
	 * @return the password
	 */
	public String getPassword() {
		return password;
	}

	/**
	 * Sets the password.
	 *
	 * @param password the new password
	 */
	public void setPassword(String password) {
		this.password = password;
	}

	/**
	 * Gets the url.
	 *
	 * @return the url
	 */
	public String getUrl() {
		if(StringUtil.isEmpty(url) || StringUtil.isBlank(url)) {
			throw new ConnectorException("The Connection URL field cannot be empty!!");
		}
		return url;
	}

	/**
	 * Sets the url.
	 *
	 * @param url the new url
	 *
	 */
	public void setUrl(String url) {
		this.url = url;
	}

	/**
	 * Load properties.
	 *
	 * @return the properties
	 */
	public Properties loadProperties() {
		Properties prop = new Properties();
		prop.put("user", getUsername());
		prop.put("password", getPassword());
		if (!customProperty.isEmpty()) {
			for (Map.Entry<String, String> entry : customProperty.entrySet()) {
				prop.put(entry.getKey(), entry.getValue());
			}
		}

		return prop;

	}

	/**
	 * Gets the solo connection.
	 *
	 * @return the solo connection
	 */
	//String classpath = "/Users/mjain/Downloads/mysql-connector-java-3.1.14-bin.jar";
	//String className = "com.mysql.cj.jdbc.Driver";
	DynamicClassLoader loader = new DynamicClassLoader();
	public Driver getSoloConnection() {

		try {
			// In order to allow the ability to invoke multiple database drivers at the same
			// time, the DriverManager
			// class needs to be initialized before Class.forName is called. Calling this
			// particular method will invoke
			// the DriverManager so it is class loaded before trying to instantiate drivers.

			//DriverManager.getLoginTimeout();
			//return (Driver) Class.forName(className).newInstance();
			DriverManager.getLoginTimeout();
			//return (Driver) Class.forName(className).newInstance();
			//logger.info("running loader");
			logger.info("class name " + className);
			logger.info("pathOrJarFile " + classpath);
			logger.info("loader " + loader);
			Driver driver = (Driver) loader.createInstance(classpath, className);
			logger.info("running loader " + driver);
			return driver;

		} catch (ClassNotFoundException e) {
			throw new ConnectorException("Failed Loading the class");
		} catch (IOException | ReflectiveOperationException e) {
			throw new ConnectorException(e.getMessage());
		}
	}

	/**
	 * Method to test the Database Connection by taking the Standard JDBC
	 * Parameters.
	 *
	 * @throws ConnectorException the connector exception
	 */
	public void test() {
		try (Connection conn = getSoloConnection().connect(getUrl(), loadProperties());) {
			logger.log(Level.FINE, "Connection established successfully");
		} catch (SQLException e) {
			throw new ConnectorException(e.getMessage());
		}

	}
	/**
	 * Gets the custom property.
	 *
	 * @return the custom property
	 */
	public Map<String, String> getCustomProperty() {
		return customProperty;
	}

	/**
	 * Sets the custom property.
	 *
	 * @param customProperty the custom property
	 */
	public void setCustomProperty(Map<String, String> customProperty) {
		this.customProperty = customProperty;
	}

}
