<template>
  <view class="bg">
    <!-- Video area: question + timer stay as cover-view inside video -->
    <view class="video-wrap">
      <video
        v-if="questionList.length > 0 && questionList[currentIndex].aiSrc"
        id="myVideo"
        class="ai-interviewer-video"
        :src="questionList[currentIndex].aiSrc"
        controls="false"
        object-fit="cover"
        autoplay="true"
        show-center-play-btn="false"
        show-loading="false"
        vslide-gesture-in-fullscreen="false"
        enable-progress-gesture="false"
        enable-play-gesture="false"
      >
        <cover-view class="operator-content">
          <cover-view class="question-wrapper">
            <cover-view class="question-number"
              >Question {{ currentIndex + 1 }}/{{ questionList.length }}</cover-view
            >
            <cover-view class="question-content">{{
              questionList[currentIndex].question
            }}</cover-view>
          </cover-view>
        </cover-view>
        <cover-view class="record-box" v-if="isRecordAudio == 2">
          <cover-view class="record-label">Recording</cover-view>
          <cover-view class="record-time">{{ formatRecordTime(totalTimer) }}</cover-view>
        </cover-view>
      </video>
    </view>

    <!-- Buttons: normal view (not inside video) so touch works and text is not clipped -->
    <view class="bottom-bar">
      <view
        class="btn-action btn-primary"
        hover-class="btn-action-hover"
        @tap="startAnswer"
        v-if="isRecordAudio == 1"
      >
        <text class="btn-action-text">Start Answer</text>
      </view>
      <view
        class="btn-action btn-primary"
        hover-class="btn-action-hover"
        @tap="nextQuestion(currentIndex)"
        v-if="isRecordAudio == 2"
      >
        <text class="btn-action-text">Complete & Next</text>
      </view>
      <view
        class="btn-action btn-submit"
        hover-class="btn-action-hover"
        @tap="submitInterview"
        v-if="isRecordAudio == 3"
      >
        <text class="btn-action-text">Submit Interview</text>
      </view>
    </view>
  </view>
</template>

<script>
var app = getApp();
export default {
  data() {
    // #ifdef APP-PLUS
    const recorderManager = uni.getRecorderManager();
    const innerAudioContext = uni.createInnerAudioContext();
    innerAudioContext.autoplay = true;
    // #endif

    return {
      btnValue: "",
      isRecordAudio: 1, // 1: Not started 2: Answering 3: All complete

      // #ifdef APP-PLUS
      recorderManager,
      innerAudioContext,
      recorderVoice: {
        voicePath: "",
        speakVoiceDuration: 0,
      },
      // #endif

      currentIndex: 0,
      questionList: [],
      answerList: [],
      questionList2: [
        {
          question: "What is MongoDB? Does it have transaction types?",
          aiSrc: "", // Set remote video URL here if needed
        },
        {
          question:
            "What is the difference between SpringCloud and SpringBoot?",
          aiSrc: "", // Set remote video URL here if needed
        },
      ],
      totalTimer: 1,
    };
  },
  onLoad() {
    var me = this;

    // #ifdef APP-PLUS
    this.initRecorder();
    // #endif

    this.initQuestion();
  },
  onReady: function (res) {
    this.videoContext = uni.createVideoContext("myVideo");
  },
  methods: {
    initQuestion() {
      var me = this;

      var candidateInfo = app.getUserInfoSession();
      console.log(candidateInfo);
      var candidateId = candidateInfo.candidateId;

      var serverUrl = app.globalData.serverUrl;
      // Call backend to prepare questions
      uni.request({
        method: "GET",
        url:
          serverUrl + "/questionLib/prepareQuestion?candidateId=" + candidateId,
        success(result) {
          console.log(result);
          var status = result.data.status;
          if (status == 200) {
            var questionList = result.data.data;
            me.questionList = questionList;
          } else if (status != 200) {
            uni.showToast({
              title: result.data.msg,
              icon: "none",
              duration: 3000,
            });
          }
        },
      });
    },

    // Initialize recorder
    initRecorder() {
      let self = this;

      // #ifdef APP-PLUS
      this.recorderManager.onStop(function (res) {
        // Get stop time and calculate time difference
        var recordEndTime = new Date();
        var recordStartTime = self.recordStartTime;
        // console.log("recordEndTime = " + recordEndTime.getTime());
        // console.log("recordStartTime = " + recordStartTime.getTime());
        var timeDiff = recordEndTime.getTime() - recordStartTime.getTime();
        // console.log("timeDiff = " + timeDiff);
        var timeDiffSeconds = Math.round(timeDiff / 1000);
        // Time difference is the duration of recorded audio
        // self.recorderVoice.speakVoiceDuration = timeDiffSeconds;
        // console.log("this.recorderVoice.speakVoiceDuration = " + self.recorderVoice.speakVoiceDuration);
        // Auto-close after 60s, so need to set isSpeaking to false
        // self.isSpeaking = false;

        // self.recorderVoice.voicePath = res.tempFilePath;

        if (timeDiffSeconds < 1) {
          uni.showToast({
            icon: "none",
            title: "Speaking time too short...",
          });
          self.startAnswer();
          return;
        }

        var recorderVoice = new Object();
        recorderVoice["speakVoiceDuration"] = timeDiffSeconds;
        recorderVoice["voicePath"] = res.tempFilePath;

        // console.log(recorderVoice);
        // self.voiceDisplay(res.tempFilePath);
        // console.log("self.currentIndex = " + self.currentIndex);
        // return;

        var currentIndex = self.currentIndex;

        var questionList = self.questionList;
        var pendingIndex = currentIndex + 1;
        if (pendingIndex < questionList.length) {
          self.currentIndex = pendingIndex;
          self.isRecordAudio = 1;

          var smsTimer = self.smsTimer;
          clearInterval(smsTimer);
        } else {
          self.isRecordAudio = 3;
        }

        // Upload audio to server
        var me = this;
        var userId = getApp().getUserInfoSession().id;
        // Upload
        var serverUrl = app.globalData.serverUrl;
        uni.uploadFile({
          header: {
            headerUserId: userId,
            headerUserToken: app.getUserSessionToken(),
          },
          url: serverUrl + "/speech/uploadVoice",
          name: "file",
          filePath: res.tempFilePath,
          success(result) {
            console.log(result);
            if (result.statusCode == 200) {
              var res = JSON.parse(result.data);
              // console.log(res);
              if (res.status == 200) {
                var answerContent = res.data;

                console.log("answerContent = " + answerContent);
                console.log("self.currentIndex = " + currentIndex);
                self.questionList[currentIndex].answerContent = answerContent;
              } else {
                // uni.showToast({
                // 	title: res.msg,
                // 	icon: 'none'
                // })
                self.questionList[currentIndex].answerContent = "";
              }
            } else {
              // uni.showToast({
              // 	title: "Upload failed",
              // 	icon: 'error'
              // })
              self.questionList[currentIndex].answerContent = "";
            }
          },
        });
      });
      // #endif

      var innerAudioContext = uni.createInnerAudioContext();
      this.innerAudioContext = innerAudioContext;
      this.innerAudioContext.autoplay = true;

      this.innerAudioContext.onEnded(function (res) {
        // console.log('innerAudioContext end' + JSON.stringify(res));
        // Playback stopped, set playback switch to -1
        self.voicePlayingIndex = -1;
      });
    },

    startRecord() {
      // console.log('Start recording');
      this.recorderManager.start({
        duration: 600000, // Default time (10 minutes), will auto-stop after specified duration
        sampleRate: 16000, // Sample rate for App and Mini Program
        // sampleRate: 8000,// Sample rate for App and Mini Program
        //encodeBitRate:96000,// Encoding bitrate, only supported by Mini Program
        numberOfChannels: 1,
        // format:'PCM',// Format aac/mp3/wav/PCM, App default is mp3, Mini Program default is aac
      });
      // Start recording time
      var recordStartTime = new Date();
      this.recordStartTime = recordStartTime;
    },
    endRecord() {
      // console.log('Recording ended');
      this.recorderManager.stop();
    },

    voiceDisplay(voicePath) {
      // console.log(1122);
      if (voicePath) {
        // console.log(3344);
        // console.log(voicePath);
        this.innerAudioContext.src = voicePath;
        this.innerAudioContext.play();
      }
    },

    submitInterview() {
      var totalTimer = this.totalTimer;
      var questionList = this.questionList;
      var candidateInfo = app.getUserInfoSession();
      if (!candidateInfo || !candidateInfo.candidateId) {
        uni.showToast({ title: "Session expired", icon: "none" });
        return;
      }
      var candidateId = candidateInfo.candidateId;
      var jobId = candidateInfo.jobId;

      var bo = {
        candidateId: candidateId,
        jobId: jobId,
        questionAnswerList: questionList,
        totalSeconds: totalTimer,
      };

      var serverUrl = app.globalData.serverUrl;
      uni.showLoading({ title: "Submitting…" });

      uni.request({
        method: "POST",
        url: serverUrl + "/interviewRecord/collect",
        data: bo,
        success(result) {
          uni.hideLoading();
          var data = result.data;
          if (!data) {
            uni.showToast({ title: "Invalid response", icon: "none" });
            return;
          }
          var status = data.status;
          if (status == 200) {
            uni.redirectTo({
              url: "/pages/me",
              animationType: "slide-in-bottom",
            });
          } else {
            uni.showToast({
              title: (data.msg && data.msg.trim()) ? data.msg : "Submit failed",
              icon: "none",
              duration: 3000,
            });
          }
        },
        fail(err) {
          uni.hideLoading();
          console.error("interviewRecord/collect fail", err);
          uni.showToast({
            title: "Network error, please try again",
            icon: "none",
            duration: 3000,
          });
        },
      });
    },

    // Timer method
    doTimer() {
      var me = this;
      var totalTimer = this.totalTimer;
      // Countdown timer
      var timerFunction = function () {
        me.totalTimer = totalTimer++;
      };
      var smsTimer = setInterval(timerFunction, 1000);
      this.smsTimer = smsTimer;
    },

    startAnswer() {
      if (this.smsTimer) {
        clearInterval(this.smsTimer);
        this.smsTimer = null;
      }
      this.totalTimer = 0; // reset so each question starts from 00:00
      this.isRecordAudio = 2;
      this.doTimer();

      this.startRecord();
    },

    nextQuestion(index) {
      var me = this;

      me.endRecord();

      console.log("The index value here is: " + index);

      if (index < 9) {
        // The 10th question doesn't need to play, stays paused
        console.log("~~~~~~~~~~~~~~~~Entering if~~~~~~~~~~~~~~~");
        this.videoContext.play();
      }

      // var questionList = this.questionList;
      // var pendingIndex = index + 1;
      // if (pendingIndex < questionList.length) {
      // 	me.currentIndex = pendingIndex;
      // 	me.isRecordAudio = 1;

      // 	var smsTimer = this.smsTimer;
      // 	clearInterval(smsTimer);
      // } else {
      // 	me.isRecordAudio = 3;
      // }
    },

    next(index) {
      var me = this;

      var questionList = this.questionList;
      var pendingIndex = index + 1;
      if (pendingIndex < questionList.length) {
        me.currentIndex = pendingIndex;
        me.isRecordAudio = 1;

        var smsTimer = this.smsTimer;
        clearInterval(smsTimer);
      } else {
        me.isRecordAudio = 3;
      }
    },

    /**
     * Format seconds as MM:SS for recording display (e.g. 01:23)
     */
    formatRecordTime(t) {
      let m = parseInt(`${(t / 60) % 60}`, 10);
      let s = parseInt(`${t % 60}`, 10);
      m = m < 10 ? "0" + m : String(m);
      s = s < 10 ? "0" + s : String(s);
      return m + ":" + s;
    },

    /**
     * Convert seconds to hours:minutes:seconds (for other use)
     */
    formatSeconds(t) {
      let h = parseInt(`${t / 60 / 60}`, 10);
      let m = parseInt(`${(t / 60) % 60}`, 10);
      let s = parseInt(`${t % 60}`, 10);
      h = h < 10 ? "0" + h : String(h);
      m = m < 10 ? "0" + m : String(m);
      s = s < 10 ? "0" + s : String(s);
      return `${h}:${m}:${s}`;
    },
  },
};
</script>

<style>
@import url("interviewer.css");
</style>
