<template>
  <div class="allSignList-box">
    <el-tabs v-model="activeTab" type="border-card">
      <el-tab-pane label="All Sign-ins" name="allSignList">
        <div class="table-wrapper">
          <!-- <el-empty 
                        description="No orders" 
                        image="data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='200' height='200'%3E%3Crect width='200' height='200' fill='%23f5f5f7'/%3E%3Ctext x='50%25' y='50%25' font-size='16' fill='%2386868b' text-anchor='middle' dominant-baseline='middle'%3ENo Data%3C/text%3E%3C/svg%3E" 
                        style="margin-top: 100px; margin-bottom: 200px;" 
                        image-size="300"></el-empty> -->

          <div class="search-area">
            <el-input
              placeholder="Sign-in Content"
              v-model="search.userId"
              prefix-icon="el-icon-search"
              clearable
              style="width: 200px; margin-right: 10px; margin-bottom: 8px"
            ></el-input>
            <el-input
              placeholder="Publisher"
              v-model="search.userId"
              prefix-icon="el-icon-search"
              clearable
              style="width: 200px; margin-right: 10px; margin-bottom: 8px"
            ></el-input>

            <el-select
              v-model="search.value"
              placeholder="Please select"
              no-data-text="No Data"
              style="width: 200px; margin-right: 10px; margin-bottom: 8px"
            >
              <el-option
                v-for="item in search.options"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              >
              </el-option>
            </el-select>

            <el-date-picker
              v-model="search.commentDate"
              value-format="yyyy-MM-dd"
              type="daterange"
              align="right"
              unlink-panels
              range-separator="to"
              start-placeholder="Publish Date"
              end-placeholder="Publish Date"
              :picker-options="pickerOptions"
              style="width: 360px; margin-right: 10px; margin-bottom: 8px"
            >
            </el-date-picker>
            <el-button type="primary" size="middle" @click="searchUser()"
              >Search</el-button
            >
          </div>

          <el-table
            :data="tableData"
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
              label="Mood"
              header-align="center"
              align="center"
              min-width="60"
            >
              <template slot-scope="scope">
                <img :src="scope.row.face" style="width: 60px; height: 60px" />
              </template>
            </el-table-column>

            <el-table-column
              label="Sign-in Content"
              header-align="center"
              align="left"
              min-width="220"
            >
              <template slot-scope="scope">
                <div class="topic-comments">{{ scope.row.titleName }}</div>
              </template>
            </el-table-column>

            <el-table-column
              prop="commentUser"
              label="Publisher"
              width="160"
              header-align="center"
              align="center"
            ></el-table-column>

            <el-table-column
              label="Review Status"
              header-align="center"
              align="center"
              width="150"
            >
              <template slot-scope="scope">
                <el-tag type="info" v-if="scope.row.status === 0"
                  >Pending</el-tag
                >
                <el-tag type="success" v-if="scope.row.status === 1"
                  >Approved</el-tag
                >
                <el-tag type="danger" v-if="scope.row.status === 2"
                  >Rejected</el-tag
                >
              </template>
            </el-table-column>

            <el-table-column
              prop="dateTime"
              label="Publish Time"
              width="240"
              header-align="center"
              align="center"
            ></el-table-column>

            <el-table-column
              label="Actions"
              width="240"
              header-align="center"
              align="center"
            >
              <template slot-scope="scope">
                <el-button
                  type="success"
                  size="mini"
                  plain
                  @click="pass(scope.row.id)"
                  >Approve</el-button
                >
                <el-button
                  type="danger"
                  size="mini"
                  plain
                  @click="unpass(scope.row.id)"
                  >Reject</el-button
                >
              </template>
            </el-table-column>
          </el-table>
        </div>

        <el-pagination
          background
          layout="prev, pager, next"
          :total="1000"
        ></el-pagination>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script>
module.exports = {
  data() {
    return {
      activeTab: "allSignList",

      dialogTopicCommentVisible: false,
      bookForm: {
        title: "",
        sort: null,
      },

      search: {
        userId: "",
        orderId: "",
        commentDate: "",
        options: [
          {
            value: "-1",
            label: "All",
          },
          {
            value: "0",
            label: "Pending",
          },
          {
            value: "1",
            label: "Approved",
          },
          {
            value: "2",
            label: "Rejected",
          },
        ],
        value: "-1",
      },

      pickerOptions: {
        firstDayOfWeek: 0, // Sunday (default for English calendars)
        shortcuts: [
          {
            text: "Last Week",
            onClick(picker) {
              const end = new Date();
              const start = new Date();
              start.setTime(start.getTime() - 3600 * 1000 * 24 * 7);
              picker.$emit("pick", [start, end]);
            },
          },
          {
            text: "Last Month",
            onClick(picker) {
              const end = new Date();
              const start = new Date();
              start.setTime(start.getTime() - 3600 * 1000 * 24 * 30);
              picker.$emit("pick", [start, end]);
            },
          },
          {
            text: "Last 3 Months",
            onClick(picker) {
              const end = new Date();
              const start = new Date();
              start.setTime(start.getTime() - 3600 * 1000 * 24 * 90);
              picker.$emit("pick", [start, end]);
            },
          },
        ],
      },

      tableData: [
        {
          id: "1111",
          titleName: "Java Backend Engineer",
          commentContent: "Very nice background!",
          time: 120,
          counts: 100,
          face: "data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='60' height='60'%3E%3Ccircle cx='30' cy='30' r='30' fill='%23f5f5f7'/%3E%3Ctext x='50%25' y='50%25' font-size='24' fill='%2386868b' text-anchor='middle' dominant-baseline='middle'%3E😐%3C/text%3E%3C/svg%3E",
          commentRate: 4,
          commentUser: "Demo User 1",
          status: 1, // 0 Pending 1 Approved 2 Rejected
          sort: 8,
          oldPrice: 2999,
          newPrice: 1599,
          dateTime: "2025-12-12 12:15:15",
        },
        {
          id: "2222",
          titleName: "Java Backend Engineer",
          commentContent: "Sample Content",
          face: "data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='60' height='60'%3E%3Ccircle cx='30' cy='30' r='30' fill='%23f5f5f7'/%3E%3Ctext x='50%25' y='50%25' font-size='24' fill='%2386868b' text-anchor='middle' dominant-baseline='middle'%3E😐%3C/text%3E%3C/svg%3E",
          time: 120,
          counts: 100,
          commentRate: 3,
          commentUser: "Demo User 2",
          status: 2,
          sort: 7,
          oldPrice: 3999,
          newPrice: 2599,
          dateTime: "2025-12-12 12:15:15",
        },
        {
          id: "2222",
          titleName: "Java Backend Engineer",
          commentContent: "Sample Content",
          time: 120,
          counts: 100,
          commentRate: 3,
          commentUser: "Demo User 3",
          face: "data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='60' height='60'%3E%3Ccircle cx='30' cy='30' r='30' fill='%23f5f5f7'/%3E%3Ctext x='50%25' y='50%25' font-size='24' fill='%2386868b' text-anchor='middle' dominant-baseline='middle'%3E😐%3C/text%3E%3C/svg%3E",
          status: 0,
          sort: 3,
          oldPrice: 3999,
          newPrice: 2599,
          dateTime: "2025-12-12 12:15:15",
        },
      ],
    };
  },
  methods: {
    pass(commentId) {
      this.$message.success("Comment approved!");
    },

    unpass(commentId) {
      this.$message.success("Comment rejected!");
    },

    modifyCommentSort(commentId) {
      this.dialogTopicCommentVisible = true;
    },
  },
};
</script>

<style>
.allSignList-box {
  /* padding: 10px; */

  display: flex;
  flex-direction: column;
  justify-content: flex-start;

  /* border: solid 10px #e3e9ef; */

  font-size: 16px;
}

.allSignList-box .table-wrapper {
  padding: 10px 10px 20px 10px;
}

.allSignList-box .table-list {
  width: 100%;
  font-size: 15px;
}

.allSignList-box .search-area {
  margin-bottom: 20px;
}

.allSignList-box .review-topic-title {
  color: #262626;
  font-size: 36px;
  font-weight: 700;

  padding: 10px 20px;
}

.allSignList-box .review-topic-detail {
  padding: 20px;
}

.allSignList-box .topic-comments {
  overflow: hidden;
  text-overflow: ellipsis;
  word-break: break-word;
  display: -webkit-box;
  -webkit-line-clamp: 1;
  -webkit-box-orient: vertical;
}
</style>
