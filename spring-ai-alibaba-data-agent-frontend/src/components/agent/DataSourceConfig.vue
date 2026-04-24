<!--
 * Copyright 2025 the original author or authors.
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
-->

<template>
  <div style="padding: 20px">
    <div style="margin-bottom: 20px;">
      <h2>数据源配置</h2>
    </div>
    <el-divider/>

    <div style="margin-bottom: 30px;">
      <el-row style="display: flex; justify-content: space-between; align-items: center;">
        <el-col :span="12">
          <h3>数据源列表</h3>
        </el-col>
        <el-col :span="12" style="text-align: right;">
          <el-button @click="dialogVisible = true" size="large" type="primary" round :icon="Plus">添加数据源</el-button>
          <el-button @click="initAgentDatasource" v-if="!initStatus" size="large" type="primary" round :icon="UploadFilled">初始化数据源</el-button>
          <el-button v-else size="large" type="primary" round loading>初始化中...</el-button>
        </el-col>
      </el-row>
    </div>

    <el-table :data="datasource" style="width: 100%" border>
      <el-table-column prop="name" label="数据源名称" min-width="120px"/>
      <el-table-column prop="type" label="数据源类型" min-width="100px"/>
      <el-table-column prop="connectionUrl" label="连接地址" min-width="200px"/>
      <el-table-column label="连接状态" min-width="50px">
        <template #default="scope">
          <el-tag :type="scope.row.testStatus === 'success' ? 'success' : 'danger'" round>
            {{ scope.row.testStatus === 'success' ? '连接成功' : '连接失败' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" min-width="40px">
        <template #default="scope">
          <el-tag :type="scope.row.status === 'active' ? 'success' : 'info'" round>
            {{ scope.row.status === 'active' ? '启用' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" min-width="100px"/>
      <el-table-column label="操作" min-width="120px">
        <template #default="scope">
          <el-button v-if="scope.row.status === 'active'" @click="changeDatasource(scope.row, false)" size="small" type="warning" round plain>禁用</el-button>
          <el-button v-else @click="changeDatasource(scope.row, true)" size="small" type="success" round plain>启用</el-button>
          <el-button @click="testConnection(scope.row)" size="small" type="primary" round plain>测试连接</el-button>
          <el-button @click="removeAgentDatasource(scope.row)" size="small" type="danger" round plain>移除</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>

  <!-- 添加数据源Dialog -->
  <el-dialog v-model="dialogVisible" title="添加数据源" width="1000">
    <el-tabs v-model="dialogActiveName" type="card" stretch>
      <el-tab-pane label="上传文件数据源" name="file">
        <div style="padding: 20px;">
          <el-alert
            title="支持上传 CSV 和 Excel 文件作为数据源"
            type="info"
            :closable="false"
            style="margin-bottom: 20px;"
          >
            <template #default>
              <p>• 支持的文件格式：.csv, .xlsx, .xls</p>
              <p>• 文件大小限制：100MB</p>
              <p>• 上传后将自动解析文件结构</p>
            </template>
          </el-alert>

          <el-upload
            ref="uploadRef"
            class="upload-demo"
            drag
            :auto-upload="false"
            :on-change="handleFileChange"
            :limit="1"
            accept=".csv,.xlsx,.xls"
            :file-list="fileList"
          >
            <el-icon class="el-icon--upload"><upload-filled /></el-icon>
            <div class="el-upload__text">
              拖拽文件到此处或 <em>点击上传</em>
            </div>
            <template #tip>
              <div class="el-upload__tip">
                支持 CSV 和 Excel 文件（.csv, .xlsx, .xls）
              </div>
            </template>
          </el-upload>

          <!-- 文件预览 -->
          <div v-if="filePreview.columns && filePreview.columns.length > 0" style="margin-top: 30px;">
            <h3>文件预览</h3>
            <el-divider/>

            <el-descriptions :column="2" border style="margin-bottom: 20px;">
              <el-descriptions-item label="文件名">{{ filePreview.fileName }}</el-descriptions-item>
              <el-descriptions-item label="文件类型">{{ filePreview.fileType }}</el-descriptions-item>
              <el-descriptions-item label="列数">{{ filePreview.columns.length }}</el-descriptions-item>
              <el-descriptions-item label="数据行数">{{ filePreview.previewData ? filePreview.previewData.length : 0 }}</el-descriptions-item>
            </el-descriptions>

            <h4>列信息</h4>
            <el-table :data="filePreview.columns" border style="margin-bottom: 20px;">
              <el-table-column prop="columnName" label="列名" width="200"/>
              <el-table-column prop="columnType" label="数据类型" width="120"/>
              <el-table-column prop="comment" label="说明" />
            </el-table>

            <h4>数据预览（前5行）</h4>
            <el-table :data="filePreview.previewData" border max-height="300">
              <el-table-column
                v-for="col in filePreview.columns"
                :key="col.columnName"
                :prop="col.columnName"
                :label="col.columnName"
                min-width="120"
              />
            </el-table>

            <!-- 数据源配置 -->
            <div style="margin-top: 30px;">
              <h4>数据源配置</h4>
              <el-row :gutter="20">
                <el-col :span="12">
                  <div class="form-item">
                    <label>数据源名称 *</label>
                    <el-input v-model="fileDatasource.name" placeholder="请输入数据源名称" size="large"/>
                  </div>
                </el-col>
                <el-col :span="12">
                  <div class="form-item">
                    <label>描述</label>
                    <el-input v-model="fileDatasource.description" placeholder="请输入描述（可选）" size="large"/>
                  </div>
                </el-col>
              </el-row>
            </div>
          </div>

          <el-divider/>
          <div style="text-align: right;">
            <el-button @click="dialogVisible = false">取消</el-button>
            <el-button
              type="primary"
              @click="createFileDatasource"
              :disabled="!filePreview.filePath || !fileDatasource.name"
              :loading="uploadingFile"
            >
              创建文件数据源
            </el-button>
          </div>
        </div>
      </el-tab-pane>
      <el-tab-pane label="选择已有数据源" name="select">
        <!-- todo: 添加分页和查询 -->
        <el-table @current-change="handleSelectDatasourceChange" :data="allDatasource" highlight-current-row style="width: 100%">
          <el-table-column property="name" label="数据源名称" width="150"/>
          <el-table-column property="type" label="数据源类型" width="100"/>
          <el-table-column property="host" label="Host" width="100"/>
          <el-table-column property="port" label="Port" width="80"/>
          <el-table-column property="description" label="描述" width="300"/>
          <el-table-column label="操作" width="150">
            <template #default="scope">
              <el-button @click="editDatasource(scope.row)" size="small" type="primary" round plain>修改</el-button>
              <el-button @click="deleteDatasource(scope.row)" size="small" type="danger" round plain>删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-divider/>
        <div style="text-align: right;">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" @click="addSelectDatasource">
            添加选中数据源
          </el-button>
        </div>
      </el-tab-pane>
      <el-tab-pane label="添加新数据源" name="add">

        <el-row :gutter="20">
          <el-col :span="12">
            <div class="form-item">
              <label>数据源名称 *</label>
              <el-input v-model="newDatasource.name" placeholder="请输入数据源名称" size="large"/>
            </div>
          </el-col>
          <el-col :span="12">
            <div class="form-item">
              <label>数据源类型 *</label>
              <!-- todo: 改为后端动态获取-->
              <el-select v-model="newDatasource.type" placeholder="请选择数据源类型" style="width: 100%" size="large">
                <el-option key="mysql" label="MySQL" value="mysql"/>
                <el-option key="postgresql" label="PostgreSQL" value="postgresql"/>
              </el-select>
            </div>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <div class="form-item">
              <label>主机地址 *</label>
              <el-input v-model="newDatasource.host" placeholder="例如：localhost 或 192.168.1.100" size="large"/>
            </div>
          </el-col>
          <el-col :span="12">
            <div class="form-item">
              <label>端口号 *</label>
              <el-input-number v-model="newDatasource.port" :min="0" :max="65535" size="large" style="width: 100%"/>
            </div>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col>
            <div class="form-item">
              <label>数据库名 *</label>
              <el-input v-model="newDatasource.databaseName" placeholder="请输入数据库名称" size="large"/>
            </div>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col>
            <div class="form-item">
              <label>连接地址</label>
              <el-input v-model="newDatasource.connectionUrl" placeholder="请输入JDBC地址（若不填则自动生成）" size="large"/>
            </div>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <div class="form-item">
              <label>用户名 *</label>
              <el-input v-model="newDatasource.username" placeholder="请输入数据库用户名" size="large"/>
            </div>
          </el-col>
          <el-col :span="12">
            <div class="form-item">
              <label>密码 *</label>
              <el-input v-model="newDatasource.password" placeholder="请输入数据库密码" size="large" show-password/>
            </div>
          </el-col>
        </el-row>
        <el-row :gutter="30">
          <el-col :span="24">
            <div class="form-item">
              <label>描述</label>
              <el-input
                  v-model="newDatasource.description"
                  :rows="4"
                  type="textarea"
                  placeholder="请输入数据源描述（可选）"
                  size="large"
              />
            </div>
          </el-col>
        </el-row>

        <el-divider/>
        <div style="text-align: right;">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" @click="createNewDatasource">
            创建并添加
          </el-button>
        </div>
      </el-tab-pane>
    </el-tabs>
  </el-dialog>
  <el-dialog v-model="editDialogVisible" title="编辑数据源" width="1000">
    <el-row :gutter="20">
      <el-col :span="12">
        <div class="form-item">
          <label>数据源名称 *</label>
          <el-input v-model="editingDatasource.name" placeholder="请输入数据源名称" size="large"/>
        </div>
      </el-col>
      <el-col :span="12">
        <div class="form-item">
          <label>数据源类型 *</label>
          <el-select v-model="editingDatasource.type" placeholder="请选择数据源类型" style="width: 100%" size="large">
            <el-option key="mysql" label="MySQL" value="mysql"/>
            <el-option key="postgresql" label="PostgreSQL" value="postgresql"/>
          </el-select>
        </div>
      </el-col>
    </el-row>
    <el-row :gutter="20">
      <el-col :span="12">
        <div class="form-item">
          <label>主机地址 *</label>
          <el-input v-model="editingDatasource.host" placeholder="例如：localhost 或 192.168.1.100" size="large"/>
        </div>
      </el-col>
      <el-col :span="12">
        <div class="form-item">
          <label>端口号 *</label>
          <el-input-number v-model="editingDatasource.port" :min="0" :max="65535" size="large" style="width: 100%"/>
        </div>
      </el-col>
    </el-row>
    <el-row :gutter="20">
      <el-col>
        <div class="form-item">
          <label>数据库名 *</label>
          <el-input v-model="editingDatasource.databaseName" placeholder="请输入数据库名称" size="large"/>
        </div>
      </el-col>
    </el-row>
    <el-row :gutter="20">
      <el-col>
        <div class="form-item">
          <label>连接地址</label>
          <el-input v-model="editingDatasource.connectionUrl" placeholder="请输入JDBC地址（若不填则自动生成）" size="large"/>
        </div>
      </el-col>
    </el-row>
    <el-row :gutter="20">
      <el-col :span="12">
        <div class="form-item">
          <label>用户名 *</label>
          <el-input v-model="editingDatasource.username" placeholder="请输入数据库用户名" size="large"/>
        </div>
      </el-col>
      <el-col :span="12">
        <div class="form-item">
          <label>密码 *</label>
          <el-input v-model="editingDatasource.password" placeholder="请输入数据库密码" size="large" show-password/>
        </div>
      </el-col>
    </el-row>
    <el-row :gutter="30">
      <el-col :span="24">
        <div class="form-item">
          <label>描述</label>
          <el-input
              v-model="editingDatasource.description"
              :rows="4"
              type="textarea"
              placeholder="请输入数据源描述（可选）"
              size="large"
          />
        </div>
      </el-col>
    </el-row>

    <el-divider/>
    <div style="text-align: right;">
      <el-button @click="editDialogVisible = false">取消</el-button>
      <el-button type="primary" @click="saveEditDatasource">
        保存修改
      </el-button>
    </div>
  </el-dialog>
</template>

<script lang="ts">
import {defineComponent, ref, onMounted, Ref, watch} from "vue"
import { Plus, UploadFilled } from '@element-plus/icons-vue'
import datasourceService from '@/services/datasource'
import { Datasource, AgentDatasource } from '@/services/datasource'
import { ApiResponse } from '@/services/common'
import { ElMessage, ElMessageBox } from 'element-plus'
import agentDatasourceService from '@/services/agentDatasource'

export default defineComponent({
  name: 'AgentDataSourceConfig',
  props: {
    agentId: {
      type: Number,
      required: true
    }
  },
  setup(props) {
    // 当前Agent关联的数据源列表
    const datasource : Ref<Datasource[]> = ref([])
    const initStatus : Ref<boolean> = ref(false)
    const dialogVisible : Ref<boolean> = ref(false)
    const dialogActiveName : Ref<string> = ref('file')
    // 所有数据源列表
    const allDatasource : Ref<Datasource[]> = ref([])
    const newDatasource : Ref<Datasource> = ref({ port: 3306 } as Datasource)
    const selectedDatasourceId : Ref<number | null> = ref(null)
    const editDialogVisible : Ref<boolean> = ref(false)
    const editingDatasource : Ref<Datasource> = ref({} as Datasource)

    // 文件上传相关
    const uploadRef = ref()
    const fileList : Ref<any[]> = ref([])
    const uploadingFile : Ref<boolean> = ref(false)
    const filePreview : Ref<any> = ref({
      fileName: '',
      fileType: '',
      filePath: '',
      columns: [],
      previewData: []
    })
    const fileDatasource : Ref<Datasource> = ref({} as Datasource)

    watch(dialogVisible, (newValue) => {
      if (newValue) {
        loadAllDatasource()
        newDatasource.value = { port: 3306 } as Datasource
        // 重置文件上传相关状态
        fileList.value = []
        filePreview.value = {
          fileName: '',
          fileType: '',
          filePath: '',
          columns: [],
          previewData: []
        }
        fileDatasource.value = {} as Datasource
      }
    })

    // 初始化Agent数据源列表
    const loadAgentDatasource = async () => {
      selectedDatasourceId.value = null
      try {
        const response = await agentDatasourceService.getAgentDatasource(props.agentId)
        const agentDatasource: AgentDatasource[] = response || []
        datasource.value = agentDatasource.map((item) => {
          const datasourceItem = { ...item.datasource };
          datasourceItem.status = item.isActive === 1 ? 'active' : 'inactive';
          return datasourceItem;
        })
      } catch (error) {
        ElMessage.error('加载当前智能体的数据源列表失败')
        console.error('Failed to load datasource:', error)
      }
    }

    const handleSelectDatasourceChange = (value: Datasource) => {
      if(value === null || value === undefined) {
        selectedDatasourceId.value = null
      } else {
        selectedDatasourceId.value = value.id
      }
    }

    const loadAllDatasource = async () => {
      try {
        const response = await datasourceService.getAllDatasource()
        allDatasource.value = response || []
      } catch (error) {
        ElMessage.error('加载所有数据源列表失败')
        console.error('Failed to load all datasource:', error)
      }
    }

    // 初始化Agent数据源
    const initAgentDatasource = async () => {
      initStatus.value = true
      try {

        // 获取智能体配置的启用数据源
        const usedDatasource : Datasource[] = datasource.value.filter((item) => item.status === 'active')
        if(!usedDatasource || usedDatasource.length === 0) {
          ElMessage.warning('当前智能体未启用任何数据源')
          return
        }

        // todo: 支持每一个数据源选择指定的数据表进行初始化（需要后端的配合）
        for (const source of usedDatasource) {
          ElMessage.primary(`正在初始化数据源: ${source.name}...`)
          const tables : ApiResponse<string[]> = await agentDatasourceService.getDatasourceTables(props.agentId, source.id);
          if(tables === null || tables.data === null || !tables.success) {
            throw new Error('获取数据源表失败')
          }
          const tablesData: string[] = tables.data;
          if(tablesData.length === 0) {
            ElMessage.warning(`数据源: ${source.name} 没有数据表`)
            continue
          }

          const response: any = await agentDatasourceService.initSchema(props.agentId, { datasourceId: source.id, tables: tablesData});
          if(response.success === undefined || response.success == null || !response.success) {
            ElMessage.error(`初始化数据源: ${source.name} 失败`)
            throw new Error('初始化数据源失败')
          }
        }

        ElMessage.success('初始化当前智能体的数据源成功')
      } catch (error) {
        ElMessage.error('初始化当前智能体的数据源失败')
        console.error('Failed to init datasource:', error)
      } finally {
        initStatus.value = false
      }
    }

    // 更改数据源状态
    const changeDatasource = async (row : Datasource, active : boolean) => {
      const datasourceId = row.id;

      // 如果是启用操作，检查是否有其他启用的数据源
      if (active) {
        const activeCount = datasource.value.filter(d => d.status === 'active' && d.id !== datasourceId).length
        if (activeCount > 0) {
          try {
            await ElMessageBox.confirm(
              '启用此数据源将自动禁用其他已启用的数据源，是否继续？',
              '提示',
              {
                confirmButtonText: '确定',
                cancelButtonText: '取消',
                type: 'warning'
              }
            )
          } catch {
            return // 用户取消操作
          }
        }
      }

      try {
        const response : ApiResponse = await agentDatasourceService.toggleDatasourceForAgent(props.agentId, { datasourceId: datasourceId, isActive: active});
        if(response.success) {
          ElMessage.success(active ? '数据源已启用，其他数据源已自动禁用' : '数据源已禁用')
          // 重新加载数据源列表以确保状态同步
          await loadAgentDatasource()
        } else {
          ElMessage.error(response.message || '操作失败！')
          console.error('Failed to change datasource:', response)
        }
      } catch (error: any) {
        const errorMsg = error?.response?.data?.message || error?.message || '操作失败'
        ElMessage.error(errorMsg)
        console.error('Failed to change datasource:', error)
      }
    }

    // 测试数据源连接
    const testConnection = async (row : Datasource) => {
      const datasourceId = row.id;
      try {
        const response : ApiResponse = await datasourceService.testConnection(datasourceId);
        if(response.success) {
          ElMessage.success('测试连接成功！')
          row.testStatus = 'success'
        } else {
          ElMessage.error('测试连接失败！')
          console.error('Failed to test connection:', response)
          row.testStatus = 'fail'
        }
      } catch (error) {
        ElMessage.error('测试连接失败！');
        console.error('Failed to test connection:', error)
        row.testStatus = 'fail'
      }
    }

    // 移除Agent数据源
    const removeAgentDatasource = async (row: Datasource) => {
      const datasourceId = row.id;

      try {
        await ElMessageBox.confirm('是否要删除当前数据源吗？', '提示', {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          type: 'warning'
        })
      } catch (error) {
        return
      }

      try {
        const response : ApiResponse = await agentDatasourceService.removeDatasourceFromAgent(props.agentId, datasourceId);
        if(response.success) {
          ElMessage.success('移除成功！')
          datasource.value = datasource.value.filter((item) => item.id !== datasourceId)
        } else {
          ElMessage.error('移除失败！')
          console.error('Failed to remove datasource:', response)
        }
      } catch (error) {
        ElMessage.error('移除失败！')
        console.error('Failed to remove datasource:', error)
      }
    }

    const addDatasourceToAgent = async (datasourceId: number) => {
      try {
        await agentDatasourceService.addDatasourceToAgent(props.agentId, datasourceId)
        await loadAgentDatasource()
        ElMessage.success('添加数据源成功')
        dialogVisible.value = false
      } catch (error) {
        ElMessage.error('添加数据源失败')
        console.error('Failed to add datasource:', error)
      }
    }

    const addSelectDatasource = async () => {
      const datasourceId = selectedDatasourceId.value;
      if(datasourceId === null || datasourceId === undefined) {
        ElMessage.warning('请选择一个数据源')
        return
      }
      await addDatasourceToAgent(datasourceId)
    }

    const validateDatasourceForm = (datasourceForm : Datasource) : string[] => {
      const errors : string[] = []

      if (!datasourceForm.name || datasourceForm.name.trim() === '') {
        errors.push('数据源名称不能为空')
      }

      if (!datasourceForm.type) {
        errors.push('请选择数据源类型')
      }

      if (!datasourceForm.host || datasourceForm.host.trim() === '') {
        errors.push('主机地址不能为空')
      }

      if (!datasourceForm.port || datasourceForm.port <= 0 || datasourceForm.port > 65535) {
        errors.push('请输入有效的端口号（1-65535）')
      }

      if (!datasourceForm.databaseName || datasourceForm.databaseName.trim() === '') {
        errors.push('数据库名不能为空')
      }

      if (!datasourceForm.username || datasourceForm.username.trim() === '') {
        errors.push('用户名不能为空')
      }

      if (!datasourceForm.password || datasourceForm.password.trim() === '') {
        errors.push('密码不能为空')
      }

      return errors
    }

    const createNewDatasource = async () => {
      const formErrors : string[] = validateDatasourceForm(newDatasource.value)
      if(formErrors.length > 0) {
        ElMessage.error(formErrors.join('\r\n'))
        return
      }
      try {
        const datasource : Datasource = await datasourceService.createDatasource(newDatasource.value)
        const id = datasource.id
        if(id === null || id === undefined) {
          throw new Error('创建数据源失败')
        }
        await addDatasourceToAgent(id)
      } catch (error) {
        ElMessage.error('创建数据源失败')
        console.error('Failed to create datasource:', error)
      }
      dialogVisible.value = false
    }
    const editDatasource = (row: Datasource) => {
      editingDatasource.value = JSON.parse(JSON.stringify(row))
      editDialogVisible.value = true
    }

    const saveEditDatasource = async () => {
      const formErrors : string[] = validateDatasourceForm(editingDatasource.value)
      if(formErrors.length > 0) {
        ElMessage.error(formErrors.join('\n'))
        return
      }

      try {
        const response : Datasource = await datasourceService.updateDatasource(editingDatasource.value.id!, editingDatasource.value)
        if(response && response.id) {
          ElMessage.success('修改成功！')
          const index = allDatasource.value.findIndex((item) => item.id === editingDatasource.value.id)
          if(index >= 0) {
            allDatasource.value[index] = response
          }
          editDialogVisible.value = false
        } else {
          ElMessage.error('修改失败！')
          console.error('Failed to update datasource:', response)
        }
      } catch (error) {
        ElMessage.error('修改失败！')
        console.error('Failed to update datasource:', error)
      }
    }

    const deleteDatasource = async (row: Datasource) => {
      const datasourceId = row.id;

      try {
        await ElMessageBox.confirm('删除后无法恢复，确定要删除该数据源吗？', '确认删除', {
          confirmButtonText: '删除',
          cancelButtonText: '取消',
          type: 'warning'
        })
      } catch (error) {
        return
      }

      try {
        const response : ApiResponse<void> = await datasourceService.deleteDatasource(datasourceId!);
        if(response.success) {
          ElMessage.success('删除成功！')
          allDatasource.value = allDatasource.value.filter((item) => item.id !== datasourceId)
        } else {
          ElMessage.error('删除失败！')
          console.error('Failed to delete datasource:', response)
        }
      } catch (error) {
        ElMessage.error('删除失败！')
        console.error('Failed to delete datasource:', error)
      }
    }

    // 文件上传相关方法
    const handleFileChange = async (file: any, fileListParam: any[]) => {
      fileList.value = fileListParam

      if (!file || !file.raw) {
        return
      }

      // 验证文件类型
      const fileName = file.name
      const fileExtension = fileName.substring(fileName.lastIndexOf('.')).toLowerCase()
      if (!['.csv', '.xlsx', '.xls'].includes(fileExtension)) {
        ElMessage.error('只支持 CSV 和 Excel 文件（.csv, .xlsx, .xls）')
        fileList.value = []
        return
      }

      // 验证文件大小（100MB）
      const maxSize = 100 * 1024 * 1024
      if (file.size > maxSize) {
        ElMessage.error('文件大小不能超过 100MB')
        fileList.value = []
        return
      }

      // 上传文件并解析
      uploadingFile.value = true
      try {
        const response = await datasourceService.uploadDataset(file.raw)

        console.log('Upload response:', response) // 调试日志

        if (response && response.success) {
          // 解析预览数据（可能是 JSON 字符串）
          let previewData = []
          if (response.preview) {
            try {
              previewData = typeof response.preview === 'string'
                ? JSON.parse(response.preview)
                : response.preview
            } catch (e) {
              console.warn('Failed to parse preview data:', e)
              previewData = []
            }
          }

          filePreview.value = {
            fileName: response.originalFilename || fileName,
            fileType: response.fileType || '',
            filePath: response.filePath || '',
            columns: response.columns || [],
            previewData: previewData
          }

          // 自动填充数据源名称
          if (!fileDatasource.value.name && fileName) {
            const nameWithoutExt = fileName.substring(0, fileName.lastIndexOf('.'))
            fileDatasource.value.name = nameWithoutExt || fileName
          }

          ElMessage.success('文件上传成功！')
        } else {
          const errorMsg = response?.message || '文件上传失败'
          ElMessage.error(errorMsg)
          fileList.value = []
        }
      } catch (error: any) {
        const errorMsg = error?.response?.data?.message || error?.message || '文件上传失败'
        ElMessage.error('文件上传失败：' + errorMsg)
        console.error('Failed to upload file:', error)
        fileList.value = []
      } finally {
        uploadingFile.value = false
      }
    }

    const createFileDatasource = async () => {
      if (!filePreview.value.filePath) {
        ElMessage.warning('请先上传文件')
        return
      }

      if (!fileDatasource.value.name) {
        ElMessage.warning('请输入数据源名称')
        return
      }

      uploadingFile.value = true
      try {
        const datasourceData: Datasource = {
          name: fileDatasource.value.name,
          description: fileDatasource.value.description || '',
          type: filePreview.value.fileType,
          filePath: filePreview.value.filePath,
          fileType: filePreview.value.fileType,
          originalFilename: filePreview.value.fileName,
          status: 'active',
          testStatus: 'success'
        }

        const response = await datasourceService.createFileDatasource(datasourceData)

        if (response.success && response.data) {
          ElMessage.success('文件数据源创建成功！')

          // 添加到当前智能体
          const addResponse = await agentDatasourceService.addDatasourceToAgent(
            String(props.agentId),
            response.data.id!
          )

          if (addResponse.success) {
            ElMessage.success('数据源已添加到当前智能体')
            dialogVisible.value = false
            await loadAgentDatasource()
          } else {
            ElMessage.warning('数据源创建成功，但添加到智能体失败')
          }
        } else {
          ElMessage.error(response.message || '创建文件数据源失败')
        }
      } catch (error) {
        ElMessage.error('创建文件数据源失败：' + error)
        console.error('Failed to create file datasource:', error)
      } finally {
        uploadingFile.value = false
      }
    }

    onMounted(() => {
      loadAgentDatasource()
    })

    return {
      props,
      Plus,
      UploadFilled,
      datasource,
      initStatus,
      dialogVisible,
      dialogActiveName,
      allDatasource,
      newDatasource,
      editDialogVisible,
      editingDatasource,
      uploadRef,
      fileList,
      uploadingFile,
      filePreview,
      fileDatasource,
      initAgentDatasource,
      changeDatasource,
      testConnection,
      removeAgentDatasource,
      loadAllDatasource,
      addSelectDatasource,
      createNewDatasource,
      handleSelectDatasourceChange,
      editDatasource,
      saveEditDatasource,
      deleteDatasource,
      handleFileChange,
      createFileDatasource
    }
  }
})
</script>

<style scoped>
.form-item {
  margin-bottom: 20px;
}

.form-item label {
  display: block;
  margin-bottom: 8px;
  font-weight: 500;
  color: #606266;
}

.upload-demo {
  width: 100%;
}

.el-icon--upload {
  font-size: 67px;
  color: #409EFF;
  margin-bottom: 16px;
}

.el-upload__text {
  font-size: 14px;
  color: #606266;
}

.el-upload__text em {
  color: #409EFF;
  font-style: normal;
}

.el-upload__tip {
  font-size: 12px;
  color: #909399;
  margin-top: 7px;
}
</style>
