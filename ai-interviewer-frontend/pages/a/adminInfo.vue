<template>
    <div class="userinfo-box">

        <div class="userinfo-desc-wrapper">
            <div style="font-size: 28px; font-weight: bold;">Account Information</div>
            <div style="font-size: 12px;">Account related settings</div>
        </div>

        <div class="userinfo-content-wrapper">

            <div style="padding: 16px 20px; background-color: #fff; width: 260px; display: flex; flex-direction: column; justify-content: flex-start;">
                <div style="width: 100px; height: 100px; border-radius: 50%; margin-top: 10px; align-self: center; background: linear-gradient(135deg, #007aff 0%, #5856d6 100%); display: flex; align-items: center; justify-content: center; color: white; font-size: 36px; font-weight: 600; box-shadow: 0 4px 12px rgba(0, 122, 255, 0.3);">AI</div>
                <div style="color: #000; font-size: 16px; align-self: center; margin-top: 10px;">Admin</div>
                <div style="font-size: 14px; align-self: center; margin-top: 2px; ">ID: 123456789123456789</div>
                <div class="vip-status">VIP Status: Expires 2024-12-25</div>
                <div class="dashed-line"></div>
            </div>

            <div class="account-info-desc">
                <!-- Top -->
                <div class="account-info-up">
                    <div class="user-account">Account Information</div>
                    <div class="account-split-line"></div>
                    
                    <el-form ref="form" :model="form" label-width="80px" class="account-form">

                        <el-row :gutter="25">
                            <el-col :span="10" >
                                <el-form-item label="Nickname">
                                    <el-input class="blank-left"></el-input>
                                </el-form-item>
                            </el-col>
                        </el-row>
                        <el-row :gutter="25">
                            <el-col :span="10">
                                <el-form-item label="Mobile">
                                    <span class="blank-left">139****6688</span>
                                </el-form-item>
                            </el-col>
                        </el-row>
                        <el-row :gutter="25">
                            <el-col :span="10">
                                <el-form-item label="Gender">
                                    <el-radio class="blank-left" v-model="form.sex" label="1">Male</el-radio>
                                    <el-radio v-model="form.sex" label="0">Female</el-radio>
                                </el-form-item>
                            </el-col>
                        </el-row>
                        <el-row :gutter="25">
                            <el-col :span="10">
                                <el-form-item label="Personal Signature">
                                    <el-input class="blank-left"></el-input>
                                </el-form-item>
                            </el-col>
                        </el-row>
                        

                        <!-- <el-row :gutter="25">
                            <el-col :span="10" >
                                <el-form-item label="Nickname">
                                    <el-input class="blank-left"></el-input>
                                </el-form-item>
                            </el-col>
                            <el-col :span="10">
                                <el-form-item label="Gender">
                                    <el-radio class="blank-left" v-model="form.sex" label="1">Male</el-radio>
                                    <el-radio v-model="form.sex" label="0">Female</el-radio>
                                </el-form-item>
                            </el-col>
                        </el-row> -->
                        
                        <el-form-item>
                            <el-button type="primary">Update Info</el-button>
                            <el-button>Cancel</el-button>
                        </el-form-item>
                    </el-form>
                </div>

                <!-- Bottom -->
                <div class="account-info-down">

                </div>
            </div>

        </div>

    </div>
</template>

<script>
module.exports = {
    data() {
        return {
            form: {
                name: '',
            }
        }
    }
}
</script>

<style>
.userinfo-desc-wrapper {
    margin-top: 14px;
    
    display: flex;
    flex-direction: column;
    justify-content: flex-start;
}

.userinfo-content-wrapper {
    margin-top: 20px;

    border: solid 10px #e3e9ef;
    background-color: #eff4f9;

    display: flex;
    flex-direction: row;
    justify-content: flex-start;
}

.vip-status {
    font-size: 14px; 
    align-self: center; 
    margin-top: 2px; 
    color: #ffa419;
    font-weight: 400;
}

.dashed-line {
    margin-top: 10px;
    border-bottom: 1.5px dashed #d8d8d8;
}

.account-info-desc {
    flex-grow: 1; 
    padding: 20px 30px;
}

.blank-left {
    margin-left: 10px;
}

.account-info-up {
    background-color: #fff; 
    height: 500px; 
    display: flex; 
    flex-direction: column;
    justify-content: flex-start;
}

.user-account {
    height: 50px; 
    width: 100%; 
    font-size: 16px; 
    font-weight: bold; 
    display: flex; 
    flex-direction: column; 
    justify-content: center;
    margin-left: 20px;
}

.account-split-line {
    width: 100%; 
    border-bottom: 1px solid #eceaea;
}

.account-form {
    padding: 30px 0;
}

</style>