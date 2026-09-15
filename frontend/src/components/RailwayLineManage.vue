<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { lineApi, type RailwayLine } from '../api'

const lines = ref<RailwayLine[]>([])
const loading = ref(false)
const dialogVisible = ref(false)
const editMode = ref(false)
const form = ref({
  id: 0,
  lineCode: '',
  lineName: '',
  lineType: '',
  description: '',
  status: 1
})

const loadLines = async () => {
  loading.value = true
  try {
    const res = await lineApi.getAll()
    lines.value = res.data
  } catch (error) {
    ElMessage.error('加载线路失败')
  } finally {
    loading.value = false
  }
}

const openDialog = (edit: boolean = false, data?: RailwayLine) => {
  editMode.value = edit
  if (edit && data) {
    form.value = { ...data }
  } else {
    form.value = { id: 0, lineCode: '', lineName: '', lineType: '', description: '', status: 1 }
  }
  dialogVisible.value = true
}

const saveLine = async () => {
  if (!form.value.lineCode || !form.value.lineName) {
    ElMessage.warning('请填写必填项')
    return
  }
  try {
    if (editMode.value) {
      await lineApi.update(form.value.id, form.value)
      ElMessage.success('更新成功')
    } else {
      await lineApi.create(form.value)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    loadLines()
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '操作失败')
  }
}

const deleteLine = async (id: number) => {
  try {
    await ElMessageBox.confirm(
      '确定要作废该线路吗？线路上还有未删除的站点时将被拒绝，需先清空站点。',
      '作废确认',
      { type: 'warning', confirmButtonText: '确认作废', cancelButtonText: '取消' }
    )
  } catch {
    return // 用户取消
  }
  try {
    await lineApi.delete(id)
    ElMessage.success('线路已作废')
    loadLines()
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '作废失败')
  }
}

onMounted(loadLines)
</script>

<template>
  <div class="manage-container">
    <div class="header-bar">
      <h2>轨道交通线路管理</h2>
      <el-button type="primary" @click="openDialog()">添加线路</el-button>
    </div>
    
    <el-table :data="lines" :loading="loading" border stripe>
      <el-table-column prop="lineCode" label="线路编码" width="120" />
      <el-table-column prop="lineName" label="线路名称" width="150" />
      <el-table-column prop="lineType" label="线路类型" width="100">
        <template #default="{ row }">
          <el-tag :type="row.lineType === '地铁' ? 'success' : 'primary'">
            {{ row.lineType || '-' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="description" label="描述" />
      <el-table-column prop="status" label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'danger'">
            {{ row.status === 1 ? '正常' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="openDialog(true, row)">编辑</el-button>
          <el-button size="small" type="danger" @click="deleteLine(row.id)">作废</el-button>
        </template>
      </el-table-column>
    </el-table>
    
    <el-dialog :title="editMode ? '编辑线路' : '添加线路'" v-model="dialogVisible" width="500px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="线路编码" required>
          <el-input v-model="form.lineCode" placeholder="如：L1" />
        </el-form-item>
        <el-form-item label="线路名称" required>
          <el-input v-model="form.lineName" placeholder="如：1号线" />
        </el-form-item>
        <el-form-item label="线路类型">
          <el-select v-model="form.lineType" placeholder="选择类型">
            <el-option label="地铁" value="地铁" />
            <el-option label="轻轨" value="轻轨" />
            <el-option label="高铁" value="高铁" />
          </el-select>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveLine">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.manage-container {
  padding: 20px;
}

.header-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.header-bar h2 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
}
</style>
