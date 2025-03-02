package blog.peterobrien.jdbc.autorest;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import oracle.dbtools.plugin.api.logging.Log;

public class AutoRESTJDBC {

	final Connection connection;
	final Log log;
	final String schema;

	public AutoRESTJDBC(Connection connection, final Log log) {
		this.log = log;
		this.connection = connection;
		String connSchema = null;

		try {
			connSchema = connection.getSchema();
			if (connSchema == null || connSchema.isEmpty()) {
				connSchema = connection.getMetaData().getUserName(); // Fallback if schema is unavailable
			}
		} catch (SQLException e) {
			log.severe(e);
		}
		this.schema = connSchema;
	}

	private Object delete(ServiceDefinition sd, String objectType,
			PrimaryKeyStructure pkStructure, Map<String, Object> values) {
		if (!isActionable(objectType, pkStructure, values)) {
			return null;
		}
		
		final StringBuilder deleteStmt = new StringBuilder();
		deleteStmt.append("delete from ");
		if (sd.owner != null) {
			deleteStmt.append(sd.owner);
			deleteStmt.append(".");
		}
		deleteStmt.append(sd.identifier);
		deleteStmt.append(" where ");

		for (PKElement pk: pkStructure.elements) {
			deleteStmt.append(pk.columnName + " = ?,");
		}
		deleteStmt.deleteCharAt(deleteStmt.length() -1);
		
		try (PreparedStatement deleteStatement = connection.prepareStatement(deleteStmt.toString())) {
	    	int keyCounter = 1;
			for (PKElement pk: pkStructure.elements) {
				deleteStatement.setObject(keyCounter, values.get(pk.columnName));
				keyCounter++;
			}
			return deleteStatement.executeUpdate();
			
		} catch (SQLException e) {
			log.severe(e);
		}
        return 0;
	}

	private Object get(ServiceDefinition sd, String objectType, final PrimaryKeyStructure pkStructure, final Map<String, Object> values) {
		if (pkStructure != null && values != null && sd.queryType == SupportedQueryType.ITEM) {
			for (PKElement pk: pkStructure.elements) {
				if (!values.containsKey(pk.columnName)) {
					log.fine("No value provided for Primary Key field " + pk.columnName);
					return null;
				}
			}

			return getItem(sd, pkStructure, values);
		}
		return getCollection(Integer.valueOf(values.get("limit").toString()), Integer.valueOf(values.get("offset").toString()), sd, pkStructure);
	}

	private Object getCollection(int limit, int offset, ServiceDefinition sd, final PrimaryKeyStructure pkStructure) {
		final StringBuilder queryStmt = new StringBuilder();
		queryStmt.append("select * from ");
		if (sd.owner != null) {
			queryStmt.append(sd.owner);
			queryStmt.append(".");
		}
		queryStmt.append(sd.identifier);
		
		if (pkStructure != null) {
			queryStmt.append(" order by ");
			for (PKElement pk: pkStructure.elements) {
				queryStmt.append(pk.columnName);
				queryStmt.append(",");
			}
			queryStmt.deleteCharAt(queryStmt.length() -1);
		}
		
		paginate(limit, offset, queryStmt);
		
		try {
			return connection.createStatement().executeQuery(queryStmt.toString());
		} catch (SQLException e) {
			log.severe(e);
		}
		return null;
	}

	private void paginate(int limit, int offset, final StringBuilder queryStmt) {
		try {
			final String driverName = this.connection.getMetaData().getDriverName();
			
			if (driverName.equals("Oracle JDBC driver") || (driverName.startsWith("Microsoft JDBC Driver") && driverName.endsWith("for SQL Server"))) {
				// Offset and fetch syntax
				queryStmt.append(" offset ");
				queryStmt.append(offset);
				queryStmt.append(" rows fetch next ");
				queryStmt.append(limit);
				queryStmt.append(" rows only ");
			} else {
				// Limit and offset syntax
				queryStmt.append(" limit ");
				queryStmt.append(limit);
				queryStmt.append(" offset ");
				queryStmt.append(offset);
			}
		} catch (SQLException e) {
			log.severe(e);
		}
	}

	private Object getItem(ServiceDefinition sd, final PrimaryKeyStructure pkStructure, final Map<String, Object> values) {
		final StringBuilder queryStmt = new StringBuilder();
		queryStmt.append("select * from ");
		if (sd.owner != null) {
			queryStmt.append(sd.owner);
			queryStmt.append(".");
		}
		queryStmt.append(sd.identifier);
		
		queryStmt.append(" where ");

		for (PKElement pk: pkStructure.elements) {
			queryStmt.append(pk.columnName + " = ?,");
		}
		queryStmt.deleteCharAt(queryStmt.length() -1);
		
		try {
	        PreparedStatement selectStatement = connection.prepareStatement(queryStmt.toString());
	    	int keyCounter = 1;
			for (PKElement pk: pkStructure.elements) {
				selectStatement.setObject(keyCounter, values.get(pk.columnName));
				keyCounter++;
			}
	        return selectStatement.executeQuery();
		} catch (SQLException e) {
			log.severe(e);
		}
		return null;
	}

	private boolean isActionable(String objectType, PrimaryKeyStructure pkStructure, final Map<String, Object> values) {
		if (!objectType.equals("TABLE")) {
			System.out.println("Data manipulation only available for database objects of type TABLE");
			return false;
		}
		if (pkStructure == null) {
			System.out.println("Table does not have a primary key defined.");
			return false;
		}
		for (PKElement pk: pkStructure.elements) {
			if (!values.containsKey(pk.columnName)) {
				log.fine("No value provided for Primary Key field " + pk.columnName);
				return false;
			}
		}
		return true;
	}

	private Object post(ServiceDefinition sd, String objectType,
			PrimaryKeyStructure pkStructure, final Map<String, Object> values) {
		if (!isActionable(objectType, pkStructure, values)) {
			return null;
		}
		
		final StringBuilder insertStmt = new StringBuilder();
		insertStmt.append("insert into ");
		if (sd.owner != null) {
			insertStmt.append(sd.owner);
			insertStmt.append(".");
		}
		insertStmt.append(sd.identifier);
		insertStmt.append(" (");
		insertStmt.append(String.join(", ", values.keySet()));
		insertStmt.append(") values (");
		insertStmt.append("?,".repeat(values.size() - 1));
		insertStmt.append("?)");
		
		try (PreparedStatement insertStatement = connection.prepareStatement(insertStmt.toString(), Statement.RETURN_GENERATED_KEYS);) {
			final Object[] valuesAsArray = values.values().toArray();
			for (int i = 0; i < values.size(); i++) {
                insertStatement.setObject(i + 1, valuesAsArray[i]);
            }
			int rowsAffected = insertStatement.executeUpdate();
            if (rowsAffected == 0) {
                throw new SQLException("Insertion failed, no rows affected.");
            }
            // Retrieve the generated keys (primary key for the new row)
            try (ResultSet generatedKeys = insertStatement.getGeneratedKeys()) {
            		final StringBuilder queryStmt = new StringBuilder();
            		queryStmt.append("select * from ");
            		if (sd.owner != null) {
            			queryStmt.append(sd.owner);
            			queryStmt.append(".");
            		}
            		queryStmt.append(sd.identifier);
            		queryStmt.append(" where ");

            		for (PKElement pk: pkStructure.elements) {
            			queryStmt.append(pk.columnName + " = ?,");
            		}
            		queryStmt.deleteCharAt(queryStmt.length() -1);
            		
                    PreparedStatement selectStatement = connection.prepareStatement(queryStmt.toString());
                    	int keyCounter = 1;
                    	while (generatedKeys.next()) {
                    		selectStatement.setObject(keyCounter,  generatedKeys.getObject(1));
                    		keyCounter++;
                    	}
                        return selectStatement.executeQuery();
            }
		} catch (SQLException e) {
			log.severe(e);
		}
		
		return null;
	}

	private Object put(ServiceDefinition sd, String objectType,
			PrimaryKeyStructure pkStructure, Map<String, Object> values) {
		if (!isActionable(objectType, pkStructure, values)) {
			return null;
		}
		
		try (ResultSet getItemRS = (ResultSet) this.getItem(sd, pkStructure, values)) {
			if (getItemRS.next()) {
				//Update
				final List<Object> updateValues = new ArrayList<Object>();
				
				
				final StringBuilder updateStmt = new StringBuilder();
				updateStmt.append("update ");
				if (sd.owner != null) {
					updateStmt.append(sd.owner);
					updateStmt.append(".");
				}
				updateStmt.append(sd.identifier);
				updateStmt.append(" set ");
				
				for (String key: values.keySet()) {
					if (!pkStructure.isKeyColumn(key)) {
						updateStmt.append(key + " = ?,");
						updateValues.add(values.get(key));
					}
				}
				updateStmt.deleteCharAt(updateStmt.length() -1);
				updateStmt.append(" where ");

				for (PKElement pk: pkStructure.elements) {
					updateStmt.append(pk.columnName + " = ?,");
				}
				updateStmt.deleteCharAt(updateStmt.length() -1);

				try (PreparedStatement updateStatement = connection.prepareStatement(updateStmt.toString());) {
					// Set the new values
					for (int i = 0; i < updateValues.size(); i++) {
						updateStatement.setObject(i + 1, updateValues.get(i));
		            }
					
					// Set PK values
					for (int i = 0; i < pkStructure.elements.size(); i++) {
						updateStatement.setObject(i + updateValues.size() + 1, values.get(pkStructure.elements.get(i).columnName));
		            }
					
					int rowsAffected = updateStatement.executeUpdate();
		            if (rowsAffected == 0) {
		                throw new SQLException("Update failed, no rows affected.");
		            }
		            return this.getItem(sd, pkStructure, values);
				}
			} else {
				//Insert
				return post(sd, objectType, pkStructure, values);
			}
			
		} catch (SQLException e) {
			log.severe(e);
		}

		return null;
	}

	public Object service(final ServiceDefinition sd, final SupportedAction action, final Map<String, Object> values) {

		try {
			final DatabaseMetaData dbMetadata = connection.getMetaData();
			String schemaPattern = sd.owner;
			String tableNamePattern = sd.identifier;
			
			// Check that the database user has access to the object
			try (ResultSet resultSet = dbMetadata.getTables(null, schemaPattern, tableNamePattern, sd.type.toStringArray());) {
				if (resultSet.next()) {
					String objectType = resultSet.getString("TABLE_TYPE");
					String objectName = resultSet.getString("TABLE_NAME");
					PrimaryKeyStructure pkStructure = null;
					if (objectType.equalsIgnoreCase("TABLE")) {
						try (ResultSet pkResultSet = dbMetadata.getPrimaryKeys(null, schemaPattern, objectName)) {
							pkStructure = new PrimaryKeyStructure(pkResultSet);
						}
					}
					return service(sd, action, objectType, pkStructure, values);
				} else {
					System.out.println("Object " + sd.owner + "." + sd.identifier + " of type " + sd.type
							+ " is not visible to the database user " + schema);
				}
			}
		} catch (SQLException e) {
			log.severe(e);
		}
		return null;
	}

	private Object service(ServiceDefinition sd, SupportedAction action, String objectType, final PrimaryKeyStructure pkStructure, final Map<String, Object> values) {
		log.fine("AutoRESTJDBC service " + objectType + ": " + sd.identifier);
		Object response = null;

		switch (action) {
		case GET:
			response = get(sd, objectType, pkStructure, values);
			break;
		case DELETE:
			response = delete(sd, objectType, pkStructure, values);
			break;
		case POST:
			response = post(sd, objectType, pkStructure, values);
			break;
		case PUT:
			response = put(sd, objectType, pkStructure, values);
			break;
		default:
			break;
		}
		return response;
	}
	
}
