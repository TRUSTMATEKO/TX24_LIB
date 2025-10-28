package kr.tx24.lib.db;

import java.sql.SQLException;

public class DBException extends SQLException {
private static final long serialVersionUID = 1L;
    
	private final int code;
    private final String query;
    private final String message;
    private final String operation;
    

    public DBException(String message, String query, SQLException cause) {
        super(message, cause);
        this.code  = cause.getErrorCode();
        this.query = query;
        this.message = cause != null ? cause.getMessage() : message;
        this.operation = extractOperation(query);
    }
    

    public DBException(String message, String query, String operation, SQLException cause) {
        super(message, cause);
        this.code  = cause.getErrorCode();
        this.query = query;
        this.operation = operation;
        this.message = cause != null ? cause.getMessage() : message;
    }
    

    public DBException(String message, SQLException cause) {
        this(message, null, cause);
    }
    
    /**
     * Constructor with just message
     */
    public DBException(String message) {
        this(message, (SQLException) null);
    }
    
    
    /**
     * Get the SQL query that caused the error
     */
    public int getCode() {
        return code;
    }
    
    /**
     * Get the SQL query that caused the error
     */
    public String getQuery() {
        return query;
    }
    
    /**
     * Get detailed error message from underlying exception
     */
    public String getMessage() {
        return message;
    }
    

    /**
     * Get operation type (SELECT, INSERT, UPDATE, DELETE, etc.)
     */
    public String getOperation() {
        return operation;
    }
    
    /**
     * Extract operation type from query
     */
    private String extractOperation(String query) {
        if (query == null || query.trim().isEmpty()) {
            return "UNKNOWN";
        }
        
        String trimmed = query.trim().toUpperCase();
        if (trimmed.startsWith("SELECT")) return "SELECT";
        if (trimmed.startsWith("INSERT")) return "INSERT";
        if (trimmed.startsWith("UPDATE")) return "UPDATE";
        if (trimmed.startsWith("DELETE")) return "DELETE";
        if (trimmed.startsWith("CREATE")) return "CREATE";
        if (trimmed.startsWith("ALTER")) return "ALTER";
        if (trimmed.startsWith("DROP")) return "DROP";
        
        return "OTHER";
    }
    
    /**
     * Get masked query (hide sensitive data)
     */
    public String getMaskedQuery() {
        if (query == null) return null;
        
        // Simple masking: replace values in quotes with ***
        return query.replaceAll("'[^']*'", "'***'")
                   .replaceAll("\"[^\"]*\"", "\"***\"");
    }
    
    /**
     * Check if this is a connection error
     */
    public boolean isConnectionError() {
        if (message == null) return false;
        
        String msg = message.toLowerCase();
        return msg.contains("connection") || 
               msg.contains("timeout") || 
               msg.contains("socket");
    }
    
    /**
     * Check if this is a syntax error
     */
    public boolean isSyntaxError() {
        if (message == null) return false;
        
        String msg = message.toLowerCase();
        return msg.contains("syntax") || 
               msg.contains("parse");
    }
    
    /**
     * Check if this is a constraint violation
     */
    public boolean isConstraintViolation() {
        if (message == null) return false;
        
        String msg = message.toLowerCase();
        return msg.contains("constraint") || 
               msg.contains("duplicate") || 
               msg.contains("unique") ||
               msg.contains("foreign key");
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("DBException [").append(operation).append("]\n");
        sb.append("  Message: ").append(getMessage()).append("\n");
        
        if (query != null) {
            sb.append("  Query: ").append(getMaskedQuery()).append("\n");
        }
        
        sb.append("  Details: ").append(message).append("\n");
        
        // Error type
        if (isConnectionError()) {
            sb.append("  Type: CONNECTION ERROR\n");
        } else if (isSyntaxError()) {
            sb.append("  Type: SYNTAX ERROR\n");
        } else if (isConstraintViolation()) {
            sb.append("  Type: CONSTRAINT VIOLATION\n");
        }
        
        return sb.toString();
    }
    
    /**
     * Get a compact error message for logging
     */
    public String toCompactString() {
        return String.format("[%s] %s | Query: %s", 
            operation, 
            getMessage(), 
            query != null ? (query.length() > 50 ? query.substring(0, 50) + "..." : query) : "N/A"
        );
    }
}
