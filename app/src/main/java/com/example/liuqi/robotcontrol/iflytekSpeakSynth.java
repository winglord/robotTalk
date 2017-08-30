package com.example.liuqi.robotcontrol;

import android.app.Activity;
import android.os.Bundle;
import android.os.Environment;

import com.iflytek.cloud.ErrorCode;
import com.iflytek.cloud.InitListener;
import com.iflytek.cloud.SpeechConstant;
import com.iflytek.cloud.SpeechError;
import com.iflytek.cloud.SpeechSynthesizer;
import com.iflytek.cloud.SynthesizerListener;

public class iflytekSpeakSynth {
    private static SpeechSynthesizer mTts;
    private static Activity myActivity;

    // 默认发音人
    private static String voicer = "mengmeng";
    private static String voiceSpeed = "100";
    private static String voicePitch = "60";

    //公共参数
    private static String mEngineType = SpeechConstant.TYPE_CLOUD;
    // 缓冲进度
    private static int mPercentForBuffering = 0;
    // 播放进度
    private static int mPercentForPlaying = 0;

    public static void initForSpeakSynth(Activity mainActivity) {
        myActivity = mainActivity;
        mTts = SpeechSynthesizer.createSynthesizer(myActivity, mTtsInitListener);
        setParamForSpeak();
    }
    /**
     * 参数设置    }

     */
    public static void setParamForSpeak(){
        // 清空参数
        mTts.setParameter(SpeechConstant.PARAMS, null);
        // 根据合成引擎设置相应参数
        if(mEngineType.equals(SpeechConstant.TYPE_CLOUD)) {
            mTts.setParameter(SpeechConstant.ENGINE_TYPE, SpeechConstant.TYPE_CLOUD);
            // 设置在线合成发音人
            mTts.setParameter(SpeechConstant.VOICE_NAME,voicer);
        }else {
            mTts.setParameter(SpeechConstant.ENGINE_TYPE, SpeechConstant.TYPE_LOCAL);
            // 设置本地合成发音人 voicer为空，默认通过语音+界面指定发音人。
            mTts.setParameter(SpeechConstant.VOICE_NAME,"");
        }
        //设置合成语速
        mTts.setParameter(SpeechConstant.SPEED, voiceSpeed);
        //设置合成音调
        mTts.setParameter(SpeechConstant.PITCH, voicePitch);
        //设置合成音量
        mTts.setParameter(SpeechConstant.VOLUME,"50");
        //设置播放器音频流类型
        mTts.setParameter(SpeechConstant.STREAM_TYPE,"3");

        // 设置播放合成音频打断音乐播放，默认为true
        mTts.setParameter(SpeechConstant.KEY_REQUEST_FOCUS, "true");

        // 设置合成音频保存路径，设置路径为sd卡请注意WRITE_EXTERNAL_STORAGE权限
        mTts.setParameter(SpeechConstant.PARAMS,"tts_audio_path="+ Environment.getExternalStorageDirectory()+"/test.pcm");
    }

    public static void changeSpeechParam(String newVoicer, String newVoiceSpeed, String newVoicePitch)
    {
        voicer = newVoicer;
        voiceSpeed = newVoiceSpeed;
        voicePitch = newVoicePitch;
    }

    public static void TrySpeaking(String text)
    {
        setParamForSpeak();
        int code = mTts.startSpeaking(text, mTtsListener);
        if (code != ErrorCode.SUCCESS) {
            if(code == ErrorCode.ERROR_COMPONENT_NOT_INSTALLED){
                //未安装则跳转到提示安装页面
                //mInstaller.install();
            }else {
                MyGoogleActivity.showTip("语音合成失败,错误码: " + code);
            }
        }
    }

    private static InitListener mTtsInitListener = new InitListener() {
        @Override
        public void onInit(int code) {
            if (code != ErrorCode.SUCCESS) {
                MyGoogleActivity.showTip("初始化失败,错误码："+code);
            } else {
                // 初始化成功，之后可以调用startSpeaking方法
                // 注：有的开发者在onCreate方法中创建完合成对象之后马上就调用startSpeaking进行合成，
                // 正确的做法是将onCreate中的startSpeaking调用移至这里
            }
        }
    };

    /**
     * 合成回调监听。
     */
    private static SynthesizerListener mTtsListener = new SynthesizerListener() {
        @Override
        public void onSpeakBegin() {
            MyGoogleActivity.sendMessage("SpeakStart-other");
        }

        @Override
        public void onSpeakPaused() {
            //showTip("暂停播放");
        }

        @Override
        public void onSpeakResumed() {
            //showTip("继续播放");
        }

        @Override
        public void onBufferProgress(int percent, int beginPos, int endPos,
                                     String info) {
            // 合成进度
            mPercentForBuffering = percent;
            MyGoogleActivity.showTip("合成进度：" + percent);
        }

        @Override
        public void onSpeakProgress(int percent, int beginPos, int endPos) {
            // 播放进度
            mPercentForPlaying = percent;
            MyGoogleActivity.showTip("播放进度：" + percent);
        }

        @Override
        public void onCompleted(SpeechError error) {
            MyGoogleActivity.AfterSpeakComplete(error == null, error.getPlainDescription(true));
        }

        @Override
        public void onEvent(int eventType, int arg1, int arg2, Bundle obj) {

        }
    };
}