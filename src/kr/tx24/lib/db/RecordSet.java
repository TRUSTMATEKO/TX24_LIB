package kr.tx24.lib.db;

import java.sql.Blob;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import kr.tx24.lib.lang.CommonUtils;
import kr.tx24.lib.map.MapFactory;
import kr.tx24.lib.map.SharedMap;
import kr.tx24.lib.map.TypeRegistry;

public class RecordSet {
	private static Logger logger 	= LoggerFactory.getLogger(RecordSet.class);
	private static final int MAX_BLOB_SIZE = 20 * 1024 * 1024;
	private static final int DEFAULT_CAPACITY = 50;
	
	private final List<SharedMap<String, Object>> rows;
	private final String[] columns;
	private final AtomicInteger idx = new AtomicInteger(-1);
    private long count;		 
    
    public RecordSet() {
    	this.rows = new ArrayList<>(0);
        this.columns = new String[0];
        this.count = 0;
    }


    public RecordSet(ResultSet rs) throws SQLException {
        if (rs == null) {
        	this.rows = new ArrayList<>(0);
            this.columns = new String[0];
            this.count = 0;
            return;
        }

        
        
        ResultSetMetaData meta = rs.getMetaData();
        int columnCount = meta.getColumnCount();
        this.columns = new String[columnCount];

        
        for (int i = 0; i < columnCount; i++) {
        	columns[i] = meta.getColumnLabel(i + 1);
        }
        
        
        this.rows = new ArrayList<>(DEFAULT_CAPACITY);

        
        while (rs.next()) {
            SharedMap<String, Object> map = MapFactory.createObjectMap(TypeRegistry.MAP_SHAREDMAP_OBJECT);

            for (int i = 1; i <= columnCount; i++) {
                Object value;
                int type = meta.getColumnType(i);

                try {
                    switch (type) {
                        // 문자열 계열
                        case java.sql.Types.CHAR,
                             java.sql.Types.VARCHAR,
                             java.sql.Types.LONGVARCHAR,
                             java.sql.Types.NCHAR,
                             java.sql.Types.NVARCHAR,
                             java.sql.Types.LONGNVARCHAR,
                             java.sql.Types.CLOB,
                             java.sql.Types.NCLOB -> value = rs.getString(i);

                        // 바이너리 계열
                        case java.sql.Types.BINARY,
                             java.sql.Types.VARBINARY,
                             java.sql.Types.LONGVARBINARY,
                             java.sql.Types.BLOB -> {
                            	try {
									Blob blob = rs.getBlob(i);
									if (blob != null) {
									    long length = blob.length();
									    int readSize = (int) Math.min(length, MAX_BLOB_SIZE);
									    value = blob.getBytes(1, readSize);
									    blob.free();
									}else {
										value = null;
									}
                        		} catch (SQLException | AbstractMethodError e) {
                        		    byte[] bytes = rs.getBytes(i);
                        		    if (bytes != null && bytes.length > MAX_BLOB_SIZE) {
                        		        value = Arrays.copyOf(bytes, MAX_BLOB_SIZE);
                        		    } else {
                        		        value = bytes;
                        		    }
                        		}
							}

                        // 숫자 계열
                        case java.sql.Types.TINYINT,
                             java.sql.Types.SMALLINT,
                             java.sql.Types.INTEGER -> value = rs.getInt(i);

                        case java.sql.Types.BIGINT -> value = rs.getLong(i);

                        case java.sql.Types.FLOAT,
                             java.sql.Types.REAL -> value = rs.getFloat(i);

                        case java.sql.Types.DOUBLE -> value = rs.getDouble(i);

                        case java.sql.Types.DECIMAL,
                             java.sql.Types.NUMERIC -> value = rs.getBigDecimal(i);

                        // 논리 계열
                        case java.sql.Types.BIT,
                             java.sql.Types.BOOLEAN -> value = rs.getBoolean(i);

                        // 날짜/시간 계열
                        case java.sql.Types.DATE,
                             java.sql.Types.TIME,
                             java.sql.Types.TIMESTAMP,
                             java.sql.Types.TIMESTAMP_WITH_TIMEZONE -> value = rs.getTimestamp(i);

                        default -> value = rs.getObject(i);
                    }
                } catch (Exception e) {
                	logger.warn("error : {}",CommonUtils.getExceptionMessage(e));
                    value = null;
                }

                map.put(columns[i - 1], value != null ? value : "");
            }
            
            rows.add(map);
            this.count++;
        }
        
        
    }
    
    public boolean next() {
        int current = idx.get();
        if (current + 1 < rows.size()) {
            return idx.compareAndSet(current, current + 1);
        }
        return false;
    }

    public boolean prev() {
        int current = idx.get();
        if (current - 1 >= 0) {
            return idx.compareAndSet(current, current - 1);
        }
        return false;
    }

    public SharedMap<String, Object> getCurrent() {
        int current = idx.get();
        if (current >= 0 && current < rows.size()) {
            return rows.get(current);
        }
        return new SharedMap<>();
    }

    public void reset() {
        idx.set(-1);
    }

    public int getCurrentIndex() {
        return idx.get();
    }
    
    public int size() {
        return rows.size();
    }

    public boolean isEmpty() {
        return rows.isEmpty();
    }
    
    public List<SharedMap<String, Object>> getRows() {
        return rows;
    }
    
    
    public SharedMap<String, Object> getRow(int index) {
        if (index >= 0 && index < rows.size()) {
            return rows.get(index);
        }
        return null;
    }
    
    public SharedMap<String, Object> getRowFirst() {
        return rows.isEmpty() ? new SharedMap<>() : rows.get(0);
    }
    
    
    public SharedMap<String, Object> getRowLast() {
        return rows.isEmpty() ? new SharedMap<>() : rows.get(rows.size() - 1);
    }
    
    
    public String[] getColumns() {
    	return columns;
    }
    
    public List<String> getColumnList() {
    	return Arrays.asList(columns);
    }
    
    
    public void count(long count) {
    	this.count = count;
    }
    
    public long count() {
    	return this.count;
    }
    
    
    
}
