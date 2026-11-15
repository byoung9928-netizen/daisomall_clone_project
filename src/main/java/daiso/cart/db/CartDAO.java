package daiso.cart.db;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.sql.DataSource;

public class CartDAO {
	private DataSource ds;
 public CartDAO() {
	try{
		Context init = new InitialContext();
		ds = (DataSource) init.lookup("java:comp/env/jdbc/mysqlDB");
	}catch(Exception ex){
		System.out.println("DB 접속실패: " + ex);
	}
 }
}
