<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { benchApi, type ChangeRecord } from '../api'

const records = ref<ChangeRecord[]>([])
const loading = ref(false)

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

const getChangeTypeLabel = (type: string) => {
  const map: Record<string, string> = {
    'LINE_CHANGE': '线路变更',
    'STATION_CHANGE': '站点变更',
    'LINE_STATION_CHANGE': '线路站点同时变更'
  }
  return map[type] || type
}

const getChangeTypeTag = (type: string) => {
  const map: Record<string, string> = {
    'LINE_CHANGE': 'warning',
    'STATION_CHANGE': 'info',
    'LINE_STATION_CHANGE': 'danger'
  }
  return map[type] || 'info'
}

onMounted(loadRecords)
</script>

<template>
  <div class="manage-container">
    <div class="header-bar">
      <h2>变更台账</h2>
      <el-button @click="loadRecords">刷新</el-button>
    </div>
    
    <el-table :data="records" :loading="loading" border stripe>
      <el-table-column prop="benchCode" label="休息台编号" width="130" />
      <el-table-column prop="changeType" label="变更类型" width="150">
        <template #default="{ row }">
          <el-tag :type="getChangeTypeTag(row.changeType)">
            {{ getChangeTypeLabel(row.changeType) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="线路变更" width="200">
        <template #default="{ row }">
          <span v-if="row.oldLineName">{{ row.oldLineName }}</span>
          <span v-if="row.oldLineName && row.newLineName" style="margin: 0 8px; color: #999">→</span>
          <span v-if="row.newLineName" style="color: #1890ff">{{ row.newLineName }}</span>
          <span v-if="!row.oldLineName && !row.newLineName">-</span>
        </template>
      </el-table-column>
      <el-table-column label="站点变更" width="200">
        <template #default="{ row }">
          <span v-if="row.oldStationName">{{ row.oldStationName }}</span>
          <span v-if="row.oldStationName && row.newStationName" style="margin: 0 8px; color: #999">→</span>
          <span v-if="row.newStationName" style="color: #52c41a">{{ row.newStationName }}</span>
          <span v-if="!row.oldStationName && !row.newStationName">-</span>
        </template>
      </el-table-column>
      <el-table-column prop="changeReason" label="变更原因" />
      <el-table-column prop="operator" label="操作人" width="100" />
      <el-table-column prop="createdAt" label="变更时间" width="180">
        <template #default="{ row }">
          {{ row.createdAt ? new Date(row.createdAt).toLocaleString() : '-' }}
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

.empty-tip {
  padding: 40px;
}
</style>
