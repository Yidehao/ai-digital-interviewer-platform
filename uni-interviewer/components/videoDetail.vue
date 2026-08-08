<template>
  <view class="bg">
    <video
      id="myVideo"
      class="ai-interviewer-video"
      src="http://your_ip:your_port/test.mp4"
      controls="false"
      object-fit="cover"
      autoplay="true"
      show-center-play-btn="false"
      show-loading="false"
      vslide-gesture-in-fullscreen="false"
      enable-progress-gesture="false"
      enable-play-gesture="false"
    ></video>
  </view>
</template>

<script>
var app = getApp();
export default {
  props: {
    screenHeight: {
      default: 0,
    },
    src: {
      default: false,
    },
  },
  data() {
    return {
      thisVlog: {}, // Current short video object
      thisVlogId: "", // Current short video primary key id
      thisVlogerId: "", // Current short video creator primary key id
      userId: "",
    };
  },
  created() {},
  methods: {},
};
</script>

<style>
.page {
  /* position: absolute;
	left: 0;
	right: 0;
	top: 0;
	bottom: 0;
	background-color: #FFFFFF; */

  /* display: flex;
	flex-direction: column; */
}

.bg {
  width: 100%;
  height: 100vh;
  /* background-color: aqua; */
}

.ai-interviewer-video {
  /* width: 100%; */
  /* height: 100vh; */
  width: 750rpx;
  height: 100%;
}

/* Fix white space at bottom of video */
/* video {
  vertical-align: middle;
} */
</style>
