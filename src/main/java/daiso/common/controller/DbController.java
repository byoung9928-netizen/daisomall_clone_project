package daiso.common.controller;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import javax.naming.InitialContext;
import javax.sql.DataSource;

import daiso.common.annotation.DaisoController;
import daiso.common.annotation.DaisoGetMapping;
import daiso.common.annotation.DaisoResponseBody;

@DaisoController
public class DbController {

    @DaisoGetMapping("/db-test")
    @DaisoResponseBody
    public String testConnection() {
        try {
            InitialContext context = new InitialContext();
            DataSource dataSource =
                (DataSource) context.lookup("java:comp/env/jdbc/mysqlDB");

            try (Connection connection = dataSource.getConnection();
                 Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery("SELECT 1")) {

                if (result.next() && result.getInt(1) == 1) {
                    return "DB 연결 성공";
                }
            }

            return "DB 연결 실패: SELECT 결과가 없습니다.";

        } catch (Exception e) {
            e.printStackTrace(); // 자세한 오류는 Eclipse 콘솔에서 확인
            return "DB 연결 실패: " + e.getClass().getSimpleName()
                + " - " + e.getMessage();
        }
    }
}