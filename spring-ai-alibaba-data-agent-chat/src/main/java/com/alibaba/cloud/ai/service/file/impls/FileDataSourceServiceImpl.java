/*
 * Copyright 2024-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.cloud.ai.service.file.impls;

import com.alibaba.cloud.ai.config.file.FileStorageProperties;
import com.alibaba.cloud.ai.entity.Datasource;
import com.alibaba.cloud.ai.pojo.ColumnInfo;
import com.alibaba.cloud.ai.service.file.FileDataSourceService;
import com.alibaba.cloud.ai.service.file.FileStorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * File data source service implementation
 */
@Slf4j
@Service
@AllArgsConstructor
public class FileDataSourceServiceImpl implements FileDataSourceService {

	private final FileStorageService fileStorageService;

	private final FileStorageProperties fileStorageProperties;

	private final ObjectMapper objectMapper;

	@Override
	public Datasource uploadAndCreateDatasource(MultipartFile file, String name, String description) {
		try {
			// Determine file type
			String originalFilename = file.getOriginalFilename();
			if (originalFilename == null) {
				throw new IllegalArgumentException("文件名不能为空");
			}

			String fileType = determineFileType(originalFilename);
			if (fileType == null) {
				throw new IllegalArgumentException("不支持的文件类型，仅支持 CSV 和 Excel 文件");
			}

			// Store file
			String filePath = fileStorageService.storeFile(file, "datasets");
			log.info("文件已存储: {}", filePath);

			// Parse schema
			List<ColumnInfo> columns = parseFileSchema(file, fileType);
			log.info("解析到 {} 个列", columns.size());

			// Create datasource entity
			Datasource datasource = Datasource.builder()
				.name(name)
				.type(fileType)
				.databaseName(name)
				.filePath(filePath)
				.fileType(fileType)
				.originalFilename(originalFilename)
				.status("active")
				.testStatus("success")
				.description(description)
				.build();

			return datasource;

		}
		catch (Exception e) {
			log.error("创建文件数据源失败", e);
			throw new RuntimeException("创建文件数据源失败: " + e.getMessage(), e);
		}
	}

	@Override
	public List<ColumnInfo> parseFileSchema(MultipartFile file, String fileType) {
		try {
			if ("csv".equalsIgnoreCase(fileType)) {
				return parseCsvSchema(file);
			}
			else if ("excel".equalsIgnoreCase(fileType)) {
				return parseExcelSchema(file);
			}
			else {
				throw new IllegalArgumentException("不支持的文件类型: " + fileType);
			}
		}
		catch (Exception e) {
			log.error("解析文件Schema失败", e);
			throw new RuntimeException("解析文件Schema失败: " + e.getMessage(), e);
		}
	}

	@Override
	public String getFilePreview(String filePath, String fileType, int limit) {
		try {
			Path fullPath = Paths.get(fileStorageProperties.getPath(), filePath);
			if (!Files.exists(fullPath)) {
				throw new FileNotFoundException("文件不存在: " + filePath);
			}

			List<Map<String, Object>> previewData;
			if ("csv".equalsIgnoreCase(fileType)) {
				previewData = readCsvData(fullPath.toFile(), limit);
			}
			else if ("excel".equalsIgnoreCase(fileType)) {
				previewData = readExcelData(fullPath.toFile(), limit);
			}
			else {
				throw new IllegalArgumentException("不支持的文件类型: " + fileType);
			}

			return objectMapper.writeValueAsString(previewData);

		}
		catch (Exception e) {
			log.error("获取文件预览失败", e);
			throw new RuntimeException("获取文件预览失败: " + e.getMessage(), e);
		}
	}

	/**
	 * Determine file type from filename
	 */
	private String determineFileType(String filename) {
		String lowerFilename = filename.toLowerCase();
		if (lowerFilename.endsWith(".csv")) {
			return "csv";
		}
		else if (lowerFilename.endsWith(".xlsx") || lowerFilename.endsWith(".xls")) {
			return "excel";
		}
		return null;
	}

	/**
	 * Parse CSV file schema
	 */
	private List<ColumnInfo> parseCsvSchema(MultipartFile file) throws IOException, CsvException {
		List<ColumnInfo> columns = new ArrayList<>();

		try (CSVReader reader = new CSVReader(new InputStreamReader(file.getInputStream()))) {
			String[] headers = reader.readNext();
			if (headers == null || headers.length == 0) {
				throw new IllegalArgumentException("CSV文件为空或没有表头");
			}

			// Read a few rows to infer types
			List<String[]> sampleRows = reader.readAll().stream().limit(100).toList();

			for (int i = 0; i < headers.length; i++) {
				String columnName = headers[i].trim();
				if (columnName.isEmpty()) {
					columnName = "column_" + (i + 1);
				}

				String columnType = inferColumnType(sampleRows, i);

				columns.add(ColumnInfo.builder()
					.columnName(columnName)
					.columnType(columnType)
					.columnIndex(i)
					.build());
			}
		}

		return columns;
	}

	/**
	 * Parse Excel file schema
	 */
	private List<ColumnInfo> parseExcelSchema(MultipartFile file) throws IOException {
		List<ColumnInfo> columns = new ArrayList<>();

		try (Workbook workbook = createWorkbook(file)) {
			Sheet sheet = workbook.getSheetAt(0);
			if (sheet == null || sheet.getPhysicalNumberOfRows() == 0) {
				throw new IllegalArgumentException("Excel文件为空");
			}

			Row headerRow = sheet.getRow(0);
			if (headerRow == null) {
				throw new IllegalArgumentException("Excel文件没有表头");
			}

			// Read sample rows for type inference
			List<Row> sampleRows = new ArrayList<>();
			int maxRows = Math.min(sheet.getPhysicalNumberOfRows(), 101);
			for (int i = 1; i < maxRows; i++) {
				Row row = sheet.getRow(i);
				if (row != null) {
					sampleRows.add(row);
				}
			}

			for (int i = 0; i < headerRow.getLastCellNum(); i++) {
				Cell cell = headerRow.getCell(i);
				String columnName = cell != null ? cell.toString().trim() : "";
				if (columnName.isEmpty()) {
					columnName = "column_" + (i + 1);
				}

				String columnType = inferExcelColumnType(sampleRows, i);

				columns.add(ColumnInfo.builder()
					.columnName(columnName)
					.columnType(columnType)
					.columnIndex(i)
					.build());
			}
		}

		return columns;
	}

	/**
	 * Infer column type from CSV sample data
	 */
	private String inferColumnType(List<String[]> sampleRows, int columnIndex) {
		boolean allNumeric = true;
		boolean allInteger = true;
		boolean allDate = true;

		for (String[] row : sampleRows) {
			if (columnIndex >= row.length) {
				continue;
			}

			String value = row[columnIndex].trim();
			if (value.isEmpty()) {
				continue;
			}

			// Check if numeric
			try {
				if (value.contains(".")) {
					Double.parseDouble(value);
					allInteger = false;
				}
				else {
					Long.parseLong(value);
				}
			}
			catch (NumberFormatException e) {
				allNumeric = false;
				allInteger = false;
			}

			// Simple date check (can be enhanced)
			if (!value.matches("\\d{4}-\\d{2}-\\d{2}.*") && !value.matches("\\d{2}/\\d{2}/\\d{4}.*")) {
				allDate = false;
			}
		}

		if (allInteger) {
			return "INTEGER";
		}
		else if (allNumeric) {
			return "DOUBLE";
		}
		else if (allDate) {
			return "DATE";
		}
		else {
			return "STRING";
		}
	}

	/**
	 * Infer column type from Excel sample data
	 */
	private String inferExcelColumnType(List<Row> sampleRows, int columnIndex) {
		// Similar logic to CSV but using Excel cell types
		// For simplicity, using basic type inference
		return "STRING"; // Can be enhanced with actual Excel cell type detection
	}

	/**
	 * Create workbook from file
	 */
	private Workbook createWorkbook(MultipartFile file) throws IOException {
		String filename = file.getOriginalFilename();
		if (filename != null && filename.toLowerCase().endsWith(".xlsx")) {
			return new XSSFWorkbook(file.getInputStream());
		}
		else {
			return new HSSFWorkbook(file.getInputStream());
		}
	}

	/**
	 * Read CSV data
	 */
	private List<Map<String, Object>> readCsvData(File file, int limit) throws IOException, CsvException {
		List<Map<String, Object>> data = new ArrayList<>();

		try (CSVReader reader = new CSVReader(new FileReader(file))) {
			String[] headers = reader.readNext();
			if (headers == null) {
				return data;
			}

			String[] row;
			int count = 0;
			while ((row = reader.readNext()) != null && count < limit) {
				Map<String, Object> rowData = new LinkedHashMap<>();
				for (int i = 0; i < headers.length && i < row.length; i++) {
					rowData.put(headers[i], row[i]);
				}
				data.add(rowData);
				count++;
			}
		}

		return data;
	}

	/**
	 * Read Excel data
	 */
	private List<Map<String, Object>> readExcelData(File file, int limit) throws IOException {
		List<Map<String, Object>> data = new ArrayList<>();

		try (FileInputStream fis = new FileInputStream(file);
				Workbook workbook = file.getName().toLowerCase().endsWith(".xlsx") ? new XSSFWorkbook(fis)
						: new HSSFWorkbook(fis)) {

			Sheet sheet = workbook.getSheetAt(0);
			Row headerRow = sheet.getRow(0);
			if (headerRow == null) {
				return data;
			}

			List<String> headers = new ArrayList<>();
			for (Cell cell : headerRow) {
				headers.add(cell.toString());
			}

			int count = 0;
			for (int i = 1; i <= sheet.getLastRowNum() && count < limit; i++) {
				Row row = sheet.getRow(i);
				if (row == null) {
					continue;
				}

				Map<String, Object> rowData = new LinkedHashMap<>();
				for (int j = 0; j < headers.size(); j++) {
					Cell cell = row.getCell(j);
					Object value = cell != null ? getCellValue(cell) : null;
					rowData.put(headers.get(j), value);
				}
				data.add(rowData);
				count++;
			}
		}

		return data;
	}

	/**
	 * Get cell value based on cell type
	 */
	private Object getCellValue(Cell cell) {
		switch (cell.getCellType()) {
			case STRING:
				return cell.getStringCellValue();
			case NUMERIC:
				if (DateUtil.isCellDateFormatted(cell)) {
					return cell.getDateCellValue();
				}
				return cell.getNumericCellValue();
			case BOOLEAN:
				return cell.getBooleanCellValue();
			case FORMULA:
				return cell.getCellFormula();
			default:
				return null;
		}
	}

}

