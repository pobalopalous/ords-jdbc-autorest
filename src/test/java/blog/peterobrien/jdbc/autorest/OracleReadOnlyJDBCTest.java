package blog.peterobrien.jdbc.autorest;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import oracle.dbtools.plugin.api.logging.Log;

/**
 * Test for AutoRESTJDBC class which uses a Oracle database. These tests confirm that the service method on AutoRESTJDBC returns a valid ResultSet object.
 * 
 * @author peobrie
 *
 */
public class OracleReadOnlyJDBCTest {
	final static String JDBC_URL = "jdbc:oracle:thin:@//dbtools-dev.oraclecorp.com:2323/DB23P";
	final Log log = mock(Log.class);

	@Before
	public void setup() {
		try (Connection connection = DriverManager
                .getConnection(JDBC_URL,
                        "HR", 
                        "oracle")) {
			connection.createStatement().executeUpdate("delete from employees where EMPLOYEE_ID = 999");
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		

	}

	@Test
	public void testGETCollection() {
		ServiceDefinition sd = new ServiceDefinition();
		sd.owner = "HR";
		sd.identifier = "EMPLOYEES";
		sd.path = "oracle/hr/employees/";
		sd.type = SupportedObjectType.TABLE;
		sd.queryType = SupportedQueryType.COLLECTION;
		
		final Map<String, Object> paramMap = new HashMap<String, Object>();
		paramMap.put("limit", 25);
		paramMap.put("offset", 25);
		try (Connection connection = DriverManager
                .getConnection(JDBC_URL,
                        "pdbdba", 
                        "oracle")) {
			AutoRESTJDBC mysql = new AutoRESTJDBC(connection, log);
			Object response = mysql.service(sd, SupportedAction.GET, paramMap);
			assertNotNull(response);
			assertTrue(response instanceof ResultSet);
			try (final ResultSet results = (ResultSet) response;) {
				int rowCount = 0;
				while (results.next()) {
					rowCount++;
					assertEquals("EMPLOYEE_ID", results.getMetaData().getColumnLabel(1));
				}
				
				assertEquals(25, rowCount);
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	@Test
	public void testGETView() {
		ServiceDefinition sd = new ServiceDefinition();
		sd.owner = "HR";
		sd.identifier = "EMP_DETAILS_VIEW";
		sd.path = "oracle/hr/empdetails/";
		sd.type = SupportedObjectType.VIEW;
		
		final Map<String, Object> paramMap = new HashMap<String, Object>();
		paramMap.put("limit", 25);
		paramMap.put("offset", 0);
				
		try (Connection connection = DriverManager
                .getConnection(JDBC_URL,
                        "pdbdba", 
                        "oracle")) {
			AutoRESTJDBC mysql = new AutoRESTJDBC(connection, log);
			Object response = mysql.service(sd, SupportedAction.GET,  paramMap);
			assertNotNull(response);
			assertTrue(response instanceof ResultSet);
			try (final ResultSet results = (ResultSet) response;) {
				int rowCount = 0;
				while (results.next()) {
					rowCount++;
					assertEquals("EMPLOYEE_ID", results.getMetaData().getColumnLabel(1));
				}
				
				assertEquals(25, rowCount);
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	@Test
	public void testGETItem() {
		ServiceDefinition sd = new ServiceDefinition();
		sd.owner = "HR";
		sd.identifier = "EMPLOYEES";
		sd.path = "oracle/hr/employees/";
		sd.type = SupportedObjectType.TABLE;
		sd.queryType = SupportedQueryType.ITEM;
		
		try (Connection connection = DriverManager
                .getConnection(JDBC_URL,
                        "pdbdba", 
                        "oracle")) {
			AutoRESTJDBC mysql = new AutoRESTJDBC(connection, log);

			final Map<String, Object> values = new HashMap<String, Object>();
			values.put("EMPLOYEE_ID", 194);
			
			Object response = mysql.service(sd, SupportedAction.GET, values);
			assertNotNull(response);
			assertTrue(response instanceof ResultSet);
			try (final ResultSet results = (ResultSet) response;) {
				assertTrue(results.next());
					assertEquals("EMPLOYEE_ID", results.getMetaData().getColumnLabel(1));
					assertEquals(BigDecimal.valueOf(194), results.getObject(1));
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	@Test
	public void testGETInvalidIdentifier() {
		ServiceDefinition sd = new ServiceDefinition();
		sd.identifier = "actors";
		sd.path = "sakila/sammy/actors/";
		sd.type = SupportedObjectType.TABLE;
		
		final Map<String, Object> paramMap = new HashMap<String, Object>();
		paramMap.put("limit", 25);
		try (Connection connection = DriverManager
                .getConnection(JDBC_URL,
                        "HR", 
                        "oracle")) {
			AutoRESTJDBC mysql = new AutoRESTJDBC(connection, log);
			Object response = mysql.service(sd, SupportedAction.GET, paramMap);
			assertNull(response);
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	
}
