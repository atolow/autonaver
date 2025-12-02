package com.example.auto.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * 엑셀 파일 읽기 및 파싱 서비스
 */
@Slf4j
@Service
public class ExcelService {
    
    /**
     * 엑셀 파일을 읽어서 행별 데이터를 Map 리스트로 반환
     * 
     * @param file 엑셀 파일 (MultipartFile)
     * @return 행별 데이터 리스트 (첫 번째 행은 헤더, 제외)
     */
    public List<Map<String, Object>> parseExcelFile(MultipartFile file) throws IOException {
        log.info("엑셀 파일 파싱 시작: fileName={}, size={}", file.getOriginalFilename(), file.getSize());
        
        List<Map<String, Object>> rows = new ArrayList<>();
        
        try (InputStream inputStream = file.getInputStream();
             Workbook workbook = new XSSFWorkbook(inputStream)) {
            
            Sheet sheet = workbook.getSheetAt(0); // 첫 번째 시트 사용
            log.info("엑셀 시트 이름: {}, 총 행 수: {}", sheet.getSheetName(), sheet.getLastRowNum() + 1);
            
            if (sheet.getLastRowNum() < 1) {
                log.warn("엑셀 파일에 데이터가 없습니다.");
                return rows;
            }
            
            // 첫 번째 행은 헤더
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw new IllegalArgumentException("엑셀 파일의 첫 번째 행(헤더)이 비어있습니다.");
            }
            
            // 헤더 컬럼명 추출
            List<String> headers = new ArrayList<>();
            for (int i = 0; i < headerRow.getLastCellNum(); i++) {
                Cell cell = headerRow.getCell(i);
                String headerName = getCellValueAsString(cell);
                headers.add(headerName != null ? headerName.trim() : "");
            }
            
            log.info("엑셀 헤더 컬럼 수: {}", headers.size());
            log.debug("헤더 컬럼명: {}", headers);
            
            // 데이터 행 읽기 (두 번째 행부터)
            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) {
                    continue; // 빈 행 건너뛰기
                }
                
                // 빈 행 체크 (모든 셀이 비어있으면 건너뛰기)
                boolean isEmptyRow = true;
                for (int i = 0; i < headers.size(); i++) {
                    Cell cell = row.getCell(i);
                    if (cell != null && !getCellValueAsString(cell).trim().isEmpty()) {
                        isEmptyRow = false;
                        break;
                    }
                }
                if (isEmptyRow) {
                    continue;
                }
                
                // 행 데이터를 Map으로 변환
                Map<String, Object> rowData = new LinkedHashMap<>();
                rowData.put("_rowNumber", rowIndex + 1); // 엑셀 행 번호 (1부터 시작)
                
                for (int colIndex = 0; colIndex < headers.size(); colIndex++) {
                    String headerName = headers.get(colIndex);
                    if (headerName == null || headerName.trim().isEmpty()) {
                        continue; // 헤더명이 없으면 건너뛰기
                    }
                    
                    Cell cell = row.getCell(colIndex);
                    Object cellValue = getCellValue(cell);
                    rowData.put(headerName.trim(), cellValue);
                }
                
                rows.add(rowData);
            }
            
            log.info("엑셀 파일 파싱 완료: 총 {}개 행 파싱됨", rows.size());
            
        } catch (IOException e) {
            log.error("엑셀 파일 읽기 실패: {}", file.getOriginalFilename(), e);
            throw e;
        }
        
        return rows;
    }
    
    /**
     * 셀 값을 Object로 반환
     */
    private Object getCellValue(Cell cell) {
        if (cell == null) {
            return null;
        }
        
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue();
                } else {
                    double numericValue = cell.getNumericCellValue();
                    // 정수인지 확인
                    if (numericValue == Math.floor(numericValue)) {
                        return (long) numericValue;
                    } else {
                        return numericValue;
                    }
                }
            case BOOLEAN:
                return cell.getBooleanCellValue();
            case FORMULA:
                // 수식 셀의 경우 계산된 값을 반환
                try {
                    if (cell.getCachedFormulaResultType() == CellType.NUMERIC) {
                        double numericValue = cell.getNumericCellValue();
                        if (numericValue == Math.floor(numericValue)) {
                            return (long) numericValue;
                        } else {
                            return numericValue;
                        }
                    } else if (cell.getCachedFormulaResultType() == CellType.STRING) {
                        return cell.getStringCellValue().trim();
                    } else if (cell.getCachedFormulaResultType() == CellType.BOOLEAN) {
                        return cell.getBooleanCellValue();
                    }
                } catch (Exception e) {
                    log.warn("수식 셀 값 계산 실패: {}", e.getMessage());
                }
                return cell.getCellFormula();
            case BLANK:
                return null;
            default:
                return null;
        }
    }
    
    /**
     * 셀 값을 String으로 반환
     */
    private String getCellValueAsString(Cell cell) {
        if (cell == null) {
            return "";
        }
        
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                } else {
                    double numericValue = cell.getNumericCellValue();
                    // 정수인지 확인
                    if (numericValue == Math.floor(numericValue)) {
                        return String.valueOf((long) numericValue);
                    } else {
                        return String.valueOf(numericValue);
                    }
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try {
                    if (cell.getCachedFormulaResultType() == CellType.NUMERIC) {
                        double numericValue = cell.getNumericCellValue();
                        if (numericValue == Math.floor(numericValue)) {
                            return String.valueOf((long) numericValue);
                        } else {
                            return String.valueOf(numericValue);
                        }
                    } else if (cell.getCachedFormulaResultType() == CellType.STRING) {
                        return cell.getStringCellValue().trim();
                    } else if (cell.getCachedFormulaResultType() == CellType.BOOLEAN) {
                        return String.valueOf(cell.getBooleanCellValue());
                    }
                } catch (Exception e) {
                    log.warn("수식 셀 값 계산 실패: {}", e.getMessage());
                }
                return cell.getCellFormula();
            case BLANK:
                return "";
            default:
                return "";
        }
    }
    
    /**
     * 상품 업로드용 엑셀 템플릿 파일 생성
     * 
     * @return 엑셀 파일 바이트 배열
     * @throws IOException 파일 생성 실패 시
     */
    public byte[] createProductTemplate() throws IOException {
        log.info("엑셀 템플릿 파일 생성 시작");
        
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            
            Sheet sheet = workbook.createSheet("상품 업로드");
            
            // 헤더 스타일 생성
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setFontHeightInPoints((short) 12);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            
            // 데이터 스타일 생성
            CellStyle dataStyle = workbook.createCellStyle();
            dataStyle.setBorderBottom(BorderStyle.THIN);
            dataStyle.setBorderTop(BorderStyle.THIN);
            dataStyle.setBorderLeft(BorderStyle.THIN);
            dataStyle.setBorderRight(BorderStyle.THIN);
            
            // 헤더 행 생성
            Row headerRow = sheet.createRow(0);
            String[] headers = {
                    "상품ID(내부용)",
                    "상품명",
                    "카테고리",
                    "판매가",
                    "재고수량",
                    "옵션명1",
                    "옵션값1",
                    "옵션명2",
                    "옵션값2",
                    "브랜드",
                    "모델명",
                    "바코드",
                    "상세설명",
                    "대표이미지URL",
                    "추가이미지URL1",
                    "배송비",
                    "배송방법",
                    "원산지",
                    "제조사",
                    "과세구분",
                    "판매상태",
                    "전시상태",
                    "guideId"
            };
            
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }
            
            // 샘플 데이터 행 생성 (예시)
            Row sampleRow = sheet.createRow(1);
            String[] sampleData = {
                    "PROD001",
                    "샘플 상품명",
                    "50000008", // 카테고리 ID 예시
                    "10000",
                    "100",
                    "색상", // 옵션명1
                    "빨강", // 옵션값1
                    "사이즈", // 옵션명2
                    "L", // 옵션값2
                    "샘플 브랜드",
                    "모델명-001",
                    "1234567890123",
                    "상세 설명을 입력하세요",
                    "https://example.com/image1.jpg",
                    "https://example.com/image2.jpg",
                    "3000",
                    "DELIVERY",
                    "국내산",
                    "제조사명",
                    "TAX",
                    "SALE",
                    "ON",
                    "" // guideId는 카테고리로 조회 필요
            };
            
            for (int i = 0; i < sampleData.length; i++) {
                Cell cell = sampleRow.createCell(i);
                cell.setCellValue(sampleData[i]);
                cell.setCellStyle(dataStyle);
            }
            
            // 컬럼 너비 자동 조정
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
                // 최소 너비 설정
                int columnWidth = sheet.getColumnWidth(i);
                if (columnWidth < 3000) {
                    sheet.setColumnWidth(i, 3000);
                } else if (columnWidth > 15000) {
                    sheet.setColumnWidth(i, 15000);
                }
            }
            
            workbook.write(outputStream);
            byte[] bytes = outputStream.toByteArray();
            
            log.info("엑셀 템플릿 파일 생성 완료: {} bytes", bytes.length);
            
            return bytes;
        }
    }
}

