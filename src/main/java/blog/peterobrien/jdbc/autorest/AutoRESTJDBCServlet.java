package blog.peterobrien.jdbc.autorest;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.parameters.Parameter;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import oracle.dbtools.plugin.api.di.annotations.Provides;
import oracle.dbtools.plugin.api.http.annotations.Dispatches;
import oracle.dbtools.plugin.api.http.annotations.PathTemplate;
import oracle.dbtools.plugin.api.logging.Log;
import oracle.dbtools.plugin.api.routes.PathTemplateMatch;
import oracle.dbtools.plugin.api.routes.PathTemplates;
import oracle.dbtools.plugin.api.servlet.HttpServletBase;

/**
 * Servlet which dynamically provides RESTful DELETE, GET, POST and PUT operations for TABLE and VIEW resources. Using OpenAPI V3 document to describe the interface and x-autorest extension to describe the associated database table/view.
 * @author peobrie
 *
 */
@Provides
@Dispatches({
		@PathTemplate(value = AutoRESTJDBCServlet.PATH_PREFIX + "/openapi.yaml", methods = {
				"GET" }, name = "APIDocument"),
		@PathTemplate(value = AutoRESTJDBCServlet.PATH_PREFIX + "/*", methods = { "GET", "DELETE", "POST",
				"PUT" }, name = "Service") })
public class AutoRESTJDBCServlet extends HttpServletBase {
	@Inject
	AutoRESTJDBCServlet(final Connection conn, final Log log,
			final @Named(AutoRESTSettings.AUTOREST_API_DOC) String apidoc, final OpenAPICache openAPICache,
			final PathTemplates pathTemplates) {
		this( new AutoRESTJDBC(conn, log), log, apidoc, openAPICache, pathTemplates);
	}

	AutoRESTJDBCServlet(final AutoRESTJDBC autoRESTJDBC, final Log log,
			final @Named(AutoRESTSettings.AUTOREST_API_DOC) String apidoc, final OpenAPICache openAPICache,
			final PathTemplates pathTemplates) {
		this.autoRESTDelegate = autoRESTJDBC;
		this.log = log;
		this.openAPIDocument = openAPICache.getOpenAPIDocument(apidoc);
		this.pathTemplates = pathTemplates;
        // Exclude null values in YAML output
        this.yamlMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
	}

	public void doDelete(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		final ServiceDefinition sd = serviceDefinition(request);
		if (sd == null) {
			response.setStatus(HttpServletResponse.SC_NOT_FOUND);
			return;
		}
		final Map<String, Object> paramMap = new HashMap<String, Object>();
		pathParameters(request, sd, paramMap);
		if (paramMap.isEmpty()) {
			response.sendError(HttpServletResponse.SC_NOT_FOUND);
			return;
		}
		Object result = this.autoRESTDelegate.service(sd, SupportedAction.DELETE, paramMap);
		if (!(result instanceof Integer)) {
			response.sendError(HttpServletResponse.SC_NOT_FOUND);
			return;
		}
		if (((Integer) result).intValue() != 1 ) {
			response.sendError(HttpServletResponse.SC_NOT_FOUND);
			return;
		}
		response.setStatus(HttpServletResponse.SC_NO_CONTENT);
	}
	
	public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		final PathTemplateMatch pathTemplate = this.pathTemplates.matchedTemplate(request);
		if (pathTemplate.name().equals("APIDocument")) {
			response.setContentType("application/yaml");
			response.setCharacterEncoding("UTF-8");

			try (var writer = response.getWriter()) {
				if (!this.openAPIDocument.getServers().isEmpty()) {
					final String requestURI = request.getRequestURL().toString();
					this.openAPIDocument.getServers().get(0).setUrl(requestURI.substring(0, requestURI.length() - 13));
				}
				this.yamlMapper.writeValue(writer, this.openAPIDocument);
			}
		} else {
			final ServiceDefinition sd = serviceDefinition(request);
			if (sd == null) {
				response.sendError(HttpServletResponse.SC_NOT_FOUND);
				return;
			}
			
			final Map<String, Object> paramMap = new HashMap<String, Object>();
			if (sd.queryType == SupportedQueryType.COLLECTION) {
				final String limitAsString = request.getParameter("limit");
				final String offsetAsString = request.getParameter("offset");
				paramMap.put("limit", (limitAsString == null) ? 25 : Integer.parseInt(limitAsString));
				paramMap.put("offset", (offsetAsString == null) ? 0 : Integer.parseInt(offsetAsString));
			} else if (sd.queryType == SupportedQueryType.ITEM) {
				pathParameters(request, sd, paramMap);
				if (paramMap.isEmpty()) {
					response.sendError(404);
					return;
				}
			}
			
			Object resultSet = this.autoRESTDelegate.service(sd, SupportedAction.GET, paramMap);
			if (resultSet == null) {
				response.sendError(HttpServletResponse.SC_NOT_FOUND);
				return;
			}

			// Convert ResultSet to JSON
			try {
				ObjectNode jsonNode = resultSetToJson((ResultSet) resultSet, sd, paramMap, request);
				if (jsonNode.isEmpty()) {
					response.sendError(HttpServletResponse.SC_NOT_FOUND);
					return;
				}
				response.setContentType("application/json");
				response.setCharacterEncoding("UTF-8");

				try (var writer = response.getWriter()) {
					this.jsonMapper.writeValue(writer, jsonNode);
				}
			} catch (SQLException e) {
				log.severe(e);
			}
		}
	}

	private void pathParameters(HttpServletRequest request, final ServiceDefinition sd,
			final Map<String, Object> paramMap) throws IOException {
		final Map<String, Parameter> pathParameters = new HashMap<String, Parameter>();
		for (Parameter param: this.openAPIDocument.getPaths().get(sd.path).getParameters()) {
			if (param.getIn().equals("path")) {
				pathParameters.put(param.getName(), param);
			}
		}
		String[] pathItemKeyParts = sd.path.split("/");
		String[] requestPathParts = request.getPathInfo().substring(PATH_PREFIX.length()).split("/");
		if (pathItemKeyParts.length != requestPathParts.length) {
			return;
		}
		for (int i=0; i < pathItemKeyParts.length; i++) {
			final String pathFragment = pathItemKeyParts[i];
			if (pathFragment.startsWith("{") && pathFragment.endsWith("}")) {
				final String parameterName = pathFragment.substring(1, (pathFragment.length() - 1));
				switch (pathParameters.get(parameterName).getSchema().getType()) {
				case "string": paramMap.put(parameterName, requestPathParts[i]); break; 
				case "integer": paramMap.put(parameterName, Integer.valueOf(requestPathParts[i])); break; 
				case "number": paramMap.put(parameterName, new BigDecimal(requestPathParts[i])); break; 
				}
				
				
			}
		}
	}

	public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        // Check if Content-Type is application/json
        if (!"application/json".equalsIgnoreCase(request.getContentType())) {
            response.setStatus(HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE);
            response.getWriter().write("{\"error\": \"Unsupported Media Type\"}");
            return;
        }

		final Map<String, Object> jsonBody = jsonMapper.readValue(request.getReader(), Map.class);
		final ServiceDefinition sd = serviceDefinition(request);
		if (sd == null) {
			response.sendError(404);
			return;
		}
		Object resultSet = this.autoRESTDelegate.service(sd, SupportedAction.POST, jsonBody);

		// Convert ResultSet to JSON
		try {
			ObjectNode jsonNode = resultSetToJson((ResultSet) resultSet, sd, jsonBody, request);
			response.setStatus(HttpServletResponse.SC_CREATED);
			response.setContentType("application/json");
			response.setCharacterEncoding("UTF-8");

			try (var writer = response.getWriter()) {
				this.jsonMapper.writeValue(writer, jsonNode);
			}
		} catch (SQLException e) {
			log.severe(e);
		}
	}
	
	public void doPut(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        // Check if Content-Type is application/json
        if (!"application/json".equalsIgnoreCase(request.getContentType())) {
            response.setStatus(HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE);
            response.getWriter().write("{\"error\": \"Unsupported Media Type\"}");
            return;
        }

		final Map<String, Object> jsonBody = jsonMapper.readValue(request.getReader(), Map.class);
		final ServiceDefinition sd = serviceDefinition(request);
		if (sd == null) {
			response.sendError(404);
			return;
		}
		final Map<String, Object> paramMap = new HashMap<String, Object>();
		pathParameters(request, sd, paramMap);
		if (paramMap.isEmpty()) {
			response.sendError(404);
			return;
		}
		Object resultSet = this.autoRESTDelegate.service(sd, SupportedAction.PUT, jsonBody);

		// Convert ResultSet to JSON
		try {
			ObjectNode jsonNode = resultSetToJson((ResultSet) resultSet, sd, jsonBody, request);
			response.setStatus(HttpServletResponse.SC_OK);
			response.setContentType("application/json");
			response.setCharacterEncoding("UTF-8");

			try (var writer = response.getWriter()) {
				this.jsonMapper.writeValue(writer, jsonNode);
			}
		} catch (SQLException e) {
			log.severe(e);
		}
	}
	
	private ServiceDefinition serviceDefinition(HttpServletRequest request) {
		final String path = request.getPathInfo().substring(PATH_PREFIX.length());

		final String pathItemKey = pathItemKey(path);
		log.fine("serviceDefinition pathItemKey: " + pathItemKey);
		if (pathItemKey == null) {
			return null;
		}
		final PathItem pathItem = this.openAPIDocument.getPaths().get(pathItemKey);
		log.fine("serviceDefinition pathItem: " + pathItem);
		if (pathItem == null) {
			return null;
		}

		final Operation operation = operation(pathItem, request);
		if (operation == null) {
			return null;
		}

		Map<?, ?> serviceDefinition = null;
		// Check for an operation specific x-autorest definition. If there is not one then check at the path
		if (operation.getExtensions() != null && operation.getExtensions().containsKey("x-autorest") ) {
			serviceDefinition = (Map<?, ?>) operation.getExtensions().get("x-autorest");
		} else if (pathItem.getExtensions() != null && pathItem.getExtensions().containsKey("x-autorest")) {
			serviceDefinition = (Map<?, ?>) pathItem.getExtensions().get("x-autorest");
		}
		this.log.fine("serviceDefinition() x-autorest: " + serviceDefinition);
		if (serviceDefinition == null) {
			return null;
		}

		if (!serviceDefinition.containsKey("identifier")) {
			this.log.fine("serviceDefinition() x-autorest missing 'identifier' attribute.");
		}

		if (!serviceDefinition.containsKey("type")) {
			this.log.fine("serviceDefinition() x-autorest missing 'type' attribute.");
		}

		ServiceDefinition sd = new ServiceDefinition();
		if (serviceDefinition.containsKey("owner")) {
			sd.owner = serviceDefinition.get("owner").toString();
		}
		sd.identifier = serviceDefinition.get("identifier").toString();
		sd.path = pathItemKey;
		switch (serviceDefinition.get("type").toString().toLowerCase()) {
		case "table":
			sd.type = SupportedObjectType.TABLE;
			break;
		case "view":
			sd.type = SupportedObjectType.VIEW;
			break;
		}
		
		// For a GET operation the query may be for a collection of resource items or a single resource item.
		if (request.getMethod().equals("GET")) {
			sd.queryType = SupportedQueryType.COLLECTION;
			if (pathItem.getParameters() != null) {
				for (Parameter param: pathItem.getParameters()) {
					if (param.getIn().equalsIgnoreCase("path")) {
						sd.queryType = SupportedQueryType.ITEM;
					}
				}
			}
		}
		return sd;
	}

	private String pathItemKey(final String path) {
		if (this.openAPIDocument.getPaths().containsKey(path)) {
			return path;
		}
		// No direct match now to iterate over paths that have parameters
		for (String key: this.openAPIDocument.getPaths().keySet()) {
			if (this.openAPIDocument.getPaths().get(key).getParameters() != null) {
				// This is a PathItem which has a parameter defined in the path
				this.log.fine("pathItemKey:" + key);
				final StringBuilder sb = new StringBuilder();
				String[] fragments = key.split("/");
				for (String fragment : fragments) {
		            // Skip empty fragments caused by leading/trailing slashes
		            if (!fragment.isEmpty() && !fragment.startsWith("{") && !fragment.endsWith("}")) {
		            	sb.append("/");
		            	sb.append(fragment);
		            }
		        }
				if (path.startsWith(sb.toString())) {
					return key;
				}
			}
		}
		
		return null;
	}
	private Operation operation(PathItem pathItem, HttpServletRequest request) {
		switch (request.getMethod()) {
		case "DELETE":
			return pathItem.getDelete();
		case "GET":
			return pathItem.getGet();
		case "POST":
			return pathItem.getPost();
		case "PUT":
			return pathItem.getPut();
		}
		return null;
	}

	private ObjectNode resultSetToJson(final ResultSet resultSet, final ServiceDefinition sd, final Map<String, Object> values, final HttpServletRequest request) throws SQLException {
		ObjectNode rootNode = this.jsonMapper.createObjectNode();
		if (sd.queryType == SupportedQueryType.COLLECTION) {
			int rowCount = 0;
			final ArrayNode items = rootNode.arrayNode();
			while (resultSet.next()) {
				final ObjectNode rowNode = items.addObject();
				resultSetToJson(resultSet, rowNode);
				rowCount++;
			}
			rootNode.putPOJO("limit", values.get("limit"));
			rootNode.putPOJO("offset", values.get("offset"));
			rootNode.putPOJO("count", rowCount);
			rootNode.set("items", items);
		} else {
			// Check if there is data
			if (resultSet.next()) {
				resultSetToJson(resultSet, rootNode);
			}
		}
		return rootNode;
	}

	private void resultSetToJson(final ResultSet resultSet, ObjectNode rootNode) throws SQLException {
		ResultSetMetaData metaData = resultSet.getMetaData();
		int columnCount = metaData.getColumnCount();

		for (int i = 1; i <= columnCount; i++) {
			String columnName = metaData.getColumnLabel(i);
			Object columnValue = resultSet.getObject(i);

			// Add each column as a field in the JSON object
			if (columnValue != null) {
				rootNode.putPOJO(columnName, columnValue);
			} else {
				rootNode.putNull(columnName);
			}
		}
	}

	private final AutoRESTJDBC autoRESTDelegate;
	private final Log log;
	private final ObjectMapper jsonMapper = new ObjectMapper();
	private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
	private final OpenAPI openAPIDocument;
	private final PathTemplates pathTemplates;
	public static final String PATH_PREFIX = "/autorest";
	/**
	 * Default serialVersionUI for serialisation.
	 */
	private static final long serialVersionUID = -258896688766893491L;

}
