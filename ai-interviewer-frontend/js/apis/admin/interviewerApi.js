window.interviewerApi = {

    uploadInterviewerImage: function(formData) {
        return instance({
            url: '/file/uploadInterviewerImage',
            method: 'post',
            data: formData,
            headers: {'Content-Type': 'multipart/form-data'}
        })
    },

    createOrUpdate: function(bo) {
        return instance({
            url: '/interviewer/createOrUpdate',
            method: 'post',
            data: bo
        })
    },

    deleteInterviewer: function(params) {
        return instance({
            url: '/interviewer/delete',
            method: 'delete',
            params: params
        })
    },

    list: function() {
        return instance({
            url: '/interviewer/list',
            method: 'get',
        })
    },

}

