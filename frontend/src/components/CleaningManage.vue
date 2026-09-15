<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  lineApi, stationApi, benchApi, cleaningApi, sittingApi,
  type RestBench, type RailwayLine, type Station,
  type CleaningOccupancy, type BenchSitting
} from '../api'

const benches = ref<RestBench[]>([])
const lines = ref<RailwayLine[]>([])
const stations = ref<Station[]>([])
const occupancies = ref<CleaningOccupancy[]>([])
const sittings = ref<BenchSitting[]>([])
const loading = ref(false)
const selected = ref<RestBench[]>([])
const cleaningReason = ref('')

const filterLineId = ref<number | null>(null)
const filterStationId = ref<number | null>(null)

const activeBenchIds = computed(() => new Set(occupancies.value.map(o => o.bench?.id)))
const sittingBenchIds = computed(() => new Set(sittings.value.map(s => s.bench?.id)))

const activeOccupancyByBench = (benchId: number) =>
  occupancies.value.find(o => o.bench?.id === benchId)
const sittingByBench = (benchId: number) =>
  sittings.value.find(s => s.bench?.id === benchId)

// 现场口径：只列出本站范围里的台，逐张勾选点名，不提供整线/整站一把锁
const displayedBenches = computed(() => benches.value)

const loadLines = async () => {
  try {
    lines.value = (await lineApi.getAll()).data
  } catch {
    ElMessage.error('加载线路失败')
  }
}

const loadStations = async () => {
  try {
    const res = filterLineId.value
      ? await stationApi.getByLineId(filterLineId.value)
      : await stationApi.getAll()
    stations.value = res.data
  } catch {
    ElMessage.error('加载站点失败')
  }
}

const loadBenches = async () => {
  loading.value = true
  try {
    const res = filterStationId.value
      ? await benchApi.getByStationId(filterStationId.value)
      : filterLineId.value
        ? await benchApi.getByLineId(filterLineId.value)
        : await benchApi.getAll()
    benches.value = res.data
  } catch {
    ElMessage.error('加载休息台失败')
  } finally {
    loading.value = false
  }
}

const refreshAll = async () => {
  const [occ, sit] = await Promise.all([
    cleaningApi.getActive(),
    sittingApi.getAll()
  ])
  occupancies.value = occ.data
  sittings.value = sit.data
}

// 开清扫：只把勾选中的具体台点名锁定
const startCleaning = async () => {
  if (selected.value.length === 0) {
    ElMessage.warning('请逐张勾选本次要清扫的具体脏台，不支持整线锁定')
    return
  }
  const codes = selected.value.map(b => b.benchCode).join('、')
  try {
    await ElMessageBox.confirm(
      `只对点名的 ${selected.value.length} 张台开清扫：${codes}。占台后新客人不能落座，在座客人不清走。`,
      '确认清扫占台',
      { type: 'warning', confirmButtonText: '开清扫' }
    )
  } catch {
    return
  }
  try {
    await cleaningApi.start({
      benchIds: selected.value.map(b => b.id),
      reason: cleaningReason.value || undefined
    })
    ElMessage.success('清扫占台已生效')
    selected.value = []
    cleaningReason.value = ''
    await Promise.all([refreshAll(), loadBenches()])
  } catch (error: any) {
    // 并发重复占台/台不存在等：后端整体回滚，没有半截单据
    ElMessage.error(error.response?.data?.message || '开清扫失败，占台未生效')
    await refreshAll()
  }
}

const finishCleaning = async (occupancy: CleaningOccupancy) => {
  try {
    await cleaningApi.finish(occupancy.id)
    ElMessage.success('清扫结束，台已放开')
    await Promise.all([refreshAll(), loadBenches()])
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '结束清扫失败')
    await refreshAll()
  }
}

// —— 以下落座/离开用于现场核对“拦新坐、不清人”口径 ——
const sitDialogVisible = ref(false)
const sitBench = ref<RestBench | null>(null)
const sitForm = ref({ passengerName: '', passengerKey: '' })

const openSitDialog = (row: RestBench) => {
  sitBench.value = row
  sitForm.value = { passengerName: '', passengerKey: '' }
  sitDialogVisible.value = true
}

const submitSit = async () => {
  if (!sitBench.value) return
  if (!sitForm.value.passengerKey.trim()) {
    ElMessage.warning('请填写客人标识')
    return
  }
  try {
    await sittingApi.sit(sitBench.value.id, {
      passengerName: sitForm.value.passengerName || undefined,
      passengerKey: sitForm.value.passengerKey
    })
    ElMessage.success('已落座')
    sitDialogVisible.value = false
    await refreshAll()
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '落座失败')
  }
}

const leaveSeat = async (row: RestBench) => {
  const sitting = sittingByBench(row.id)
  if (!sitting) return
  try {
    await sittingApi.leave(sitting.id)
    ElMessage.success('客人已离开')
    await refreshAll()
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '离开失败')
  }
}

const onLineChange = () => {
  filterStationId.value = null
  selected.value = []
  loadStations()
  loadBenches()
}

const onStationChange = () => {
  selected.value = []
  loadBenches()
}

onMounted(async () => {
  await Promise.all([loadLines(), loadStations()])
  await Promise.all([loadBenches(), refreshAll()])
})
</script>

<template>
  <div class="manage-container">
    <div class="header-bar">
      <h2>清扫占台</h2>
    </div>

    <el-alert
      type="info"
      :closable="false"
      title="按现场口径：必须逐张点名具体脏台，只锁本站被勾选的台，不支持整线一把梭。占台后新客人不能落座，已在座客人不清走、可坐到自行离开。"
      style="margin-bottom: 16px"
    />

    <div class="filter-bar">
      <el-select v-model="filterLineId" placeholder="按线路筛选" clearable style="width: 200px; margin-right: 12px"
                 @change="onLineChange">
        <el-option v-for="line in lines" :key="line.id" :label="line.lineName" :value="line.id" />
      </el-select>
      <el-select v-model="filterStationId" placeholder="按站点筛选（本站脏台）" clearable style="width: 220px"
                 @change="onStationChange">
        <el-option v-for="station in stations" :key="station.id" :label="station.stationName" :value="station.id" />
      </el-select>
    </div>

    <div class="action-bar">
      <el-input
        v-model="cleaningReason"
        placeholder="清扫原因（可选）"
        style="width: 260px; margin-right: 12px"
      />
      <el-button type="primary" :disabled="selected.length === 0" @click="startCleaning">
        开清扫占台（点名 {{ selected.length }} 张）
      </el-button>
    </div>

    <el-table
      :data="displayedBenches"
      v-loading="loading"
      border
      stripe
      row-key="id"
      @selection-change="(val: RestBench[]) => (selected = val)"
    >
      <el-table-column
        :selectable="(row: RestBench) => !activeBenchIds.has(row.id) && row.status !== 0"
        width="48"
        type="selection"
      />
      <el-table-column prop="benchCode" label="休息台编号" width="130" />
      <el-table-column label="所属线路" width="130">
        <template #default="{ row }">
          {{ row.railwayLine?.lineName || '-' }}
        </template>
      </el-table-column>
      <el-table-column label="所属站点" width="150">
        <template #default="{ row }">
          {{ row.station?.stationName || '-' }}
        </template>
      </el-table-column>
      <el-table-column label="座位状态" width="120">
        <template #default="{ row }">
          <el-tag v-if="sittingBenchIds.has(row.id)" type="success">
            在座：{{ sittingByBench(row.id)?.passengerName || sittingByBench(row.id)?.passengerKey }}
          </el-tag>
          <el-tag v-else type="info">空台</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="清扫状态" width="140">
        <template #default="{ row }">
          <el-tag v-if="activeBenchIds.has(row.id)" type="danger">清扫中（拦新坐）</el-tag>
          <el-tag v-else-if="row.status === 2" type="warning">待转运</el-tag>
          <el-tag v-else type="success">可落座</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="260" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="activeBenchIds.has(row.id)"
            size="small"
            type="danger"
            @click="finishCleaning(activeOccupancyByBench(row.id)!)"
          >结束清扫</el-button>
          <el-button
            v-else
            size="small"
            type="primary"
            :disabled="sittingBenchIds.has(row.id)"
            @click="openSitDialog(row)"
          >客人落座</el-button>
          <el-button
            v-if="sittingBenchIds.has(row.id)"
            size="small"
            @click="leaveSeat(row)"
          >客人离开</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog title="客人落座" v-model="sitDialogVisible" width="420px">
      <el-alert
        v-if="sitBench && activeBenchIds.has(sitBench.id)"
        type="error"
        :closable="false"
        title="该台清扫占台中，新客人不能落座"
        style="margin-bottom: 12px"
      />
      <el-form :model="sitForm" label-width="90px">
        <el-form-item label="休息台">
          <el-input :model-value="sitBench?.benchCode" disabled />
        </el-form-item>
        <el-form-item label="客人称呼">
          <el-input v-model="sitForm.passengerName" placeholder="如：张三" />
        </el-form-item>
        <el-form-item label="客人标识" required>
          <el-input v-model="sitForm.passengerKey" placeholder="如：票号/手机号" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="sitDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitSit">落座</el-button>
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

.filter-bar,
.action-bar {
  margin-bottom: 16px;
}
</style>
