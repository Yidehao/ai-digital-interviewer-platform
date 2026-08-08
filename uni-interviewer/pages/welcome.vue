<!-- Previously logged in page -->
<template>
	<view class="page">

		<view class="login-box">

			<text class="logined-mobile">Enter AI Interview</text>

			<view class="input-group">
				<text class="input-label">Phone Number</text>
				<view class="input-wrapper">
					<text class="txt-prefix-words">+1</text>
					<input type="number" 
						class="mobile-input" 
						:value="mobile"
						:model="mobile"
						@input="typingMobile"
						placeholder="Please enter phone number" 
						placeholder-class="mobile-placeholder"
						maxlength="10"/>
				</view>
			</view>
			
			<view class="input-group">
				<text class="input-label">Verification Code</text>
				<view class="input-wrapper-with-button">
					<input type="number" 
						class="mobile-verify-code-input" 
						:value="verifyCode"
						:model="verifyCode"
						@input="typingVerifyCode"
						placeholder="Please enter verification code" 
						placeholder-class="mobile-placeholder"
						maxlength="6"/>
					<view
						:class="{'btn-code':!codeTouched, 'btn-code-touched': codeTouched, 'btn-get-code': true}"
						@touchstart="touchstartCode"
						@touchend="touchendCode"
						@click="getCode()">
						<text class="code-btn-text">{{codeBtnText}}</text>
					</view>
				</view>
			</view>
			
		</view>
		
		<view class="bottom-side-box">
			<view 
				:class="{'btn-login':!loginTouched, 'btn-login-touched': loginTouched, 'btn-go-login': true}"
				@touchstart="touchstartLogin"
				@touchend="touchendLogin"
				@click="doVerify()">
					<text class="login-btn-text">Enter Interview</text>
			</view>
		</view>
	</view>
</template>

<script>
	var system = uni.getSystemInfoSync();
	var app = getApp();
	export default {
		data() {
			return {
				statusBarHeight: 0,
				
				nickname: "",
				mobile: "",
				verifyCode: "",
				codeBtnText: "Get Code",
				codeTimes: 0,
				
				loginTouched: false,
				codeTouched: false,
			}
		},
		onLoad() {
			this.statusBarHeight = system.statusBarHeight;
		},
		methods: {
			onlyChooseCn() {
				uni.showToast({
					title: 'United States only',
					icon: "none",
					duration: 2000
				});
			},

			goBack() {
				app.goBack(1);
			},

			touchstartLogin() {
				this.loginTouched = true;
			},
			touchendLogin() {
				this.loginTouched = false;
			},
			touchstartCode() {
				// Disable touch during timer operation
				if(this.codeTimes > 0) {
					return;
				} else {
					this.codeTouched = true;
				}
			},
			touchendCode() {
				// Disable touch during timer operation
				if(this.codeTimes > 0) {
					return;
				} else {
					this.codeTouched = false;
				}
			},
			close() {
				uni.navigateBack({
					delta: 1,
					animationType: "slide-out-bottom"
				})
			},
			typingMobile(e) {
				var event = e;
				this.mobile = e.detail.value;
			},
			typingVerifyCode(e) {
				var event = e;
				this.verifyCode = e.detail.value;
			},
			typingNickname(e) {
				var event = e;
				this.nickname = e.detail.value;
			},
			getCode() {
				var me = this;
				
				if(me.codeTimes > 0) {
					return;
				}
				
				me.codeTouched = true;
				
				var mobile = me.mobile;
				
				if (app.isStrEmpty(mobile) || mobile.length != 10) {
					uni.showToast({
						title: "Invalid phone number",
						icon: "none"
					});
					return;
				}
				
				var serverUrl = app.globalData.serverUrl;
				// Call backend to send verification code
				uni.request({
					method: "POST",
					url: serverUrl + "/welcome/getSMSCode?mobile=" + mobile,
					success(result) {
						var status = result.data.status;
						// console.log(result);
						
						if (status != 200) {
							uni.showToast({
								title: result.data.msg,
								icon: "none"
							});
						}
						
						// Start 60-second countdown
						if(me.codeTimes == 0) {
							me.doTimer(59);
						}
					}
				});
					
			},
			// Countdown method for sending verification code
			doTimer(times) {
				var me = this;
				// Countdown timer
				var sendCodeBtnFunction = function(){
					var left = times--;
	
					if (left <= 0) {
						me.codeTouched = false;
						me.codeBtnText = "Send Code";
						clearInterval(smsTimer);
					} else {
						me.codeBtnText = left + "s";
					}
					me.codeTimes = left;
				};
				var smsTimer = setInterval(sendCodeBtnFunction, 1000);
			},
			
			doVerify() {
				var me = this;
				var verifyCode = me.verifyCode;
				var mobile = me.mobile;
				
				if (app.isStrEmpty(verifyCode)) {
					uni.showToast({
						title: "Please enter verification code",
						icon: "none"
					});
					return;
				}
				
				if (app.isStrEmpty(mobile) || mobile.length != 10) {
					uni.showToast({
						title: "Invalid phone number",
						icon: "none"
					});
					return;
				}
				 
				var nickname = me.nickname;
				
				var serverUrl = app.globalData.serverUrl;
				// Call backend for login/registration
				uni.request({
					method: "POST",
					url: serverUrl + "/welcome/verify",
					data: {
						"mobile": mobile,
						"smsCode": verifyCode,
					},
					success(result) {
						// console.log(result);
						var status = result.data.status;
						if (status == 200) {
							var userInfo = result.data.data;
							app.setUserInfoSession(userInfo);
							// app.setUserSessionToken(userInfo.userToken);
							
							uni.navigateTo({
								url: "/pages/interviewer",
								animationType: "slide-in-bottom",
							});
							
						} else if (status != 200) {
							uni.showToast({
								title: result.data.msg,
								icon: "none",
								duration: 3000
							});
						}
					}
				});
			},
			
		}
	}
</script>

<style>
	@import url("welcome.css");
</style>
