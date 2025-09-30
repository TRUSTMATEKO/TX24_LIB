package kr.tx24.lib.db.scheme;

import java.util.List;

/**
 * @author juseop
 *
 */
public class Catalog {

	public String name	= "";
	public List<Table> tables	= null;
	public List<Table> views	= null;
	public List<Table> seqs		= null;
	public List<Table> functions= null;
	public List<Table> procedures= null;
	public List<Table> events= null;
	
	public Catalog() {
		// TODO Auto-generated constructor stub
	}

}	
