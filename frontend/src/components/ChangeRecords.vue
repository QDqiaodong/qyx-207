<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { benchApi, type ChangeRecord } from '../api'

const records = ref<ChangeRecord[]>([])
const loading = ref(false)
const reversing = ref(false)

const loadRecords = async () => {
  loading.value = true
  try {
    const res = await benchApi.getRecords()
    records.value = res.data
  } catch (error) {
    ElMessage.error('加载变更台账失败')
  } finally {
    loading.value = false
  }
}

// 每张台只有"最近一条未被冲正的正式变更"可冲正（与后端口径一致：
// 冲正单本身、已被冲正的记录都不能再冲，中间隔着记录不许跳冲）
const reversibleRecordIds = computed(() => {
  const ids = new Set<number>()
  const settled = new Set<number>()
  const sorted = [...records.value].sort((a, b) => b.id - a.id)
  for (const r of sorted) {
    if (settled.has(r.benchId)) continue
    if (r.reversalOfId || r.reversedById) continue
    settled.add(r.benchId)
    ids.add(r.id)
  }
  return ids
})

const reverseRecord = async (row: ChangeRecord) => {
  let reason = ''
  try {
    const res = await ElMessageBox.prompt(
      `确认冲正休息台「${row.benchCode}」的变更记录 #${row.id} 吗？` +
      `将补记一条反向记录把影响抵回，原记录保留并标记已被冲正，休息台回到变更前的线路站点。`,
      '冲正确认',
      {
        confirmButtonText: '确认冲正',
        cancelButtonText: '取消',
        inputPlaceholder: '冲正原因（选填）',
        type: 'warning'
      }
    )
    reason = res.value || ''
  } catch {
    return // 取消冲正
  }
  reversing.value = true
  try {
    await benchApi.reverseRecord(row.id, { reason: reason || undefined })
    ElMessage.success('冲正成功，已补记反向记录')
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '冲正失败')
  } finally {
    reversing.value = false
    // 无论成败都刷新：失败时（如该条刚被他人冲正）要看到台账最新状态
    loadRecords()
  }
}

const getChangeTypeLabel = (type: string) => {
  const map: Record<string, string> = {
    'LINE_CHANGE': '线路变更',
    'STATION_CHANGE': '站点变更',
    'LINE_STATION_CHANGE': '线路站点同时变更',
    'REVERSAL': '冲正'
  }
  return map[type] || type
}

const getChangeTypeTag = (type: string) => {
  const map: Record<string, string> = {
    'LINE_CHANGE': 'warning',
    'STATION_CHANGE': 'info',
    'LINE_STATION_CHANGE': 'danger',
    'REVERSAL': 'danger'
  }
  return map[type] || 'info'
}

// 已被冲正的记录整行置灰，一眼看出已失效
const rowClassName = ({ row }: { row: ChangeRecord }) => {
  return row.reversedById ? 'reversed-row' : ''
}

onMounted(loadRecords)
</script>

<template>
  <div class="manage-container">
    <div class="header-bar">
      <h2>变更台账</h2>
      <el-button @click="loadRecords">刷新</el-button>
    </div>

    <el-alert
      type="info"
      :closable="false"
      title="台账只进不出：写错的记录不能改不能删，只能冲正——补记一条反向记录把影响抵回，原记录保留并标记已被冲正。每张台只能冲正最近一条未冲正的变更。"
      style="margin-bottom: 16px"
    />

    <el-table :data="records" :loading="loading" border stripe :row-class-name="rowClassName">
      <el-table-column prop="id" label="单号" width="80" />
      <el-table-column prop="benchCode" label="休息台编号" width="120" />
      <el-table-column prop="changeType" label="变更类型" width="150">
        <template #default="{ row }">
          <el-tag :type="getChangeTypeTag(row.changeType)">
            {{ getChangeTypeLabel(row.changeType) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="线路变更" width="180">
        <template #default="{ row }">
          <span v-if="row.oldLineName">{{ row.oldLineName }}</span>
          <span v-if="row.oldLineName && row.newLineName" style="margin: 0 8px; color: #999">→</span>
          <span v-if="row.newLineName" style="color: #1890ff">{{ row.newLineName }}</span>
          <span v-if="!row.oldLineName && !row.newLineName">-</span>
        </template>
      </el-table-column>
      <el-table-column label="站点变更" width="180">
        <template #default="{ row }">
          <span v-if="row.oldStationName">{{ row.oldStationName }}</span>
          <span v-if="row.oldStationName && row.newStationName" style="margin: 0 8px; color: #999">→</span>
          <span v-if="row.newStationName" style="color: #52c41a">{{ row.newStationName }}</span>
          <span v-if="!row.oldStationName && !row.newStationName">-</span>
        </template>
      </el-table-column>
      <el-table-column prop="changeReason" label="变更原因" min-width="140" />
      <el-table-column label="冲正状态" width="190">
        <template #default="{ row }">
          <template v-if="row.reversalOfId">
            <el-tag type="danger">冲正单</el-tag>
            <span class="reversal-link">冲正 #{{ row.reversalOfId }}</span>
          </template>
          <template v-else-if="row.reversedById">
            <el-tag type="info">已被冲正</el-tag>
            <span class="reversal-link">被 #{{ row.reversedById }} 冲正</span>
          </template>
          <span v-else style="color: #67c23a">有效</span>
        </template>
      </el-table-column>
      <el-table-column prop="operator" label="操作人" width="90" />
      <el-table-column prop="createdAt" label="变更时间" width="170">
        <template #default="{ row }">
          {{ row.createdAt ? new Date(row.createdAt).toLocaleString() : '-' }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="90" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="reversibleRecordIds.has(row.id)"
            size="small"
            type="danger"
            plain
            :loading="reversing"
            @click="reverseRecord(row)"
          >冲正</el-button>
          <span v-else>-</span>
        </template>
      </el-table-column>
    </el-table>

    <div v-if="records.length === 0 && !loading" class="empty-tip">
      <el-empty description="暂无变更记录" />
    </div>
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

.reversal-link {
  margin-left: 6px;
  color: #909399;
  font-size: 12px;
}

.empty-tip {
  padding: 40px;
}

:deep(.reversed-row) {
  color: #a8abb2;
  background-color: #fafafa;
}
</style>
