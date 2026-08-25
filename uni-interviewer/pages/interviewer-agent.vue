<template>
  <view class="bg">
    <view class="video-wrap">
      <video
        v-if="current.aiSrc"
        id="agentVideo"
        class="ai-interviewer-video"
        :src="current.aiSrc"
        controls="false"
        object-fit="cover"
        autoplay="true"
        show-center-play-btn="false"
        show-loading="false"
        enable-progress-gesture="false"
        enable-play-gesture="false"
      ></video>
      <!-- A follow-up has no avatar clip: it did not exist until a moment ago, so nothing was ever
           rendered for it. The panel below carries the text either way, which is why the video is
           optional rather than the question being hidden without one. -->
      <view class="question-panel">
        <text class="question-number">Question {{ answered + 1 }}</text>
        <text class="question-content">{{ current.question || placeholder }}</text>
      </view>
      <view class="record-box" v-if="phase === 'recording'">
        <text class="record-label">Recording</text>
        <text class="record-time">{{ formatRecordTime(elapsed) }}</text>
      </view>
    </view>

    <view class="bottom-bar">
      <view class="btn-action btn-primary" hover-class="btn-action-hover"
            @tap="startAnswer" v-if="phase === 'ready'">
        <text class="btn-action-text">Start Answer</text>
      </view>
      <view class="btn-action btn-primary" hover-class="btn-action-hover"
            @tap="finishAnswer" v-if="phase === 'recording'">
        <text class="btn-action-text">Done Answering</text>
      </view>
      <view class="btn-action btn-muted" v-if="phase === 'waiting' || phase === 'sending'">
        <text class="btn-action-text">{{ phase === 'sending' ? 'Sending…' : 'Interviewer is thinking…' }}</text>
      </view>
      <view class="btn-action btn-submit" hover-class="btn-action-hover"
            @tap="goToResults" v-if="phase === 'done'">
        <text class="btn-action-text">Finish</text>
      </view>
    </view>
  </view>
</template>

<script>
// The agent interview.
//
// Same recorder, same STT endpoint and same look as pages/interviewer.vue. What differs is that
// there is no question list: the server decides what to ask next after reading the answer, so the
// client holds exactly one question at a time and asks for the next one.
//
// WHY POLLING RATHER THAN SSE
//
//   The agent path streams over SSE and the eval harness uses it. EventSource does not exist in
//   app-plus or mp-weixin, which is two of this project's three targets, so a stream-only client
//   would leave the agent loop reachable only from a browser - and this file exists precisely to
//   make it reachable from the product.
//
//   GET /interview/{sessionId}/poll?afterSeq=N is a plain request that returns the pending
//   question or nothing. Short poll, not long poll: parking a request thread per waiting candidate
//   for the tens of seconds a model turn takes would spend the 48-concurrent result measured in
//   Phase 8 to save traffic that costs almost nothing.
//
// WHAT THIS FILE DELIBERATELY DOES NOT DO
//
//   It does not touch pages/interviewer.vue. That page still runs every job whose interview_mode
//   is 'scripted', which is all of them until someone changes a column. Rollback is a database
//   update, not a redeploy.

var app = getApp();

// Long enough that a model turn does not feel polled, short enough to be invisible. A turn takes
// tens of seconds, so this is a few dozen cheap requests per question.
var POLL_MS = 1500;
// A turn that produces nothing for this long is a stall, not thinking. Told plainly rather than
// leaving a candidate watching a spinner during a timed interview.
var STALL_MS = 180000;

export default {
  data() {
    // #ifdef APP-PLUS
    const recorderManager = uni.getRecorderManager();
    // #endif
    return {
      // #ifdef APP-PLUS
      recorderManager,
      // #endif
      sessionId: "",
      current: { turnId: null, seq: -1, question: "", aiSrc: "" },
      phase: "waiting", // waiting | ready | recording | sending | done
      answered: 0,
      elapsed: 0,
      placeholder: "Connecting to your interviewer…",
      recordStartTime: null,
      pollTimer: null,
      tickTimer: null,
      lastProgressAt: 0,
    };
  },

  onLoad() {
    // #ifdef APP-PLUS
    this.initRecorder();
    // #endif
    this.startSession();
  },

  onUnload() {
    this.stopPolling();
    this.stopTick();
  },

  methods: {
    startSession() {
      var self = this;
      var info = app.getUserInfoSession();
      if (!info || !info.candidateId) {
        uni.showToast({ title: "Session expired", icon: "none" });
        return;
      }
      uni.request({
        method: "POST",
        url: app.globalData.serverUrl + "/interview/" + info.candidateId + "/start",
        success(result) {
          var body = result.data;
          if (body && body.status === 200 && body.data && body.data.sessionId) {
            self.sessionId = body.data.sessionId;
            self.lastProgressAt = Date.now();
            self.startPolling();
          } else {
            // Refused means an unknown candidate or one already mid-interview. Saying which is
            // more useful than "failed", because only one of them is the candidate's problem.
            self.placeholder = "Could not start. You may already have an interview in progress.";
            uni.showToast({ title: "Could not start interview", icon: "none", duration: 3000 });
          }
        },
        fail() {
          self.placeholder = "Could not reach the server.";
        },
      });
    },

    startPolling() {
      var self = this;
      this.stopPolling();
      this.pollTimer = setInterval(function () { self.pollOnce(); }, POLL_MS);
      this.pollOnce();
    },

    stopPolling() {
      if (this.pollTimer) {
        clearInterval(this.pollTimer);
        this.pollTimer = null;
      }
    },

    pollOnce() {
      var self = this;
      if (!this.sessionId) return;
      // Never poll over a recording or an in-flight submit: a question arriving mid-answer would
      // replace the one being answered and the recording would be submitted against the wrong turn.
      if (this.phase === "recording" || this.phase === "sending") return;

      uni.request({
        method: "GET",
        url: app.globalData.serverUrl + "/interview/" + self.sessionId
             + "/poll?afterSeq=" + self.current.seq,
        success(result) {
          var body = result.data;
          if (!body || body.status !== 200 || !body.data) return;
          var payload = body.data;

          if (payload.question) {
            self.current = {
              turnId: payload.turnId,
              seq: payload.seq,
              question: payload.question,
              aiSrc: payload.aiSrc || "",
            };
            self.phase = "ready";
            self.lastProgressAt = Date.now();
            return;
          }
          if (payload.done) {
            self.phase = "done";
            self.placeholder = "That is the end of the interview. Thank you.";
            self.stopPolling();
            return;
          }
          if (Date.now() - self.lastProgressAt > STALL_MS) {
            self.placeholder = "The interviewer is taking longer than expected.";
          }
        },
      });
    },

    initRecorder() {
      var self = this;
      // #ifdef APP-PLUS
      this.recorderManager.onStop(function (res) {
        var seconds = Math.round((new Date().getTime() - self.recordStartTime) / 1000);
        if (seconds < 1) {
          uni.showToast({ icon: "none", title: "Speaking time too short…" });
          self.phase = "ready";
          return;
        }
        self.transcribeAndSubmit(res.tempFilePath);
      });
      // #endif
    },

    startAnswer() {
      this.phase = "recording";
      this.elapsed = 0;
      this.recordStartTime = new Date().getTime();
      this.startTick();
      // #ifdef APP-PLUS
      this.recorderManager.start({
        duration: 600000,
        sampleRate: 16000,
        numberOfChannels: 1,
      });
      // #endif
    },

    finishAnswer() {
      this.stopTick();
      this.phase = "sending";
      // #ifdef APP-PLUS
      this.recorderManager.stop();
      // #endif
    },

    transcribeAndSubmit(voicePath) {
      var self = this;
      var info = app.getUserInfoSession();
      uni.uploadFile({
        header: {
          headerUserId: info.id,
          headerUserToken: app.getUserSessionToken(),
        },
        url: app.globalData.serverUrl + "/speech/uploadVoice",
        name: "file",
        filePath: voicePath,
        success(result) {
          var body = null;
          try {
            body = JSON.parse(result.data);
          } catch (e) {
            body = null;
          }
          var transcript = body && body.status === 200 && body.data ? body.data.transcript : "";
          var confidence = body && body.status === 200 && body.data ? body.data.confidence : null;

          if (!transcript) {
            // The server classifies speech failures by who can fix them and returns a message the
            // candidate can act on - re-record, or wait because the service is down and their
            // interview is safe. Showing body.msg beats any string this client could invent,
            // because only the server knows which of those happened.
            var reason = (body && body.msg)
                || "Could not hear that — please answer again";
            uni.showToast({ title: reason, icon: "none", duration: 4000 });
            self.phase = "ready";
            return;
          }
          self.submitAnswer(transcript, confidence);
        },
        fail() {
          uni.showToast({ title: "Upload failed — please answer again", icon: "none" });
          self.phase = "ready";
        },
      });
    },

    submitAnswer(transcript, confidence) {
      var self = this;
      var url = app.globalData.serverUrl + "/interview/" + this.sessionId + "/answer";
      var data = { turnId: this.current.turnId, transcript: transcript };
      if (confidence !== null && confidence !== undefined) {
        data.sttConfidence = confidence;
      }
      uni.request({
        method: "POST",
        url: url,
        data: data,
        header: { "content-type": "application/x-www-form-urlencoded" },
        success(result) {
          var body = result.data;
          if (body && body.status === 200) {
            self.answered += 1;
            self.lastProgressAt = Date.now();
            // Cleared so the panel does not show the answered question while the next is decided.
            self.current.question = "";
            self.placeholder = "Interviewer is thinking…";
            self.phase = "waiting";
          } else {
            uni.showToast({ title: "Answer not accepted — please try again", icon: "none" });
            self.phase = "ready";
          }
        },
        fail() {
          uni.showToast({ title: "Could not send answer", icon: "none" });
          self.phase = "ready";
        },
      });
    },

    goToResults() {
      uni.redirectTo({ url: "/pages/me", animationType: "slide-in-bottom" });
    },

    startTick() {
      var self = this;
      this.stopTick();
      this.tickTimer = setInterval(function () { self.elapsed += 1; }, 1000);
    },

    stopTick() {
      if (this.tickTimer) {
        clearInterval(this.tickTimer);
        this.tickTimer = null;
      }
    },

    formatRecordTime(seconds) {
      var m = Math.floor(seconds / 60);
      var s = seconds % 60;
      return (m < 10 ? "0" + m : m) + ":" + (s < 10 ? "0" + s : s);
    },
  },
};
</script>

<style scoped>
.bg { display: flex; flex-direction: column; height: 100vh; background: #0e1116; }
.video-wrap { position: relative; flex: 1; }
.ai-interviewer-video { width: 100%; height: 100%; }
.question-panel {
  position: absolute; left: 0; right: 0; top: 0;
  padding: 32rpx; background: rgba(14, 17, 22, 0.72);
}
.question-number { display: block; font-size: 24rpx; color: #8b97a8; margin-bottom: 10rpx; }
.question-content { display: block; font-size: 34rpx; color: #f2f5f9; line-height: 1.5; }
.record-box {
  position: absolute; right: 32rpx; bottom: 32rpx;
  padding: 12rpx 24rpx; border-radius: 999rpx; background: rgba(200, 40, 40, 0.9);
}
.record-label { font-size: 24rpx; color: #fff; margin-right: 12rpx; }
.record-time { font-size: 24rpx; color: #fff; }
.bottom-bar { padding: 32rpx; background: #0e1116; }
.btn-action {
  height: 96rpx; border-radius: 12rpx;
  display: flex; align-items: center; justify-content: center;
}
.btn-primary { background: #2f6df6; }
.btn-submit { background: #1f9d55; }
.btn-muted { background: #232a35; }
.btn-action-hover { opacity: 0.8; }
.btn-action-text { font-size: 32rpx; color: #fff; }
</style>
