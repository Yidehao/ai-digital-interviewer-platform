<template>
  <div class="orderCreate-box">
    <el-tabs v-model="activeTab" type="border-card">
      <el-tab-pane :label="activeTitle" name="createCandidate">
        <div class="table-wrapper">
          <el-form
            ref="candidateForm"
            :model="candidateForm"
            :rules="rules"
            label-width="140px"
          >
            <el-form-item label="Job Position" prop="jobId">
              <el-select
                v-model="candidateForm.jobId"
                placeholder="Please select job position"
                no-data-text="No Data"
                style="width: 360px"
              >
                <!-- <el-option label="Java Senior Engineer" value="1"></el-option>
                                <el-option label="Java Architect" value="2"></el-option>
                                <el-option label="Manager" value="3 "></el-option> -->
                <el-option
                  v-for="item in jobNameList"
                  :key="item.jobId"
                  :label="item.jobName"
                  :value="item.jobId"
                >
                </el-option>
              </el-select>
            </el-form-item>

            <el-form-item label="Name" prop="realName">
              <el-input
                v-model="candidateForm.realName"
                style="width: 360px"
                maxlength="12"
              ></el-input>
            </el-form-item>
            <el-form-item label="ID Number" prop="identityNum">
              <el-input
                v-model="candidateForm.identityNum"
                style="width: 360px"
                maxlength="18"
              ></el-input>
            </el-form-item>
            <el-form-item label="Gender" prop="sex">
              <el-select
                v-model="candidateForm.sex"
                placeholder="Please select gender"
                no-data-text="No Data"
                style="width: 360px"
              >
                <el-option label="Private" value="2"></el-option>
                <el-option label="Female" value="0"></el-option>
                <el-option label="Male" value="1"></el-option>
              </el-select>
            </el-form-item>
            <el-form-item label="Mobile" prop="mobile">
              <el-input
                v-model="candidateForm.mobile"
                style="width: 360px"
                maxlength="11"
              ></el-input>
            </el-form-item>
            <el-form-item label="Email" prop="email">
              <el-input
                v-model="candidateForm.email"
                style="width: 360px"
              ></el-input>
            </el-form-item>

            <el-form-item label="Birthday">
              <div class="birthday-selects">
                <el-select
                  v-model="birthdayYear"
                  placeholder="Year"
                  no-data-text="No Data"
                  class="birthday-year"
                  @change="syncBirthday"
                >
                  <el-option
                    v-for="y in yearOptions"
                    :key="y"
                    :label="String(y)"
                    :value="y"
                  ></el-option>
                </el-select>
                <el-select
                  v-model="birthdayMonth"
                  placeholder="Month"
                  no-data-text="No Data"
                  class="birthday-month"
                  @change="syncBirthday"
                >
                  <el-option
                    v-for="m in monthOptions"
                    :key="m.value"
                    :label="m.label"
                    :value="m.value"
                  ></el-option>
                </el-select>
                <el-select
                  v-model="birthdayDay"
                  placeholder="Day"
                  no-data-text="No Data"
                  class="birthday-day"
                  @change="syncBirthday"
                >
                  <el-option
                    v-for="d in dayOptions"
                    :key="d"
                    :label="String(d)"
                    :value="d"
                  ></el-option>
                </el-select>
              </div>
            </el-form-item>

            <el-form-item>
              <el-button type="primary" @click="createCandidate"
                >Submit</el-button
              >
              <el-button>Cancel</el-button>
            </el-form-item>
          </el-form>
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script>
module.exports = {
  data() {
    return {
      activeTab: "createCandidate",
      activeTitle: "",

      candidateForm: {
        jobId: "",
        realName: "",
        identityNum: "",
        sex: "",
        mobile: "",
        email: "",
        birthday: "",
      },
      jobNameList: [],

      birthdayYear: null,
      birthdayMonth: null,
      birthdayDay: null,

      rules: {
        jobId: [
          {
            required: true,
            message: "Please select job position",
            trigger: "blur",
          },
        ],
        realName: [
          { required: true, message: "Name cannot be empty", trigger: "blur" },
        ],
        identityNum: [
          {
            required: true,
            message: "ID number cannot be empty",
            trigger: "blur",
          },
        ],
        sex: [
          {
            required: true,
            message: "Gender cannot be empty",
            trigger: "blur",
          },
        ],
        mobile: [
          {
            required: true,
            message: "Mobile cannot be empty",
            trigger: "blur",
          },
        ],
        email: [
          { required: true, message: "Email cannot be empty", trigger: "blur" },
        ],
      },
    };
  },
  computed: {
    yearOptions: function () {
      var arr = [];
      var end = new Date().getFullYear();
      for (var y = end; y >= 1900; y--) { arr.push(y); }
      return arr;
    },
    monthOptions: function () {
      return [
        { label: "Jan", value: "01" }, { label: "Feb", value: "02" }, { label: "Mar", value: "03" },
        { label: "Apr", value: "04" }, { label: "May", value: "05" }, { label: "Jun", value: "06" },
        { label: "Jul", value: "07" }, { label: "Aug", value: "08" }, { label: "Sep", value: "09" },
        { label: "Oct", value: "10" }, { label: "Nov", value: "11" }, { label: "Dec", value: "12" },
      ];
    },
    dayOptions: function () {
      var max = 31;
      if (this.birthdayYear && this.birthdayMonth) {
        max = new Date(this.birthdayYear, parseInt(this.birthdayMonth, 10), 0).getDate();
      }
      var arr = [];
      for (var d = 1; d <= max; d++) { arr.push(d < 10 ? "0" + d : String(d)); }
      return arr;
    },
  },
  watch: {
    birthdayYear: function () { this.syncBirthday(); },
    birthdayMonth: function () { this.syncBirthday(); },
  },
  mounted() {
    var me = this;

    var candidateId = this.$route.query.candidateId;
    console.log("candidateId = " + candidateId);

    if (app.isNotEmpty(candidateId)) {
      var params = {
        candidateId: candidateId,
      };

      me.activeTitle = "Edit Candidate";

      this.getCandidate(candidateId);
    } else {
      me.activeTitle = "Create Candidate";
    }

    this.initJobNameList();
  },
  methods: {
    getCandidate(candidateId) {
      candidateApi.detail(candidateId).then((response) => {
        var candidate = response.data;
        candidate.sex = String(candidate.sex);
        this.candidateForm = candidate;
        this.parseBirthdayToSelects(candidate.birthday);
      });
    },

    parseBirthdayToSelects(birthday) {
      if (!birthday || typeof birthday !== "string") {
        this.birthdayYear = null;
        this.birthdayMonth = null;
        this.birthdayDay = null;
        return;
      }
      var parts = birthday.trim().split(/[-/]/);
      if (parts.length >= 3) {
        this.birthdayYear = parseInt(parts[0], 10) || null;
        this.birthdayMonth = parts[1].length === 1 ? "0" + parts[1] : parts[1];
        this.birthdayDay = parts[2].length === 1 ? "0" + parts[2] : parts[2];
      } else {
        this.birthdayYear = null;
        this.birthdayMonth = null;
        this.birthdayDay = null;
      }
    },

    syncBirthday() {
      if (!this.birthdayYear || !this.birthdayMonth) {
        this.candidateForm.birthday = "";
        return;
      }
      var maxDay = new Date(this.birthdayYear, parseInt(this.birthdayMonth, 10), 0).getDate();
      var d = this.birthdayDay ? parseInt(this.birthdayDay, 10) : 0;
      if (d > maxDay || d < 1) {
        this.birthdayDay = maxDay < 10 ? "0" + maxDay : String(maxDay);
      }
      if (this.birthdayYear && this.birthdayMonth && this.birthdayDay) {
        var m = this.birthdayMonth.length === 1 ? "0" + this.birthdayMonth : this.birthdayMonth;
        var dayStr = this.birthdayDay.length === 1 ? "0" + this.birthdayDay : this.birthdayDay;
        this.candidateForm.birthday = this.birthdayYear + "-" + m + "-" + dayStr;
      } else {
        this.candidateForm.birthday = "";
      }
    },

    createCandidate() {
      console.log(this.candidateForm);

      var candidateForm = this.candidateForm;

      var formName = "candidateForm";
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
      var bo = this.candidateForm;
      console.log(bo);

      candidateApi.createOrUpdate(bo).then((response) => {
        console.log(response);

        this.$message({
          showClose: true,
          message: "Candidate information saved successfully!",
          type: "success",
          duration: 2000,
        });

        this.clearForm();
      });
    },

    clearForm() {
      this.candidateForm = {
        id: "",
        realName: "",
        identityNum: "",
        sex: "",
        mobile: "",
        email: "",
        birthday: "",
      };
      this.birthdayYear = null;
      this.birthdayMonth = null;
      this.birthdayDay = null;
    },

    initJobNameList() {
      jobApi.nameList().then((response) => {
        console.log(response);
        var jobNameList = response.data;
        this.jobNameList = jobNameList;
      });
    },
  },
};
</script>

<style>
.orderCreate-box {
  /* padding: 10px; */

  display: flex;
  flex-direction: column;
  justify-content: flex-start;

  /* border: solid 10px #e3e9ef; */

  font-size: 16px;
}

.orderCreate-box .table-wrapper {
  padding: 10px 10px 20px 10px;
}

.orderCreate-box .table-list {
  width: 100%;
  font-size: 15px;
}

.orderCreate-box .search-area {
  margin-bottom: 20px;
}

/* Birthday: three dropdowns in a row, same style as other form selects */
.birthday-selects {
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.birthday-selects .birthday-year {
  width: 110px;
}
.birthday-selects .birthday-month {
  width: 120px;
}
.birthday-selects .birthday-day {
  width: 90px;
}
</style>
