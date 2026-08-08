<template>
	<view class="page-content">
		
		<view class="submit-box">
			<text class="icon-submit-emoji">✓</text>
			<text class="done-text">Interview completed, please wait for result notification!</text>
		</view>
		
		<view class="say-something-block">
			<view class="say-something-box" style="margin-top: 4px;">
				<text class="more-text">This App is a demo version for project demonstration</text>
			</view>
			
			<view class="say-something-box" style="margin-top: 4px;">
				<text class="more-text">Source code and related content copyright reserved</text>
			</view>
			
			<view class="say-something-box" style="margin-top: 3px;">
				<text class="more-text">www.example.com</text>
			</view>
			
			<view class="say-something-box" style="margin-top: 3px;">
				<text class="more-text">®️ AI Interviewer Project</text>
			</view>
			
			<view class="smill-box">
				<text class="smill-emoji">😊</text>
			</view>
		</view>
		
	</view>
</template>

<script>
	var app = getApp();
	export default {
		data() {
			return {
				env: app.getAppEnv(),
			}
		},
		onShow() {
		},
		onLoad() {
		},
		methods:{
		}
	}
</script>

<style>
	@import url("me.css");
</style>
