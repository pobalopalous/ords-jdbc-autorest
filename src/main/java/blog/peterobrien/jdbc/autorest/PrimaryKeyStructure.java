package blog.peterobrien.jdbc.autorest;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class PrimaryKeyStructure {
	List<PKElement> elements = new ArrayList<PKElement>();

	PrimaryKeyStructure(final ResultSet resultSet) throws SQLException {
		while (resultSet.next()) {
			elements.add(new PKElement(resultSet));
		}
	}
	
	boolean isKeyColumn(final String key) {
		for (PKElement pk: elements) {
			if (pk.columnName.equals(key)) {
				return true;
			}
		}
		return false;
	}
}
