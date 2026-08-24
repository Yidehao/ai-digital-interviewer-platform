window.myrouter = {

    routes: [

        { path: '/', component: httpVueLoader('candidate/list.vue') },
		{ path: '/dashboard', component: httpVueLoader('dashboard.vue') },

        { path: '/candidateMng/candidateList', component: httpVueLoader('candidate/list.vue') },
        { path: '/candidateMng/candidateCreate', component: httpVueLoader('candidate/create.vue') },
        { path: '/candidateMng/interviewRecord', component: httpVueLoader('candidate/interviewRecord.vue') },
        { path: '/candidateMng/reviewQueue', component: httpVueLoader('review/queue.vue') },
		
        { path: '/jobMng/jobList', component: httpVueLoader('job/list.vue') },
		{ path: '/jobMng/jobCreate', component: httpVueLoader('job/create.vue') },

        { path: '/aiMng/aiSettings', component: httpVueLoader('settings/aiMng.vue') },
        { path: '/aiMng/questionLib', component: httpVueLoader('settings/questionLib.vue') },
	],

    // role=1 means only admin can see; role=2 means only regular users can see
    menuList: [

        { title: 'Candidate Management', path: '/candidateMng', index: 'candidateMng', icon: 'el-icon-s-order', children: [
                { title: 'Candidate List', path: '/candidateMng/candidateList', index: 'candidateList',  children: [], role: 1, },
                { title: 'Create Candidate', path: '/candidateMng/candidateCreate', index: 'candidateCreate',  children: [], role: 1, },
                { title: 'Interview Records', path: '/candidateMng/interviewRecord', index: 'interviewRecord', children: [], role: 1, },
                { title: 'Review Queue', path: '/candidateMng/reviewQueue', index: 'reviewQueue', children: [], role: 1, },
            ], role: 1, 
        },
        { title: 'Job Management', path: '/jobMng', index: 'jobMng', icon: 'el-icon-s-help', children: [
                { title: 'Job List', path: '/jobMng/jobList', index: 'jobList', children: [], role: 1, },
                { title: 'Create Job', path: '/jobMng/jobCreate', index: 'jobCreate', children: [], role: 1, },
            ], role: 1,
        },
        { title: 'AI Interviewer', path: '/aiMng', index: 'aiMng', icon: 'el-icon-s-tools', children: [
                { title: 'Interviewer Settings', path: '/aiMng/aiSettings', index: 'aiSettings',  children: [], role: 1, },
                { title: 'Question Library', path: '/aiMng/questionLib', index: 'questionLib',  children: [], role: 1, },
            ], role: 1,
        },
        
    ]
}