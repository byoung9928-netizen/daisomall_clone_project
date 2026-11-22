package daiso.admin.report;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.Map;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xddf.usermodel.chart.AxisPosition;
import org.apache.poi.xddf.usermodel.chart.BarDirection;
import org.apache.poi.xddf.usermodel.chart.ChartTypes;
import org.apache.poi.xddf.usermodel.chart.XDDFBarChartData;
import org.apache.poi.xddf.usermodel.chart.XDDFDataSourcesFactory;
import org.apache.poi.xssf.usermodel.XSSFChart;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

final class AdminExcelWriter {
    byte[] write(AdminReport report) throws IOException {
        try (XSSFWorkbook book = new XSSFWorkbook();
             ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            writeData(book, report);
            writeChart(book, report);
            book.write(bytes);
            return bytes.toByteArray();
        }
    }

    // ===== 첫 번째 시트: 필터에 맞는 전체 데이터 =====
    private void writeData(XSSFWorkbook book, AdminReport report) {
        XSSFSheet sheet = book.createSheet("데이터");
        Font bold = book.createFont();
        bold.setBold(true);
        CellStyle headerStyle = book.createCellStyle();
        headerStyle.setFont(bold);
        CellStyle dateStyle = book.createCellStyle();
        dateStyle.setDataFormat(book.createDataFormat().getFormat("yyyy-mm-dd hh:mm"));
        Row header = sheet.createRow(0);
        for (int i = 0; i < report.headers.size(); i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(report.headers.get(i));
            cell.setCellStyle(headerStyle);
            sheet.setColumnWidth(i, 24 * 256);
        }
        int index = 1;
        for (Object[] values : report.rows) {
            Row row = sheet.createRow(index++);
            for (int i = 0; i < values.length; i++) {
                Object value = values[i];
                if (value == null) continue;
                Cell cell = row.createCell(i);
                if (value instanceof Timestamp) {
                    cell.setCellValue(((Timestamp) value).toLocalDateTime());
                    cell.setCellStyle(dateStyle);
                } else if (value instanceof Number) {
                    cell.setCellValue(((Number) value).doubleValue());
                } else {
                    cell.setCellValue(value.toString());
                }
            }
        }
        sheet.createFreezePane(0, 1);
        sheet.setAutoFilter(new CellRangeAddress(0, index - 1, 0, report.headers.size() - 1));
    }

    // ===== 두 번째 시트: 집계표와 Excel에서 편집할 수 있는 차트 =====
    private void writeChart(XSSFWorkbook book, AdminReport report) {
        XSSFSheet sheet = book.createSheet("차트");
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue(report.categoryLabel);
        header.createCell(1).setCellValue(report.valueLabel);
        sheet.setColumnWidth(0, 34 * 256);
        sheet.setColumnWidth(1, 16 * 256);
        if (report.chart.isEmpty()) {
            sheet.createRow(1).createCell(0).setCellValue("조회 조건에 맞는 데이터가 없습니다.");
            return;
        }
        int index = 1;
        for (Map.Entry<String, Long> entry : report.chart.entrySet()) {
            Row row = sheet.createRow(index++);
            row.createCell(0).setCellValue(entry.getKey());
            row.createCell(1).setCellValue(entry.getValue());
        }
        XSSFDrawing drawing = sheet.createDrawingPatriarch();
        XSSFChart chart = drawing.createChart(drawing.createAnchor(0, 0, 0, 0, 3, 1, 15, 23));
        chart.setTitleText(report.chartTitle);
        XDDFBarChartData data = (XDDFBarChartData) chart.createData(ChartTypes.BAR,
                chart.createCategoryAxis(AxisPosition.BOTTOM), chart.createValueAxis(AxisPosition.LEFT));
        data.setBarDirection(BarDirection.COL);
        data.addSeries(XDDFDataSourcesFactory.fromStringCellRange(sheet,
                    new CellRangeAddress(1, index - 1, 0, 0)),
                XDDFDataSourcesFactory.fromNumericCellRange(sheet,
                    new CellRangeAddress(1, index - 1, 1, 1))).setTitle(report.valueLabel, null);
        chart.plot(data);
    }
}
