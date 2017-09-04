package com.example.liuqi.robotcontrol;

import com.unity3d.player.*;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;

import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.AndroidRuntimeException;
import android.graphics.PixelFormat;
import android.view.Window;

import com.iflytek.cloud.SpeechUtility;
import com.iflytek.cloud.SpeechConstant;
import com.iflytek.cloud.SpeechError;
import com.iflytek.cloud.TextUnderstander;
import com.iflytek.cloud.TextUnderstanderListener;
import com.iflytek.cloud.UnderstanderResult;
import com.iflytek.cloud.ErrorCode;
import com.iflytek.speech.util.JsonParser;

import com.baidu.speech.EventListener;
import com.baidu.speech.EventManager;
import com.baidu.speech.EventManagerFactory;

public class MyGoogleActivity extends com.google.unity.GoogleUnityActivity
{
    // 语音合成对象
    private TextUnderstander mTextUnderstander;
    private final String APP_ID = "593fca5d";

    //唤醒变量
    private EventManager mWpEventManager;
    public static boolean wakeUpFlag = false;
	private boolean unityInitFlag = false;

    //合成选择
    private String currentSdk = "xunfei";

	// Setup activity layout
	@Override protected void onCreate (Bundle savedInstanceState)
	{
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		super.onCreate(savedInstanceState);

		getWindow().setFormat(PixelFormat.RGBX_8888); // <--- This makes xperia play happy
		
	}

	// unity通信部分开始

	public void startInitRobot()
    {
        //初始化蓝牙功能
        blueToothControl.setBluetooth(this);

        //初始化引擎
        SpeechUtility.createUtility(this, SpeechConstant.APPID +"="+APP_ID);

        //初始化听写
        iflytekSpeechListen.initForSpeechListen(this);
        //初始化语义理解
        mTextUnderstander = TextUnderstander.createTextUnderstander(this, null);

        // 初始化合成对象
        iflytekSpeakSynth.initForSpeakSynth(this);
        baiduSpeakSynth.initialTts(this);
		
		//初始化唤醒
		initWakeUp();
		unityInitFlag = true;
    }

    public static void showTip(final String str) {
        UnityPlayer.UnitySendMessage("ToastShow","updateLog",str);
    }

    public static void showLog(final String str) {
        UnityPlayer.UnitySendMessage("LogTextShow","updateLog",str);
    }

    public static void showLogBuff(final String str) {
        UnityPlayer.UnitySendMessage("LogTextShow","updateLogBuff",str);
    }

    public static void sendMessage(final String str) {
        UnityPlayer.UnitySendMessage("LogTextShow","updateMessage",str);
    }

    private void startSearchForBlueTooth()
    {
        blueToothControl.SearchForBlueTooth();
    }

    private void sendMessageByBlueTooth(String msgText)
    {
        blueToothControl.sendMessageByBlueTooth(msgText);
    }

    public void changeSpeechParam(String sdkType, String newVoicer, String newVoiceSpeed, String newVoicePitch)
    {
        if (sdkType.equals("xunfei"))
        {
            iflytekSpeakSynth.changeSpeechParam(newVoicer, newVoiceSpeed, newVoicePitch);
        }
        else
        {
            baiduSpeakSynth.changeSpeechParam(newVoicer, newVoiceSpeed, newVoicePitch);
        }
        currentSdk = sdkType;
    }

    public void changeSdkType(String sdkType)
    {
        currentSdk = sdkType;
        showLog("sdk:" + currentSdk + "#");
    }

    private void understanderText(String text){
        mTextUnderstander.understandText(text, mTextUnderstanderListener);
    }

    private void stopListenForSleep(String sleepText)
    {
        wakeUpFlag = false;
        initWakeUp();
        speakingText(sleepText);
    }

    private void speakingText(String showText)
    {
        if (currentSdk.equals("xunfei")) {
            iflytekSpeakSynth.TrySpeaking(showText);
            showLog("iflytekSpeakSynth sdk:" + currentSdk + "#");
        }
        else
        {
            baiduSpeakSynth.TrySpeaking(showText);
            showLog("baiduSpeakSynth sdk:" + currentSdk + "#");
        }
    }

    // unity通信部分结束

    public static void AfterSpeakComplete(Boolean noerror, String errorLog)
    {
        if (noerror) {
            sendMessage("SpeakSuccess-other");
        }
        else {
            showTip(errorLog);
        }
        if (wakeUpFlag)
        {
            iflytekSpeechListen.startSpeechListen();
        }
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
                sendMessage("UnderStandSuccess-" + word);
            }
            else
            {
                sendMessage("UnderStandFailed-none");
            }
        }};

	private void initWakeUp()
	{
		// 唤醒功能打开步骤
        // 1) 创建唤醒事件管理器
        mWpEventManager = EventManagerFactory.create(MyGoogleActivity.this, "wp");

        // 2) 注册唤醒事件监听器
        mWpEventManager.registerListener(new EventListener() {
            @Override
            public void onEvent(String name, String params, byte[] data, int offset, int length) {
                try {
                    showTip(String.format("event: name=%s, params=%s", name, params));
                    JSONObject json = new JSONObject(params);
                    if ("wp.data".equals(name)) { // 每次唤醒成功, 将会回调name=wp.data的时间, 被激活的唤醒词在params的word字段
                        String word = json.getString("word");
                        sendMessage("WakeUpSuccess-wakeup");
                        wakeUpFlag = true;
                        mWpEventManager.send("wp.stop", null, null, 0, 0);
                        //通过多线程处理蓝牙注册消息
                        mHandler.post(mRunnable);
                    } else if ("wp.exit".equals(name)) {
                        sendMessage("WakeUpStoped-other");
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
        showTip("等待唤醒");
        sendMessage("WaitWakeUp-other");
        wakeUpFlag = false;
	}

    @Override
    protected void onResume() {
        super.onResume();
		
		if (unityInitFlag)
		{
			initWakeUp();
		}
        sendMessage("MachineResume-other");
    }

    @Override
    protected void onPause() {
        super.onPause();
		
        if (unityInitFlag)
        {
            if (wakeUpFlag)
            {
                wakeUpFlag = false;
            }
            else
            {
                // 停止唤醒监听
                mWpEventManager.send("wp.stop", null, null, 0, 0);
            }
        }
    }

    @Override
    protected void onDestroy() {
        blueToothControl.bluetoothDestroy();
        mHandler.removeCallbacks(mRunnable);
        super.onDestroy();
    }

    //多线程处理蓝牙注册
    private Handler mHandler = new Handler();
    private Runnable mRunnable = new Runnable() {
        public void run() {
            //注册蓝牙接收广播
            blueToothControl.registerBlueTooth();
        }
    };

}
