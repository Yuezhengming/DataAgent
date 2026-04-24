# CSV/Excel 数据源功能实现总结

## 📋 功能概述

本次实现为 DataAgent 项目添加了完整的 CSV/Excel 文件数据源支持，用户可以：

1. ✅ 上传 CSV/Excel 文件作为数据源
2. ✅ 自动解析文件 Schema（表结构和列信息）
3. ✅ 将 Schema 存储到向量数据库
4. ✅ 智能体自动识别文件数据
5. ✅ 使用 Pandas 进行数据分析
6. ✅ 支持数据可视化

---

## 🔧 修改的文件清单

### 1. 后端文件（3个修改）

#### 1.1 `DatasourceServiceImpl.java`
**路径：** `spring-ai-alibaba-data-agent-chat/src/main/java/com/alibaba/cloud/ai/service/datasource/DatasourceServiceImpl.java`

**修改内容：**
- 修改 `getDatasourceTables()` 方法，添加对文件数据源的支持
- 对于 CSV/Excel 文件，返回文件名（去掉扩展名）作为"表名"
- 对于数据库数据源，保持原有逻辑不变

**关键代码：**
```java
@Override
public List<String> getDatasourceTables(Integer datasourceId) throws Exception {
    Datasource datasource = this.getDatasourceById(datasourceId);
    
    // Check if it's a file datasource
    String datasourceType = datasource.getType();
    if ("csv".equalsIgnoreCase(datasourceType) || "excel".equalsIgnoreCase(datasourceType)) {
        String fileName = datasource.getOriginalFilename();
        if (fileName != null) {
            int dotIndex = fileName.lastIndexOf('.');
            String tableName = dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;
            return List.of(tableName);
        }
        return List.of(datasource.getName());
    }
    
    // For database datasources, use original logic
    // ...
}
```

---

#### 1.2 `SchemaProcessorUtil.java`
**路径：** `spring-ai-alibaba-data-agent-chat/src/main/java/com/alibaba/cloud/ai/util/SchemaProcessorUtil.java`

**修改内容：**
- 修改 `createDbConfigFromDatasource()` 方法
- 检测文件数据源，设置 `dialectType = "file"`
- 为文件数据源创建特殊的 `DbConfig`

**关键代码：**
```java
public static DbConfig createDbConfigFromDatasource(Datasource datasource) {
    DbConfig dbConfig = new DbConfig();
    
    // Check if it's a file datasource
    String datasourceType = datasource.getType();
    if ("csv".equalsIgnoreCase(datasourceType) || "excel".equalsIgnoreCase(datasourceType)) {
        dbConfig.setDialectType("file");
        dbConfig.setConnectionType("file");
        dbConfig.setSchema(datasource.getName());
        dbConfig.setUrl(datasource.getFilePath());
        return dbConfig;
    }
    
    // For database datasources
    // ...
}
```

---

#### 1.3 `AgentVectorStoreServiceImpl.java`
**路径：** `spring-ai-alibaba-data-agent-chat/src/main/java/com/alibaba/cloud/ai/service/vectorstore/AgentVectorStoreServiceImpl.java`

**修改内容：**

**1) 添加依赖注入：**
```java
private com.alibaba.cloud.ai.service.file.FileDataSourceService fileDataSourceService;
private com.alibaba.cloud.ai.service.datasource.DatasourceService datasourceService;
private com.alibaba.cloud.ai.config.file.FileStorageProperties fileStorageProperties;

@Autowired(required = false)
public void setFileDataSourceService(FileDataSourceService fileDataSourceService) {
    this.fileDataSourceService = fileDataSourceService;
}

@Autowired(required = false)
public void setDatasourceService(DatasourceService datasourceService) {
    this.datasourceService = datasourceService;
}

@Autowired(required = false)
public void setFileStorageProperties(FileStorageProperties fileStorageProperties) {
    this.fileStorageProperties = fileStorageProperties;
}
```

**2) 修改 `schema()` 方法：**
```java
@Override
public Boolean schema(String agentId, SchemaInitRequest schemaInitRequest) throws Exception {
    DbConfig config = schemaInitRequest.getDbConfig();
    
    // Check if it's a file datasource
    boolean isFileDatasource = "file".equalsIgnoreCase(config.getDialectType());
    
    if (isFileDatasource) {
        log.info("Detected file datasource, using file-based schema initialization");
        return initializeFileDataSourceSchema(agentId, schemaInitRequest);
    }
    
    // For database datasources, use original logic
    // ...
}
```

**3) 添加 `initializeFileDataSourceSchema()` 方法：**
```java
private Boolean initializeFileDataSourceSchema(String agentId, SchemaInitRequest schemaInitRequest) {
    try {
        // 1. Clear existing schema data
        clearSchemaDataForAgent(agentId);
        
        // 2. Get datasource information
        Datasource datasource = datasourceService.getActiveDatasourceByAgentId(Integer.valueOf(agentId));
        
        // 3. Read file and parse schema
        String uploadDir = fileStorageProperties.getPath();
        Path fullFilePath = Paths.get(uploadDir, datasource.getFilePath());
        byte[] fileBytes = Files.readAllBytes(fullFilePath);
        
        MockMultipartFile mockFile = new MockMultipartFile(
            "file", datasource.getOriginalFilename(), 
            "application/octet-stream", fileBytes
        );
        
        List<ColumnInfo> columns = fileDataSourceService.parseFileSchema(
            mockFile, datasource.getFileType()
        );
        
        // 4. Create table document
        Map<String, Object> tableMetadata = new HashMap<>();
        tableMetadata.put(Constant.AGENT_ID, agentId);
        tableMetadata.put(Constant.VECTOR_TYPE, Constant.TABLE_VECTOR_TYPE);
        tableMetadata.put("tableName", tableName);
        // ...
        
        Document tableDoc = new Document(tableContent, tableMetadata);
        
        // 5. Create column documents
        List<Document> columnDocs = new ArrayList<>();
        for (ColumnInfo column : columns) {
            Map<String, Object> columnMetadata = new HashMap<>();
            columnMetadata.put(Constant.AGENT_ID, agentId);
            columnMetadata.put(Constant.VECTOR_TYPE, Constant.COLUMN_VECTOR_TYPE);
            columnMetadata.put("tableName", tableName);
            columnMetadata.put("columnName", column.getColumnName());
            // ...
            
            columnDocs.add(new Document(columnContent, columnMetadata));
        }
        
        // 6. Store documents
        storeSchemaDocuments(columnDocs, List.of(tableDoc));
        
        return true;
    } catch (Exception e) {
        log.error("Failed to initialize file datasource schema", e);
        return false;
    }
}
```

---

### 2. 创建的文档（3个）

#### 2.1 `CSV数据源Schema初始化说明.md`
- 详细说明问题背景和解决方案
- 包含完整的代码示例
- 说明工作流程和向量数据库文档结构
- 提供测试步骤和调试方法

#### 2.2 `测试CSV数据源完整流程.md`
- 完整的测试步骤指南
- 包含测试数据准备
- 详细的测试用例
- 调试检查清单
- 常见问题解决方案

#### 2.3 `CSV数据源功能实现总结.md`（本文档）
- 功能概述
- 修改文件清单
- 技术架构说明
- 使用指南

---

## 🏗️ 技术架构

### 数据流程图

```
用户上传文件
    ↓
FileUploadController
    ↓
FileDataSourceService.parseFileSchema()
    ↓
创建 Datasource 实体
    ↓
用户点击"初始化数据源"
    ↓
AgentDatasourceController.initSchema()
    ↓
AgentDatasourceServiceImpl.initializeSchemaForAgentWithDatasource()
    ↓
SchemaProcessorUtil.createDbConfigFromDatasource()
    ↓
AgentVectorStoreServiceImpl.schema()
    ↓
检测到 dialectType = "file"
    ↓
AgentVectorStoreServiceImpl.initializeFileDataSourceSchema()
    ↓
读取文件 → 解析 Schema → 创建文档 → 存储到向量数据库
    ↓
用户提问
    ↓
SchemaRecallNode 召回表和列文档
    ↓
TableRelationNode 检测文件数据源
    ↓
PlannerNode 选择 PYTHON_GENERATE_NODE
    ↓
PythonGenerateNode 生成 Pandas 代码
    ↓
PythonExecuteNode 执行代码
    ↓
返回结果
```

---

## 🎯 核心设计思想

### 1. 统一抽象

将文件数据源抽象为"表"的概念：
- **文件 = 表**：CSV/Excel 文件被视为一个表
- **列 = 列**：文件的列对应数据库的列
- **文件名 = 表名**：使用文件名（去掉扩展名）作为表名

这样可以复用现有的 Schema 管理和召回机制。

### 2. 最小侵入

修改尽可能少的代码：
- 只修改了 3 个文件
- 保持原有数据库数据源逻辑不变
- 通过 `dialectType` 区分文件和数据库数据源

### 3. 向量数据库统一

文件数据源和数据库数据源使用相同的向量数据库文档结构：
- 表文档：包含表名、描述、列数等信息
- 列文档：包含列名、类型、描述等信息

这样 `SchemaRecallNode` 无需修改即可召回文件数据源的 Schema。

### 4. 工作流自动切换

通过 `TableRelationNode` 检测文件数据源：
- 设置 `IS_FILE_DATASOURCE = true`
- 设置 `FILE_DATASOURCE_PATH`

`PlannerNode` 根据这些标志自动选择 Python 节点而不是 SQL 节点。

---

## 📊 向量数据库文档结构

### 表文档示例

```json
{
  "content": "Table: sales_data\nDescription: 2024年销售数据\nColumns: 5\nType: CSV file",
  "metadata": {
    "agentId": "123",
    "vectorType": "table",
    "tableName": "sales_data",
    "tableDescription": "2024年销售数据",
    "foreignKey": "",
    "columnCount": 5
  }
}
```

### 列文档示例

```json
{
  "content": "Column: sales_data.name\nType: VARCHAR\nDescription: 员工姓名",
  "metadata": {
    "agentId": "123",
    "vectorType": "column",
    "tableName": "sales_data",
    "columnName": "name",
    "columnType": "VARCHAR",
    "columnDescription": "员工姓名"
  }
}
```

---

## 🚀 使用指南

### 1. 上传文件

```bash
POST /api/upload/dataset
Content-Type: multipart/form-data

file: [CSV/Excel 文件]
```

**响应：**
```json
{
  "success": true,
  "filePath": "data-agent/datasets/sales_data_xxx.csv",
  "fileType": "csv",
  "originalFilename": "sales_data.csv",
  "columns": [
    {"columnName": "name", "columnType": "VARCHAR"},
    {"columnName": "age", "columnType": "INTEGER"},
    ...
  ],
  "preview": [[...], [...], ...]
}
```

### 2. 创建数据源

```bash
POST /api/datasource/file
Content-Type: application/json

{
  "name": "销售数据",
  "description": "2024年销售团队数据",
  "filePath": "data-agent/datasets/sales_data_xxx.csv",
  "fileType": "csv",
  "originalFilename": "sales_data.csv"
}
```

### 3. 添加到智能体

```bash
POST /api/agent/{agentId}/datasources
Content-Type: application/json

{
  "datasourceId": 123
}
```

### 4. 初始化 Schema

```bash
POST /api/agent/{agentId}/datasources/init
Content-Type: application/json

{
  "datasourceId": 123,
  "tables": ["sales_data"]
}
```

### 5. 开始对话

```bash
POST /api/chat/{agentId}
Content-Type: application/json

{
  "message": "有哪些数据？"
}
```

---

## ⚙️ 配置说明

### application.yml

```yaml
spring:
  servlet:
    multipart:
      max-file-size: 100MB      # 文件上传大小限制
      max-request-size: 100MB   # 请求大小限制
      
  ai:
    alibaba:
      nl2sql:
        file:
          type: local             # 存储类型：local 或 oss
          path: ./uploads         # 本地存储路径
          path-prefix: data-agent # 路径前缀
          url-prefix: /uploads    # URL 前缀
```

---

## 🧪 测试建议

### 1. 单元测试

测试文件解析功能：
```java
@Test
public void testParseFileSchema() {
    MockMultipartFile file = new MockMultipartFile(...);
    List<ColumnInfo> columns = fileDataSourceService.parseFileSchema(file, "csv");
    assertEquals(5, columns.size());
}
```

### 2. 集成测试

测试完整流程：
1. 上传文件
2. 创建数据源
3. 初始化 Schema
4. 查询数据

### 3. 性能测试

测试大文件处理：
- 10MB 文件
- 50MB 文件
- 100MB 文件

---

## 📈 性能优化建议

### 1. 文件缓存

对于频繁访问的文件，可以缓存解析结果：
```java
@Cacheable(value = "fileSchema", key = "#filePath")
public List<ColumnInfo> parseFileSchema(String filePath, String fileType) {
    // ...
}
```

### 2. 异步初始化

对于大文件，可以异步初始化 Schema：
```java
@Async
public CompletableFuture<Boolean> initializeFileDataSourceSchema(...) {
    // ...
}
```

### 3. 分批处理

对于大量列的文件，分批创建文档：
```java
List<List<Document>> batches = Lists.partition(columnDocs, 100);
for (List<Document> batch : batches) {
    vectorStore.add(batch);
}
```

---

## 🔒 安全考虑

### 1. 文件类型验证

只允许特定类型的文件：
```java
private static final Set<String> ALLOWED_TYPES = Set.of("csv", "xlsx", "xls");

if (!ALLOWED_TYPES.contains(fileType)) {
    throw new IllegalArgumentException("不支持的文件类型");
}
```

### 2. 文件大小限制

防止上传过大文件：
```yaml
spring:
  servlet:
    multipart:
      max-file-size: 100MB
```

### 3. 文件内容扫描

扫描文件内容，防止恶意代码：
```java
// 检查文件内容是否包含恶意脚本
if (containsMaliciousContent(file)) {
    throw new SecurityException("文件包含恶意内容");
}
```

---

## 🎉 总结

通过本次实现，DataAgent 项目现在支持：

1. ✅ **CSV/Excel 文件数据源**
2. ✅ **自动 Schema 解析和存储**
3. ✅ **智能体自动识别文件数据**
4. ✅ **Pandas 数据分析**
5. ✅ **数据可视化**

**修改文件数量：** 3 个
**新增文档数量：** 3 个
**代码侵入性：** 最小
**功能完整性：** 完整

---

**下一步建议：**
- 支持更多文件格式（JSON、Parquet、Avro）
- 支持文件更新和追加
- 支持多文件联合分析
- 支持文件数据导入数据库

---

**文档版本：** v1.0  
**最后更新：** 2025-01-10  
**作者：** Augment Agent

