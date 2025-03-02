package blog.peterobrien.jdbc.autorest;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.PathItem.HttpMethod;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.servers.Server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.Before;
import org.junit.Test;

import java.util.Map;

import oracle.dbtools.plugin.api.logging.Log;
import oracle.dbtools.plugin.api.routes.PathTemplateMatch;
import oracle.dbtools.plugin.api.routes.PathTemplates;

/**
 * Unit tests for the AutoRESTJDBCServletTest using Mockito.
 * The setUp() method programmatically defines an OpenAPI document for:
 * <pre>
 * ---
 * openapi: "3.0.1"
 * servers:
 * - url: "https://localhost/ords/mydb/autorest"
 * paths:
 *   /resources/:
 *     get:
 *     post:
 *     extensions:
 *       x-autorest:
 *         type: "table"
 *         identifier: "employees"
 *   /resources/{id}:
 *     get:
 *     put:
 *     delete:
 *     extensions:
 *       x-autorest:
 *         type: "table"
 *         identifier: "employees"
 *     parameters:
 *     - name: "id"
 *       in: "path"
 *       required: true
 *       schema:
 *         type: "integer"
 *         types:
 *         - "integer"
 * </pre>
 * Which is all that is required to have AutoREST functionality working
 * @author peobrie
 *
 */
public class AutoRESTJDBCServletTest {

    private AutoRESTJDBCServlet myServlet;
    private AutoRESTJDBC autoRESTJDBC;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private Log log;
    private OpenAPICache openAPICache;
    private PathTemplates pathTemplates;
    private OpenAPI openAPI;
    private StringWriter stringWriter;

    @Before
    public void setUp() throws IOException {
    	autoRESTJDBC = mock(AutoRESTJDBC.class);
    	log = mock(Log.class);
    	openAPICache = mock(OpenAPICache.class);
    	pathTemplates = mock(PathTemplates.class);

    	final Server server = new Server();
    	openAPI = new OpenAPI();
    	openAPI.addServersItem(server);
    	// Define collections GET and POST
    	final PathItem collectionsPathItem = new PathItem();
    	collectionsPathItem.addExtension("x-autorest", Map.of("identifier", "employees", "type", "table"));

    	// Define collections GET
    	final Operation collectionsGetOperation = new Operation();
    	collectionsPathItem.operation(HttpMethod.GET, collectionsGetOperation);
    	// Define collections POST
    	final Operation collectionsPostOperation = new Operation();
    	collectionsPathItem.operation(HttpMethod.POST, collectionsPostOperation);
    	openAPI.path("/resources/", collectionsPathItem);

    	// Define resource item GET, DELETE and PUT
    	final PathItem resourcePathItem = new PathItem();
    	resourcePathItem.addExtension("x-autorest", Map.of("identifier", "employees", "type", "table"));
    	Parameter idParam = new Parameter();
    	idParam.name("id");
    	idParam.in("path");
    	idParam.schema( new IntegerSchema());
    	resourcePathItem.addParametersItem(idParam);
    	// Define resource item GET
    	final Operation resourceGetOperation = new Operation();
    	// Define resource item DELETE
    	final Operation resourceDeleteOperation = new Operation();
    	// Define resource item PUT
    	final Operation resourcePutOperation = new Operation();

    	resourcePathItem.operation(HttpMethod.GET, resourceGetOperation);
    	resourcePathItem.operation(HttpMethod.DELETE, resourceDeleteOperation);
    	resourcePathItem.operation(HttpMethod.PUT, resourcePutOperation);
    	openAPI.path("/resources/{id}", resourcePathItem);

        when(openAPICache.getOpenAPIDocument(AutoRESTSettings._AUTOREST_API_DOC.defaultValue().toString())).thenReturn(openAPI);
        
        myServlet = new AutoRESTJDBCServlet(autoRESTJDBC, log, "openapi.yaml", openAPICache, pathTemplates);
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        stringWriter = new StringWriter();

        // Set up the PrintWriter to use StringWriter
        PrintWriter writer = new PrintWriter(stringWriter);
        when(response.getWriter()).thenReturn(writer);        
        
        
    }

    @Test
    public void testDoGetAPIDocument() throws ServletException, IOException {
    	final PathTemplateMatch pathTemplateMatch = mock(PathTemplateMatch.class);
        when(request.getRequestURL()).thenReturn(new StringBuffer("https://localhost/ords/mydb/autorest/openapi.yaml"));
        when(request.getMethod()).thenReturn("GET");
        when(pathTemplates.matchedTemplate(request)).thenReturn(pathTemplateMatch);
        when(pathTemplateMatch.name()).thenReturn("APIDocument");
        
        myServlet.doGet(request, response);

        verify(response).getWriter();
        verify(response).setContentType("application/yaml");
        verify(response).setCharacterEncoding("UTF-8");
        assertTrue("Response not an OpenAPI document", this.stringWriter.toString().contains("openapi"));
    }

    @Test
    public void testDoGetCollection() throws ServletException, IOException {
    	final PathTemplateMatch pathTemplateMatch = mock(PathTemplateMatch.class);
        when(request.getPathInfo()).thenReturn("/autorest/resources/");
        when(request.getMethod()).thenReturn("GET");
        when(request.toString()).thenReturn("https://localhost/ords/mydb/autorest/resources/");
        when(pathTemplates.matchedTemplate(request)).thenReturn(pathTemplateMatch);
        when(pathTemplateMatch.name()).thenReturn("Service");

        ResultSet rs = mock(ResultSet.class);
        when(autoRESTJDBC.service(any(ServiceDefinition.class), eq(SupportedAction.GET), any(Map.class))).thenReturn(rs);

        myServlet.doGet(request, response);

        verify(response).getWriter();
    }

    @Test
    public void testDoGetItem() throws ServletException, IOException, SQLException {
    	final PathTemplateMatch pathTemplateMatch = mock(PathTemplateMatch.class);
        when(request.getPathInfo()).thenReturn("/autorest/resources/101");
        when(request.getMethod()).thenReturn("GET");
        when(pathTemplates.matchedTemplate(request)).thenReturn(pathTemplateMatch);
        when(pathTemplateMatch.name()).thenReturn("Service");

        // Mock the ResultSet so that we have data to return in the HttpServletResponse
        ResultSet rs = mock(ResultSet.class);
        when(autoRESTJDBC.service(any(ServiceDefinition.class), eq(SupportedAction.GET), any(Map.class))).thenReturn(rs);
        
        when(rs.next()).thenReturn(true);
        
        // Mock a row with id=101
        ResultSetMetaData md = mock(ResultSetMetaData.class);
        when(rs.getMetaData()).thenReturn(md);
        when(rs.getObject(1)).thenReturn(101);
        
        when(md.getColumnCount()).thenReturn(1);
        when(md.getColumnLabel(1)).thenReturn("id");

        myServlet.doGet(request, response);

        verify(response).getWriter();
        verify(response).setContentType("application/json");
        verify(response).setCharacterEncoding("UTF-8");
        assertEquals("Response not for the resource item", "{\"id\":101}", this.stringWriter.toString());
        
    }

    @Test
    public void testDoPost() throws ServletException, IOException {
        when(request.getPathInfo()).thenReturn("/autorest/resources/");
        when(request.getMethod()).thenReturn("POST");
        when(request.getContentType()).thenReturn("application/json");
        when(request.getReader()).thenReturn(new BufferedReader(new StringReader("{\"id\":\"value\"}")));

        ResultSet rs = mock(ResultSet.class);
        when(autoRESTJDBC.service(any(ServiceDefinition.class), eq(SupportedAction.POST), any(Map.class))).thenReturn(rs);

        myServlet.doPost(request, response);

        verify(response).setStatus(HttpServletResponse.SC_CREATED);
        verify(response).getWriter();
        verify(response).setContentType("application/json");
        verify(response).setCharacterEncoding("UTF-8");
    }

    @Test
    public void testDoPut() throws ServletException, IOException {
        when(request.getPathInfo()).thenReturn("/autorest/resources/101");
        when(request.getMethod()).thenReturn("PUT");
        when(request.getContentType()).thenReturn("application/json");
        when(request.getReader()).thenReturn(new BufferedReader(new StringReader("{\"key\":\"updatedValue\"}")));

        ResultSet rs = mock(ResultSet.class);
        when(autoRESTJDBC.service(any(ServiceDefinition.class), eq(SupportedAction.PUT), any(Map.class))).thenReturn(rs);
        myServlet.doPut(request, response);

        verify(response).setStatus(HttpServletResponse.SC_OK);
        verify(response).getWriter();
        verify(response).setContentType("application/json");
        verify(response).setCharacterEncoding("UTF-8");
    }

    @Test
    public void testDoDelete() throws ServletException, IOException {
        when(request.getPathInfo()).thenReturn("/autorest/resources/101");
        when(request.getMethod()).thenReturn("DELETE");

        // The executeUpdate on a Statement object will return a number indicating how many rows affected
        when(autoRESTJDBC.service(any(ServiceDefinition.class), eq(SupportedAction.DELETE), any(Map.class))).thenReturn(Integer.valueOf(1));
        myServlet.doDelete(request, response);

        verify(response).setStatus(HttpServletResponse.SC_NO_CONTENT);
    }
}
