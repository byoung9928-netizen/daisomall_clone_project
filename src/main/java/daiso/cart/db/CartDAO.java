
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
 	 //2. CartController cartAdd에서 addCart로 넘어옴
 	 public boolean addCart(String memberId,String productId, int quantity) throws SQLException{
            addCartAndCheckNew(memberId, productId, quantity);
            return true;
         }

     public boolean addCartAndCheckNew(String memberId, String productId, int quantity) throws SQLException {
            return addCart(memberId, productId, quantity, false);
         }

     public boolean addSoldOutProduct(String memberId, String productId) throws SQLException {
            return addCart(memberId, productId, 1, true);
         }

     private boolean addCart(String memberId, String productId, int quantity, boolean preview) throws SQLException {

 		 	//수량이 1~99개 범위를 벗어나면 저장 중단
 		 	if(quantity <1 || quantity >99) {
 		 		throw new IllegalArgumentException(
 		 				"수량은 1~99개여야 합니다."
 		 				);
 		 	}
 		 	//db 연결을 가져옴
 		 	//try가 끝나면 연결은 자동으로 닫힘
 		 	try(Connection con =ds.getConnection()){
 		 		//db연결의 자동저장설정을 기억해두는 코드
 		 		boolean originalAutoCommit = con.getAutoCommit();
 		 		//db데이터를 변경할 수 있도록 읽기 전용으로 해제
 		 		con.setReadOnly(false);
 		 		//sql마다 바로 확정하지않고, 나중에 commit()또는 rollback()으로 처리하도록 설정
 		 		con.setAutoCommit(false);
 		 		
 		 		try {
 		 			//- 상품판매와 상태와 재고 확인
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
                        String status = rs.getString("status");
                        stockQuantity = rs.getInt("stock_quantity");
                    if (preview) {
                            if (!CartBean.isTemporarilySoldOut(status, stockQuantity)) {
                                throw new IllegalArgumentException("현재 품절인 상품만 미리 담을 수 있습니다. 상품 목록을 새로고침해 주세요.");
                            }
                        } else if (!"AVAILABLE".equals(status)) {
                            throw new IllegalArgumentException("판매중인 상품이 아닙니다.");
                        }
 		 				
 		 				}
 		 			}
                    //- 이미 장바구니에 담긴 수량 확인
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
                    if (preview) {
                        if (currentQuantity > 0) {
                            con.commit();
                            return false;
                        }
                        try (PreparedStatement save = con.prepareStatement(
                                "INSERT INTO cart_items (member_id, product_id, quantity) VALUES (?, ?, 1) "
                                + "ON DUPLICATE KEY UPDATE quantity = cart_items.quantity")) {
                            save.setString(1, memberId);
                            save.setString(2, productId);
                            save.executeUpdate();
                        }
                        con.commit();
                        return true;
                    }
 		 		// - 합산할 수량 확인
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

 		            // - 새 항목 추가 또는 기존 수량 합산
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

                    return currentQuantity == 0 && result > 0;

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
    public List<String> deleteCartItems(String memberId, long[] cartItemIds) throws SQLException {
        if (cartItemIds == null || cartItemIds.length == 0) {
            throw new IllegalArgumentException("삭제할 상품을 선택해 주세요.");
        }
        String where = " WHERE cart_item_id = ? AND member_id = ?";
        List<String> removed = new ArrayList<>();
        try (Connection con = ds.getConnection()) {
            boolean originalAutoCommit = con.getAutoCommit();
            con.setReadOnly(false);
            con.setAutoCommit(false);
            try (PreparedStatement find = con.prepareStatement(
                        "SELECT product_id FROM cart_items" + where + " FOR UPDATE");
                    PreparedStatement delete = con.prepareStatement("DELETE FROM cart_items" + where)) {
                find.setString(2, memberId);
                delete.setString(2, memberId);
                for (long cartItemId : java.util.Arrays.stream(cartItemIds).distinct().sorted().toArray()) {
                    find.setLong(1, cartItemId);
                    String productId;
                    try (ResultSet row = find.executeQuery()) {
                        if (!row.next()) continue;
                        productId = row.getString("product_id");
                    }
                    delete.setLong(1, cartItemId);
                    if (delete.executeUpdate() == 1) removed.add(productId);
                }
                con.commit();
                return removed;
            } catch (SQLException | RuntimeException error) {
                con.rollback();
                throw error;
            } finally {
                con.setAutoCommit(originalAutoCommit);
            }
        }
    }

 	 //매장픽업담기:매장 재고 확인 후 장바구니 수량 저장
    public boolean addPickupCart(String memberId, String storeId, String productId, int quantity) throws SQLException {
 	 			if(quantity<1||quantity>99) {
 	 				throw new IllegalArgumentException("수량은 1~99개로 입력해 주세요.");
 	 			}
 	 			String stockSql = 
 	 					"SELECT quantity FROM store_inventory "
 	 					+"WHERE store_id = ? AND product_id = ? AND is_carried = 1 "
 	 					+"FOR UPDATE";
 	 			String cartSql = 
 	 					"SELECT quantity FROM pickup_cart_items "
 	 					+"WHERE member_id = ? AND store_id = ? AND product_id = ? "
 	 					+"FOR UPDATE";
 	 			String saveSql = 
 	 					"INSERT INTO pickup_cart_items (member_id, store_id, product_id, quantity) "
 	 					+"VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE quantity = ?";
 	 		try (Connection con = ds.getConnection()){
 	 			boolean originalAutoCommit = con.getAutoCommit();
 	 			boolean originalReadOnly= con.isReadOnly();
 	 			con.setReadOnly(false);
 	 			con.setAutoCommit(false);
 	 			
 	 			try {
 	 				//같은 매장과 상품에 대한 재고 확인, 담기 요청을 순서대로 처리
 	 				int stock;
 	 				try (PreparedStatement ps = con.prepareStatement(stockSql)){
 	 					ps.setString(1, storeId);
 	 					ps.setString(2, productId);
 	 					
 	 					try (ResultSet rs = ps.executeQuery()){
 	 						if(!rs.next()) {
 	 							throw new IllegalArgumentException("이 매장에서 취급하는 상품을 찾을 수 없습니다.");
 	 							
 	 						}
 	 						stock = rs.getInt("quantity");
 	 					}
 	 				}
 	 				int currentQuantity = 0;
 	 				try(PreparedStatement ps = con.prepareStatement(cartSql)){
 	 					ps.setString(1, memberId);
 	 					ps.setString(2, storeId);
 	 					ps.setString(3, productId);
 	 					
 	 					try (ResultSet rs = ps.executeQuery()){
 	 						if(rs.next()) {
 	 							currentQuantity = rs.getInt("quantity");
 	 						}
 	 					}
 	 				}
 	 				
 	 				int totalQuantity = currentQuantity + quantity;
 	 				if(totalQuantity >99||totalQuantity>stock) {
 	 					throw new IllegalArgumentException(
 	 							"이미 담은 수량을 포함해 최대 " + Math.min(stock, 99)
 	 							+"개까지 담을 수 있습니다.");
 	 					
 	 				}
 	 				
 	 				try(PreparedStatement ps = con.prepareStatement(saveSql)){
 	 					ps.setString(1, memberId);
 	 					ps.setString(2, storeId);
 	 					ps.setString(3, productId);
 	 					ps.setInt(4, totalQuantity);
 	 					ps.setInt(5, totalQuantity);
 	 					ps.executeUpdate();
 	 				}
 	 				con.commit();
					return currentQuantity == 0;
 	 			} catch (SQLException | RuntimeException e) {
 	 				con.rollback();
 	 				throw e;
 	 			} finally {
 	 				con.setAutoCommit(originalAutoCommit);
 	 				con.setReadOnly(originalReadOnly);
 	 			}
 	 		}
 	 	}
 	 //로그인한 회원의 픽업상품을 매장별로 조회
 	 public List<CartBean> getPickupCartList(String memberId) throws SQLException{
 		 String sql =
 				 "SELECT c.store_id, store_name, c.product_id, "
 				 +"p.name AS product_name, p.price, p.image_url, c.quantity, "
 				 +"CASE WHEN i.is_carried = 1 THEN i.quantity ELSE 0 END AS pickup_stock "
 				 +"FROM pickup_cart_items c "
 				 +"JOIN store_inventory i "
 				 +"ON i.store_id = c.store_id AND i.product_id = c.product_id "
 				 +"JOIN store_locations s ON s.store_id =c.store_id "
 				 +"JOIN products p ON p.product_id = c.product_id "
 				 +"WHERE c. member_id = ? "
 				 +"ORDER BY s.store_name, c.store_id, p.name, c.product_id";
 		 List<CartBean> items = new ArrayList<>();
 		 
 		 try(Connection con = ds.getConnection();
 				 PreparedStatement ps = con.prepareStatement(sql)){
 			 ps.setString(1, memberId);
 			 
 			 try (ResultSet rs = ps.executeQuery()){
 				 while(rs.next()) {
 					 CartBean item = new CartBean();
 					 item.setStoreId(rs.getString("store_id"));
 					 item.setStoreName(rs.getString("store_name"));
 					 item.setProductId(rs.getString("product_id"));
 					 item.setProductName(rs.getString("product_name"));
 					 item.setPrice(rs.getInt("price"));
 					 item.setImageUrl(rs.getString("image_url"));
 					 item.setQuantity(rs.getInt("quantity"));
 					 item.setStockQuantity(rs.getInt("pickup_stock"));
 					 items.add(item);
 				 }
 			 }
 		 }
 		 return items;
 	 }
 	 //픽업장바구니에서 선택한 매장의 상품만 삭제
 	 public boolean deletePickupCart(String memberId, String storeId,String productId)
 	 		throws SQLException{
 		 String sql = "DELETE FROM pickup_cart_items "
 				 +"WHERE member_id = ? AND store_id = ? AND product_id =?";
 		 try(Connection con = ds.getConnection()){
 			 con.setReadOnly(false);
 			 
 			 try(PreparedStatement ps = con.prepareStatement(sql)){
 				 ps.setString(1, memberId);
 				 ps.setString(2, storeId);
 				 ps.setString(3,productId);
 				 
 				 boolean deleted = ps.executeUpdate()==1;
 				 if(!con.getAutoCommit()) {
 					 con.commit();
 				 }
 				 return deleted;
 			 }
 			 
 		 }
 	 }
 	 // ===== 픽업 상품 수량 변경: 선택한 매장의 재고 기준 =====
 	 public boolean updatePickupQuantity(String memberId, String storeId,
 	                                    String productId, int change)
 	        throws SQLException {
 	    if (change != -1 && change != 1) {
 	        throw new IllegalArgumentException("수량은 한 번에 1개씩 변경해 주세요.");
 	    }

 	    String sql =
 	            "UPDATE pickup_cart_items c "
 	            + "JOIN store_inventory i "
 	            + "ON i.store_id = c.store_id AND i.product_id = c.product_id "
 	            + "SET c.quantity = c.quantity + ? "
 	            + "WHERE c.member_id = ? AND c.store_id = ? AND c.product_id = ? "
 	            + "AND c.quantity + ? BETWEEN 1 AND 99 "
 	            + "AND (? = -1 OR (i.is_carried = 1 AND c.quantity + ? <= i.quantity))";

 	    try (Connection con = ds.getConnection()) {
 	        boolean originalReadOnly = con.isReadOnly();
 	        boolean autoCommit = con.getAutoCommit();
 	        con.setReadOnly(false);

 	        try (PreparedStatement ps = con.prepareStatement(sql)) {
 	            ps.setInt(1, change);
 	            ps.setString(2, memberId);
 	            ps.setString(3, storeId);
 	            ps.setString(4, productId);
 	            ps.setInt(5, change);
 	            ps.setInt(6, change);
 	            ps.setInt(7, change);

 	            boolean updated = ps.executeUpdate() == 1;
 	            if (!autoCommit) {
 	                con.commit();
 	            }
 	            return updated;
 	        } catch (SQLException e) {
 	            if (!autoCommit) {
 	                con.rollback();
 	            }
 	            throw e;
 	        } finally {
 	            con.setReadOnly(originalReadOnly);
 	        }
 	    }
 	}
 	}//cartdao끝
