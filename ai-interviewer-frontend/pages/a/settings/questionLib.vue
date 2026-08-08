<template>
  <div class="allQuestionList-box">
    <el-tabs v-model="activeTab" type="border-card">
      <el-tab-pane label="Question Library" name="allTechTagsList">
        <div class="table-wrapper">
          <div class="search-area">
            <el-input
              placeholder="Interviewer"
              v-model="searchBO.aiName"
              prefix-icon="el-icon-user"
              clearable
              style="width: 200px; margin-right: 10px; margin-bottom: 8px"
            ></el-input>
            <el-input
              placeholder="Question"
              v-model="searchBO.question"
              prefix-icon="el-icon-folder-opened"
              clearable
              style="width: 200px; margin-right: 10px; margin-bottom: 8px"
            ></el-input>
            <el-button type="primary" size="middle" @click="searchQuestion()"
              >Search</el-button
            >

            <el-button
              type="primary"
              plain
              size="middle"
              @click="createQuestion()"
              >Add Question</el-button
            >
          </div>

          <el-table
            :data="questionList"
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
              label="Question"
              header-align="center"
              align="left"
              min-width="180"
            >
              <template slot-scope="scope">{{ scope.row.question }}</template>
            </el-table-column>

            <el-table-column
              label="Reference Answer"
              header-align="center"
              align="left"
              min-width="220"
            >
              <template slot-scope="scope">{{
                scope.row.referenceAnswer
              }}</template>
            </el-table-column>

            <el-table-column
              prop="interviewerName"
              label="Interviewer"
              width="160"
              header-align="center"
              align="center"
            ></el-table-column>

            <el-table-column
              label="AI Video"
              header-align="center"
              align="center"
              width="140"
            >
              <template slot-scope="scope">
                <!-- <a :href="scope.row.aiSrc" target="_blank">View</a> -->
                <video
                  v-if="scope.row.aiSrc"
                  :src="scope.row.aiSrc"
                  controls
                  :autoplayNew="false"
                  class="ai-video"
                >
                  <!-- <source :src="scope.row.aiSrc" type="video/mp4" /> -->
                </video>
              </template>
            </el-table-column>

            <el-table-column
              label="Enabled"
              header-align="center"
              align="center"
              width="100"
            >
              <template slot-scope="scope">
                <el-switch
                  :value="scope.row.isOn"
                  :active-value="activeValue"
                  :inactive-value="inactiveValue"
                  @change="
                    setQuestionDisplay(scope.row.questionLibId, scope.row.isOn)
                  "
                >
                </el-switch>
              </template>
            </el-table-column>

            <!-- <el-table-column prop="dateTime" label="Create Time" width="190" header-align="center" align="center"></el-table-column> -->
            <!-- <el-table-column prop="dateTime" label="Update Time" width="190" header-align="center" align="center"></el-table-column> -->

            <el-table-column
              label="Actions"
              width="200"
              header-align="center"
              align="center"
            >
              <template slot-scope="scope">
                <el-button
                  type="primary"
                  size="mini"
                  plain
                  @click="modifyQuestion(scope.row.questionLibId)"
                  >Edit</el-button
                >
                <el-button
                  type="danger"
                  size="mini"
                  plain
                  @click="deleteQuestion(scope.row.questionLibId)"
                  >Delete</el-button
                >
              </template>
            </el-table-column>
          </el-table>
        </div>

        <el-pagination
          background
          @current-change="doPageList"
          layout="total, prev, pager, next"
          :page-size="questionLibListPageInfo.pageSize"
          :total="questionLibListPageInfo.records"
        >
        </el-pagination>
      </el-tab-pane>
    </el-tabs>

    <el-dialog
      :title="titleOperate"
      :visible.sync="dialogQuestionVisible"
      top="3vh"
      width="50%"
      class="question-lib-dialog"
    >
      <el-form ref="questionForm" :model="questionForm" :rules="rules">
        <el-form-item
          label="Related Interviewer"
          prop="interviewerId"
          label-width="140px"
        >
          <el-select
            v-model="questionForm.interviewerId"
            placeholder="Please select interviewer"
            no-data-text="No Data"
            class="question-lib-field"
          >
            <el-option
              v-for="item in interviewerList"
              :key="item.id"
              :label="item.aiName"
              :value="item.id"
            >
            </el-option>
          </el-select>
        </el-form-item>

        <el-form-item label="Question" prop="question" label-width="140px">
          <el-input
            v-model="questionForm.question"
            class="question-lib-field"
          ></el-input>
        </el-form-item>

        <el-form-item
          label="Reference Answer"
          prop="referenceAnswer"
          label-width="140px"
        >
          <el-input
            type="textarea"
            :rows="6"
            placeholder="Please enter content"
            class="question-lib-field"
            v-model="questionForm.referenceAnswer"
          >
          </el-input>
        </el-form-item>

        <el-form-item label="AI Video" prop="aiSrc" label-width="140px">
          <div
            style="
              display: flex;
              flex-direction: row;
              justify-content: flex-start;
            "
          >
            <el-upload
              drag
              class="course-cover-uploader"
              action=""
              :http-request="(p) => uploadVideo(p)"
              :show-file-list="false"
            >
              <i class="el-icon-upload" style="margin: 70px 0 16px"></i>
              <div class="el-upload__text">
                Drag video here<br /><em>Click to upload</em>
              </div>
              <div class="el-upload__tip" slot="tip" style="margin-top: -16px">
                Only MP4 files allowed, max 10MB. Recommended sizes: 540*960,
                720*1280 or 1080*1920px
              </div>
            </el-upload>
            <!-- <img v-if="questionForm.aiSrc" :src="questionForm.aiSrc" class="course-cover"> -->

            <!-- {{ questionForm.aiSrc }} -->
            <video
              v-if="questionForm.aiSrc"
              :src="questionForm.aiSrc"
              id="myVideo"
              controls
              :autoplayNew="false"
              class="course-cover"
            >
              <!-- <source :src="questionForm.aiSrc" type="video/mp4" /> -->
              <!-- <source src="https://example.com/interview-demo.mp4" type="video/mp4" /> -->
            </video>
          </div>
        </el-form-item>
      </el-form>

      <div slot="footer" class="dialog-footer">
        <el-button @click="dialogQuestionVisible = false">Cancel</el-button>
        <el-button type="primary" @click="submitQuestion">Save</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
module.exports = {
  data() {
    return {
      activeTab: "allTechTagsList",

      search: {
        question: "",
        referenceAnswer: "",
        interviewerId: "",
        aiSrc: "",
      },

      titleOperate: "Add Question",
      dialogQuestionVisible: false,

      dialogTagsSortVisible: false,
      questionForm: {
        id: "",
        question: "",
        referenceAnswer: "",
        interviewer: "",
        interviewerId: "",
        aiSrc: "",
      },

      rules: {
        question: [
          {
            required: true,
            message: "Please enter question title",
            trigger: "blur",
          },
        ],
        referenceAnswer: [
          {
            required: true,
            message: "Please enter reference answer",
            trigger: "blur",
          },
        ],
        interviewerId: [
          {
            required: true,
            message: "Please select related interviewer",
            trigger: "blur",
          },
        ],
        aiSrc: [
          {
            required: true,
            message: "Please upload question video",
            trigger: "blur",
          },
        ],
      },

      activeValue: 1,
      inactiveValue: 0,

      questionList: [],
      questionLibListPageInfo: {
        page: 1,
        pageSize: 10,
        records: 0, // Total records
        total: 0, // Total pages
      },
      searchBO: {
        page: 0,
        pageSize: 0,
        aiName: "",
        question: "",
      },

      interviewerList: [],
    };
  },
  mounted() {
    this.initInterviewerList();
    this.initQuestionLibList(1, this.questionLibListPageInfo.pageSize);
  },
  methods: {
    initQuestionLibList(page, pageSize) {
      var searchBO = this.searchBO;
      searchBO.page = page;
      searchBO.pageSize = pageSize;

      console.log(searchBO);

      questionLibApi.list(searchBO).then((response) => {
        console.log(response);
        var questionList = response.data.rows;
        this.questionList = questionList;

        this.questionLibListPageInfo.records = response.data.records;
        this.questionLibListPageInfo.total = response.data.total;
        this.questionLibListPageInfo.page = page;
        this.questionLibListPageInfo.pageSize = pageSize;
      });
    },

    doPageList(page) {
      this.initQuestionLibList(page, this.questionLibListPageInfo.pageSize);
    },

    initInterviewerList() {
      interviewerApi.list().then((response) => {
        // console.log(response);
        var interviewerList = response.data;
        this.interviewerList = interviewerList;
      });
    },

    submitQuestion2() {
      var bo = this.questionForm;
      console.log(bo);

      this.dialogQuestionVisible = false;
    },

    submitQuestion() {
      var questionForm = this.questionForm;

      var formName = "questionForm";
      this.$refs[formName].validate((valid) => {
        if (valid) {
          this.$confirm("Confirm submission?", "Confirm", {
            confirmButtonText: "Confirm",
            cancelButtonText: "Cancel",
            type: "warning",
          })
            .then(() => {
              this.submitContent();
            })
            .catch(() => {});
        } else {
          console.log("Submission error!!!");
          return false;
        }
      });
    },

    submitContent() {
      var bo = this.questionForm;
      console.log(bo);

      questionLibApi.createOrUpdate(bo).then((response) => {
        console.log(response);

        this.$message({
          showClose: true,
          message: "Question saved successfully!",
          type: "success",
          duration: 2000,
        });

        // this.questionLibListPageInfo = {
        //     page: 1,
        //     pageSize: 10,
        //     records: 0,     // Total records
        //     total: 0,       // Total pages
        // };
        this.initQuestionLibList(
          this.questionLibListPageInfo.page,
          this.questionLibListPageInfo.pageSize,
        );
      });

      this.dialogQuestionVisible = false;
    },

    searchQuestion() {
      var bo = this.searchBO;
      console.log(bo);

      this.initQuestionLibList(1, this.questionLibListPageInfo.pageSize);
    },

    uploadVideo(params) {
      console.log("Custom upload", params);

      const file = params.file;

      var isOK = this.beforeVideoUpload(file);
      if (!isOK) {
        console.log("Intercepted... isOK = " + isOK);
        return;
      }

      // Create FormData object
      var formData = new FormData();
      formData.append("file", params.file);

      questionLibApi.uploadInterviewVideo(formData).then((response) => {
        console.log(response);
        var videoUrl = response.data;
        this.questionForm.aiSrc = videoUrl;
        // console.log(faceUrl);
      });
    },

    beforeVideoUpload(file) {
      const isMP4 = file.type === "video/mp4";
      const isLtSize = file.size / 1024 / 1024 < 10;

      if (!isMP4) {
        this.$message.error("Only MP4 format allowed for AI video upload!");
      }
      if (!isLtSize) {
        this.$message.error("AI video file size cannot exceed 10MB!");
      }
      return isMP4 && isLtSize;
    },

    deleteQuestion(questionLibId) {
      this.$confirm(
        "Are you sure you want to delete this question?",
        "Confirm",
        {
          confirmButtonText: "Confirm Delete",
          cancelButtonText: "Cancel",
          type: "success",
        },
      )
        .then(() => {
          var params = {
            questionLibId: questionLibId,
          };
          questionLibApi.delete(params).then((response) => {
            console.log(response);

            this.$message({
              showClose: true,
              message: "Question deleted successfully!",
              type: "success",
              duration: 2000,
            });

            this.clearForm();
            this.initQuestionLibList(1, this.questionLibListPageInfo.pageSize);
          });
        })
        .catch(() => {});
    },

    setQuestionDisplay(questionId, isDisplay) {
      // console.log("questionId = " + questionId);
      // console.log("isDisplay = " + isDisplay);

      if (isDisplay == 1) {
        isDisplay = 0;
        // Disable
        var params = {
          questionLibId: questionId,
        };
        questionLibApi.hide(params).then((response) => {});
      } else if (isDisplay == 0) {
        isDisplay = 1;
        // Enable
        var params = {
          questionLibId: questionId,
        };
        questionLibApi.show(params).then((response) => {});
      }

      // After successful modification, refresh query
      this.refreshQuestionList(questionId, isDisplay);
    },

    refreshQuestionList(questionId, isDisplay) {
      console.log("questionId = " + questionId);
      console.log("isDisplay = " + isDisplay);

      var questionList = this.questionList;
      for (var i = 0; i < questionList.length; i++) {
        var question = questionList[i];
        if (question.questionLibId == questionId) {
          question.isOn = isDisplay;
          break;
        }
      }
    },

    createQuestion() {
      this.titleOperate = "Add Question";
      this.dialogQuestionVisible = true;
      this.clearForm();
    },

    clearForm() {
      this.questionForm = {
        id: "",
        question: "",
        referenceAnswer: "",
        interviewer: "",
        aiSrc: "",
      };
    },

    modifyQuestion(questionId) {
      console.log("questionId = " + questionId);

      var me = this;
      me.titleOperate = "Edit Question";

      // this.clearForm();

      var questionList = this.questionList;
      for (var i = 0; i < questionList.length; i++) {
        var tmp = questionList[i];
        if (tmp.questionLibId == questionId) {
          me.questionForm = tmp;
          me.questionForm.id = questionId;
          break;
        }
      }

      this.dialogQuestionVisible = true;
    },

    deleteTag(tagId) {
      this.$confirm("Are you sure you want to delete this tag?", "Confirm", {
        confirmButtonText: "Confirm",
        cancelButtonText: "Cancel",
        type: "warning",
      })
        .then(() => {
          this.$message.success("Tag deleted successfully!");
        })
        .catch(() => {});
    },

    setSort() {
      this.dialogTagsSortVisible = true;
    },

    modifyCommentSort(commentId) {
      this.dialogTopicCommentVisible = true;
    },
  },
};
</script>

<style>
.allTechTagsList-box {
  /* padding: 10px; */

  display: flex;
  flex-direction: column;
  justify-content: flex-start;

  /* border: solid 10px #e3e9ef; */

  font-size: 16px;
}

.allTechTagsList-box .table-wrapper {
  padding: 10px 10px 20px 10px;
}

.allTechTagsList-box .table-list {
  width: 100%;
  font-size: 15px;
}

.allTechTagsList-box .search-area {
  margin-bottom: 20px;
}

.allTechTagsList-box .review-topic-title {
  color: #262626;
  font-size: 36px;
  font-weight: 700;

  padding: 10px 20px;
}

.allTechTagsList-box .review-topic-detail {
  padding: 20px;
}

.allTechTagsList-box .topic-comments {
  overflow: hidden;
  text-overflow: ellipsis;
  word-break: break-word;
  display: -webkit-box;
  -webkit-line-clamp: 1;
  -webkit-box-orient: vertical;
}

.ai-video {
  width: 90px;
  height: 160px;
  border-radius: 6px;
}

.allQuestionList-box .el-upload-dragger {
  width: 180px;
  height: 320px;
}

.allQuestionList-box .course-cover {
  /* width: 260px;
    height: 160px; */
  /* width: 360px;
    height: 220px; */
  width: 180px;
  height: 320px;
  margin-left: 10px;
  border-radius: 6px;
}

/* Make Add/Edit Question dialog responsive */
.question-lib-dialog .el-form-item__label {
  white-space: nowrap;
}
.question-lib-dialog .el-form-item__content {
  max-width: 100%;
}

.question-lib-dialog .question-lib-field {
  width: 100%;
  max-width: 680px;
}

.question-lib-dialog .question-lib-field .el-input__inner,
.question-lib-dialog .question-lib-field .el-textarea__inner {
  width: 100%;
  box-sizing: border-box;
}

.list-course-cover {
  width: 135px;
  height: 240px;
  border-radius: 6px;
}
</style>
