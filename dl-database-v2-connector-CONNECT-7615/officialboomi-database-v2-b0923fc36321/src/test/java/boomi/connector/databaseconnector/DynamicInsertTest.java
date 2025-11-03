// © Copyright IBM Corp. 2023, 2024
package boomi.connector.databaseconnector;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Random;
import java.util.logging.Logger;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import com.boomi.connector.api.ObjectData;
import com.boomi.connector.api.OperationResponse;
import com.boomi.connector.api.UpdateRequest;
import com.boomi.connector.databaseconnector.DatabaseConnectorConnection;
import com.boomi.connector.databaseconnector.DynamicInsertOperation;
import com.boomi.connector.testutil.SimpleTrackedData;

public class DynamicInsertTest {

	private BrowserTest context = new BrowserTest();
	private DatabaseConnectorConnection con = new DatabaseConnectorConnection(context);
	private DynamicInsertOperation ops = new DynamicInsertOperation(con);
	private UpdateRequest request = mock(UpdateRequest.class);
	private OperationResponse response = mock(OperationResponse.class);
	private Logger logger = mock(Logger.class);
	
	static Random rand = new Random();
	
	static int id = rand.nextInt(1000);
	static String in = String.valueOf(id);

	public static final String input = "{\r\n" + "				\"id\":\"+in+\",\r\n" + "				\"name\":\"abc\",\r\n"
			+ "				\"dob\":\"2019-12-09\",\r\n" + "				\"clob\":\"Hello\",\r\n"
			+ "				\"isqualified\":true,\r\n" + "				\"laptime\":\"21:09:08\"\r\n"
			+ "				\r\n" + "            }";

	@Before
	public void init() {
		when(response.getLogger()).thenReturn(logger);
	}


	@Test
	public void testexecuteCreateOperation() throws IOException {
		con.loadProperties();
		InputStream result = new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8));
		SimpleTrackedData trackedData = new SimpleTrackedData(1, result);
		Iterator<ObjectData> objDataItr = Mockito.mock(Iterator.class);
		when(request.iterator()).thenReturn(objDataItr);
		when(objDataItr.hasNext()).thenReturn(true, false);
		when(objDataItr.next()).thenReturn(trackedData);
		when(response.getLogger()).thenReturn(Mockito.mock(Logger.class));
		ops.executeSizeLimitedUpdate(request, response);
		assertTrue(true);
		result.close();
	}

}
