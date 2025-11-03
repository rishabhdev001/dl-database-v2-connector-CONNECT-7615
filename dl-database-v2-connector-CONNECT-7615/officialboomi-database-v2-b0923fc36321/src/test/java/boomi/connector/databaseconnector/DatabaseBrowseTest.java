// © Copyright IBM Corp. 2023, 2024
package boomi.connector.databaseconnector;

import java.util.Arrays;

import org.junit.Test;

import com.boomi.connector.api.ObjectDefinitionRole;
import com.boomi.connector.api.ObjectDefinitions;
import com.boomi.connector.api.ObjectTypes;
import com.boomi.connector.databaseconnector.DatabaseConnectorBrowser;
import com.boomi.connector.databaseconnector.DatabaseConnectorConnection;

public class DatabaseBrowseTest {
	

	@Test
	public void testgetObjectDefinitions_CREATE() {
		BrowserTest context = new BrowserTest();
		DatabaseConnectorConnection conn = new DatabaseConnectorConnection(context);
		DatabaseConnectorBrowser browser = new DatabaseConnectorBrowser(conn);
		conn.loadProperties();
		ObjectTypes objTypes= browser.getObjectTypes();
		ObjectDefinitions objDefinitions = browser.getObjectDefinitions("datatype",   Arrays.asList(ObjectDefinitionRole.INPUT, ObjectDefinitionRole.OUTPUT));	
	}

}
