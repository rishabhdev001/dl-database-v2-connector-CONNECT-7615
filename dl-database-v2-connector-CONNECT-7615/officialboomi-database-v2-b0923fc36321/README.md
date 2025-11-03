[//]: # (© Copyright IBM Corp. 2023, 2024)
# DATABASE CONNECTOR V2 #
Use the Database connector to select, insert, update, upsert and delete records in any JDBC supported database using Database drivers. 

Database Connector will user the java JDBC to communicate to the user defined database and to manipulate the records. 

To configure the connector to interact with Database, please set up two components:

* Database connection — The connection represents a specific Database.

* Database operation — The operation represents an action against a specific schema in database. You will create one or more operations, one for each type of interaction required by your integration scenario.

The connector supports any SQL Database which will support native SQL queries compatible with JDBC 4.2

The connector supports the below datatypes

* Varchar
* Clob
* Date
* Time
* Boolean
* Integer
* JSON

## Prerequisites ##
To use the connector and implement a connection to Database from Boomi Integration, do the following.

* Install and login to Database with necessary credentials.
* Setting up databse as below.

	* Create neccessary schema with table
	* Create neccessary columns for the table.
	
* Identify the database driver and the class name of the driver.
* Upload the database driver jar to custom library and point the custom library to the database connector.	
* Class name of the driver is used to invoke the driver JAR file imported into Dell Boomi.

## Building this connector project ##
To build this project, the following dependencies must be met

 * JDK 1.8 or above
 * Apache Maven 3.5.0
 
### Compiling ###
Once your JAVA_HOME points to your installation of JDK 1.8 (or above) and JAVA_HOME/bin and Apache maven are in your PATH, issue the following command to build the jar file:
```
  mvn install
```
or if you don't want to run the unit tests, then run 
```
  mvn install -DskipTests
``` 
### Running the unit tests ###
To run the unit tests, please use below command 
``` 
  mvn install 
```
It will run the unit tests and build the jar file. Both the CAR file and the Test Reports will be available inside target folder.


# Supported operation #

Connector support 6 Operations. Apart from stored procedure and Upsert all the Operations can be classified into 2 types i.e, Dynamic Operation and Standard Operation. 

The Dynamic Operation will make use of Statement Class to build the Dynamic Queries Whereas Standard Operation will make use of PreparedStatement.

Create a separate operation component for each action/object combination that your integration requires.

The Database Connector operations support the following actions:

* Insert: Inserts records to database.
* Get: Gets the records from database.
* Update: Updates the existing records.
* Delete: Deletes the existing records.
* Stored Procedure: For calling procedures.
* Upsert: Update the existing records else Inserts the new record.

## OPERATION DETAILS ##

## INSERT ##
Insert is an outbound action where the connector will form the insert query based on the incoming request and executes the query to the database table selected as object in import wizard. 
For Standard Insert you need to set the parameter for query in operation request profile.
In Dynamic insert the data will be sent to the table which was choosen in the object type.

> _**Note:**_ The query parameter is set only in request profile, the query will take the parameters from the request profile and map the values to the query.

## GET ##
Get is an inbound action which retrieves the records from the database and show the resultset in JSON response. In Dynamic get the data will be retrieved from the table which was choosen in the object type based on the input parameters.

## UPDATE ##
Update operation is used to update the existing records in the database. The fields which we want to update is provided in the incoming request and table name will be taken from the object type.

## DELETE ##

The DELETE operation will be supported by the connector, to delete records from the database based on the inputs and the SQL statements provided by the user. The connector will operate with MULTI-Model like behavior. The DELETE operation will take JSON Document(s) as input and deleted record as a JSON response of the operation

## STORED PROCEDURE ##

The STORED PROCEDURE operation is used to execute the procedure in the database. Connector will use Callable Statements to call the procedure present in the database.

## UPSERT ##

UPSERT operation allows Inputs to atomically either insert a row, or on the basis of the row already existing, UPDATE that existing row instead, while safely giving little to no further thought to concurrency. The UPSERT Operation determine the conflicting keys first and then it will INSERT or UPDATE Inputs Automatically in the database. 

## Additional resources ##
https://help.boomi.com/bundle/connectors/page/r-atm-Database_operation_a66d6242-d23f-461e-af4a-883efabc08d4.html

https://en.wikipedia.org/wiki/Java_Database_Connectivity
