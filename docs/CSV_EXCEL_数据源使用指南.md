# CSV/Excel 数据源使用指南

## 📋 功能概述

DataAgent 现已支持上传本地 CSV 和 Excel 文件作为数据源，无需配置数据库即可进行数据分析。系统会自动解析文件结构，并使用 Pandas 进行数据分析。

## ✨ 功能特性

- ✅ 支持 CSV 文件（.csv）
- ✅ 支持 Excel 文件（.xlsx, .xls）
- ✅ 自动推断列类型（整数、浮点数、日期、字符串）
- ✅ 文件预览功能（前5行数据）
- ✅ 拖拽上传，操作简便
- ✅ 文件大小限制：100MB
- ✅ 使用 Pandas 进行强大的数据分析

## 🚀 使用步骤

### 1. 上传文件数据源

1. 进入智能体详情页面
2. 点击"数据源配置"标签
3. 点击"添加数据源"按钮
4. 选择"上传文件数据源"标签页
5. 拖拽或点击上传 CSV/Excel 文件

### 2. 预览文件信息

上传成功后，系统会自动显示：
- **文件基本信息**：文件名、文件类型、列数、数据行数
- **列信息表格**：列名、数据类型、说明
- **数据预览**：前5行数据

### 3. 配置数据源

在预览区域下方：
- 输入**数据源名称**（必填）
- 输入**描述**（可选）
- 点击"创建文件数据源"按钮

### 4. 开始分析

数据源创建成功后：
1. 系统会自动将数据源添加到当前智能体
2. 数据源状态自动设置为"启用"
3. 可以直接在"对话"标签页中提问进行数据分析

## 💡 使用示例

### 示例 1：销售数据分析

假设你有一个 `sales_data.csv` 文件，包含以下列：
- `date`（日期）
- `product`（产品名称）
- `quantity`（销售数量）
- `revenue`（销售额）

**上传文件后，你可以这样提问：**

```
1. 显示销售额最高的前10个产品
2. 按月份统计总销售额，并绘制折线图
3. 计算每个产品的平均销售数量
4. 找出销售额超过10000的所有记录
5. 分析销售趋势，预测下个月的销售额
```

### 示例 2：用户行为数据分析

假设你有一个 `user_behavior.xlsx` 文件，包含：
- `user_id`（用户ID）
- `action`（行为类型）
- `timestamp`（时间戳）
- `duration`（持续时间）

**你可以这样提问：**

```
1. 统计每种行为类型的数量
2. 找出最活跃的10个用户
3. 按小时统计用户活跃度
4. 计算平均会话持续时间
5. 绘制用户行为分布饼图
```

## 🔧 技术实现

### 后端架构

1. **文件上传**：`FileUploadController.uploadDataset()`
   - 接收文件上传请求
   - 验证文件类型和大小
   - 保存文件到本地存储

2. **文件解析**：`FileDataSourceServiceImpl`
   - 使用 OpenCSV 解析 CSV 文件
   - 使用 Apache POI 解析 Excel 文件
   - 自动推断列类型
   - 提取 Schema 信息

3. **数据源创建**：`DatasourceController.createFileDatasource()`
   - 创建文件类型数据源记录
   - 保存文件路径和元数据

4. **Graph 工作流**：
   - `TableRelationNode`：检测文件数据源
   - `PlannerNode`：指示使用 Python 节点
   - `PythonGenerateNode`：生成 Pandas 代码
   - `PythonExecuteNode`：执行数据分析

### 前端实现

1. **文件上传组件**：`DataSourceConfig.vue`
   - 拖拽上传界面
   - 文件预览功能
   - 数据源配置表单

2. **API 服务**：`datasource.ts`
   - `uploadDataset()`：上传文件
   - `createFileDatasource()`：创建数据源

## 📊 数据类型推断规则

系统会自动推断列的数据类型：

| 数据示例 | 推断类型 | 说明 |
|---------|---------|------|
| 1, 2, 3 | INTEGER | 整数 |
| 1.5, 2.3, 3.14 | DOUBLE | 浮点数 |
| 2024-01-01, 2024/01/01 | DATE | 日期 |
| Hello, World | STRING | 字符串 |

## ⚠️ 注意事项

1. **文件大小限制**：单个文件不超过 100MB
2. **文件格式**：仅支持 .csv, .xlsx, .xls 格式
3. **编码格式**：CSV 文件建议使用 UTF-8 编码
4. **列名要求**：
   - 第一行必须是列名
   - 列名不能为空
   - 建议使用英文列名（中文也支持）
5. **数据质量**：
   - 避免空列
   - 避免混合数据类型
   - 日期格式尽量统一

## 🐛 常见问题

### Q1: 上传文件后提示"文件解析失败"？

**A:** 请检查：
- 文件格式是否正确（.csv, .xlsx, .xls）
- CSV 文件编码是否为 UTF-8
- Excel 文件是否损坏
- 文件是否包含有效数据

### Q2: 数据类型推断不准确？

**A:** 系统基于前100行数据推断类型。如果数据不一致，可能导致推断错误。建议：
- 确保同一列的数据类型一致
- 清理异常数据
- 使用明确的数据格式

### Q3: 分析大文件时速度慢？

**A:** 
- 文件数据源使用 Pandas 加载到内存
- 建议文件大小控制在 50MB 以内
- 对于大数据集，建议导入数据库后使用 SQL 分析

### Q4: 如何更新文件数据？

**A:** 
- 当前版本不支持文件更新
- 需要删除旧数据源，重新上传新文件
- 未来版本将支持文件追加和更新功能

## 🔄 工作流程图

```
用户上传文件
    ↓
文件解析（OpenCSV/Apache POI）
    ↓
Schema 提取 + 类型推断
    ↓
创建文件数据源记录
    ↓
关联到智能体
    ↓
用户提问
    ↓
TableRelationNode 检测文件数据源
    ↓
PlannerNode 生成执行计划（使用 Python）
    ↓
PythonGenerateNode 生成 Pandas 代码
    ↓
PythonExecuteNode 执行分析
    ↓
返回分析结果
```

## 📚 相关文件

### 后端文件
- `FileUploadController.java` - 文件上传接口
- `DatasourceController.java` - 数据源管理接口
- `FileDataSourceService.java` - 文件解析服务接口
- `FileDataSourceServiceImpl.java` - 文件解析服务实现
- `TableRelationNode.java` - 数据源检测节点
- `PlannerNode.java` - 计划生成节点
- `PythonGenerateNode.java` - Python 代码生成节点

### 前端文件
- `DataSourceConfig.vue` - 数据源配置组件
- `datasource.ts` - 数据源 API 服务

### 数据库
- `schema.sql` - MySQL 数据库 Schema
- `schema-h2.sql` - H2 数据库 Schema

## 🎯 未来规划

- [ ] 支持更多文件格式（JSON, Parquet, TSV）
- [ ] 支持文件数据更新和追加
- [ ] 支持多 Sheet Excel 文件
- [ ] 支持压缩文件（.zip, .gz）
- [ ] 支持在线文件 URL 导入
- [ ] 支持数据预处理（去重、填充缺失值等）
- [ ] 支持大文件分块上传
- [ ] 支持文件数据源的定时刷新

## 📞 技术支持

如有问题，请联系开发团队或提交 Issue。

---

**版本**：v1.0  
**更新日期**：2025-01-10  
**作者**：DataAgent 开发团队

