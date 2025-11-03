// © Copyright IBM Corp. 2023, 2024
package boomi.connector.databaseconnector;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.boomi.connector.api.Connector;
import com.boomi.connector.databaseconnector.DatabaseConnectorConnector;
import com.boomi.connector.testutil.ConnectorTestContext;

public class BrowseTestProcedure extends ConnectorTestContext {
	
	private static final Logger logger = Logger.getLogger(BrowseTestProcedure.class.getName());
	
	public BrowseTestProcedure() {
		
		Properties props = new Properties();
		try(FileInputStream file =  new FileInputStream("src/test/resources/db.properties");) {
			props.load(file);
		} catch (FileNotFoundException e) {
			logger.log(Level.INFO, "NoFile", e);
		} catch (IOException e) {
			logger.log(Level.INFO, "Input not proper", e);
		}
		addConnectionProperty("className", props.getProperty("cn"));
		addConnectionProperty("username", props.getProperty("u"));
		addConnectionProperty("password", props.getProperty("p"));
		addConnectionProperty("url", props.getProperty("ip"));
		addConnectionProperty("classpath", props.getProperty("cp"));
		setOperationCustomType("STOREDPROCEDUREWRITE");
		setObjectTypeId("procedure_write");
		addOperationProperty("batchCount", null);
		
	}
	
	@Override
	protected Class<? extends Connector> getConnectorClass() {
		return DatabaseConnectorConnector.class;
	}

}
