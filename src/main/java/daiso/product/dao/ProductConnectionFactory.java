package daiso.product.dao;
import java.sql.Connection;
import java.sql.SQLException;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;

public final class ProductConnectionFactory {
    // 생성 제한
    private ProductConnectionFactory() { }

    // 상품 조회용 DB 연결과 일관된 조회 트랜잭션 설정
    public static Connection openQueryConnection() throws SQLException, NamingException {
        DataSource source = InitialContext.doLookup("java:comp/env/jdbc/mysqlDB");
        Connection conn = source.getConnection();
        try {
            conn.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            conn.setAutoCommit(false);
            return conn;
        } catch (SQLException error) {
            try {
                conn.close();
            } catch (SQLException closeError) {
                error.addSuppressed(closeError);
            }
            throw error;
        }
    }

    // 상품 등록과 수정 및 삭제용 DB 연결
    public static Connection openWriteConnection() throws SQLException, NamingException {
        DataSource source = InitialContext.doLookup("java:comp/env/jdbc/mysqlDB");
        return source.getConnection();
    }
}
