package kr.tx24.lib.db.scheme;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * @author juseop
 *
 */
public class Column {
	@JsonIgnore
	public int p	= 0; 	//ORDINAL_POSITION
	public String n	= ""; 	//COLUMN_NAME
	public String r	= ""; 	//REMARKS
	public String t	= ""; 	//TYPE_NAME
	
	public Column() {
		// TODO Auto-generated constructor stub
	}

}
