package kr.tx24.lib.db.scheme;
import java.util.List;

/**
 * @author juseop
 *
 */
public class SqlResult {
	

	public int ret					= 0;
	public String msg				= "";
	public String duration			= "";
	public List<String> columns		= null;
	public List<List<Object>> datas	= null;
	
	
	public SqlResult() {
		// TODO Auto-generated constructor stub
	}

}
