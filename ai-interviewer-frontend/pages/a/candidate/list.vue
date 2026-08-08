<template>
    <div class="orderlist-box">

        <el-tabs v-model="activeTab" type="border-card">
            <el-tab-pane label="Candidate List" name="allOrderList">

                <div class="table-wrapper">

                    <div class="search-area">
                        <el-input placeholder="Name" v-model="searchBO.realName" prefix-icon="el-icon-user" clearable style="width: 200px; margin-right: 10px; margin-bottom: 8px;"></el-input>
                        <el-input placeholder="Mobile" v-model="searchBO.mobile" prefix-icon="el-icon-mobile-phone" clearable style="width: 200px; margin-right: 10px; margin-bottom: 8px;"></el-input>
                        <el-button type="primary" size="middle" @click="searchCandidate()">Search</el-button>
                    </div>

                    <el-table :data="candidateList" border stripe class="table-list" empty-text="No Data">
                        <el-table-column type="index" header-align="center" align="center" width="50"></el-table-column>
                        <el-table-column label="Candidate Name" header-align="center" align="center" width="180">
                            <template slot-scope="scope">
                                {{scope.row.realName}}
                            </template>
                        </el-table-column>
                        <el-table-column label="ID Number" header-align="center" align="center" width="220">
                            <template slot-scope="scope">
                                {{scope.row.identityNum}}
                            </template>
                        </el-table-column>
                        <el-table-column label="Gender" header-align="center" align="center" width="80" class-name="candidate-gender-cell">
                            <template slot-scope="scope">
                                <span>
                                    <el-tag type="success" v-if="scope.row.sex == 1" class="candidate-gender-tag">Male</el-tag>
                                    <el-tag type="danger" v-if="scope.row.sex == 0" class="candidate-gender-tag">Female</el-tag>
                                    <el-tag type="info" v-if="scope.row.sex == 2" class="candidate-gender-tag">Private</el-tag>
                                </span>
                            </template>
                        </el-table-column>
                        <el-table-column label="Mobile" header-align="center" align="center" width="180">
                            <template slot-scope="scope">
                                {{scope.row.mobile}}
                            </template>
                        </el-table-column>
                        <el-table-column label="Email" header-align="center" align="center" width="220">
                            <template slot-scope="scope">
                                {{scope.row.email}}
                            </template>
                        </el-table-column>
                        <el-table-column label="Birthday" header-align="center" align="center" width="120">
                            <template slot-scope="scope">
                                {{ scope.row.birthday }}
                            </template>
                        </el-table-column>
                        <el-table-column prop="jobName" label="Job Position" width="180" header-align="center" align="center"></el-table-column>
                        <el-table-column prop="createTime" label="Create Time" min-width="180" header-align="center" align="center"></el-table-column>
                        <el-table-column label="Actions" width="200" header-align="center" align="center">
                            <template slot-scope="scope">
                                <div class="candidate-actions">
                                    <el-button type="primary" size="mini" plain @click="gotoModifyCandidate(scope.row.candidateId)">Edit</el-button>
                                    <el-button type="danger" size="mini" plain @click="deleteCandidate(scope.row.candidateId)">Delete</el-button>
                                </div>
                            </template>
                        </el-table-column>
                    </el-table>

                </div>

                <el-pagination
                    background
                    @current-change="doPageList"
                    layout="total, prev, pager, next"
                    :page-size="candidateListPageInfo.pageSize"
                    :total="candidateListPageInfo.records">
                    </el-pagination>

            </el-tab-pane>
        </el-tabs>

    </div>
</template>

<script>
module.exports = {
    data() {
        return {
            activeTab: "allOrderList",

            dialogUserInfoVisible: false,

            searchBO: {
                page: 0,
                pageSize: 0,
                realName: "",
                mobile: "",
            },

            candidateList: [],
            candidateListPageInfo: {
                page: 1,
                pageSize: 10,
                records: 0,     // Total records
                total: 0,       // Total pages
            },
        }
    },
    mounted() {
        this.initCandidateList(1, this.candidateListPageInfo.pageSize);
    },
    methods: {

        initCandidateList(page, pageSize) {

            var searchBO = this.searchBO;
            searchBO.page = page;
            searchBO.pageSize = pageSize;

            console.log(searchBO);

            candidateApi.list(searchBO).then(response => {
                console.log(response);
                var candidateList = response.data.rows;
                this.candidateList = candidateList;

                this.candidateListPageInfo.records = response.data.records;
                this.candidateListPageInfo.total = response.data.total;
                this.candidateListPageInfo.page = page;
                this.candidateListPageInfo.pageSize = pageSize;
            });
        },

        doPageList(page) {
            this.initCandidateList(page, this.candidateListPageInfo.pageSize);
        },

        searchCandidate() {
            var bo = this.searchBO;
            console.log(bo);

            this.initCandidateList(1, this.candidateListPageInfo.pageSize);
        },

        gotoModifyCandidate(candidateId) {
            var targetPath = "/candidateMng/candidateCreate?candidateId=" + candidateId;
            this.$parent.$router.push({
                path: targetPath
            });
        },

        deleteCandidate(candidateId) {

            this.$confirm('Are you sure you want to delete this candidate?', 'Confirm', {
                confirmButtonText: 'Confirm Delete',
                cancelButtonText: 'Cancel',
                type: 'warning'
            }).then(() => {

                candidateApi.delete(candidateId).then(response => {
                    console.log(response);

                    this.$message({
                        showClose: true,
                        message: 'Candidate deleted successfully!',
                        type: 'success',
                        duration: 2000
                    });

                    this.initCandidateList(1, this.candidateListPageInfo.pageSize);
                });

            }).catch(() => {
            });

        },

    },
}
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

/* Gender 列：标签内文字垂直居中 */
.orderlist-box .candidate-gender-cell .cell {
    display: flex;
    align-items: center;
    justify-content: center;
}
.orderlist-box .candidate-gender-tag {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    line-height: 1.2;
}

/* Actions 列：与 Job List / AI Interviewer / Question Library 统一（横向、间距） */
.orderlist-box .candidate-actions {
    display: flex;
    flex-direction: row;
    flex-wrap: nowrap;
    align-items: center;
    justify-content: center;
    gap: 8px;
}

</style>