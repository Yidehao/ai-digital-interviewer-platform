window.app = {
    
    // Local/own environment URLs (replace with your actual domains if needed)
    portalIndexUrl: "http://localhost:8080/portal/index.html",           // Portal homepage URL
    writerLoginUrl: "http://localhost:8080/writer/passport.html",        // Login page
    writerIndexUrl: "http://localhost:8080/writer/contentMng.html",      // Writer center homepage
    writerInfoUrl: "http://localhost:8080/writer/accountInfo.html",      // User information completion page
    adminCenterUrl: "http://localhost:8080/admin/contentReview.html",    // Admin management platform homepage

    // userServerUrl: "http://localhost:8080/user",        // User service backend API address
    // fsServerUrl: "http://localhost:8080/files",         // File service backend API address
    // adminServerUrl: "http://localhost:8080/admin",      // Admin management service backend API address
    // articleServerUrl: "http://localhost:8080/article",  // Article service backend API address

    /**
     * If testing locally with localhost, cookieDomain can be empty.
     * For IP or domain name testing, change cookieDomain to the corresponding IP or domain name
     * Examples:
     *    IP:  192.168.1.111
     *    Domain:   .example.com
     */
    cookieDomain: "",  

    // Check if user is logged in
    judgeUserLoginStatus: function(pageVue) {
        var me = this;
        var utoken = me.getCookie("utoken");
        var uid = me.getCookie("uid");
        
        // console.log("utoken=" + utoken);
        // console.log("uid=" + uid);

        // If both utoken and uid exist in cookies, user is logged in
        if ( me.isNotEmpty(utoken) && me.isNotEmpty(uid)) {
            // Get user info from session storage, if not available, request from backend
            var userInfo = me.fetchUserInfo();
            if (me.isNotEmpty(userInfo)) {
                // User exists, display user info on homepage
                pageVue.userInfo = userInfo;
            } else {
                // User doesn't exist, make request to backend
                var userServerUrl = me.userServerUrl;
                axios.post(
                        userServerUrl + '/user/getUserInfo?userId=' + uid
                        // ,
                        // {}, 
                        // {
                        //     headers: {
                        //         'headerUserId': uid,
                        //         'headerUserToken': utoken
                        //     }
                        // }
                        )
                    .then(res => {
                        // debugger
                        if (res.data.status == 200) {
                            var userInfo = res.data.data;
                            // console.log("app:" + userInfo);
                            // After getting user info, need to check user status to prevent issues if admin has banned the account
                            var activeStatus = userInfo.activeStatus;
                            // If account is frozen, logout
                            if (activeStatus == 2) {
                                alert("Your account has been banned due to violations...");
                                // me.logout(pageVue);
                                return false;
                            }
                            // Save to session storage
                            // me.saveUserInfo(userInfo);
                            // Display user info on homepage
                            pageVue.userInfo = userInfo;
                            return userInfo;
                        } else {
                            console.log(res.data.msg);
                            // Error occurred, return null directly
                            // If utoken or uid is incorrect or tampered with, cannot get user info, return null
                            return false;
                        }
                    });
            }
        } else {
            // Neither token nor uid exists, user has not logged in
            return false;
        }
    },

    logout(pageVue) {
        var me = this;
        var uid = me.getCookie("uid");
        var userServerUrl = me.userServerUrl;
        axios.post(
                userServerUrl + '/passport/logout?userId=' + uid)
            .then(res => {
                if (res.data.status == 200) {
                    // Delete user info from sessionStorage
                    me.deleteUserInfo();
                    // Delete user cookies
                    me.deleteCookie("utoken");
                    me.deleteCookie("uid");
                    // Set user info to null
                    pageVue.userInfo = null;
                    console.log("User logged out");
                } else {
                    console.log(res.data.msg);
                }
            });
    },

    // Save user info in sessionStorage (data validity period: from opening browser to closing browser)
    // localStorage is permanent, not suitable for storing user info
    // Cookies are also not ideal for storing user info, and cookie size is limited to 4k
    saveUserInfo: function(userInfo) {
        var userInfoStr = JSON.stringify(userInfo);
        sessionStorage.setItem("globalUserInfo", userInfoStr);
    },
    // Read user info from sessionStorage
    fetchUserInfo: function() {
        var userInfoStr = sessionStorage.getItem("globalUserInfo");
        return JSON.parse(userInfoStr);
    },
    // Delete user info from sessionStorage
    deleteUserInfo: function() {
        sessionStorage.removeItem("globalUserInfo");
    },



    isEmpty: function (str) {
        if (str == null || str =="" || str == undefined) {
            return true;
        } else {
            return false;
        }
    },

    isNotEmpty: function (str) {
        if (str != null && str !="" && str != undefined) {
            return true;
        } else {
            return false;
        }
    },

    getCookie: function (cname) {
        var name = cname + "=";
        var ca = document.cookie.split(';');
        for (var i = 0; i < ca.length; i++) {
            var c = ca[i];
            // console.log(c)
            while (c.charAt(0) == ' ') c = c.substring(1);
                if (c.indexOf(name) != -1){
                    return c.substring(name.length, c.length);
                }
            }
        return "";
    },

    setCookie: function(name, value) {
        var Days = 365;
        var exp = new Date(); 
        exp.setTime(exp.getTime() + Days*24*60*60*1000);
        var cookieContent = name + "="+ encodeURIComponent (value) + ";path=/;";
        if (this.cookieDomain != null && this.cookieDomain != undefined && this.cookieDomain != '') {
            cookieContent += "domain=" + this.cookieDomain;
        }
        document.cookie = cookieContent + cookieContent;
        // document.cookie = name + "="+ encodeURIComponent (value) + ";path=/;domain=" + cookieDomain;//expires=" + exp.toGMTString();
    },

    deleteCookie: function(name) {
        var cookieContent = name + "=; expires=Thu, 01 Jan 1970 00:00:00 UTC; path=/;";
        if (this.cookieDomain != null && this.cookieDomain != undefined && this.cookieDomain != '') {
            cookieContent += "domain=" + this.cookieDomain;
        }
        document.cookie = cookieContent;
    },

    getUrlParam2(paramName) {
        var reg = new RegExp("(^|&)" + paramName + "=([^&]*)(&|$)");    // Construct a regex object containing the target parameter
        var r = window.location.search.substr(1).match(reg);            // Match target parameter
        if (r != null) return decodeURI(r[2]); return null;             // Return parameter value
    },

    getUrlParam(paramName) {
        let geturl = window.location.href;
        let getInfoObj = geturl.split('?')[1];
        let paramsObj = new URLSearchParams(getInfoObj);
        var result = paramsObj.get(paramName);
        return result;
    },

    getPageName() {
        var thisPath = window.location.pathname;
        var pathArray = thisPath.split("/");
        var pageNameSuffix = pathArray[pathArray.length - 1];
        var thisPage = pageNameSuffix.split(".")[0];
        // console.log(thisPage);
        return thisPage;
    },

    // Format time as: X minutes ago, X days ago
    // time 2020-09-10 20:20:20
    getDateBeforeNow: function(stringTime) {
        var minute = 1000 * 60;
        var hour = minute * 60;
        var day = hour * 24;
        var week = day * 7;
        var month = day * 30;

        var time1 = new Date().getTime(); // Current timestamp
        // console.log(time1);
        var time2 = Date.parse(new Date(stringTime)); // Specified time timestamp
        // console.log(time2);
        var time = time1 - time2;

        var result = null;
        if(time < 0) {
            // alert("Set time cannot be earlier than current time!");
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
        return result;
        console.log(result);
    }
}
