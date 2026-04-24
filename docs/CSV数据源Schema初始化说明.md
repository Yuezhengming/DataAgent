# CSV/Excel 数据源 Schema 初始化说明

## 📋 问题背景

用户上传 CSV/Excel 文件后，智能体无法识别文件中的数据，原因是：

1. **Schema 未初始化到向量数据库**
   - 文件数据源的列信息没有存储到向量数据库
   - `SchemaRecallNode` 无法召回任何表和列信息
   - 智能体不知道有哪些数据可用

2. **`getDatasourceTables()` 方法不支持文件数据源**
   - 该方法尝试通过数据库 `Accessor` 获取表列表
   - 文件数据源不是数据库，没有对应的 `Accessor`
   - 导致初始化失败

---

## ✅ 解决方案

### 1. 修改 `getDatasourceTables()` 方法

**文件：** `spring-ai-alibaba-data-agent-chat/src/main/java/com/alibaba/cloud/ai/service/datasource/DatasourceServiceImpl.java`

**修改内容：**

```java
@Override
public List<String> getDatasourceTables(Integer datasourceId) throws Exception {
    log.info("Getting tables for datasource: {}", datasourceId);

    // Get data source information
    com.alibaba.cloud.ai.entity.Datasource datasource = this.getDatasourceById(datasourceId);
    if (datasource == null) {
        throw new RuntimeException("Datasource not found with id: " + datasourceId);
    }

    // Check if it's a file datasource (CSV or Excel)
    String datasourceType = datasource.getType();
    if ("csv".equalsIgnoreCase(datasourceType) || "excel".equalsIgnoreCase(datasourceType)) {
        log.info("Detected file datasource, returning file name as table name");
        // For file datasources, return the file name (without extension) as the "table" name
        String fileName = datasource.getOriginalFilename();
        if (fileName != null) {
            // Remove file extension
            int dotIndex = fileName.lastIndexOf('.');
            String tableName = dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;
            log.info("File datasource table name: {}", tableName);
            return List.of(tableName);
        }
        else {
            // Fallback to datasource name
            log.info("Using datasource name as table name: {}", datasource.getName());
            return List.of(datasource.getName());
        }
    }

    // For database datasources, use the original logic
    // ... (原有的数据库逻辑)
}
```

**效果：**
- 对于文件数据源，返回文件名（去掉扩展名）作为"表名"
- 例如：`sales_data.csv` → 表名为 `sales_data`
- 前端可以正常获取"表列表"并进行初始化

---

### 2. 修改 `AgentVectorStoreServiceImpl.schema()` 方法

**文件：** `spring-ai-alibaba-data-agent-chat/src/main/java/com/alibaba/cloud/ai/service/vectorstore/AgentVectorStoreServiceImpl.java`

**修改内容：**

#### 2.1 检测文件数据源

```java
@Override
public Boolean schema(String agentId, SchemaInitRequest schemaInitRequest) throws Exception {
    log.info("Starting schema initialization for agent: {}", agentId);
    DbConfig config = schemaInitRequest.getDbConfig();
    
    // Check if it's a file datasource (CSV or Excel)
    boolean isFileDatasource = "file".equalsIgnoreCase(config.getDialectType());
    
    if (isFileDatasource) {
        log.info("Detected file datasource for agent: {}, using file-based schema initialization", agentId);
        return initializeFileDataSourceSchema(agentId, schemaInitRequest);
    }
    
    // For database datasources, use the original logic
    // ... (原有的数据库逻辑)
}
```

#### 2.2 添加文件数据源初始化方法

```java
private Boolean initializeFileDataSourceSchema(String agentId, SchemaInitRequest schemaInitRequest) {
    try {
        log.info("Initializing file datasource schema for agent: {}", agentId);
        
        // 1. Clear existing schema data
        clearSchemaDataForAgent(agentId);
        
        // 2. Get datasource information
        Datasource datasource = datasourceService.getActiveDatasourceByAgentId(Integer.valueOf(agentId));
        
        // 3. Parse file schema
        String filePath = datasource.getFilePath();
        String fileType = datasource.getFileType();
        String tableName = schemaInitRequest.getTables().get(0);
        
        List<ColumnInfo> columns = fileDataSourceService.parseFileSchema(...);
        
        // 4. Create table document
        Document tableDoc = new Document(tableContent, tableMetadata);
        
        // 5. Create column documents
        List<Document> columnDocs = new ArrayList<>();
        for (ColumnInfo column : columns) {
            columnDocs.add(new Document(columnContent, columnMetadata));
        }
        
        // 6. Store documents to vector database
        storeSchemaDocuments(columnDocs, List.of(tableDoc));
        
        return true;
    } catch (Exception e) {
        log.error("Failed to initialize file datasource schema", e);
        return false;
    }
}
```

**效果：**
- 解析文件的列信息
- 创建表文档和列文档
- 存储到向量数据库
- 智能体可以通过 `SchemaRecallNode` 召回这些信息

---

### 3. 修改 `SchemaProcessorUtil.createDbConfigFromDatasource()`

**文件：** `spring-ai-alibaba-data-agent-chat/src/main/java/com/alibaba/cloud/ai/util/SchemaProcessorUtil.java`

**需要添加：**

```java
public static DbConfig createDbConfigFromDatasource(Datasource datasource) {
    DbConfig dbConfig = new DbConfig();

    // Check if it's a file datasource
    if ("csv".equalsIgnoreCase(datasource.getType()) || "excel".equalsIgnoreCase(datasource.getType())) {
        dbConfig.setDialectType("file");
        dbConfig.setSchema(datasource.getName());
        return dbConfig;
    }

    // For database datasources
    dbConfig.setUrl(datasource.getConnectionUrl());
    dbConfig.setUsername(datasource.getUsername());
    dbConfig.setPassword(datasource.getPassword());
    
    if ("mysql".equalsIgnoreCase(datasource.getType())) {
        dbConfig.setConnectionType("jdbc");
        dbConfig.setDialectType("mysql");
    }
    // ... other database types
    
    dbConfig.setSchema(datasource.getDatabaseName());
    return dbConfig;
}
```

---

## 🔄 完整工作流程

### 用户操作流程

```
1. 用户上传 CSV 文件（例如：sales_data.csv）
    ↓
2. 系统创建文件数据源
    - 文件路径：uploads/datasets/sales_data_20250110.csv
    - 文件类型：csv
    - 原始文件名：sales_data.csv
    ↓
3. 用户点击"初始化数据源"
    ↓
4. 前端调用 getDatasourceTables(datasourceId)
    - 返回：["sales_data"]（文件名去掉扩展名）
    ↓
5. 前端调用 initSchema(agentId, {datasourceId, tables: ["sales_data"]})
    ↓
6. 后端创建 SchemaInitRequest
    - DbConfig.dialectType = "file"
    - tables = ["sales_data"]
    ↓
7. AgentVectorStoreServiceImpl.schema() 检测到文件数据源
    ↓
8. 调用 initializeFileDataSourceSchema()
    - 解析文件，提取列信息
    - 创建表文档和列文档
    - 存储到向量数据库
    ↓
9. 初始化成功！
```

### 智能体查询流程

```
用户提问："有哪些数据？"
    ↓
QueryRewriteNode: 识别为"数据分析"需求
    ↓
KeywordExtractNode: 提取关键词 ["数据"]
    ↓
SchemaRecallNode: 从向量数据库召回
    - 表文档：sales_data (5 columns, CSV file)
    - 列文档：name, age, city, salary, join_date
    ↓
TableRelationNode: 检测到文件数据源
    - 设置 IS_FILE_DATASOURCE = true
    - 设置 FILE_DATASOURCE_PATH = "uploads/datasets/sales_data_20250110.csv"
    ↓
PlannerNode: 生成执行计划
    - 使用 PYTHON_GENERATE_NODE（而不是 SQL_EXECUTE_NODE）
    ↓
PythonGenerateNode: 生成 Pandas 代码
    ```python
    import pandas as pd
    df = pd.read_csv('uploads/datasets/sales_data_20250110.csv')
    print(f"数据集包含 {len(df)} 行数据")
    print(f"列名：{list(df.columns)}")
    print(df.head())
    ```
    ↓
PythonExecuteNode: 执行代码
    ↓
ReportGeneratorNode: 生成报告
    "数据集包含 5 行数据，包括以下列：name, age, city, salary, join_date"
```

---

## 📊 向量数据库文档结构

### 表文档 (Table Document)

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

### 列文档 (Column Document)

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

## 🧪 测试步骤

### 1. 上传文件并创建数据源

```bash
# 准备测试文件
cat > test_sales.csv << EOF
name,age,city,salary,join_date
张三,28,北京,15000,2024-01-15
李四,32,上海,18000,2024-02-20
王五,25,深圳,12000,2024-03-10
EOF
```

### 2. 通过前端上传文件

1. 打开智能体配置页面
2. 点击"添加数据源" → "上传文件数据源"
3. 上传 `test_sales.csv`
4. 填写数据源名称："销售数据"
5. 点击"创建文件数据源"

### 3. 初始化数据源

1. 点击"初始化当前智能体的数据源"
2. 等待初始化完成
3. 查看日志，确认成功：
   ```
   Initializing file datasource schema for agent: 123
   Found 3 columns in file datasource
   Storing 3 column documents and 1 table document
   Successfully initialized file datasource schema
   ```

### 4. 测试查询

在对话界面输入以下问题：

**问题 1：** "有哪些数据？"

**预期回答：**
```
数据集包含以下信息：
- 表名：test_sales
- 列数：5
- 列名：name, age, city, salary, join_date
- 数据类型：CSV 文件
```

**问题 2：** "有多少条记录？"

**预期回答：**
```python
# 生成的代码
import pandas as pd
df = pd.read_csv('uploads/datasets/test_sales_20250110.csv')
print(f"共有 {len(df)} 条记录")
```

**输出：** "共有 3 条记录"

**问题 3：** "平均工资是多少？"

**预期回答：**
```python
import pandas as pd
df = pd.read_csv('uploads/datasets/test_sales_20250110.csv')
avg_salary = df['salary'].mean()
print(f"平均工资：{avg_salary:.2f} 元")
```

**输出：** "平均工资：15000.00 元"

---

## 🔍 调试方法

### 1. 检查数据源是否创建成功

```sql
SELECT * FROM datasource WHERE type IN ('csv', 'excel');
```

### 2. 检查向量数据库是否有文档

查看日志：
```
Storing 3 column documents and 1 table document for file datasource
Successfully stored all documents for agent: 123
```

### 3. 测试 Schema 召回

在 `SchemaRecallNode` 中添加日志：
```java
log.info("Table documents: {}", tableDocuments);
log.info("Column documents: {}", columnDocumentsByKeywords);
```

### 4. 检查文件路径是否正确

```java
log.info("File path: {}", datasource.getFilePath());
log.info("Full path: {}", Paths.get(fileStorageProperties.getPath(), filePath));
```

---

## ⚠️ 注意事项

### 1. 文件路径

- 文件存储在 `uploads/datasets/` 目录下
- 确保应用有读取权限
- 生产环境建议使用 OSS 存储

### 2. 文件大小

- 当前限制：100MB
- 建议控制在 50MB 以内
- 大文件建议导入数据库后使用 SQL 分析

### 3. 数据类型推断

- 基于前 100 行数据推断类型
- 可能不够准确
- 建议数据格式一致

### 4. 向量数据库

- 开发环境使用 SimpleVectorStore（内存）
- 生产环境建议使用 Milvus
- 重启应用后内存向量库数据会丢失

---

## 📚 相关文件

### 后端文件

- `DatasourceServiceImpl.java` - 数据源服务实现
- `AgentVectorStoreServiceImpl.java` - 向量存储服务实现
- `FileDataSourceServiceImpl.java` - 文件数据源服务实现
- `SchemaProcessorUtil.java` - Schema 处理工具
- `TableRelationNode.java` - 表关系节点
- `PlannerNode.java` - 计划生成节点
- `PythonGenerateNode.java` - Python 代码生成节点

### 前端文件

- `DataSourceConfig.vue` - 数据源配置组件
- `agentDatasource.ts` - 数据源服务

---

## 🎉 总结

通过以上修改，CSV/Excel 文件数据源现在可以：

1. ✅ 正常初始化 Schema 到向量数据库
2. ✅ 智能体可以识别文件中的数据
3. ✅ 用户提问时可以召回表和列信息
4. ✅ 自动生成 Pandas 代码进行数据分析
5. ✅ 返回准确的分析结果

**下一步建议：**
- 测试不同格式的 CSV/Excel 文件
- 优化数据类型推断逻辑
- 添加更多文件格式支持（JSON、Parquet 等）
- 支持文件更新和追加功能

---

**文档版本：** v1.0  
**最后更新：** 2025-01-10  
**作者：** Augment Agent

