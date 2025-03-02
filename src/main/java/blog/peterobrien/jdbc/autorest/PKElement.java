package blog.peterobrien.jdbc.autorest;

import java.sql.ResultSet;
import java.sql.SQLException;

public class PKElement {
	public PKElement(ResultSet resultSet) throws SQLException {
        columnName = resultSet.getString("COLUMN_NAME");
        keyName = resultSet.getString("PK_NAME");
        keySequence = resultSet.getShort("KEY_SEQ");
	}
	String columnName;
    String keyName;
    short keySequence;
}
