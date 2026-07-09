<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { lineApi, stationApi, benchApi, type RestBench, type RailwayLine, type Station } from '../api'

const benches = ref<RestBench[]>([])
const lines = ref<RailwayLine[]>([])
const stations = ref<Station[]>([])
const loading = ref(false)
const dialogVisible = ref(false)
const editMode = ref(false)

const filterLineId = ref<number | null>(null)
const filterStationId = ref<number | null>(null)

const form = ref({
  id: 0,
  benchCode: '',
  material: '',
  specification: '',
  positionDesc: '',
  lineId: 0,
  stationId: 0,
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
  try {
    const res = await stationApi.getAll()
    stations.value = res.data
  } catch {
    ElMessage.error('加载站点失败')
  }
}

const loadStationsByLine = async (lineId: number) => {
  try {
    const res = await stationApi.getByLineId(lineId)
    stations.value = res.data
  } catch {
    ElMessage.error('加载站点失败')
  }
}

const loadBenches = async () => {
  loading.value = true
  try {
    const res = await benchApi.getAll()
    benches.value = res.data
  } catch (error) {
    ElMessage.error('加载休息台失败')
  } finally {
    loading.value = false
  }
}

const loadBenchesByLine = async (lineId: number) => {
  loading.value = true
  try {
    const res = await benchApi.getByLineId(lineId)
    benches.value = res.data
  } catch (error) {
    ElMessage.error('加载休息台失败')
  } finally {
    loading.value = false
  }
}

const loadBenchesByStation = async (stationId: number) => {
  loading.value = true
  try {
    const res = await benchApi.getByStationId(stationId)
    benches.value = res.data
  } catch (error) {
    ElMessage.error('加载休息台失败')
  } finally {
    loading.value = false
  }
}

const openDialog = (edit: boolean = false, data?: RestBench) => {
  editMode.value = edit
  if (edit && data) {
    form.value = { 
      ...data, 
      lineId: data.railwayLine?.id || data.lineId,
      stationId: data.station?.id || data.stationId
    }
    if (form.value.lineId) {
      loadStationsByLine(form.value.lineId)
    }
  } else {
    form.value = { id: 0, benchCode: '', material: '', specification: '', positionDesc: '', lineId: 0, stationId: 0, status: 1 }
    stations.value = []
  }
  dialogVisible.value = true
}

const saveBench = async () => {
  if (!form.value.benchCode || !form.value.lineId || !form.value.stationId) {
    ElMessage.warning('请填写必填项')
    return
  }
  try {
    if (editMode.value) {
      await benchApi.update(form.value.id, form.value)
      ElMessage.success('更新成功')
    } else {
      await benchApi.create(form.value)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    loadBenches()
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '操作失败')
  }
}

const deleteBench = async (id: number) => {
  try {
    await ElMessageBox.confirm('确定要删除该休息台吗？', '提示', { type: 'warning' })
    await benchApi.delete(id)
    ElMessage.success('删除成功')
    loadBenches()
  } catch {}
}

watch(filterLineId, (val) => {
  if (val) {
    loadStationsByLine(val)
    loadBenchesByLine(val)
    filterStationId.value = null
  } else {
    loadStations()
    loadBenches()
  }
})

watch(filterStationId, (val) => {
  if (val) {
    loadBenchesByStation(val)
  } else if (!filterLineId.value) {
    loadBenches()
  }
})

onMounted(() => {
  loadLines()
  loadStations()
  loadBenches()
})
</script>

<template>
  <div class="manage-container">
    <div class="header-bar">
      <h2>候车休息台管理</h2>
      <el-button type="primary" @click="openDialog()">添加休息台</el-button>
    </div>
    
    <div class="filter-bar">
      <el-select v-model="filterLineId" placeholder="按线路筛选" clearable style="width: 180px; margin-right: 12px">
        <el-option 
          v-for="line in lines" 
          :key="line.id" 
          :label="line.lineName" 
          :value="line.id" 
        />
      </el-select>
      <el-select v-model="filterStationId" placeholder="按站点筛选" clearable style="width: 180px">
        <el-option 
          v-for="station in stations" 
          :key="station.id" 
          :label="station.stationName" 
          :value="station.id" 
        />
      </el-select>
    </div>
    
    <el-table :data="benches" :loading="loading" border stripe>
      <el-table-column prop="benchCode" label="休息台编号" width="130" />
      <el-table-column label="所属线路" width="130">
        <template #default="{ row }">
          <el-tag type="primary">{{ row.railwayLine?.lineName || '-' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="所属站点" width="150">
        <template #default="{ row }">
          <el-tag type="success">{{ row.station?.stationName || '-' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="material" label="材质" width="120" />
      <el-table-column prop="specification" label="摆放规格" width="150" />
      <el-table-column prop="positionDesc" label="位置描述" />
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
          <el-button size="small" type="danger" @click="deleteBench(row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    
    <el-dialog :title="editMode ? '编辑休息台' : '添加休息台'" v-model="dialogVisible" width="600px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="休息台编号" required>
          <el-input v-model="form.benchCode" placeholder="如：B001" />
        </el-form-item>
        <el-form-item label="所属线路" required>
          <el-select v-model="form.lineId" placeholder="选择线路" @change="loadStationsByLine(form.lineId)">
            <el-option 
              v-for="line in lines" 
              :key="line.id" 
              :label="line.lineName" 
              :value="line.id" 
            />
          </el-select>
        </el-form-item>
        <el-form-item label="所属站点" required>
          <el-select v-model="form.stationId" placeholder="选择站点">
            <el-option 
              v-for="station in stations" 
              :key="station.id" 
              :label="station.stationName" 
              :value="station.id" 
            />
          </el-select>
        </el-form-item>
        <el-form-item label="材质">
          <el-input v-model="form.material" placeholder="如：不锈钢、木质、塑料" />
        </el-form-item>
        <el-form-item label="摆放规格">
          <el-input v-model="form.specification" placeholder="如：双人座、三人座" />
        </el-form-item>
        <el-form-item label="位置描述">
          <el-input v-model="form.positionDesc" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveBench">确定</el-button>
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
