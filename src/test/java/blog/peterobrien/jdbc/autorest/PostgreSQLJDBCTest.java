package blog.peterobrien.jdbc.autorest;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;

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
import org.junit.Test;

import oracle.dbtools.plugin.api.logging.Log;

/**
 * Test for AutoRESTJDBC class which uses a Postgres database with sakila example data. These tests confirm that the service method on AutoRESTJDBC returns a valid ResultSet object.
 * 
 * @author peobrie
 *
 */

public class PostgreSQLJDBCTest {
	final static String JDBC_URL = "jdbc:postgresql://localhost:5432/sakila";
	final Log log = mock(Log.class);

	@Before
	public void setup() {
		try (Connection connection = DriverManager
                .getConnection(JDBC_URL,
                        "sammy", 
                        "welcome1")) {
			connection.createStatement().executeUpdate("delete from actor where actor_id = 999");
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		

	}

	@Test
	public void testGETCollection() {
		ServiceDefinition sd = new ServiceDefinition();
		sd.identifier = "actor";
		sd.path = "sakila/sammy/actors/";
		sd.type = SupportedObjectType.TABLE;
		
		final Map<String, Object> values = new HashMap<String, Object>();
		values.put("limit", 25);
		values.put("offset", 25);
		try (Connection connection = DriverManager
                .getConnection(JDBC_URL,
                        "sammy", 
                        "welcome1")) {
			AutoRESTJDBC mysql = new AutoRESTJDBC(connection, this.log);
			Object response = mysql.service(sd, SupportedAction.GET, values);
			assertNotNull(response);
			assertTrue(response instanceof ResultSet);
			try (final ResultSet results = (ResultSet) response;) {
				int rowCount = 0;
				while (results.next()) {
					rowCount++;
					assertEquals("actor_id", results.getMetaData().getColumnLabel(1));
					assertEquals(25 + rowCount, results.getObject(1));
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
		sd.identifier = "sales_by_film_category";
		sd.path = "sakila/sammy/sales_by_film_category/";
		sd.type = SupportedObjectType.VIEW;
		
		final Map<String, Object> values = new HashMap<String, Object>();
		values.put("limit", 25);
		values.put("offset", 0);
		
		final String[] categories = {"Sports", "Sci-Fi", "Animation", "Drama", "Comedy","Action","New","Games",
"Foreign","Family", "Documentary", "Horror", "Children", "Classics", "Travel","Music"};
		Set<String> categoriesSet = new HashSet<>(Arrays.asList(categories));
		
		try (Connection connection = DriverManager
                .getConnection(JDBC_URL,
                        "sammy", 
                        "welcome1")) {
			AutoRESTJDBC mysql = new AutoRESTJDBC(connection, this.log);
			Object response = mysql.service(sd, SupportedAction.GET, values);
			assertNotNull(response);
			assertTrue(response instanceof ResultSet);
			try (final ResultSet results = (ResultSet) response;) {
				int rowCount = 0;
				while (results.next()) {
					rowCount++;
					assertEquals("category", results.getMetaData().getColumnLabel(1));
					assertTrue(categoriesSet.contains(results.getObject(1)));
				}
				
				assertEquals(categories.length, rowCount);
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	@Test
	public void testGETItem() {
		ServiceDefinition sd = new ServiceDefinition();
		sd.identifier = "actor";
		sd.path = "sakila/sammy/actors/";
		sd.type = SupportedObjectType.TABLE;
		sd.queryType = SupportedQueryType.ITEM;
		
		try (Connection connection = DriverManager
                .getConnection(JDBC_URL,
                        "sammy", 
                        "welcome1")) {
			AutoRESTJDBC mysql = new AutoRESTJDBC(connection, this.log);

			final Map<String, Object> values = new HashMap<String, Object>();
			values.put("actor_id", 1);
			
			Object response = mysql.service(sd, SupportedAction.GET, values);
			assertNotNull(response);
			assertTrue(response instanceof ResultSet);
			try (final ResultSet results = (ResultSet) response;) {
				assertTrue(results.next());
					assertEquals("actor_id", results.getMetaData().getColumnLabel(1));
					assertEquals(1, results.getObject(1));
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
		
		final Map<String, Object> values = new HashMap<String, Object>();
		values.put("limit", 25);
		try (Connection connection = DriverManager
                .getConnection(JDBC_URL,
                        "sammy", 
                        "welcome1")) {
			AutoRESTJDBC mysql = new AutoRESTJDBC(connection, this.log);
			Object response = mysql.service(sd, SupportedAction.GET, values);
			assertNull(response);
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	@Test
	public void testPOST() {
		ServiceDefinition sd = new ServiceDefinition();
		sd.identifier = "actor";
		sd.path = "sakila/sammy/actors/";
		sd.type = SupportedObjectType.TABLE;
		
		try (Connection connection = DriverManager
                .getConnection(JDBC_URL,
                        "sammy", 
                        "welcome1")) {
			AutoRESTJDBC mysql = new AutoRESTJDBC(connection, this.log);
			
			final Map<String, Object> values = new HashMap<String, Object>();
			values.put("actor_id", 999);
			values.put("first_name", "Ted");
			values.put("last_name", "Test");
			
			Object response = mysql.service(sd, SupportedAction.POST, values);
			assertNotNull(response);
			assertTrue(response instanceof ResultSet);
			try (final ResultSet results = (ResultSet) response;) {
				assertTrue(results.next());
				assertEquals("actor_id", results.getMetaData().getColumnLabel(1));
				assertEquals(values.get("actor_id"), results.getObject(1));
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
	
	@Test
	public void testDELETE() {
		ServiceDefinition sd = new ServiceDefinition();
		sd.identifier = "actor";
		sd.path = "sakila/sammy/actors/";
		sd.type = SupportedObjectType.TABLE;
		
		try (Connection connection = DriverManager
                .getConnection(JDBC_URL,
                        "sammy", 
                        "welcome1")) {
			AutoRESTJDBC mysql = new AutoRESTJDBC(connection, this.log);
			
			// Setup the record to be deleted
			connection.createStatement().executeUpdate("insert into actor (actor_id, first_name, last_name) values (999, 'Ted', 'Test')");
			
			
			final Map<String, Object> values = new HashMap<String, Object>();
			values.put("actor_id", 999);
			
			Object response = mysql.service(sd, SupportedAction.DELETE, values);
			assertEquals(1, response);
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}		
	
}
