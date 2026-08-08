<template>
    <div class="orderCreate-box">

        <el-tabs v-model="activeTab" type="border-card">
            <el-tab-pane :label="activeTitle" name="createJob">

                <div class="table-wrapper">

                    <el-form ref="jobForm" :rules="rules" :model="jobForm" label-width="140px">
                        <el-form-item label="Job Name" prop="jobName">
                            <el-input v-model="jobForm.jobName" style="width: 360px;"></el-input>
                        </el-form-item>

                        <el-form-item label="Job Description" prop="jobDesc">
                            <el-input
                                type="textarea"
                                :rows="3"
                                placeholder="Please enter content"
                                style="width: 360px;"
                                v-model="jobForm.jobDesc">
                                </el-input>
                        </el-form-item>

                        <el-form-item label="Status" prop="status">
                            <el-radio class="blank-left" v-model="jobForm.status" label="1" style="margin-left: 30px;">Open</el-radio>
                            <el-radio v-model="jobForm.status" label="0">Closed</el-radio>
                        </el-form-item>

                        <el-form-item label="Interviewer" prop="interviewerId">
                            <el-select v-model="jobForm.interviewerId" placeholder="Please select interviewer" no-data-text="No Data" style="width: 360px;">
                                <!-- <el-option label="AI Interviewer 1" value="1"></el-option>
                                <el-option label="AI Interviewer 2" value="2"></el-option>
                                <el-option label="AI Interviewer 3" value="3 "></el-option> -->
                                <el-option
                                    v-for="item in interviewerList"
                                    :key="item.id"
                                    :label="item.aiName"
                                    :value="item.id">
                                </el-option>
                            </el-select>
                        </el-form-item>

                        <el-form-item label="Prompt Prefix" prop="prompt">
                            <el-input
                                type="textarea"
                                :rows="5"
                                placeholder="Please enter content"
                                style="width: 360px;"
                                v-model="jobForm.prompt">
                                </el-input>
                        </el-form-item>
                        

                        <el-form-item>
                            <el-button type="primary" @click="confirmSubmit">Submit</el-button>
                            <el-button @click="cleanForm">Cancel</el-button>
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
            activeTab: "createJob",
            activeTitle: "",

            jobForm: {
                id: "",
                jobName: "",
                jobDesc: "",
                status: "",
                interviewerId: "",
                prompt: "",
            },

            rules: {
                jobName: [
                    { required: true, message: 'Please enter job name', trigger: 'blur' },
                ],
                jobDesc: [
                    { required: true, message: 'Please enter job description', trigger: 'blur' }
                ],
                status: [
                    { required: true, message: 'Please select job status', trigger: 'blur' },
                ],
                interviewerId: [
                    { required: true, message: 'Please select interviewer for this job', trigger: 'blur' }
                ],
                prompt: [
                    { required: true, message: 'Please enter prompt for AI to analyze interview results', trigger: 'blur' }
                ],
            },

            interviewerList: [],
        }
    },
    mounted() {
        var me = this;

        var jobId = this.$route.query.jobId;
        console.log("jobId = " + jobId);

        if (app.isNotEmpty(jobId)) {
            var params = {
                jobId: jobId
            }

            me.activeTitle = "Edit Job"

            this.getJobDetail(jobId);
        } else {
            me.activeTitle = "Create Job"
        }

        this.initInterviewerList();
    },
    methods: {

        getJobDetail(id) {

            jobApi.detail(id).then(response => {
                // console.log(response);
                var job = response.data;
                job.status = job.status.toString();
                this.jobForm = job;
                console.log(job);

                // this.jobForm = {
                //     id: job.id,
                //     jobName: job.jobName,
                //     jobDesc: job.jobDesc,
                //     status: job.status,
                //     interviewerId: job.interviewerId,
                //     prompt: job.prompt,
                // }
            });

        },
        
        confirmSubmit() {
            var jobForm = this.jobForm;

            var formName = "jobForm";
            this.$refs[formName].validate((valid) => {
                if (valid) {
                    this.$confirm('Confirm submission?', 'Confirm', {
                        confirmButtonText: 'Confirm',
                        cancelButtonText: 'Cancel',
                        type: 'warning'
                    }).then(() => {
                        this.submitContent();
                    }).catch(() => {
                    });;
                } else {
                    console.log('Submission error!!!');
                    return false;
                }
            });
            
        },

        submitContent() {
            var bo = this.jobForm;
            console.log(bo);

            jobApi.createOrUpdate(bo).then(response => {
                console.log(response);

                this.$message({
                    showClose: true,
                    message: 'Job information saved successfully!',
                    type: 'success',
                    duration: 2000
                });

                this.cleanForm();
            });
        },

        initInterviewerList() {
            interviewerApi.list().then(response => {
                // console.log(response);
                var interviewerList = response.data;
                this.interviewerList = interviewerList;
            });
        },

        cleanForm() {
            this.jobForm = {
                id: "",
                jobName: "",
                jobDesc: "",
                status: "",
                interviewerId: "",
                prompt: "",
            }
        }
    },
}
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

</style>