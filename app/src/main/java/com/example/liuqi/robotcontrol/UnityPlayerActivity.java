package com.example.liuqi.robotcontrol;

import com.unity3d.player.*;
import android.app.Activity;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Window;

import java.util.HashMap;
import java.util.LinkedHashMap;

import org.json.JSONException;
import org.json.JSONObject;

import android.widget.Toast;
import android.os.Environment;
import android.text.TextUtils;
import android.util.AndroidRuntimeException;
import android.content.SharedPreferences;

import com.iflytek.cloud.SpeechUtility;
import com.iflytek.cloud.RecognizerResult;
import com.iflytek.cloud.SpeechError;
import com.iflytek.cloud.SpeechConstant;
import com.iflytek.cloud.SpeechSynthesizer;
import com.iflytek.cloud.SpeechRecognizer;
import com.iflytek.cloud.RecognizerListener;
import com.iflytek.cloud.TextUnderstander;
import com.iflytek.cloud.TextUnderstanderListener;
import com.iflytek.cloud.UnderstanderResult;
import com.iflytek.cloud.InitListener;
import com.iflytek.cloud.ErrorCode;
import com.iflytek.cloud.SynthesizerListener;
import com.iflytek.speech.util.JsonParser;
import com.iflytek.speech.setting.IatSettings;

import com.baidu.speech.EventListener;
import com.baidu.speech.EventManager;
import com.baidu.speech.EventManagerFactory;



public class UnityPlayerActivity extends Activity
{
	protected UnityPlayer mUnityPlayer; // don't change the name of this variable; referenced from native code
	
	//组件
    private SharedPreferences mSharedPreferences;
    private HashMap<String, String> mIatResults = new LinkedHashMap<String, String>();
    // 语音识别
    private final String APP_ID = "593fca5d";
    private SpeechRecognizer mIat;
    private TextUnderstander mTextUnderstander;
    // 语音合成对象
    private SpeechSynthesizer mTts;
    private String mEngineType = SpeechConstant.TYPE_CLOUD;
    // 默认发音人
    private String voicer = "xiaoyan";
    // 缓冲进度
    private int mPercentForBuffering = 0;
    // 播放进度
    private int mPercentForPlaying = 0;
    //唤醒变量
    private EventManager mWpEventManager;
    private boolean wakeUpFlag = false;
	private boolean unityInitFlag = false;
	

	// Setup activity layout
	@Override protected void onCreate (Bundle savedInstanceState)
	{
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		super.onCreate(savedInstanceState);

		getWindow().setFormat(PixelFormat.RGBX_8888); // <--- This makes xperia play happy

		mUnityPlayer = new UnityPlayer(this);
		setContentView(mUnityPlayer);
		mUnityPlayer.requestFocus();
		
	}
	
	public void startInitRobot()
    {
        //初始化引擎
        SpeechUtility.createUtility(this, SpeechConstant.APPID +"="+APP_ID);

        //初始化语音识别
        mIat = SpeechRecognizer.createRecognizer(this, mInitListener);
        mSharedPreferences = getSharedPreferences(IatSettings.PREFER_NAME,
                Activity.MODE_PRIVATE);
        setParamForListen();

        //初始化语义理解
        mTextUnderstander = TextUnderstander.createTextUnderstander(this, null);

        // 初始化合成对象
        mTts = SpeechSynthesizer.createSynthesizer(this, mTtsInitListener);
        setParamForSpeak();
		
		//初始化唤醒
		initWakeUp();
		unityInitFlag = true;
    }

    //开始听写
    public void startSpeechListen()
    {
        mIatResults.clear();
        setParamForListen();
        int ret = mIat.startListening(mRecognizerListener);
        if (ret != ErrorCode.SUCCESS) {
            showTip("听写失败,错误码：" + ret);
        } else {
            showTip(getString(R.string.text_begin));
            sendMessage("ListenStart");
        }
    }

    /**
     * 参数设置
     */
    public void setParamForListen() {
        // 清空参数
        mIat.setParameter(SpeechConstant.PARAMS, null);

        // 设置听写引擎
        mIat.setParameter(SpeechConstant.ENGINE_TYPE, mEngineType);
        // 设置返回结果格式
        mIat.setParameter(SpeechConstant.RESULT_TYPE, "json");

        String lag = mSharedPreferences.getString("iat_language_preference",
                "mandarin");
        if (lag.equals("en_us")) {
            // 设置语言
            mIat.setParameter(SpeechConstant.LANGUAGE, "en_us");
            mIat.setParameter(SpeechConstant.ACCENT, null);
        } else {
            // 设置语言
            mIat.setParameter(SpeechConstant.LANGUAGE, "zh_cn");
            // 设置语言区域
            mIat.setParameter(SpeechConstant.ACCENT, lag);
        }

        // 设置语音前端点:静音超时时间，即用户多长时间不说话则当做超时处理
        mIat.setParameter(SpeechConstant.VAD_BOS, mSharedPreferences.getString("iat_vadbos_preference", "4000"));

        // 设置语音后端点:后端点静音检测时间，即用户停止说话多长时间内即认为不再输入， 自动停止录音
        mIat.setParameter(SpeechConstant.VAD_EOS, mSharedPreferences.getString("iat_vadeos_preference", "1000"));

        // 设置标点符号,设置为"0"返回结果无标点,设置为"1"返回结果有标点
        mIat.setParameter(SpeechConstant.ASR_PTT, mSharedPreferences.getString("iat_punc_preference", "1"));

        // 设置音频保存路径，保存音频格式支持pcm、wav，设置路径为sd卡请注意WRITE_EXTERNAL_STORAGE权限
        // 注：AUDIO_FORMAT参数语记需要更新版本才能生效
        mIat.setParameter(SpeechConstant.AUDIO_FORMAT,"wav");
        mIat.setParameter(SpeechConstant.ASR_AUDIO_PATH, Environment.getExternalStorageDirectory()+"/msc/iat.wav");
    }

    /**
     * 初始化听写监听器
     */
    private InitListener mInitListener = new InitListener() {

        @Override
        public void onInit(int code) {
            if (code != ErrorCode.SUCCESS) {
                showTip("初始化失败，错误码：" + code);
            }
        }
    };

    private RecognizerListener mRecognizerListener = new RecognizerListener() {

        @Override
        public void onBeginOfSpeech() {
            // 此回调表示：sdk内部录音机已经准备好了，用户可以开始语音输入
            showTip("开始说话");
        }

        @Override
        public void onError(SpeechError error) {
            // Tips：
            // 错误码：10118(您没有说话)，可能是录音机权限被禁，需要提示用户打开应用的录音权限。
            // 如果使用本地功能（语记）需要提示用户开启语记的录音权限。
            showTip("Listen error" + error.getPlainDescription(true));
            startSpeechListen();
        }

        @Override
        public void onEndOfSpeech() {
            // 此回调表示：检测到了语音的尾端点，已经进入识别过程，不再接受语音输入
            showTip("结束说话");
        }

        @Override
        public void onResult(RecognizerResult results, boolean isLast) {
            if (!isLast) {
                //解析语音
                String word = parseVoice(results.getResultString());
                if (word.equals("休息吧")){
                    speakingText("有需要随时呼叫小度");
                    wakeUpFlag = false;
                }
                else{
                    showTip(word);
                    understanderText(word);
                }
            }
        }
        @Override
        public void onVolumeChanged(int volume, byte[] data) {
            showTip("当前正在说话，音量大小：" + volume);
        }

        @Override
        public void onEvent(int eventType, int arg1, int arg2, Bundle obj) {
            // 以下代码用于获取与云端的会话id，当业务出错时将会话id提供给技术支持人员，可用于查询会话日志，定位出错原因
            // 若使用本地能力，会话id为null
            //	if (SpeechEvent.EVENT_SESSION_ID == eventType) {
            //		String sid = obj.getString(SpeechEvent.KEY_EVENT_SESSION_ID);
            //		Log.d(TAG, "session id =" + sid);
            //	}
        }
    };

    /**
     * 初始化合成监听。
     */
    private InitListener mTtsInitListener = new InitListener() {
        @Override
        public void onInit(int code) {
            if (code != ErrorCode.SUCCESS) {
                showTip("初始化失败,错误码："+code);
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
    private SynthesizerListener mTtsListener = new SynthesizerListener() {
        @Override
        public void onSpeakBegin() {
            //showTip("开始播放");
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
            //showTip("000");
        }

        @Override
        public void onSpeakProgress(int percent, int beginPos, int endPos) {
            // 播放进度
            mPercentForPlaying = percent;
            //showTip("000");
        }

        @Override
        public void onCompleted(SpeechError error) {
            if (error == null) {
                //showTip("播放完成");
            }
            else {
                showTip(error.getPlainDescription(true));
            }
            if (wakeUpFlag)
            {
                startSpeechListen();
            }
        }

        @Override
        public void onEvent(int eventType, int arg1, int arg2, Bundle obj) {

        }
    };

    private void showTip(final String str) {
        UnityPlayer.UnitySendMessage("ToastShow","updateLog",str);
    }

    private void showLog(final String str) {
        UnityPlayer.UnitySendMessage("LogTextShow","updateLog",str);
    }

    private void sendMessage(final String str) {
        UnityPlayer.UnitySendMessage("LogTextShow","updateMessage",str);
    }

    /**
     * 参数设置
     */
    private void setParamForSpeak(){
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
        mTts.setParameter(SpeechConstant.SPEED, "80");
        //设置合成音调
        mTts.setParameter(SpeechConstant.PITCH, "65");
        //设置合成音量
        mTts.setParameter(SpeechConstant.VOLUME,"50");
        //设置播放器音频流类型
        mTts.setParameter(SpeechConstant.STREAM_TYPE,"3");

        // 设置播放合成音频打断音乐播放，默认为true
        mTts.setParameter(SpeechConstant.KEY_REQUEST_FOCUS, "true");

        // 设置合成音频保存路径，设置路径为sd卡请注意WRITE_EXTERNAL_STORAGE权限
        mTts.setParameter(SpeechConstant.PARAMS,"tts_audio_path="+Environment.getExternalStorageDirectory()+"/test.pcm");
    }

    private TextUnderstanderListener mTextUnderstanderListener = new TextUnderstanderListener(){
        @Override
        public void onError(SpeechError error) {
            // TODO Auto-generated method stub
            showTip("onError Code："	+ error.getErrorCode());
        }
        @Override
        public void onResult(UnderstanderResult result) {
            String word = JsonParser.parseUnderstandResult(
                    result.getResultString().toString());
            if (!TextUtils.isEmpty(word)) {
                sendMessage("ListenSuccess");
                speakingText(word);
            }
            else
            {
                sendMessage("ListenFailed");
                speakingText("小度听不懂，请换个说法");
            }
        }};

    private void understanderText(String text){
        mTextUnderstander.understandText(text, mTextUnderstanderListener);
    }

    /**
     * 解析语音json
     */
    public String parseVoice(String resultString) {
        String text = JsonParser.parseIatResult(resultString);

        String sn = null;
        // 读取json结果中的sn字段
        try {
            JSONObject resultJson = new JSONObject(resultString);
            sn = resultJson.optString("sn");
        } catch (JSONException e) {
            e.printStackTrace();
        }

        mIatResults.put(sn, text);

        StringBuffer resultBuffer = new StringBuffer();
        for (String key : mIatResults.keySet()) {
            resultBuffer.append(mIatResults.get(key));
        }
        return resultBuffer.toString();
    }

    private void speakingText(String showText)
    {
        int code = mTts.startSpeaking(showText, mTtsListener);
//		/**
//		 * 只保存音频不进行播放接口,调用此接口请注释startSpeaking接口
//		 * text:要合成的文本，uri:需要保存的音频全路径，listener:回调接口
//		*/
//		String path = Environment.getExternalStorageDirectory()+"/tts.pcm";
//		int code = mTts.synthesizeToUri(text, path, mTtsListener);

        if (code != ErrorCode.SUCCESS) {
            if(code == ErrorCode.ERROR_COMPONENT_NOT_INSTALLED){
                //未安装则跳转到提示安装页面
                //mInstaller.install();
            }else {
                showTip("语音合成失败,错误码: " + code);
            }
        }
    }
	
	
	private void initWakeUp()
	{
		// 唤醒功能打开步骤
        // 1) 创建唤醒事件管理器
        mWpEventManager = EventManagerFactory.create(UnityPlayerActivity.this, "wp");

        // 2) 注册唤醒事件监听器
        mWpEventManager.registerListener(new EventListener() {
            @Override
            public void onEvent(String name, String params, byte[] data, int offset, int length) {
                try {
                    showTip(String.format("event: name=%s, params=%s", name, params));
                    JSONObject json = new JSONObject(params);
                    if ("wp.data".equals(name)) { // 每次唤醒成功, 将会回调name=wp.data的时间, 被激活的唤醒词在params的word字段
                        String word = json.getString("word");
                        sendMessage("WakeUpSuccess");
                        speakingText("有什么吩咐吗");
                        wakeUpFlag = true;
                    } else if ("wp.exit".equals(name)) {
                        sendMessage("WakeUpStoped");
                        wakeUpFlag = false;
                    }
                } catch (JSONException e) {
                    throw new AndroidRuntimeException(e);
                }
            }
        });

        // 3) 通知唤醒管理器, 启动唤醒功能
        HashMap params = new HashMap();
        params.put("kws-file", "assets:///WakeUp.bin"); // 设置唤醒资源, 唤醒资源请到 http://yuyin.baidu.com/wake#m4 来评估和导出
        mWpEventManager.send("wp.start", new JSONObject(params).toString(), null, 0, 0);
        showTip("开始唤醒");
        sendMessage("WaitWakeUp");
	}

    @Override
    protected void onResume() {
        super.onResume();

		mUnityPlayer.resume();
		
		if (unityInitFlag)
		{
			initWakeUp();
		}
        
    }

    @Override
    protected void onPause() {
        super.onPause();
		
		mUnityPlayer.pause();

        if (unityInitFlag)
        {
            // 停止唤醒监听
            mWpEventManager.send("wp.stop", null, null, 0, 0);
            showTip("结束唤醒");
        }
    }

	// Quit Unity
	@Override protected void onDestroy ()
	{
		mUnityPlayer.quit();
		super.onDestroy();
	}

	// This ensures the layout will be correct.
	@Override public void onConfigurationChanged(Configuration newConfig)
	{
		super.onConfigurationChanged(newConfig);
		mUnityPlayer.configurationChanged(newConfig);
	}

	// Notify Unity of the focus change.
	@Override public void onWindowFocusChanged(boolean hasFocus)
	{
		super.onWindowFocusChanged(hasFocus);
		mUnityPlayer.windowFocusChanged(hasFocus);
	}

	// For some reason the multiple keyevent type is not supported by the ndk.
	// Force event injection by overriding dispatchKeyEvent().
	@Override public boolean dispatchKeyEvent(KeyEvent event)
	{
		if (event.getAction() == KeyEvent.ACTION_MULTIPLE)
			return mUnityPlayer.injectEvent(event);
		return super.dispatchKeyEvent(event);
	}

	// Pass any events not handled by (unfocused) views straight to UnityPlayer
	@Override public boolean onKeyUp(int keyCode, KeyEvent event)     { return mUnityPlayer.injectEvent(event); }
	@Override public boolean onKeyDown(int keyCode, KeyEvent event)   { return mUnityPlayer.injectEvent(event); }
	@Override public boolean onTouchEvent(MotionEvent event)          { return mUnityPlayer.injectEvent(event); }
	/*API12*/ public boolean onGenericMotionEvent(MotionEvent event)  { return mUnityPlayer.injectEvent(event); }
}