// © Copyright IBM Corp. 2023, 2024
// Copyright (c) 2020 Boomi, Inc.
package com.boomi.connector.databaseconnector.util;



/**
 * The Class DatabaseConnectorConstants.
 *
 * @author swastik.vn
 */
public class DatabaseConnectorConstants {

	/**
	 * Instantiates a new database connector constants.
	 */
	private DatabaseConnectorConstants() {

	}

	/** The Constant INTEGER. */
	public static final String INTEGER = "integer";

	/** The Constant NUMBER. */
	public static final String NUMBER = "number";

	/** The Constant DOUBLE. */
	public static final String DOUBLE = "double";

	/** The Constant FLOAT. */
	public static final String FLOAT = "float";

	/** The Constant DECIMAL. */
	public static final String DECIMAL = "decimal";

	/**The Constant BIGDECIMAL. */
	public static final String BIGDECIMAL = "bigdecimal";

	/**The Constant BIGINT. */
	public static final String BIGINT = "bigint";

	/** The Constant STRING. */
	public static final String STRING = "string";
	
	/** The Constant IN_WITHSPACE. */
	public static final String IN_WITHSPACE= "IN (";
	
	/** The Constant IN_WITHOUTSPACE. */
	public static final String IN_WITHOUTSPACE = "IN(";
	
	/** The Constant OBJECT. */
	public static final String OBJECT = "object";

	/** The Constant DATE. */
	public static final String DATE = "date";
	
	/** The Constant DATE. */
	public static final String NVARCHAR = "nvarchar";

	/** The Constant CHAR. */
	public static final String CHAR = "char";

	/** The Constant TIME. */
	public static final String TIME = "time";

	/** The Constant LONG. */
	public static final String LONG = "long";
	
	/** The Constant JSON. */
	public static final String JSON = "JSON";
	
	/** The Constant BLOB. */
	public static final String BLOB = "BLOB";
	
	/** The Constant CLOB. */
	public static final String CLOB = "CLOB";
	
	/** The Constant IN. */
	public static final String IN = "IN";
	
	/** The Constant IN_SMALL. */
	public static final String IN_SMALL = "in";
	
	/** The Constant BINARY_DOUBLE. */
	public static final String BINARY_DOUBLE = "BINARY_DOUBLE";

	/** The Constant BOOLEAN. */
	public static final String BOOLEAN = "boolean";

	/** The Constant DUPLICATE_PRIMARY_KEY. */
	public static final String DUPLICATE_PRIMARY_KEY = "1062";

	/** The Constant DUPLICATE_PRIMARY_KEY_MESSAGE. */
	public static final String DUPLICATE_PRIMARY_KEY_MESSAGE = "Failed creating records Duplicate primary keys {0}";

	/** The Constant QUERY_INITIAL. */
	public static final String QUERY_INITIAL = "Insert into ";

	/** The Constant QUERY_VALUES. */
	public static final String QUERY_VALUES = ")values(";

	/** The Constant USER_INPUT_ERROR. */
	public static final String USER_INPUT_ERROR = "Please Check the input Requests!!";
	
	/** The Constant LAST_INSERT_ID. */
	public static final String LAST_INSERT_ID = "SELECT LAST_INSERT_ID();";

	/** The Constant SUCCESS_RESPONSE_CODE. */
	public static final String SUCCESS_RESPONSE_CODE = "200";

	/** The Constant SUCCESS_RESPONSE_MESSAGE. */
	public static final String SUCCESS_RESPONSE_MESSAGE = "Ok";

	/** The Constant QUERY. */
	public static final String QUERY = "query";

	/** The Constant SELECT_INITIAL. */
	public static final String SELECT_INITIAL = "Select * from ";

	/** The Constant JSON_DRAFT4_DEFINITION. */
	public static final String JSON_DRAFT4_DEFINITION = "\n\"$schema\": \"http://json-schema.org/draft-07/schema#\",";

	/** The Constant INSERT_TYPE. */
	public static final String INSERT_TYPE = "InsertType";

	/** The Constant DYNAMIC_INSERT. */
	public static final String DYNAMIC_INSERT = "Dynamic Insert";

	/** The Constant STANDARD_INSERT. */
	public static final String STANDARD_INSERT = "Standard Insert";

	/** The Constant SCHEMA_BUILDER_EXCEPTION. */
	public static final String SCHEMA_BUILDER_EXCEPTION = "Exception while generating request schema for DatabaseConnector{0}";

	/** The Constant CLASSNAME. */
	public static final String CLASSNAME = "className";

	/** The Constant USERNAME. */
	public static final String USERNAME = "username";

	/** The Constant PASS. */
	public static final String PASS = "password";

	/** The Constant DATABASENAME. */
	public static final String DATABASENAME = "databaseName";

	/** The Constant URL. */
	public static final String URL = "url";

	/** The Constant CLASSPATH. */
	public static final String CLASSPATH = "classpath";

	/** The Constant TABLE. */
	public static final String TABLE = "TABLE";

	/** The Constant TABLE_NAME. */
	public static final String TABLE_NAME = "TABLE_NAME";

	/** The Constant BATCH_COUNT. */
	public static final String BATCH_COUNT = "batchCount";

	/** The Constant REMAINING_BATCH_RECORDS. */
	public static final String REMAINING_BATCH_RECORDS = " Total Number of remaining records in the batch: ";

	/** The Constant COMMIT_OPTION. */
	public static final String COMMIT_OPTION = "CommitOption";

	/** The Constant COMMIT_BY_ROWS. */
	public static final String COMMIT_BY_ROWS = "Commit By Rows";

	/** The Constant COMMIT_BY_PROFILE. */
	public static final String COMMIT_BY_PROFILE = "Commit By Profile";

	/** The Constant BATCH_NUM. */
	public static final String BATCH_NUM = " Batch Number: ";

	/** The Constant BATCH_RECORDS. */
	public static final String BATCH_RECORDS = " Total Number of records in the batch: ";

	/** The Constant GET_TYPE. */
	public static final String GET_TYPE = "GetType";

	/** The Constant DELETE_TYPE. */
	public static final String DELETE_TYPE = "DeleteType";

	/** The Constant INSERTION_TYPE. */
	public static final String INSERTION_TYPE = "InsertionType";

	/** The Constant DYNAMIC_GET. */
	public static final String DYNAMIC_GET = "Dynamic Get";

	/** The Constant STANDARD_GET. */
	public static final String STANDARD_GET = "Standard Get";

	/** The Constant MAX_ROWS. */
	public static final String MAX_ROWS = "maxRows";

	/** The Constant LINK_ELEMENT. */
	public static final String LINK_ELEMENT = "linkElement";

	/** The Constant GROUP_BY. */
	public static final String GROUP_BY = " group by ";

	/** The Constant WHERE. */
	public static final String WHERE = " where ";

	/** The Constant AND. */
	public static final String AND = " and";

	/** The Constant STOREDPROCEDUREWRITE. */
	public static final String STOREDPROCEDUREWRITE = "STOREDPROCEDUREWRITE";

	/** The Constant PROCEDURE_NAME. */
	public static final String PROCEDURE_NAME = "PROCEDURE_NAME";

	/** The Constant GET. */
	public static final String GET = "GET";

	/** The Constant SUCCESS_EXECUTION. */
	public static final String SUCCESS_EXECUTION = "Stored Procedure Executed Successfully";

	/** The Constant TYPE. */
	public static final String TYPE = "Type";

	/** The Constant DYNAMIC_UPDATE. */
	public static final String DYNAMIC_UPDATE = "Dynamic Update";

	/** The Constant STANDARD_UPDATE. */
	public static final String STANDARD_UPDATE = "Standard Update";

	/** The Constant QUERY_STATUS. */
	public static final String QUERY_STATUS = "Success";

	/** The Constant BATCHEXECUTION. */
	public static final String BATCHEXECUTION = "Batch excecuted Successfully";

	/** The Constant RECORD_ADDED. */
	public static final String RECORD_ADDED = "Record added to batch successfully";

	/** The Constant REMAINING_BATCH. */
	public static final String REMAINING_BATCH = "Remaining batch executed successfully";

	/** The Constant CREATE. */
	public static final String CREATE = "CREATE";

	/** The Constant DYNAMIC_DELETE. */
	public static final String DYNAMIC_DELETE = "Dynamic Delete";

	/** The Constant STANDARD_DELETE. */
	public static final String STANDARD_DELETE = "Standard Delete";

	/** The Constant DELETE. */
	public static final String DELETE = "DELETE";

	/** The Constant DELETE_QUERY. */
	public static final String DELETE_QUERY = "DELETE FROM ";

	/** The Constant COLUMN_NAME. */
	public static final String COLUMN_NAME = "COLUMN_NAME";

	/** The Constant MSSQLSERVER. */
	public static final String MSSQLSERVER = "Microsoft SQL Server";

	/** The Constant STATUS. */
	public static final String STATUS = "Status";

	/** The Constant BATCH_NUM_STATUS. */
	public static final String BATCH_NUM_STATUS = "Batch Number";

	/** The Constant REDORDS. */
	public static final String RECORDS = "No of records in batch";

	/** The Constant QUERY_RESPONSE. */
	public static final String QUERY_RESPONSE = "Query";

	/** The Constant ROWS_EFFECTED. */
	public static final String ROWS_EFFECTED = "Rows effected";

	/** The Constant DATA_TYPE. */
	public static final String DATA_TYPE = "DATA_TYPE";

	/** The Constant TYPE_NAME. */
	public static final String TYPE_NAME = "TYPE_NAME";
	
	/** The Constant UNKNOWN_DATATYPE. */
	public static final String UNKNOWN_DATATYPE = "1111";

	/** The Constant EXEC. */
	public static final String EXEC = "EXEC ";

	/** The Constant FAILED_BATCH_NUM. */
	public static final String FAILED_BATCH_NUM = "Failed Batch number: ";
	
	/** The Constant SQL_QUERY. */
	public static final String SQL_QUERY = "SQLQuery";
	
	/** The Constant NON_UNIQUE. */
	public static final String NON_UNIQUE = "NON_UNIQUE";
	
	/** The Constant FAILED_BATCH_RECORDS. */
	public static final String FAILED_BATCH_RECORDS="No of records in Failed batch: ";
	
	/** The Constant INPUT_ERROR. */
	public static final String INPUT_ERROR = "Please check the input data!!";
	
	/** The Constant COMMA. */
	public static final String COMMA = ",";
	
	/** The Constant SINGLE_QUOTE. */
	public static final String SINGLE_QUOTE = "'";
	
	/** The Constant PARAM. */
	public static final String PARAM = "?,";
	
	/** The Constant BACKSLASH. */
	public static final String BACKSLASH = "\"";
	
	/** The Constant ORACLE. */
	public static final String ORACLE = "Oracle";
	
	/** The Constant POSTGRESQL. */
	public static final String POSTGRESQL = "PostgreSQL";
	
	/** The Constant MSSQL. */
	public static final String MSSQL = "Microsoft SQL Server";
	
	/** The Constant MYSQL. */
	public static final String MYSQL = "MySQL";
	
	/** The Constant DOT. */
	public static final String DOT = ".";
	
	/** The Constant OPEN_IN. */
	public static final String OPEN_IN ="IN\\(";
	
	/** The Constant OPEN_EXEC. */
	public static final String OPEN_EXEC = "EXEC(";
	
	/** The Constant IDENTITY_QUERY. */
	public static final String IDENTITY_QUERY = "SELECT COLUMN_NAME FROM ALL_TAB_COLUMNS WHERE IDENTITY_COLUMN = 'YES' AND TABLE_NAME='";
	
	

}
