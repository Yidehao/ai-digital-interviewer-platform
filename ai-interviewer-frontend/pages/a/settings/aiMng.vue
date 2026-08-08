<template>
  <div class="carouselContentCreate-box">
    <el-tabs v-model="activeTab" type="border-card">
      <el-tab-pane
        label="AI Interviewer Management"
        name="carouselContentCreate"
      >
        <div class="table-wrapper">
          <el-form
            ref="interviewerForm"
            :model="interviewerForm"
            :rules="rules"
            label-width="140px"
          >
            <el-form-item label="AI Avatar Image" prop="link">
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
                  :http-request="(p) => uploadAI(p)"
                  :show-file-list="false"
                >
                  <i class="el-icon-upload" style="margin: 70px 0 16px"></i>
                  <div class="el-upload__text">
                    Drag image here<br /><em>Click to upload</em>
                  </div>
                  <div
                    class="el-upload__tip"
                    slot="tip"
                    style="margin-top: -16px"
                  >
                    Only PNG or JPG files allowed, max 2MB. Recommended sizes:
                    540*960, 720*1280 or 1080*1920px
                  </div>
                </el-upload>
                <img
                  v-if="interviewerForm.link"
                  :src="interviewerForm.link"
                  class="course-cover"
                />
              </div>
            </el-form-item>

            <el-form-item label="Image URL" prop="image">
              <el-input
                v-model="interviewerForm.image"
                style="width: 500px"
                maxlength="255"
                show-word-limit
              >
                <template slot="prepend"
                  ><i class="el-icon-picture"></i
                ></template>
              </el-input>
            </el-form-item>

            <el-form-item label="Interviewer Name" prop="aiName">
              <el-input
                v-model="interviewerForm.aiName"
                style="width: 500px"
                maxlength="12"
                show-word-limit
              >
                <template slot="prepend"
                  ><i class="el-icon-s-custom"></i
                ></template>
              </el-input>
            </el-form-item>

            <el-form-item>
              <el-button type="primary" @click="confirmSubmit">Save</el-button>
              <el-button @click="cleanForm">Cancel</el-button>
            </el-form-item>
          </el-form>
        </div>

        <div class="table-wrapper">
          <el-table
            :data="interviewerList"
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
              label="AI Avatar"
              header-align="center"
              align="center"
              width="240"
            >
              <template slot-scope="scope">
                <a :href="scope.row.image" target="_blank"
                  ><img :src="scope.row.image" class="list-course-cover"
                /></a>
              </template>
            </el-table-column>
            <el-table-column
              label="Interviewer Name"
              header-align="center"
              align="center"
              min-width="120"
            >
              <template slot-scope="scope">
                {{ scope.row.aiName }}
              </template>
            </el-table-column>

            <el-table-column
              prop="createTime"
              label="Create Time"
              width="220"
              header-align="center"
              align="center"
            ></el-table-column>
            <el-table-column
              prop="updatedTime"
              label="Update Time"
              width="220"
              header-align="center"
              align="center"
            ></el-table-column>

            <el-table-column
              label="Actions"
              width="220"
              header-align="center"
              align="center"
            >
              <template slot-scope="scope">
                <el-button
                  type="primary"
                  size="mini"
                  plain
                  @click="modifyInterviewer(scope.row.id)"
                  >Edit</el-button
                >
                <el-button
                  type="danger"
                  size="mini"
                  plain
                  @click="deleteInterviewer(scope.row.id)"
                  >Delete</el-button
                >
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script>
module.exports = {
  data() {
    return {
      activeTab: "carouselContentCreate",
      url: "",

      interviewerForm: {
        id: "",
        link: "",
        image: "",
        aiName: "",
      },

      rules: {
        image: [
          {
            required: true,
            message:
              "Please upload interviewer avatar image or enter external link",
            trigger: "blur",
          },
        ],
        aiName: [
          {
            required: true,
            message: "Interviewer name cannot be empty",
            trigger: "blur",
          },
        ],
      },

      interviewerList: [],
      interviewerList2: [
        {
          id: "1001",
          link: "",
          image: "https://example.com/ai-interviewer-1.png",
          aiName: "AI Interviewer 1",
          createTime: "2025-12-12 12:15:15",
          updatedTime: "2025-12-12 12:15:15",
        },
        {
          id: "1002",
          link: "",
          image: "https://example.com/ai-interviewer-2.png",
          aiName: "AI Interviewer 2",
          createTime: "2025-12-12 12:15:15",
          updatedTime: "2025-12-12 12:15:15",
        },
      ],
    };
  },
  mounted() {
    var me = this;

    this.initInterviewerList();
  },
  methods: {
    deleteInterviewer(interviewerId) {
      this.$confirm(
        "Are you sure you want to delete this interviewer?",
        "Confirm",
        {
          confirmButtonText: "Confirm Delete",
          cancelButtonText: "Cancel",
          type: "success",
        },
      )
        .then(() => {
          var params = {
            interviewerId: interviewerId,
          };
          interviewerApi.deleteInterviewer(params).then((response) => {
            // console.log(response);

            this.$message({
              showClose: true,
              message: "Interviewer deleted successfully!",
              type: "success",
              duration: 2000,
            });

            this.cleanForm();
            this.initInterviewerList();
          });
        })
        .catch(() => {});
    },

    modifyInterviewer(interviewerId) {
      var me = this;
      var interviewerList = this.interviewerList;
      for (var i = 0; i < interviewerList.length; i++) {
        var tmp = interviewerList[i];
        if (tmp.id == interviewerId) {
          me.interviewerForm = tmp;
          me.interviewerForm.link = tmp.image;
          break;
        }
      }
      this.initInterviewerList();
    },

    initInterviewerList() {
      interviewerApi.list().then((response) => {
        // console.log(response);
        var interviewerList = response.data;
        this.interviewerList = interviewerList;
      });
    },

    uploadAI(params) {
      console.log("Custom upload", params);

      const file = params.file;

      var isOK = this.beforeAIUpload(file);
      if (!isOK) {
        console.log("Intercepted... isOK = " + isOK);
        return;
      }

      // Create FormData object
      var formData = new FormData();
      formData.append("file", params.file);

      interviewerApi.uploadInterviewerImage(formData).then((response) => {
        console.log(response);
        var faceUrl = response.data;
        this.interviewerForm.link = faceUrl;
        this.interviewerForm.image = faceUrl;
        console.log(faceUrl);
      });
    },

    beforeAIUpload2(file) {
      const isMP4 = file.type === "video/mp4";
      const isLtSize = file.size / 1024 / 1024 < 10;

      if (!isMP4) {
        this.$message.error("Only MP4 format allowed for AI avatar upload!");
      }
      if (!isLtSize) {
        this.$message.error("AI avatar file size cannot exceed 10MB!");
      }
      return isMP4 && isLtSize;
    },

    beforeAIUpload(file) {
      const isJPG = file.type === "image/jpeg";
      const isPNG = file.type === "image/png";
      const isLt2M = file.size / 1024 / 1024 < 2;

      if (!isJPG && !isPNG) {
        this.$message.error("Avatar image can only be JPG or PNG format!");
        return false;
      }
      if (!isLt2M) {
        this.$message.error("Avatar image size cannot exceed 2MB!");
        return false;
      }
      return (isJPG || isPNG) && isLt2M;
    },

    confirmSubmit() {
      var interviewerForm = this.interviewerForm;

      var formName = "interviewerForm";
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
      var bo = this.interviewerForm;
      console.log(bo);

      interviewerApi.createOrUpdate(bo).then((response) => {
        console.log(response);

        this.$message({
          showClose: true,
          message: "AI Interviewer saved successfully!",
          type: "success",
          duration: 2000,
        });

        this.cleanForm();
        this.initInterviewerList();
      });
    },

    cleanForm() {
      this.interviewerForm = {
        id: "",
        link: "",
        image: "",
        aiName: "",
      };
    },
  },
};
</script>

<style>
.carouselContentCreate-box {
  /* padding: 10px; */

  display: flex;
  flex-direction: column;
  justify-content: flex-start;

  /* border: solid 10px #e3e9ef; */

  font-size: 16px;
}

.carouselContentCreate-box .table-wrapper {
  padding: 10px 10px 20px 10px;
}

.carouselContentCreate-box .table-list {
  width: 100%;
  font-size: 15px;
}

.carouselContentCreate-box .search-area {
  margin-bottom: 20px;
}

/* .carouselContentCreate-box .course-cover-uploader {
    width: 260px;  360  400
    height: 160px;      245
} */

.carouselContentCreate-box .el-upload-dragger {
  width: 180px;
  height: 320px;
}

.carouselContentCreate-box .course-cover {
  /* width: 260px;
    height: 160px; */
  /* width: 360px;
    height: 220px; */
  width: 180px;
  height: 320px;
  margin-left: 10px;
  border-radius: 6px;
}

.list-course-cover {
  width: 135px;
  height: 240px;
  border-radius: 6px;
}
</style>
