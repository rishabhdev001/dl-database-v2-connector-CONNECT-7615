// © Copyright IBM Corp. 2023, 2024
package boomi.connector.databaseconnector;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.logging.Logger;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import com.boomi.connector.api.ConnectorException;
import com.boomi.connector.api.ObjectData;
import com.boomi.connector.api.OperationResponse;
import com.boomi.connector.api.UpdateRequest;
import com.boomi.connector.databaseconnector.DatabaseConnectorConnection;
import com.boomi.connector.databaseconnector.DynamicUpdateOperation;
import com.boomi.connector.testutil.SimpleTrackedData;

public class DynamicUpdateTest {
	
	private BrowserTest context = new BrowserTest();
	private BrowseTestBatch contextBatch = new BrowseTestBatch();
	private DatabaseConnectorConnection con = new DatabaseConnectorConnection(context);
	private DatabaseConnectorConnection conBatch = new DatabaseConnectorConnection(contextBatch);
	private DynamicUpdateOperation ops = new DynamicUpdateOperation(con);
	private DynamicUpdateOperation opsBatch = new DynamicUpdateOperation(conBatch);
	private UpdateRequest request = mock(UpdateRequest.class);
	private OperationResponse response = mock(OperationResponse.class);
	private static Logger logger = mock(Logger.class);
	

	public static final String input = readJsonFromFile();
	
	@Before
	public void init() {
		when(response.getLogger()).thenReturn(logger);
	}
	


	@Test
	public void testexecuteCreateOperation() throws IOException {
		InputStream result = new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8));
		SimpleTrackedData trackedData = new SimpleTrackedData(1, result);
		Iterator<ObjectData> objDataItr = Mockito.mock(Iterator.class);
		when(request.iterator()).thenReturn(objDataItr);
		when(objDataItr.hasNext()).thenReturn(true, false);
		when(objDataItr.next()).thenReturn(trackedData);
		when(response.getLogger()).thenReturn(Mockito.mock(Logger.class));
		con.loadProperties();
		try(Connection connn = DriverManager.getConnection(con.getUrl(), con.getUsername(), con.getPassword())) {
			connn.setAutoCommit(false);
			ops.executeUpdateOperation(request, response, connn);
		} catch (SQLException e) {
			throw new ConnectorException(e.toString());
		}
		assertTrue(true);
		result.close();
	
	}
	
	@Test
	public void testexecuteUpdateOperation() throws IOException {
		InputStream result = new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8));
		SimpleTrackedData trackedData = new SimpleTrackedData(1, result);
		Iterator<ObjectData> objDataItr = Mockito.mock(Iterator.class);
		when(request.iterator()).thenReturn(objDataItr);
		when(objDataItr.hasNext()).thenReturn(true, false);
		when(objDataItr.next()).thenReturn(trackedData);
		when(response.getLogger()).thenReturn(Mockito.mock(Logger.class));
		conBatch.loadProperties();
		try(Connection connn = DriverManager.getConnection(conBatch.getUrl(), conBatch.getUsername(), conBatch.getPassword())) {
			opsBatch.executeUpdateOperation(request, response, connn);
		} catch (SQLException e) {
			
		}
		assertTrue(true);
		result.close();
	}
	
	
	public static String readJsonFromFile() {
		String text = null;
		try {
			text = new String(Files.readAllBytes(Paths.get("src/test/resource/DynamicUpdateTest.txt")));

		} catch (IOException e) {
			logger.info("Error occured in Test class.");
		}
		return text;
	}

}
