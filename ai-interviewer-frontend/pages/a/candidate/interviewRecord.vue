<template>
    <div class="orderlist-box">

        <el-tabs v-model="activeTab" type="border-card">
            <el-tab-pane label="Interview Record List" name="allOrderList">

                <div class="table-wrapper">

                    <div class="search-area">
                        <el-input placeholder="Name" v-model="searchBO.realName" prefix-icon="el-icon-user" clearable style="width: 200px; margin-right: 10px; margin-bottom: 8px;"></el-input>
                        <el-input placeholder="Mobile" v-model="searchBO.mobile" prefix-icon="el-icon-mobile-phone" clearable style="width: 200px; margin-right: 10px; margin-bottom: 8px;"></el-input>
                        <el-button type="primary" size="middle" @click="searchRecord()">Search</el-button>
                    </div>

                    <el-table :data="interviewerRecordList" border stripe class="table-list" empty-text="No Data">
                        <el-table-column type="index" header-align="center" align="center" width="50"></el-table-column>
                        <el-table-column label="Candidate Name" header-align="center" align="center" width="200">
                            <template slot-scope="scope">
                                {{scope.row.realName}}
                            </template>
                        </el-table-column>
                        <el-table-column label="ID Number" header-align="center" align="center" width="240">
                            <template slot-scope="scope">
                                {{scope.row.identityNum}}
                            </template>
                        </el-table-column>
                        <el-table-column label="Gender" header-align="center" align="center" width="100">
                            <template slot-scope="scope">
                                <span>
                                    <el-tag type="success" v-if="scope.row.sex == 1">Male</el-tag>
                                    <el-tag type="danger" v-if="scope.row.sex == 0">Female</el-tag>
                                    <el-tag type="info" v-if="scope.row.sex == 2">Private</el-tag>
                                </span>
                            </template>
                        </el-table-column>
                        <el-table-column label="Mobile" header-align="center" align="center" width="200">
                            <template slot-scope="scope">
                                {{scope.row.mobile}}
                            </template>
                        </el-table-column>
                        <el-table-column label="Interview Date" header-align="center" align="center" width="180">
                            <template slot-scope="scope">
                                {{ scope.row.createTime }}
                            </template>
                        </el-table-column>
                        <el-table-column prop="jobName" label="Job Position" width="220" header-align="center" align="center"></el-table-column>

                        <el-table-column label="Total Duration" header-align="center" align="center" width="120">
                            <template slot-scope="scope">
                                {{formatSeconds(scope.row.takeTime)}}
                            </template>
                        </el-table-column>

                        <el-table-column label="Actions" min-width="160" header-align="center" align="center">
                            <template slot-scope="scope">
                                <el-button type="primary" size="mini" plain @click="showRecordDesc(scope.row.interviewRecordId)">View Details</el-button>
                            </template>
                        </el-table-column>
                    </el-table>

                </div>

                <el-dialog title="" :visible.sync="dialogRecordDescVisible" top="10vh" width="60%" >
                    
                    <div v-if="resultDetail" v-html="resultDetail"></div>

                    <div slot="footer" class="dialog-footer">
                        <el-button @click="dialogRecordDescVisible = false">Cancel</el-button>
                        <el-button type="primary" @click="dialogRecordDescVisible = false">Confirm</el-button>
                    </div>
                </el-dialog>

                <el-pagination
                    background
                    @current-change="doPageList"
                    layout="total, prev, pager, next"
                    :page-size="interviewerRecordListPageInfo.pageSize"
                    :total="interviewerRecordListPageInfo.records">
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

            dialogRecordDescVisible: false,

            resultDetail: "",

            search: {
                realName: "",
                mobile: "",
            },
            
            tableData: [{
                id: "123456789123456789",
				realName: "John Doe",
                identityNum: "123456789123456789",
                sex: "Male",
				mobile: "13961889999",
				scroe: 100,
				birthday: "1990-01-01",
                targetJob: "Senior Java Engineer",
                dateTime: "2023-12-12 12:15:15",
			}, {
                id: "123456789123456789",
				realName: "Jane Smith",
                identityNum: "123456789123456789",
                sex: "Male",
				mobile: "13961889999",
				scroe: 95,
				birthday: "1990-01-01",
                targetJob: "Java Architect",
                dateTime: "2023-12-12 12:15:15",
			}],

            searchBO: {
                page: 0,
                pageSize: 0,
                realName: "",
                mobile: "",
            },
            interviewerRecordList: [],
            interviewerRecordListPageInfo: {
                page: 1,
                pageSize: 10,
                records: 0,     // Total records
                total: 0,       // Total pages
            },
        }
    },
    mounted() {
        this.initInterviewerRecordListList(1, this.interviewerRecordListPageInfo.pageSize);
    },
    methods: {

        formatSeconds(t) {
            let h = parseInt(`${t / 60 / 60}`)
            let m = parseInt(`${t / 60 % 60}`)
            let s = parseInt(`${t % 60}`)
            // Ternary expression to pad zeros: if less than 10, pad with zero in front; if greater than 10, no padding needed
            h = h < 10 ? '0' + h : h
            m = m < 10 ? '0' + m : m
            s = s < 10 ? '0' + s : s
            return `${h}:${m}:${s}`
        },
        
        initInterviewerRecordListList(page, pageSize) {

            var searchBO = this.searchBO;
            searchBO.page = page;
            searchBO.pageSize = pageSize;

            console.log(searchBO);

            interviewerRecordApi.list(searchBO).then(response => {
                console.log(response);
                var interviewerRecordList = response.data.rows;
                this.interviewerRecordList = interviewerRecordList;

                this.interviewerRecordListPageInfo.records = response.data.records;
                this.interviewerRecordListPageInfo.total = response.data.total;
                this.interviewerRecordListPageInfo.page = page;
                this.interviewerRecordListPageInfo.pageSize = pageSize;
            });
        },

        doPageList(page) {
            this.initInterviewerRecordListList(page, this.interviewerRecordListPageInfo.pageSize);
        },

        searchRecord() {
            var bo = this.searchBO;
            console.log(bo);

            this.initInterviewerRecordListList(1, this.interviewerRecordListPageInfo.pageSize);
        },

        gotoModifyCandidate(candidateId) {
            var targetPath = "/candidateMng/candidateCreate?candidateId=" + candidateId;
            this.$parent.$router.push({
                path: targetPath
            });
        },

        showRecordDesc(interviewRecordId) {
            this.dialogRecordDescVisible = true;

            // console.log(interviewRecordId);

            var interviewerRecordList = this.interviewerRecordList;
            for (var i = 0 ; i < interviewerRecordList.length ; i ++) {
                var tempId = interviewerRecordList[i].interviewRecordId;
                if (interviewRecordId == tempId) {
                    this.resultDetail = interviewerRecordList[i].result;
                }
            }


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

</style>