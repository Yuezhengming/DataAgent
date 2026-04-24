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

package com.alibaba.cloud.ai.service.file;

import com.alibaba.cloud.ai.entity.Datasource;
import com.alibaba.cloud.ai.pojo.ColumnInfo;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * File data source service interface
 */
public interface FileDataSourceService {

	/**
	 * Upload file and create datasource
	 * @param file uploaded file
	 * @param name datasource name
	 * @param description datasource description
	 * @return created datasource
	 */
	Datasource uploadAndCreateDatasource(MultipartFile file, String name, String description);

	/**
	 * Parse file and extract schema information
	 * @param file uploaded file
	 * @param fileType file type (csv or excel)
	 * @return list of column information
	 */
	List<ColumnInfo> parseFileSchema(MultipartFile file, String fileType);

	/**
	 * Get file data preview
	 * @param filePath file path
	 * @param fileType file type
	 * @param limit number of rows to preview
	 * @return preview data as JSON string
	 */
	String getFilePreview(String filePath, String fileType, int limit);

}

