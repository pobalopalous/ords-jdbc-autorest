package blog.peterobrien.jdbc.autorest;

public enum SupportedQueryType {
	    COLLECTION,
	    ITEM;

	    // Convert enum to string (optional, for convenience)
	    public String[] toStringArray() {
	        return java.util.Arrays.stream(values())
	                               .map(Enum::name)
	                               .toArray(String[]::new);
	    }
	
}
