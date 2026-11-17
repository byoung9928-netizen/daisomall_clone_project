
 	package daiso.cart.db;
 	import java.sql.Connection;
 	import java.sql.PreparedStatement;
 	import java.sql.ResultSet;
 	import java.sql.SQLException;
 	import java.util.ArrayList;
 	import java.util.List;
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
 	 public List<CartBean> getCartList(String memberId)
 	 			throws SQLException{
 		 	List<CartBean> cartList = new ArrayList<>();
 		 	
 		 	String sql =
 		 		    "SELECT c.cart_item_id, c.product_id, c.quantity, "
 		 		    + "p.name AS product_name, p.price, p.image_url, "
 		 		    + "p.status AS product_status, p.stock_quantity "
 		 		    + "FROM cart_items c "
 		 		    + "JOIN products p ON c.product_id = p.product_id "
 		 		    + "WHERE c.member_id = ? "
 		 		    + "ORDER BY c.created_at DESC, c.cart_item_id DESC";
 		 	try (
 		 		Connection con = ds.getConnection();
 		 		PreparedStatement pstmt = con.prepareStatement(sql))
 		 	{
 		 		pstmt.setString(1, memberId);
 		 		try (ResultSet rs = pstmt.executeQuery()){
 		 			while (rs.next()) {
 		 				CartBean cart = new CartBean();

 		 			    cart.setCartItemId(rs.getLong("cart_item_id"));
 		 			    cart.setProductId(rs.getString("product_id"));
 		 			    cart.setProductName(rs.getString("product_name"));
 		 			    cart.setPrice(rs.getInt("price"));
 		 			    cart.setQuantity(rs.getInt("quantity"));
 		 			    cart.setImageUrl(rs.getString("image_url"));
 		 			    cart.setProductStatus(rs.getString("product_status"));
 		 			    cart.setStockQuantity(rs.getInt("stock_quantity"));

 		 			    cartList.add(cart);
 		 			}
 		 		}
 		 	}
 		 	return cartList;
 	 }
 	 public boolean addCart(String memberId,String productId, int quantity) throws SQLException{
 		 	
 		 	if(quantity <1 || quantity >99) {
 		 		throw new IllegalArgumentException(
 		 				"수량은 1~99개여야 합니다."
 		 				);
 		 	}
 		 	
 		 	try(Connection con =ds.getConnection()){
 		 		
 		 		boolean originalAutoCommit = con.getAutoCommit();
 		 		con.setReadOnly(false);
 		 		con.setAutoCommit(false);
 		 		
 		 		try {
 		 			//1. 상품판매와 상태와 재고 확인
 		 			String productSql=
 		 					"SELECT status, stock_quantity "
 		 						+ "FROM products "
 		 						+ "WHERE product_id = ? FOR UPDATE";
 		 			int stockQuantity;
 		 			try(PreparedStatement pstmt= con.prepareStatement(productSql)){
 		 				pstmt.setString(1, productId);
 		 				try(ResultSet rs=pstmt.executeQuery()){
 		 					if(!rs.next()) {
 		 						throw new IllegalArgumentException(
 		 								"상품을 찾을 수 없습니다."
 		 								);
 		 						
 		 					}
 		 				if(!"AVAILABLE".equals(
 		 						rs.getString("status"))) {
 		 					throw new IllegalArgumentException(
 		 							"판매중인 상품이 아닙니다."
 		 							);
 		 					
 		 				}
 		 				stockQuantity = rs.getInt("stock_quantity");
 		 				
 		 				}
 		 			}
 		 			//2. 이미 장바구니에 담긴 수량 확인
 		 			String cartSql = 
 		 					"SELECT quantity FROM cart_items "
 		 					+"WHERE member_id = ? "
 		 					+"AND product_id = ? FOR UPDATE";
 		 			int currentQuantity = 0;
 		 			
 		 			try (PreparedStatement pstmt = 
 		 					con.prepareStatement(cartSql)){
 		 				pstmt.setString(1,  memberId);
 		 				pstmt.setString(2, productId);
 		 				
 		 				try (ResultSet rs = pstmt.executeQuery()){
 		 					
 		 					if(rs.next()) {
 		 						currentQuantity = 
 		 								rs.getInt("quantity");
 		 					}
 		 				}
 		 			}
 		 		// 3. 합산할 수량 확인
 		            long totalQuantity =
 		                (long) currentQuantity + quantity;

 		            if (totalQuantity > 99) {
 		                throw new IllegalArgumentException(
 		                    "같은 상품은 최대 99개까지 담을 수 있습니다."
 		                );
 		            }

 		            if (totalQuantity > stockQuantity) {
 		                throw new IllegalArgumentException(
 		                    "재고보다 많이 담을 수 없습니다."
 		                );
 		            }

 		            // 4. 새 항목 추가 또는 기존 수량 합산
 		            String sql =
 		                "INSERT INTO cart_items "
 		                + "(member_id, product_id, quantity) "
 		                + "VALUES (?, ?, ?) "
 		                + "ON DUPLICATE KEY UPDATE "
 		                + "quantity = cart_items.quantity + ?, "
 		                + "updated_at = CURRENT_TIMESTAMP";

 		            int result;

 		            try (PreparedStatement pstmt =
 		                    con.prepareStatement(sql)) {

 		                pstmt.setString(1, memberId);
 		                pstmt.setString(2, productId);
 		                pstmt.setInt(3, quantity);
 		                pstmt.setInt(4, quantity);

 		                result = pstmt.executeUpdate();
 		            }

 		            con.commit();

 		            return result > 0;

 		        } catch (SQLException | RuntimeException e) {
 		            con.rollback();
 		            throw e;

 		        }finally {
 		        	con.setAutoCommit(originalAutoCommit);
 		        }
 		
 		 	}
 		 	
 		 }
 	 	public boolean updateCartQuantity(String memberId, long cartItemId, int change)
 				throws SQLException{

 	 			//버튼 한번에 1개씩만 변경
 	 		if (change != -1 && change != 1) {
 	 			throw new IllegalArgumentException(
 				 "수량은 한개씩 변경할 수 있습니다."
 	 					);
 	 		}
 	 		String sql =
 	 				"UPDATE cart_items c "
 	 						+"JOIN products p ON c.product_id = p.product_id "
 	 						+"SET c.quantity = c.quantity + ?, "
 	 						+"c.updated_at = CURRENT_TIMESTAMP "
 	 						+"WHERE c.cart_item_id = ? "
 	 						+"AND c.member_id = ? "
 	 						+"AND c.quantity + ? BETWEEN 1 AND 99 "
 	 						+"AND ( ? =  -1 OR (p.status = 'AVAILABLE' "
 	 						+"AND c.quantity + ? <= p.stock_quantity))";
 	 		try(Connection con = ds.getConnection()){
 	 			boolean originalAutocommit = con.getAutoCommit();
 			
 	 			con.setReadOnly(false);
 	 			con.setAutoCommit(false);
 		
 	 			try {
 	 				int result;
 			
 	 				try(PreparedStatement pstmt = con.prepareStatement(sql)){
 	 					pstmt.setInt(1, change);
 	 					pstmt.setLong(2, cartItemId);
 	 					pstmt.setString(3,memberId);
 	 					pstmt.setInt(4, change);
 	 					pstmt.setInt(5, change);
 	 					pstmt.setInt(6, change);
 				
 						result = pstmt.executeUpdate();
 					}
 					con.commit();
 					return result >0;
 				}catch(SQLException | RuntimeException e) {
 					con.rollback();
 					throw e;
 				}finally {
 					con.setAutoCommit(originalAutocommit);
 				}
 	 		}
 	 	}
 	 			public boolean deleteCart(String memberId, long cartItemId)
 	 			        throws SQLException {

 	 			    String sql =
 	 			        "DELETE FROM cart_items "
 	 			        + "WHERE cart_item_id = ? "
 	 			        + "AND member_id = ?";

 	 			    try (Connection con = ds.getConnection()) {
 	 			        boolean originalAutoCommit = con.getAutoCommit();

 	 			        con.setReadOnly(false);
 	 			        con.setAutoCommit(false);

 	 			        try {
 	 			            int result;

 	 			            try (PreparedStatement pstmt =
 	 			                    con.prepareStatement(sql)) {

 	 			                pstmt.setLong(1, cartItemId);
 	 			                pstmt.setString(2, memberId);

 	 			                result = pstmt.executeUpdate();
 	 			            }

 	 			            con.commit();
 	 			            return result > 0;

 	 			        } catch (SQLException | RuntimeException e) {
 	 			            con.rollback();
 	 			            throw e;

 	 			        } finally {
 	 			            con.setAutoCommit(originalAutoCommit);
 	 			        }
 		 

	 	}//conncetion을 쓰는 try
	 	
	 	
 	 			}
}//cartdao끝
