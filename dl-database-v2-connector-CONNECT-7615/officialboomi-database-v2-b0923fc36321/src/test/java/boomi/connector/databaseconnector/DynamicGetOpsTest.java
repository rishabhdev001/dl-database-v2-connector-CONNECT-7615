// © Copyright IBM Corp. 2023, 2024
package boomi.connector.databaseconnector;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.logging.Logger;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import com.boomi.connector.api.ObjectData;
import com.boomi.connector.api.OperationResponse;
import com.boomi.connector.api.UpdateRequest;
import com.boomi.connector.databaseconnector.DatabaseConnectorConnection;
import com.boomi.connector.databaseconnector.get.DynamicGetOperation;

public class DynamicGetOpsTest {

	private final BrowserTest context = new BrowserTest();
	private final DatabaseConnectorConnection con = new DatabaseConnectorConnection(context);
	private final DynamicGetOperation ops = new DynamicGetOperation(con);
	private final UpdateRequest request = mock(UpdateRequest.class);
	private final OperationResponse response = mock(OperationResponse.class);
	private final Logger logger = mock(Logger.class);

	@Before
	public void init() {
		when(response.getLogger()).thenReturn(logger);
		context.addConnectionProperty("classpath", "");
		context.addConnectionProperty("className", "org.h2.Driver");
		context.addConnectionProperty("url", "jdbc:h2:mem:testdb");
	}

	@Test
	public void testexecuteCreateOperation() throws IOException {
		con.loadProperties();
		ObjectData trackedData = mock(ObjectData.class);
		Map<String, String> dynamicProperties = new HashMap<>();
		dynamicProperties.put("sqlQuery", "select 1");
		when(trackedData.getDynamicProperties()).thenReturn(dynamicProperties);
		when(trackedData.getLogger()).thenReturn(Mockito.mock(Logger.class));

		Iterator<ObjectData> objDataItr = Mockito.mock(Iterator.class);
		when(request.iterator()).thenReturn(objDataItr);
		when(objDataItr.hasNext()).thenReturn(true, false);
		when(objDataItr.next()).thenReturn(trackedData);
		ops.executeSizeLimitedUpdate(request, response);
		assertTrue(true);
	}

}
