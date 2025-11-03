// © Copyright IBM Corp. 2023, 2024
package boomi.connector.databaseconnector;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.boomi.connector.api.Connector;
import com.boomi.connector.api.OperationType;
import com.boomi.connector.databaseconnector.DatabaseConnectorConnector;
import com.boomi.connector.testutil.ConnectorTestContext;

public class BrowseTestBatch extends ConnectorTestContext {
	
	private static final Logger logger = Logger.getLogger(BrowseTestBatch.class.getName());
	
	BrowseTestBatch()   {

		Properties props = new Properties();
		try(FileInputStream file =  new FileInputStream("src/test/resources/db.properties");) {
			props.load(file);
		} catch (FileNotFoundException e) {
			logger.log(Level.INFO, "NoFile", e);
		} catch (IOException e) {
			logger.log(Level.INFO, "Input not proper", e);
		} 
		Map<String, String> customProperty = new HashMap<String, String>();
		addConnectionProperty("className", props.getProperty("cn"));
		addConnectionProperty("username", props.getProperty("u"));
		addConnectionProperty("password", props.getProperty("p"));
		addConnectionProperty("url", props.getProperty("ip"));
		addConnectionProperty("classpath", props.getProperty("cp"));
		addConnectionProperty("CustomProperties", customProperty);
		setOperationType(OperationType.UPDATE);
		setObjectTypeId("datatype");
		addOperationProperty("InsertType", "Dynamic Insert");	
		addOperationProperty("CommitOption", "Commit By Rows");
		long val = 2;
		addOperationProperty("batchCount", val);
		
	}

	@Override
	protected Class<? extends Connector> getConnectorClass() {
		return DatabaseConnectorConnector.class;
	}

}
