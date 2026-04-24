# 测试 CSV 数据源完整流程

## 📋 测试目标

验证 CSV/Excel 文件数据源的完整功能：
1. ✅ 文件上传和数据源创建
2. ✅ Schema 初始化到向量数据库
3. ✅ 智能体识别文件数据
4. ✅ 自动生成 Pandas 代码
5. ✅ 执行数据分析并返回结果

---

## 🚀 测试步骤

### 步骤 1：准备测试数据

创建一个测试 CSV 文件 `sales_data.csv`：

```csv
name,age,city,salary,join_date
张三,28,北京,15000,2024-01-15
李四,32,上海,18000,2024-02-20
王五,25,深圳,12000,2024-03-10
赵六,35,广州,20000,2024-01-05
钱七,29,杭州,16000,2024-02-15
```

保存为 `sales_data.csv` 文件。

---

### 步骤 2：启动应用

#### 2.1 启动后端

```bash
cd spring-ai-alibaba-data-agent-management
mvn clean spring-boot:run
```

**等待启动完成，看到以下日志：**
```
Started DataAgentManagementApplication in X.XXX seconds
```

#### 2.2 启动前端

```bash
cd spring-ai-alibaba-data-agent-frontend
npm install  # 首次运行需要安装依赖
npm run dev
```

**访问：** http://localhost:5173

---

### 步骤 3：创建智能体

1. 登录系统（如果需要）
2. 点击"创建智能体"
3. 填写信息：
   - **名称：** 销售数据分析助手
   - **描述：** 分析销售团队数据
   - **模型：** qwen-plus
4. 点击"创建"

**记录智能体 ID**（例如：123）

---

### 步骤 4：上传文件并创建数据源

#### 4.1 上传文件

1. 进入智能体配置页面
2. 点击"数据源配置"标签
3. 点击"上传文件数据源"
4. 拖拽或选择 `sales_data.csv` 文件
5. 等待上传完成

**预期结果：**
- 显示文件预览
- 显示列信息：name, age, city, salary, join_date
- 显示数据预览（前5行）

#### 4.2 创建数据源

1. 填写数据源信息：
   - **数据源名称：** 销售数据
   - **描述：** 2024年销售团队数据
2. 点击"创建文件数据源"

**预期结果：**
- 提示"数据源创建成功"
- 数据源列表中显示新创建的数据源
- 状态为"已启用"（绿色）

---

### 步骤 5：初始化数据源 Schema

#### 5.1 点击初始化按钮

1. 在数据源列表中找到刚创建的数据源
2. 点击"初始化当前智能体的数据源"按钮
3. 等待初始化完成

**预期结果：**
- 提示"Schema 初始化成功"

#### 5.2 检查后端日志

查看后端控制台，应该看到以下日志：

```
Initializing file datasource schema for agent: 123
Parsing file schema: filePath=data-agent/datasets/sales_data_xxx.csv, fileType=csv, tableName=sales_data
Reading file from: /path/to/uploads/data-agent/datasets/sales_data_xxx.csv
Found 5 columns in file datasource
Storing 5 column documents and 1 table document for file datasource
Successfully initialized file datasource schema for agent: 123
```

---

### 步骤 6：测试智能体对话

#### 6.1 进入对话界面

1. 点击"对话"标签
2. 确认智能体已选择

#### 6.2 测试问题 1：查看数据

**输入：** "有哪些数据？"

**预期回答：**
```
数据集包含以下信息：
- 表名：sales_data
- 列数：5
- 列名：name, age, city, salary, join_date
- 数据类型：CSV 文件
- 描述：2024年销售团队数据
```

**检查后端日志：**
```
SchemaRecallNode: Found 1 table documents
SchemaRecallNode: Found 5 column documents
TableRelationNode: Detected file datasource, setting IS_FILE_DATASOURCE=true
PlannerNode: Using PYTHON_GENERATE_NODE for file datasource
```

#### 6.3 测试问题 2：统计数据

**输入：** "有多少条记录？"

**预期回答：**
```
数据集共有 5 条记录。
```

**生成的 Python 代码：**
```python
import pandas as pd
df = pd.read_csv('uploads/data-agent/datasets/sales_data_xxx.csv')
print(f"共有 {len(df)} 条记录")
```

#### 6.4 测试问题 3：数据分析

**输入：** "平均工资是多少？"

**预期回答：**
```
平均工资为 16,200 元。
```

**生成的 Python 代码：**
```python
import pandas as pd
df = pd.read_csv('uploads/data-agent/datasets/sales_data_xxx.csv')
avg_salary = df['salary'].mean()
print(f"平均工资：{avg_salary:.2f} 元")
```

#### 6.5 测试问题 4：分组统计

**输入：** "每个城市有多少人？"

**预期回答：**
```
各城市人数统计：
- 北京：1 人
- 上海：1 人
- 深圳：1 人
- 广州：1 人
- 杭州：1 人
```

**生成的 Python 代码：**
```python
import pandas as pd
df = pd.read_csv('uploads/data-agent/datasets/sales_data_xxx.csv')
city_count = df['city'].value_counts()
print("各城市人数统计：")
for city, count in city_count.items():
    print(f"- {city}：{count} 人")
```

#### 6.6 测试问题 5：可视化

**输入：** "画一个工资分布图"

**预期回答：**
- 返回一个柱状图或直方图
- 显示每个人的工资

**生成的 Python 代码：**
```python
import pandas as pd
import matplotlib.pyplot as plt

df = pd.read_csv('uploads/data-agent/datasets/sales_data_xxx.csv')

plt.figure(figsize=(10, 6))
plt.bar(df['name'], df['salary'])
plt.xlabel('姓名')
plt.ylabel('工资（元）')
plt.title('员工工资分布')
plt.xticks(rotation=45)
plt.tight_layout()
plt.savefig('salary_distribution.png')
print("图表已保存为 salary_distribution.png")
```

---

## 🔍 调试检查清单

### 1. 文件上传检查

- [ ] 文件成功上传到 `uploads/data-agent/datasets/` 目录
- [ ] 数据源记录已创建（检查数据库 `datasource` 表）
- [ ] `file_path`、`file_type`、`original_filename` 字段正确

**SQL 查询：**
```sql
SELECT id, name, type, file_path, file_type, original_filename 
FROM datasource 
WHERE type IN ('csv', 'excel');
```

### 2. Schema 初始化检查

- [ ] 后端日志显示"Successfully initialized file datasource schema"
- [ ] 向量数据库中有文档（检查日志）
- [ ] 表文档和列文档都已创建

**检查方法：**
在 `AgentVectorStoreServiceImpl` 中添加日志：
```java
log.info("Stored documents count: {}", columnDocs.size() + 1);
```

### 3. Schema 召回检查

- [ ] `SchemaRecallNode` 能召回表文档
- [ ] `SchemaRecallNode` 能召回列文档
- [ ] 召回的文档包含正确的 `agentId`

**检查方法：**
在 `SchemaRecallNode` 中添加日志：
```java
log.info("Recalled table documents: {}", tableDocuments.size());
log.info("Recalled column documents: {}", columnDocuments.size());
```

### 4. 文件数据源检测

- [ ] `TableRelationNode` 检测到文件数据源
- [ ] 设置了 `IS_FILE_DATASOURCE = true`
- [ ] 设置了 `FILE_DATASOURCE_PATH`

**检查方法：**
在 `TableRelationNode` 中添加日志：
```java
log.info("IS_FILE_DATASOURCE: {}", state.get(Constant.IS_FILE_DATASOURCE));
log.info("FILE_DATASOURCE_PATH: {}", state.get(Constant.FILE_DATASOURCE_PATH));
```

### 5. Python 代码生成检查

- [ ] `PlannerNode` 选择了 `PYTHON_GENERATE_NODE`
- [ ] `PythonGenerateNode` 生成了 Pandas 代码
- [ ] 代码中包含正确的文件路径

**检查方法：**
在 `PythonGenerateNode` 中添加日志：
```java
log.info("Generated Python code: {}", pythonCode);
```

### 6. Python 代码执行检查

- [ ] `PythonExecuteNode` 成功执行代码
- [ ] 返回了正确的结果
- [ ] 没有 Python 错误

**检查方法：**
在 `PythonExecuteNode` 中添加日志：
```java
log.info("Python execution result: {}", result);
```

---

## ⚠️ 常见问题

### 问题 1：文件上传失败（413 错误）

**原因：** 文件大小超过限制

**解决方案：**
```yaml
# application.yml
spring:
  servlet:
    multipart:
      max-file-size: 100MB
      max-request-size: 100MB
```

### 问题 2：Schema 初始化失败

**原因：** 文件路径不正确或文件不存在

**解决方案：**
1. 检查文件是否存在：
   ```bash
   ls -la uploads/data-agent/datasets/
   ```
2. 检查文件权限：
   ```bash
   chmod 644 uploads/data-agent/datasets/*
   ```

### 问题 3：智能体无法识别数据

**原因：** Schema 未初始化或向量数据库为空

**解决方案：**
1. 重新初始化 Schema
2. 检查向量数据库配置
3. 如果使用 SimpleVectorStore（内存），重启应用后需要重新初始化

### 问题 4：Python 代码执行失败

**原因：** Python 环境缺少依赖或文件路径错误

**解决方案：**
1. 检查 Python 环境：
   ```bash
   python3 -c "import pandas; print(pandas.__version__)"
   ```
2. 安装依赖：
   ```bash
   pip install pandas openpyxl matplotlib
   ```
3. 检查文件路径是否正确

### 问题 5：向量数据库重启后数据丢失

**原因：** 使用了 SimpleVectorStore（内存向量库）

**解决方案：**
- 开发环境：每次重启后重新初始化 Schema
- 生产环境：使用 Milvus 等持久化向量数据库

---

## 📊 测试结果记录

| 测试项 | 预期结果 | 实际结果 | 状态 |
|--------|---------|---------|------|
| 文件上传 | 成功上传并显示预览 | | ⬜ |
| 数据源创建 | 创建成功并显示在列表 | | ⬜ |
| Schema 初始化 | 初始化成功 | | ⬜ |
| 查看数据 | 返回表和列信息 | | ⬜ |
| 统计记录数 | 返回 5 条记录 | | ⬜ |
| 计算平均工资 | 返回 16200 元 | | ⬜ |
| 分组统计 | 返回各城市人数 | | ⬜ |
| 数据可视化 | 生成图表 | | ⬜ |

---

## 🎯 成功标准

所有以下条件都满足，则测试通过：

1. ✅ 文件成功上传并创建数据源
2. ✅ Schema 成功初始化到向量数据库
3. ✅ 智能体能识别文件中的表和列
4. ✅ 能正确回答"有哪些数据"
5. ✅ 能生成正确的 Pandas 代码
6. ✅ Python 代码能成功执行
7. ✅ 返回正确的分析结果
8. ✅ 支持数据可视化

---

## 📝 测试报告模板

```
测试日期：2025-01-10
测试人员：[你的名字]
测试环境：
- 操作系统：macOS / Windows / Linux
- Java 版本：17
- Python 版本：3.9+
- 数据库：MySQL 8.0
- 向量数据库：SimpleVectorStore / Milvus

测试结果：
- 文件上传：✅ 通过 / ❌ 失败
- Schema 初始化：✅ 通过 / ❌ 失败
- 数据识别：✅ 通过 / ❌ 失败
- 代码生成：✅ 通过 / ❌ 失败
- 代码执行：✅ 通过 / ❌ 失败

问题记录：
1. [问题描述]
   - 原因：[原因分析]
   - 解决方案：[解决方案]

总结：
[测试总结]
```

---

**祝测试顺利！** 🎉

