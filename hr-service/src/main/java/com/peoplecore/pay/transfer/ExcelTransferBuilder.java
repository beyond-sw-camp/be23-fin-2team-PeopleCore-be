package com.peoplecore.pay.transfer;

import com.peoplecore.pay.dtos.PayrollTransferDto;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Function;

/**
 * 은행별 대량이체 엑셀(.xlsx) 공통 빌더 - 제목/메타/헤더/데이터/합계 5단 + 셀서식
 *
 * 은행별 generator 가 컬럼 정의(label, getter)만 넘기면 워크북 생성:
 *   builder.build(transfers, "KB국민은행", "2026-05", List.of(
 *       new Column.Text("입금은행코드", PayrollTransferDto::getBankCode),
 *       new Column.Text("입금계좌번호", PayrollTransferDto::getAccountNumber),
 *       new Column.Text("예금주",       PayrollTransferDto::getEmpName),
 *       new Column.Numeric("이체금액",  PayrollTransferDto::getNetPay),
 *       new Column.Text("적요",         PayrollTransferDto::getMemo)
 *   ));
 */
public class ExcelTransferBuilder {

    public sealed interface Column permits Column.Text, Column.Numeric {
        String label();

        record Text(String label, Function<PayrollTransferDto, String> getter) implements Column {}
        record Numeric(String label, Function<PayrollTransferDto, Long> getter) implements Column {}
    }

    public byte[] build(List<PayrollTransferDto> transfers,
                        String bankDisplayName,
                        String payYearMonth,
                        List<Column> columns) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("대량이체");

            CellStyle titleStyle      = titleStyle(wb);
            CellStyle metaStyle       = metaStyle(wb);
            CellStyle headerStyle     = headerStyle(wb);
            CellStyle textStyle       = textStyle(wb);
            CellStyle numStyle        = numericStyle(wb);
            CellStyle totalLabelStyle = totalLabelStyle(wb);
            CellStyle totalNumStyle   = totalNumericStyle(wb);

            int colCount = columns.size();

            // 0행: 제목
            Row titleRow = sheet.createRow(0);
            titleRow.setHeightInPoints(24);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue(bankDisplayName + " 대량이체 신청서");
            titleCell.setCellStyle(titleStyle);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, colCount - 1));

            // 1행: 메타 (급여월/생성일)
            Row metaRow = sheet.createRow(1);
            Cell metaCell = metaRow.createCell(0);
            metaCell.setCellValue(String.format("급여월: %s    생성일: %s",
                    payYearMonth, LocalDate.now().format(DateTimeFormatter.ISO_DATE)));
            metaCell.setCellStyle(metaStyle);
            sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, colCount - 1));

            // 2행: 빈 행 (시각적 여백)

            // 3행: 헤더
            int headerRowIdx = 3;
            Row headerRow = sheet.createRow(headerRowIdx);
            for (int c = 0; c < colCount; c++) {
                Cell cell = headerRow.createCell(c);
                cell.setCellValue(columns.get(c).label());
                cell.setCellStyle(headerStyle);
            }

            // 4행~: 데이터
            int dataStart = headerRowIdx + 1;
            long totalAmount = 0L;
            int amountColIdx = findAmountColumnIndex(columns);
            for (int i = 0; i < transfers.size(); i++) {
                PayrollTransferDto t = transfers.get(i);
                Row row = sheet.createRow(dataStart + i);
                for (int c = 0; c < colCount; c++) {
                    Cell cell = row.createCell(c);
                    Column col = columns.get(c);
                    if (col instanceof Column.Numeric n) {
                        Long v = n.getter().apply(t);
                        long val = v != null ? v : 0L;
                        cell.setCellValue(val);
                        cell.setCellStyle(numStyle);
                        if (c == amountColIdx) {
                            totalAmount += val;
                        }
                    } else if (col instanceof Column.Text txt) {
                        String v = txt.getter().apply(t);
                        cell.setCellValue(v != null ? v : "");
                        cell.setCellStyle(textStyle);
                    }
                }
            }

            // 합계 행
            int totalRowIdx = dataStart + transfers.size();
            Row sumRow = sheet.createRow(totalRowIdx);
            // 합계 라벨: 0 ~ amountColIdx-1 까지 머지 (없으면 마지막 컬럼 직전까지)
            int labelEndCol = amountColIdx > 0 ? amountColIdx - 1 : colCount - 2;
            Cell labelCell = sumRow.createCell(0);
            labelCell.setCellValue("합계 (" + transfers.size() + "명)");
            labelCell.setCellStyle(totalLabelStyle);
            if (labelEndCol >= 1) {
                sheet.addMergedRegion(new CellRangeAddress(totalRowIdx, totalRowIdx, 0, labelEndCol));
            }
            // 머지된 영역 외 셀도 스타일 적용
            for (int c = 1; c <= labelEndCol; c++) {
                Cell c2 = sumRow.createCell(c);
                c2.setCellStyle(totalLabelStyle);
            }
            // 합계 셀
            if (amountColIdx >= 0) {
                Cell amtSum = sumRow.createCell(amountColIdx);
                amtSum.setCellValue(totalAmount);
                amtSum.setCellStyle(totalNumStyle);
                // 합계 셀 이후 컬럼은 빈 셀 + 스타일
                for (int c = amountColIdx + 1; c < colCount; c++) {
                    Cell c2 = sumRow.createCell(c);
                    c2.setCellStyle(totalLabelStyle);
                }
            }

            // 서버 컨테이너에 fontconfig가 없어도 동작하도록 자동 너비 대신 고정 너비 사용
            for (int c = 0; c < colCount; c++) {
                sheet.setColumnWidth(c, 22 * 256);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }

    private int findAmountColumnIndex(List<Column> cols) {
        for (int i = 0; i < cols.size(); i++) {
            if (cols.get(i) instanceof Column.Numeric) return i;
        }
        return -1;
    }

    private CellStyle titleStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setBold(true);
        f.setFontHeightInPoints((short) 14);
        s.setFont(f);
        s.setAlignment(HorizontalAlignment.CENTER);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        return s;
    }

    private CellStyle metaStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setFontHeightInPoints((short) 9);
        f.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
        s.setFont(f);
        s.setAlignment(HorizontalAlignment.LEFT);
        return s;
    }

    private CellStyle headerStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setBold(true);
        f.setColor(IndexedColors.WHITE.getIndex());
        s.setFont(f);
        s.setFillForegroundColor(IndexedColors.GREY_50_PERCENT.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setAlignment(HorizontalAlignment.CENTER);
        applyBorders(s);
        return s;
    }

    private CellStyle textStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        s.setAlignment(HorizontalAlignment.LEFT);
        applyBorders(s);
        return s;
    }

    private CellStyle numericStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        s.setAlignment(HorizontalAlignment.RIGHT);
        s.setDataFormat(wb.createDataFormat().getFormat("#,##0"));
        applyBorders(s);
        return s;
    }

    private CellStyle totalLabelStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setBold(true);
        s.setFont(f);
        s.setAlignment(HorizontalAlignment.RIGHT);
        s.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        applyBorders(s);
        return s;
    }

    private CellStyle totalNumericStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setBold(true);
        s.setFont(f);
        s.setAlignment(HorizontalAlignment.RIGHT);
        s.setDataFormat(wb.createDataFormat().getFormat("#,##0"));
        s.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        applyBorders(s);
        return s;
    }

    private void applyBorders(CellStyle s) {
        s.setBorderTop(BorderStyle.THIN);
        s.setBorderBottom(BorderStyle.THIN);
        s.setBorderLeft(BorderStyle.THIN);
        s.setBorderRight(BorderStyle.THIN);
    }
}
