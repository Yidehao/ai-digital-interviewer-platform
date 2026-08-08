
// Create axios instance
const instance = axios.create({
    // baseURL: process.env.VUE_APP_BASE_API, // url = base url + request url
    baseURL: "http://127.0.0.1:8080", // url = base url + request url
    withCredentials: true, // send cookies when cross-domain requests
    timeout: 5000 // request timeout
});

// Axios request interceptor
instance.interceptors.request.use(
    config => {
        // do something before request is sent
        
        var userInfo = cookieUtils.getUserInfo();
        // console.log(userInfo);
        if (userInfo) {
            // console.log("userId = " + userInfo.id);
            config.headers['headerUserId'] = userInfo.id;
        }

        var userToken = cookieUtils.getToken();
        // console.log("userToken = " + userToken);
        if (userToken) {
            // console.log("userToken = " + userToken);
            config.headers['headerUserToken'] = userToken;
        }

        return config
    },
    error => {
        // do something with request error
        console.log(error) // for debug
        return Promise.reject(error)
    }
);

// Axios response interceptor
instance.interceptors.response.use(
    /**
     * If you want to get http information such as headers or status
     * Please return  response => response
    */

    /**
     * Determine the request status by custom code
     * Here is just an example
     * You can also judge the status by HTTP Status Code
     */
    response => {
        const res = response.data
        // console.log(res);
        // if the custom code is not 20000, it is judged as an error.
        if (res.status !== 200) {

            // 599 represents form validation, need to extract all field error messages from data
            if (res.status == 599) {
                var errorData = res.data;
                var errorMsg = "";
                for (var key in errorData) {
                    if (errorData.hasOwnProperty(key)) {
                        errorMsg += (errorData[key]) + "\n";
                    }
                }
                swal("Error", errorMsg, "error");
            } else {
                swal("Error", res.msg, "error");
            }
            
            // Message({
            //     message: res.msg || 'Error',
            //     type: 'error',
            //     duration: 2 * 1000,
            //     showClose: true
            // })

            // if (res.status === 500) {
            //     Message({
            //         message: res.msg,
            //         type: "error",
            //         showClose: true,
            //         duration: 2000,
            //     });
            //     return;
            // };

            // 50008: Illegal token; 50012: Other clients logged in; 50014: Token expired;
            if (res.code === 50008 || res.code === 50012 || res.code === 50014) {
                // to re-login
                MessageBox.confirm('You have been logged out, you can cancel to stay on this page, or log in again', 'Confirm logout', {
                    confirmButtonText: 'Re-Login',
                    cancelButtonText: 'Cancel',
                    type: 'warning'
                }).then(() => {
                    store.dispatch('user/resetToken').then(() => {
                        location.reload()
                    })
                })
            }
            return Promise.reject(new Error(res.msg || 'Error'))
        } else {
            return res
        }
    },
    error => {
        console.log('err: ' + error) // for debug
        console.log('err: ' + error.data) // for debug
        swal("Error", error.data, "error");
        // Message({
        //     message: error.message,
        //     type: 'error',
        //     duration: 5 * 1000
        // })
        return Promise.reject(error)
    }
)

// window.request = {

//     sendSmsCode3: function(params) {
//         return instance({
//             url: '/passport/getSMSCode',
//             method: 'post',
//             params: params
//         })
//     },
    
//     sendSmsCode2: function(mobile) {
//         const url = '/passport/getSMSCode?mobile=' + mobile;
//         instance.post(url)
//         .then(response => {
//             console.log(response); // Handle response data
//         })
//         .catch(error => {
//             console.error(error); // Handle error
//         });
//     }
// }

