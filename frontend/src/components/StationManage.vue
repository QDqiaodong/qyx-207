<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { lineApi, stationApi, type Station, type RailwayLine } from '../api'

const stations = ref<Station[]>([])
const lines = ref<RailwayLine[]>([])
const loading = ref(false)
const dialogVisible = ref(false)
const editMode = ref(false)
const selectedLineId = ref<number | null>(null)

const form = ref({
  id: 0,
  stationCode: '',
  stationName: '',
  lineOrder: 0,
  lineId: 0,
  status: 1
})

const loadLines = async () => {
  try {
    const res = await lineApi.getAll()
    lines.value = res.data
  } catch {
    ElMessage.error('加载线路失败')
  }
}

const loadStations = async () => {
  loading.value = true
  try {
    const res = await stationApi.getAll()
    stations.value = res.data
  } catch (error) {
    ElMessage.error('加载站点失败')
  } finally {
    loading.value = false
  }
}

const loadStationsByLine = async (lineId: number) => {
  loading.value = true
  try {
    const res = await stationApi.getByLineId(lineId)
    stations.value = res.data
  } catch (error) {
    ElMessage.error('加载站点失败')
  } finally {
    loading.value = false
  }
}

const openDialog = (edit: boolean = false, data?: Station) => {
  editMode.value = edit
  if (edit && data) {
    form.value = { 
      ...data, 
      lineId: data.railwayLine?.id || data.lineId 
    }
  } else {
    form.value = { id: 0, stationCode: '', stationName: '', lineOrder: 0, lineId: 0, status: 1 }
  }
  dialogVisible.value = true
}

const saveStation = async () => {
  if (!form.value.stationCode || !form.value.stationName || !form.value.lineId) {
    ElMessage.warning('请填写必填项')
    return
  }
  try {
    if (editMode.value) {
      await stationApi.update(form.value.id, form.value)
      ElMessage.success('更新成功')
    } else {
      await stationApi.create(form.value)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    loadStations()
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '操作失败')
  }
}

const deleteStation = async (id: number) => {
  try {
    await ElMessageBox.confirm('确定要删除该站点吗？', '提示', { type: 'warning' })
    await stationApi.delete(id)
    ElMessage.success('删除成功')
    loadStations()
  } catch {}
}

watch(selectedLineId, (val) => {
  if (val) {
    loadStationsByLine(val)
  } else {
    loadStations()
  }
})

onMounted(() => {
  loadLines()
  loadStations()
})
</script>

<template>
  <div class="manage-container">
    <div class="header-bar">
      <h2>站点管理</h2>
      <el-button type="primary" @click="openDialog()">添加站点</el-button>
    </div>
    
    <div class="filter-bar">
      <el-select v-model="selectedLineId" placeholder="按线路筛选" clearable style="width: 200px">
        <el-option 
          v-for="line in lines" 
          :key="line.id" 
          :label="line.lineName" 
          :value="line.id" 
        />
      </el-select>
    </div>
    
    <el-table :data="stations" :loading="loading" border stripe>
      <el-table-column prop="stationCode" label="站点编码" width="120" />
      <el-table-column prop="stationName" label="站点名称" width="150" />
      <el-table-column label="所属线路" width="150">
        <template #default="{ row }">
          {{ row.railwayLine?.lineName || '-' }}
        </template>
      </el-table-column>
      <el-table-column prop="lineOrder" label="线路顺序" width="100" />
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
          <el-button size="small" type="danger" @click="deleteStation(row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    
    <el-dialog :title="editMode ? '编辑站点' : '添加站点'" v-model="dialogVisible" width="500px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="站点编码" required>
          <el-input v-model="form.stationCode" placeholder="如：S001" />
        </el-form-item>
        <el-form-item label="站点名称" required>
          <el-input v-model="form.stationName" placeholder="如：人民广场站" />
        </el-form-item>
        <el-form-item label="所属线路" required>
          <el-select v-model="form.lineId" placeholder="选择线路">
            <el-option 
              v-for="line in lines" 
              :key="line.id" 
              :label="line.lineName" 
              :value="line.id" 
            />
          </el-select>
        </el-form-item>
        <el-form-item label="线路顺序">
          <el-input-number v-model="form.lineOrder" :min="0" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveStation">确定</el-button>
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
  margin-bottom: 16px;
}

.header-bar h2 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
}

.filter-bar {
  margin-bottom: 16px;
}
</style>
