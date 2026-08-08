<template>
  <div class="orderlist-box">
    <el-tabs v-model="activeTab" type="border-card">
      <el-tab-pane label="Job List" name="allJobList">
        <div class="table-wrapper">
          <el-table
            :data="jobList"
            border
            stripe
            class="table-list"
            empty-text="No Data"
          >
            <el-table-column
              type="index"
              header-align="center"
              align="center"
              width="50"
            ></el-table-column>
            <el-table-column
              label="Job Name"
              header-align="center"
              align="center"
              width="180"
            >
              <template slot-scope="scope">
                {{ scope.row.jobName }}
              </template>
            </el-table-column>
            <el-table-column
              label="Job Description"
              header-align="center"
              align="left"
              width="220"
              class-name="job-desc-cell"
            >
              <template slot-scope="scope">
                {{ scope.row.jobDesc }}
              </template>
            </el-table-column>
            <el-table-column
              label="Status"
              header-align="center"
              align="center"
              width="80"
              class-name="job-status-cell"
            >
              <template slot-scope="scope">
                <span
                  ><el-tag
                    type="success"
                    v-if="scope.row.status == 1"
                    class="job-status-tag"
                    >Open</el-tag
                  ></span
                >
                <span
                  ><el-tag
                    type="danger"
                    v-if="scope.row.status == 0"
                    class="job-status-tag"
                    >Closed</el-tag
                  ></span
                >
              </template>
            </el-table-column>
            <el-table-column
              label="Interviewer"
              header-align="center"
              align="center"
              width="160"
            >
              <template slot-scope="scope">
                {{ scope.row.interviewerName }}
              </template>
            </el-table-column>
            <el-table-column
              label="Prompt Prefix"
              header-align="center"
              align="left"
              min-width="180"
            >
              <template slot-scope="scope">
                {{ scope.row.prompt }}
              </template>
            </el-table-column>

            <el-table-column
              prop="createTime"
              label="Create Time"
              width="180"
              header-align="center"
              align="center"
            ></el-table-column>
            <el-table-column
              prop="updatedTime"
              label="Update Time"
              width="180"
              header-align="center"
              align="center"
            ></el-table-column>

            <el-table-column
              label="Actions"
              width="200"
              header-align="center"
              align="center"
            >
              <template slot-scope="scope">
                <div class="job-actions">
                  <el-button
                    type="primary"
                    size="mini"
                    plain
                    @click="gotoModifyJob(scope.row.jobId)"
                    >Edit</el-button
                  >
                  <el-button
                    type="danger"
                    size="mini"
                    plain
                    @click="deleteJob(scope.row.jobId)"
                    >Delete</el-button
                  >
                </div>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <el-pagination
          background
          @current-change="doPageList"
          layout="total, prev, pager, next"
          :page-size="jobListPageInfo.pageSize"
          :total="jobListPageInfo.records"
        >
        </el-pagination>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script>
module.exports = {
  data() {
    return {
      activeTab: "allJobList",

      dialogUserInfoVisible: false,

      search: {
        userId: "",
        orderId: "",
      },

      jobList: [],
      jobListPageInfo: {
        page: 1,
        pageSize: 10,
        records: 0, // Total records
        total: 0, // Total pages
      },
      searchBO: {},
    };
  },
  mounted() {
    this.initJobList(1, this.jobListPageInfo.pageSize);
  },
  methods: {
    gotoModifyJob(jobId) {
      var targetPath = "/jobMng/jobCreate?jobId=" + jobId;
      this.$parent.$router.push({
        path: targetPath,
      });
    },

    doPageList(page) {
      this.initJobList(page, this.jobListPageInfo.pageSize);
    },

    initJobList(page, pageSize) {
      console.log(page);

      var searchBO = this.searchBO;
      searchBO.page = page;
      searchBO.pageSize = pageSize;

      jobApi.list(searchBO).then((response) => {
        console.log(response);
        var jobList = response.data.rows;
        this.jobList = jobList;

        this.jobListPageInfo.records = response.data.records;
        this.jobListPageInfo.total = response.data.total;
        this.jobListPageInfo.page = page;
        this.jobListPageInfo.pageSize = pageSize;
      });
    },

    deleteJob(jobId) {
      this.$confirm("Are you sure you want to delete this job?", "Confirm", {
        confirmButtonText: "Confirm Delete",
        cancelButtonText: "Cancel",
        type: "warning",
      })
        .then(() => {
          jobApi.delete(jobId).then((response) => {
            //     console.log(response);

            this.$message({
              showClose: true,
              message: "Job deleted successfully!",
              type: "success",
              duration: 2000,
            });

            this.initJobList(1, this.jobListPageInfo.pageSize);
          });
        })
        .catch(() => {});
    },
  },
};
</script>

<style>
.orderlist-box {
  /* padding: 10px; */

  display: flex;
  flex-direction: column;
  justify-content: flex-start;

  /* border: solid 10px #e3e9ef; */

  font-size: 16px;
}

.orderlist-box .table-wrapper {
  padding: 10px 10px 20px 10px;
}

.orderlist-box .table-list {
  width: 100%;
  font-size: 15px;
}

.orderlist-box .search-area {
  margin-bottom: 20px;
}

.orderlist-box .job-status-cell .cell {
  display: flex;
  align-items: center;
  justify-content: center;
}
.orderlist-box .job-status-tag {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  line-height: 1.2;
}

.orderlist-box .job-desc-cell .cell {
  word-break: break-word;
  overflow-wrap: break-word;
  white-space: normal;
  line-height: 1.4;
}

.orderlist-box .job-actions {
  display: flex;
  flex-direction: row;
  flex-wrap: nowrap;
  align-items: center;
  justify-content: center;
  gap: 8px;
}
</style>
