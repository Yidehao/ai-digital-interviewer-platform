window.reviewApi = {

    /**
     * The review queue. Carries no scores by design - see ReviewController.
     */
    queue: function(unreviewedOnly) {
        return instance({
            url: '/review/queue?unreviewedOnly=' + (unreviewedOnly ? 'true' : 'false'),
            method: 'get',
        })
    },

    /**
     * The interview, with no assessment attached. Fetched first, on its own, so a reviewer can
     * read what the candidate said without the model's opinion of it on screen.
     */
    transcript: function(sessionId) {
        return instance({
            url: '/review/' + sessionId + '/transcript',
            method: 'get',
        })
    },

    /**
     * The model's assessment. Deliberately a separate call from the transcript, and deliberately
     * only made AFTER the reviewer has recorded their own scores.
     */
    verdict: function(sessionId) {
        return instance({
            url: '/review/' + sessionId + '/verdict',
            method: 'get',
        })
    },

    decide: function(bo) {
        return instance({
            url: '/review/decide',
            method: 'post',
            data: bo,
        })
    },

}
