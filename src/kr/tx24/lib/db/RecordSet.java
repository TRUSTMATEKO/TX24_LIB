package kr.tx24.lib.db;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import kr.tx24.lib.map.SharedMap;

public class RecordSet {

	private final List<SharedMap<String, Object>> rows = new ArrayList<>();
    private String[] columns = null;
    private int idx = -1; 		// 커서 인덱스
    private long count = 0;		// 전체의 갯수를 가져가기 위하여 
    
    public RecordSet() {
    }


    public RecordSet(ResultSet rs) throws SQLException {
        if (rs == null) {
        	this.columns = new String[0];
        	return;
        }

        ResultSetMetaData meta = rs.getMetaData();
        int columnCount = meta.getColumnCount();
        this.columns = new String[columnCount];

        
        for (int i = 0; i < columnCount; i++) {
        	columns[i] = meta.getColumnLabel(i + 1);
        }

        while (rs.next()) {
            SharedMap<String, Object> map = new SharedMap<>();

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
                            java.io.InputStream input = rs.getBinaryStream(i);
                            if (input != null) {
                                try (input) {
                                    value = input.readAllBytes();
                                }
                            } else {
                                value = null;
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
                    value = null;
                }

                map.put(columns[i - 1], value != null ? value : "");
            }

            rows.add(map);
        }
    }
    
    public boolean next() {
        if (idx + 1 < rows.size()) {
            idx++;
            return true;
        }
        return false;
    }

    public boolean prev() {
        if (idx - 1 >= 0) {
            idx--;
            return true;
        }
        return false;
    }

    public SharedMap<String, Object> getCurrent() {
        if (idx >= 0 && idx < rows.size()) {
            return rows.get(idx);
        }
        return new SharedMap<>();
    }

    public void reset() {
        idx = -1;
    }

    public int getCurrentIndex() {
        return idx;
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
