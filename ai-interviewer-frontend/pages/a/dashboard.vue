<template>
    <div class="dashboard-box">
        
        <el-card shadow="always">
            <div slot="header" class="clearfix">
                <span class="pending-task-title">Pending Tasks</span>
            </div>

            <div class="all-tasks-wrapper">

                <div class="single-task">
                    <span class="every-title">Welcome to Admin Dashboard</span>
                </div>

                

            </div>
        </el-card>
        
    </div>
</template>

<script>
module.exports = {
    data() {
        return {
            pendingCounts: {}
        }
    },
    mounted() {
        var me = this;

    },
    methods: {
        setPath(index, path) {
            // console.log("setPath index = " + index);
            this.$emit("changemenuformrouteview", index);
            this.$router.push({path: path});
            // this.$emit('updateProjectId','xxxxxx')
        }
    },
}
</script>

<style>
.dashboard-box {
    /* padding: 10px; */
    display: flex;
    flex-direction: column;
    justify-content: flex-start;

    /* border: solid 10px #e3e9ef; */
    font-size: 16px;
}

.dashboard-box .all-tasks-wrapper {
    display: flex;
    flex-direction: row;
    justify-content: space-around;
    flex-wrap: wrap;
    margin-top: 10px;
    margin-bottom: 10px;
}

.dashboard-box .pending-task-title {
    color: #1d1d1f;
    font-weight: 600;
    font-size: 22px;
}

.dashboard-box .single-task {
    display: flex;
    flex-direction: column;
    justify-content: center;
    align-items: center;
    margin: 20px;
    width: 200px;
    padding: 40px 30px;
    border: 0.5px solid rgba(0, 0, 0, 0.1);
    border-radius: 12px;
    background-color: rgba(255, 255, 255, 0.95);
    backdrop-filter: saturate(180%) blur(20px);
    -webkit-backdrop-filter: saturate(180%) blur(20px);
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
    transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
    cursor: pointer;
}

.single-task:hover {
    background-color: rgba(255, 255, 255, 1);
    transform: translateY(-4px);
    box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
}

.dashboard-box .every-title {
    font-size: 17px;
    color: #1d1d1f;
    align-self: center;
    font-weight: 500;
    text-align: center;
}  

.dashboard-box .every-counts {
    font-size: 50px;
    /* color: #119e8f; */
    font-weight: bold;
    align-self: center;
}
</style>