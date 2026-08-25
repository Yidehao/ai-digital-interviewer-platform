// The review console is the one part of this panel behind auth, because it is the one part that
// serves interview transcripts and hiring assessments. The token is held by the operator and
// entered once; it is a shared secret rather than a per-user login, and reviewApi.token() says so
// out loud rather than implying an account system that does not exist.
function reviewAuthHeaders() {
    var token = localStorage.getItem('reviewAdminToken');
    if (!token) {
        token = window.prompt(
            'Review console token\n\nThis screen serves interview transcripts and assessments, so it '
            + 'requires the token set as REVIEW_ADMIN_TOKEN on the server.');
        if (token) { localStorage.setItem('reviewAdminToken', token); }
    }
    return { headerAdminToken: token || '' };
}

window.reviewApi = {

    /** Clears a wrong or rotated token so the next call asks again. */
    forgetToken: function() {
        localStorage.removeItem('reviewAdminToken');
    },

    /**
     * The review queue. Carries no scores by design - see ReviewController.
     */
    queue: function(unreviewedOnly) {
        return instance({
            url: '/review/queue?unreviewedOnly=' + (unreviewedOnly ? 'true' : 'false'),
            method: 'get',
            headers: reviewAuthHeaders(),
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
            headers: reviewAuthHeaders(),
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
            headers: reviewAuthHeaders(),
        })
    },

    decide: function(bo) {
        return instance({
            url: '/review/decide',
            method: 'post',
            data: bo,
            headers: reviewAuthHeaders(),
        })
    },

}
