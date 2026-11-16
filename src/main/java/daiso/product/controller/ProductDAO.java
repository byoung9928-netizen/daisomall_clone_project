
package daiso.product.controller;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ProductDAO {

	public List<ProductDTO> findAll(Connection conn) throws SQLException {

		// 1. 상품 목록을 저장할 리스트
		List<ProductDTO> list = new ArrayList<>();

		// 2. SQL 작성
		String sql = "SELECT product_id, name, price, " + "stock_quantity FROM products";

		// 3. SQL 실행 및 자원 정리
		try (PreparedStatement pstmt = conn.prepareStatement(sql); ResultSet rs = pstmt.executeQuery()) {

			// 4. 조회된 상품을 한 행씩 읽기
			while (rs.next()) {

				// 상품 한 개를 담을 DTO 생성
				ProductDTO product = new ProductDTO();

				// ResultSet 값을 DTO에 저장
				product.setProductId(rs.getString("product_id"));
				product.setName(rs.getString("name"));
				product.setPrice(rs.getInt("price"));
				product.setStockQuantity(rs.getInt("stock_quantity"));

				// 리스트에 상품 추가
				list.add(product);
			}
		}

		// 5. 조회된 상품 목록 반환
		return list;
	}

	public ProductDTO findById(Connection conn, String productId) throws SQLException {

		ProductDTO product = null;

		// 1. 상품 ID를 조건으로 조회하는 SQL 작성
		String sql = "SELECT product_id, name, price, " + "stock_quantity FROM products " + "WHERE product_Id = ?";

		// 2. PreparedStatement 생성
		try (PreparedStatement pstmt = conn.prepareStatement(sql)) {

			// 3. ?에 productId 값을 넣기
			pstmt.setString(1, productId);

			// 4. SQL 실행
			try (ResultSet rs = pstmt.executeQuery()) {

				if (rs.next()) {

					product = new ProductDTO();

					product.setProductId(rs.getString("product_id"));
					product.setName(rs.getString("name"));
					product.setPrice(rs.getInt("price"));
					product.setStockQuantity(rs.getInt("stock_quantity"));
				}
			}

		}
		return product;
	}

	public List<ProductDTO> searchProducts(Connection conn, String keyword) throws SQLException {

		List<ProductDTO> list = new ArrayList<>();

		// 1. SQL 작성
		String sql = "SELECT product_id, name, price, " + "stock_quantity FROM products " + "WHERE name LIKE ?";

		// 2. PreparedStatement 생성
		try (PreparedStatement pstmt = conn.prepareStatement(sql)) {

			// 3. 검색어 바인딩
			pstmt.setString(1, "%" + keyword + "%");

			// 4. ResultSet 처리
			try (ResultSet rs = pstmt.executeQuery()) {
				while (rs.next()) {
					ProductDTO product = new ProductDTO();

					// ResultSet 값을 DTO에 저장
					product.setProductId(rs.getString("product_id"));
					product.setName(rs.getString("name"));
					product.setPrice(rs.getInt("price"));
					product.setStockQuantity(rs.getInt("stock_quantity"));

					// 리스트에 상품 추가
					list.add(product);
				}
			}
		}

		return list;
	}

}
