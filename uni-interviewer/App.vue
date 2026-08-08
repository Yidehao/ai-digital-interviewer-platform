<script>
	import provinceList from './json/area_province.js';
	import cityList from './json/area_city.js';
	import districtList from './json/area_district.js';
	export default {
		globalData: {
			serverUrl: "http://192.168.0.104:8080",		// Dev URL, cannot use localhost
			// serverUrl: "http://localhost",					// Wrong example
			
			minNode: {},  
			 
			provinceList: provinceList,
			cityList: cityList,
			districtList: districtList,
			
			env: "production",
			// env: "development",
		},
		data() {
			return {
			}
		},
		onLaunch: function() {
			console.log('App Launch')
			
			
			// this.clearUserInfo();
			
				
			// Portrait orientation only
			// #ifdef APP-PLUS
			plus.screen.lockOrientation("portrait-primary")
			// #endif
						
			// this.showTabBarRedDot(2);
			// this.hideTabBarRedDot(2);
			
			// this.setTabBarRedNumber(0, 2);
			// this.removeTabBarBadge(0);
		},
		onShow: function() {
			console.log('App Show:' + this.getAppEnv());
			
			// WebSocket reconnection
			// if (!me.globalData.chatSocketOpen) {
			// 	me.globalData.chatSocketOpen = true;
			// }
		},
		onHide: function() {
			console.log('App Hide')
		},
		methods: {
			
			getAppEnv() {
				return this.globalData.env;
			},
			
			removeTabBarBadge(index, number) {
				uni.removeTabBarBadge({
				  index: index,
				})	
			},
			setTabBarRedNumber(index, number) {
				uni.setTabBarBadge({
				  index: index,
				  text: number+""
				})	
			},
			showTabBarRedDot(index) {
				uni.showTabBarRedDot({
					index: index
				});
			},
			hideTabBarRedDot(index) {
				uni.hideTabBarRedDot({
					index: index
				});
			},

			goBack(pages) {
				if (pages == null || pages == "" || pages == undefined) {
					pages = 1;
				}
				uni.navigateBack({
					delta: pages
				})
			},

			closeWSConnect() {
				this.globalData.CHAT.close();
			},
			
			// Calculate age based on birthday
			getAge(birthday){     
				if (birthday == null || birthday == undefined || birthday =='') {
					return 0;
				}
			    var returnAge;  
			    var strBirthdayArr = birthday.split("-");  
			    var birthYear = strBirthdayArr[0];  
			    var birthMonth = strBirthdayArr[1];  
			    var birthDay = strBirthdayArr[2];  
			      
			    var d = new Date();  
			    var nowYear = d.getFullYear();  
			    var nowMonth = d.getMonth() + 1;  
			    var nowDay = d.getDate();  
			      
			    if(nowYear == birthYear){  
			        returnAge = 0;// Same year, age is 0
			    }  
			    else{  
			        var ageDiff = nowYear - birthYear ; // Year difference
			        if(ageDiff > 0){  
			            if(nowMonth == birthMonth) {  
			                var dayDiff = nowDay - birthDay;// Day difference
			                if(dayDiff < 0)  
			                {  
			                    returnAge = ageDiff - 1;  
			                }  
			                else  
			                {  
			                    returnAge = ageDiff ;  
			                }  
			            }  
			            else  
			            {  
			                var monthDiff = nowMonth - birthMonth;// Month difference
			                if(monthDiff < 0)  
			                {  
			                    returnAge = ageDiff - 1;  
			                }  
			                else  
			                {  
			                    returnAge = ageDiff ;  
			                }  
			            }  
			        }  
			        else  
			        {  
			            returnAge = -1;// Return -1 indicates invalid birth date (later than today)
			        }  
			    }  
			    return returnAge;// Return age in years
			},
			
			// Check if string is empty
			isStrEmpty (str) {
				if (str == null || str == undefined || str == "") {
					return true;
				} else {
					return false;
				}
				
			},
			// Check if user is logged in
			userIsLogin() {
				var userToken = this.getUserSessionToken();
				// var userInfo = this.getUserInfoSession();
				// console.log("userInfo=" + userInfo);
				console.log("userToken=" + userToken);
				 // && !this.isStrEmpty(userInfo)
				if (!this.isStrEmpty(userToken)) {
					return true;
				} else {
					return false;
				}
			},
			
			// Session storage for user login, token and userInfo
			setUserSessionToken(token){
				uni.setStorageSync("userToken", token);
			},
			getUserSessionToken() {
				var token = uni.getStorageSync("userToken");
				if (this.isStrEmpty(token)) {
					return "";
				}
				return token;
			},
			setUserInfoSession(user){
				uni.setStorageSync("userInfo", JSON.stringify(user));
			},
			getUserInfoSession() {
				var user = uni.getStorageSync("userInfo");
				if (this.isStrEmpty(user)) {
					return null;
				}
				return JSON.parse(user);
			},
			clearUserInfo() {
				uni.removeStorageSync("userInfo");
				uni.removeStorageSync("userToken");
			},
			clearUserToken() {
				uni.removeStorageSync("userToken");
			},
			
			graceNumber(number) {
				if (number == 0) {
					return "0";
				} else if (number > 999 && number <= 9999) {
					return (number/1000).toFixed(1) + 'k';
				} else if (number > 9999 && number <= 99999) {
					return (number/10000).toFixed(1) + 'w';
				} else if (number > 99999) {
					return "10w+";
				} 
				return number;
			},
			
			getDateBeforeNow(stringTime) {
				// console.log(stringTime);
				stringTime = new Date(stringTime.replace(/-/g,'/'))
				
				var minute = 1000 * 60;
				var hour = minute * 60;
				var day = hour * 24;
				var week = day * 7;
				var month = day * 30;
					
				var time1 = new Date().getTime(); // Current timestamp
				// console.log(time1);
				// console.log(new Date(stringTime));
				var time2 = Date.parse(new Date(stringTime)); // Specified time timestamp
				// console.log(time2);
				var time = time1 - time2;
					
			    var result = null;
				if(time < 0) {
					// alert("The set time cannot be earlier than the current time!");
					result = stringTime;
				}else if(time/month >= 1){
					result = parseInt(time/month) + " months ago";
				}else if(time/week >= 1){
					result = parseInt(time/week) + " weeks ago";
				}else if(time/day >= 1){
					result = parseInt(time/day) + " days ago";
				}else if(time/hour >= 1){
					result = parseInt(time/hour) + " hours ago";
				}else if(time/minute >= 1){
					result = parseInt(time/minute) + " minutes ago";
				}else {
					result = "Just now";
				}
				// console.log(result);
				return result;
			},
			
			dateFormat(fmt, date) {
			    let ret;
			    const opt = {
			        "Y+": date.getFullYear().toString(),        // Year
			        "m+": (date.getMonth() + 1).toString(),     // Month
			        "d+": date.getDate().toString(),            // Day
			        "H+": date.getHours().toString(),           // Hour
			        "M+": date.getMinutes().toString(),         // Minute
			        "S+": date.getSeconds().toString()          // Second
			        // Add more format characters as needed, must be converted to string
			    };
			    for (let k in opt) {
			        ret = new RegExp("(" + k + ")").exec(fmt);
			        if (ret) {
			            fmt = fmt.replace(ret[1], (ret[1].length == 1) ? (opt[k]) : (opt[k].padStart(ret[1].length, "0")))
			        };
			    };
			    return fmt;
			},
			
		}
	}
</script>

<style>
/* Common CSS for each page */
/* iOS System Font Stack */
page, view, text, input, button {
	font-family: -apple-system, BlinkMacSystemFont, "SF Pro Display", "SF Pro Text", "Helvetica Neue", Helvetica, Arial, sans-serif;
	-webkit-font-smoothing: antialiased;
	-moz-osx-font-smoothing: grayscale;
}

/* Ensure proper scaling on iOS */
* {
	box-sizing: border-box;
}
</style>
