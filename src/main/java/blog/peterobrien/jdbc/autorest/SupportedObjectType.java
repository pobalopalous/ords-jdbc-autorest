package blog.peterobrien.jdbc.autorest;

public enum SupportedObjectType {
	    TABLE,
	    VIEW;

	    // Convert enum to string (optional, for convenience)
	    public String[] toStringArray() {
	        return java.util.Arrays.stream(values())
	                               .map(Enum::name)
	                               .toArray(String[]::new);
	    }
	
}
