package com.example.liuqi.robotcontrol;

import com.unity3d.player.*;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.ArrayList;
import java.util.UUID;
import java.util.HashMap;
import java.util.LinkedHashMap;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.os.Handler;
import android.os.Message;
import android.os.Environment;
import android.os.Bundle;
import android.widget.Toast;
import android.widget.TextView;
import android.text.TextUtils;
import android.util.AndroidRuntimeException;
import android.util.Log;
import android.content.BroadcastReceiver;
import android.content.SharedPreferences;
import android.content.Intent;
import android.content.Context;
import android.content.IntentFilter;
import android.content.DialogInterface;
import android.graphics.PixelFormat;
import android.view.Window;

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


public class MyGoogleActivity extends com.google.unity.GoogleUnityActivity
{
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
    private String voicer = "mengmeng";
    private String voiceSpeed = "100";
    private String voicePitch = "60";
    // 缓冲进度
    private int mPercentForBuffering = 0;
    // 播放进度
    private int mPercentForPlaying = 0;
    //唤醒变量
    private EventManager mWpEventManager;
    private boolean wakeUpFlag = false;
	private boolean unityInitFlag = false;

    //蓝牙相关
    public static final String PROTOCOL_SCHEME_RFCOMM = "btspp";
    private BluetoothAdapter blueadapter=null;
    private DeviceReceiver mydevice=new DeviceReceiver();
    private boolean hasregister=false;
    private String lockName = "WINGLORD";
    private Boolean findTargetBT = false;
    private BluetoothServerSocket mserverSocket = null;
    private ServerThread startServerThread = null;
    private clientThread clientConnectThread = null;
    private BluetoothSocket socket = null;
    private BluetoothDevice device = null;
    private readThread mreadThread = null;;
	

	// Setup activity layout
	@Override protected void onCreate (Bundle savedInstanceState)
	{
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		super.onCreate(savedInstanceState);

		getWindow().setFormat(PixelFormat.RGBX_8888); // <--- This makes xperia play happy
		
	}

    public void changeSpeechParam(String sdkType, String newVoicer, String newVoiceSpeed, String newVoicePitch)
    {
        voicer = newVoicer;
        voiceSpeed = newVoiceSpeed;
        voicePitch = newVoicePitch;
    }

    private void setBluetooth(){
        blueadapter=BluetoothAdapter.getDefaultAdapter();

        if(blueadapter!=null){  //Device support Bluetooth
            //确认开启蓝牙
            if(!blueadapter.isEnabled()){
                //请求用户开启
                Intent intent=new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(intent, RESULT_FIRST_USER);
                //使蓝牙设备可见，方便配对
                Intent in=new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
                in.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 200);
                startActivity(in);
                //直接开启，不经过提示
                blueadapter.enable();
            }
        }
        else{   //Device does not support Bluetooth

            AlertDialog.Builder dialog = new AlertDialog.Builder(this);
            dialog.setTitle("No bluetooth devices");
            dialog.setMessage("Your equipment does not support bluetooth, please change device");

            dialog.setNegativeButton("cancel",
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {

                        }
                    });
            dialog.show();
        }
    }

    private void startSearchForBlueTooth() {
        if (blueadapter != null && !blueadapter.isDiscovering()) {
            blueadapter.startDiscovery();
        }
    }

    private void bluetoothResume()
    {
        BluetoothMsg.serviceOrCilent=BluetoothMsg.ServerOrCilent.CILENT;

        if(BluetoothMsg.isOpen)
        {
            showLog( "连接已经打开，可以通信。如果要再建立连接，请先断开！");
            return;
        }
        if(BluetoothMsg.serviceOrCilent==BluetoothMsg.ServerOrCilent.CILENT)
        {
            String address = BluetoothMsg.BlueToothAddress;
            if(!address.equals("null"))
            {
                device = blueadapter.getRemoteDevice(address);
                clientConnectThread = new clientThread();
                clientConnectThread.start();
                BluetoothMsg.isOpen = true;
            }
            else
            {
                showLog("address is null !");
            }
        }
        else if(BluetoothMsg.serviceOrCilent==BluetoothMsg.ServerOrCilent.SERVICE)
        {
            startServerThread = new ServerThread();
            startServerThread.start();
            BluetoothMsg.isOpen = true;
        }
    }

    private void bluetoothDestroy()
    {
        if (BluetoothMsg.serviceOrCilent == BluetoothMsg.ServerOrCilent.CILENT)
        {
            shutdownClient();
        }
        else if (BluetoothMsg.serviceOrCilent == BluetoothMsg.ServerOrCilent.SERVICE)
        {
            shutdownServer();
        }
        BluetoothMsg.isOpen = false;
        BluetoothMsg.serviceOrCilent = BluetoothMsg.ServerOrCilent.NONE;
    }

    private void sendMessageByBlueTooth(String msgText)
    {
        if (msgText.length()>0)
        {
            sendMessageHandle(msgText);
        }
        else
        {
            showLog("发送内容不能为空!");
        }
    }
	
	public void startInitRobot()
    {
        //初始化蓝牙功能
        setBluetooth();

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
            sendMessage("ListenStart-other");
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
            //showTip("开始说话");
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
            //showTip("结束说话");
        }

        @Override
        public void onResult(RecognizerResult results, boolean isLast) {
            if (!isLast) {
                //解析语音
                String word = parseVoice(results.getResultString());
                if (word.equals("休息吧")){
					sendMessage("ListenStop-sleep");
                    wakeUpFlag = false;
                    initWakeUp();
                }
                else{
                    showTip(word);
                    sendMessage("ListenSuccess-" + word);
                }
            }
        }
        @Override
        public void onVolumeChanged(int volume, byte[] data) {
            //showTip("当前正在说话，音量大小：" + volume);
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
            sendMessage("SpeakStart-other");
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
            showTip("合成进度：" + percent);
        }

        @Override
        public void onSpeakProgress(int percent, int beginPos, int endPos) {
            // 播放进度
            mPercentForPlaying = percent;
            showTip("播放进度：" + percent);
        }

        @Override
        public void onCompleted(SpeechError error) {
            if (error == null) {
                sendMessage("SpeakSuccess-other");
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

    private void showLogBuff(final String str) {
        UnityPlayer.UnitySendMessage("LogTextShow","updateLogBuff",str);
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
                sendMessage("UnderStandSuccess-" + word);
            }
            else
            {
                sendMessage("UnderStandFailed-none");
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
        setParamForSpeak();
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
                        //注册蓝牙接收广播
                        if(!hasregister){
                            hasregister=true;
                            startSearchForBlueTooth();
                            showLog("start search bt");
                            IntentFilter filterStart=new IntentFilter(BluetoothDevice.ACTION_FOUND);
                            IntentFilter filterEnd=new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
                            registerReceiver(mydevice, filterStart);
                            registerReceiver(mydevice, filterEnd);
                        }
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
        if(blueadapter!=null&&blueadapter.isDiscovering()){
            blueadapter.cancelDiscovery();
        }
        if(hasregister){
            hasregister=false;
            unregisterReceiver(mydevice);
        }
        super.onDestroy();
        bluetoothDestroy();
    }

    private class DeviceReceiver extends BroadcastReceiver{
        @Override
        public void onReceive(Context context, Intent intent) {
            String action =intent.getAction();
            if(BluetoothDevice.ACTION_FOUND.equals(action)){    //搜索到新设备
                BluetoothDevice btd=intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                //搜索没有配过对的蓝牙设备
                if (btd.getName().equals(lockName)) {
                    final String msg = btd.getName()+'\n'+ btd.getAddress();
                    findTargetBT = true;

                    BluetoothMsg.BlueToothAddress=msg.substring(msg.length()-17);

                    if(BluetoothMsg.lastblueToothAddress!=BluetoothMsg.BlueToothAddress) {
                        BluetoothMsg.lastblueToothAddress = BluetoothMsg.BlueToothAddress;
                    }
                    //蓝牙相关
                    bluetoothResume();
                }
            }
            else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)){
                if (!findTargetBT)
                {
                    sendMessage("SearchBlueToothFailed-bluetooth");
                }
            }
        }
    }

    private Handler LinkDetectedHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            showLogBuff((String)msg.obj);
        }
    };

    //开启客户端
    private class clientThread extends Thread {
        @Override
        public void run() {
            try {
                //创建一个Socket连接：只需要服务器在注册时的UUID号
                // socket = device.createRfcommSocketToServiceRecord(BluetoothProtocols.OBEX_OBJECT_PUSH_PROTOCOL_UUID);
                socket = device.createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"));
                //连接
                Message msg2 = new Message();
                msg2.obj = "请稍候，正在连接服务器:"+BluetoothMsg.BlueToothAddress + "!!";
                msg2.what = 0;
                LinkDetectedHandler.sendMessage(msg2);

                socket.connect();

                Message msg = new Message();
                msg.obj = "已经连接上服务端！可以发送信息" + "!!";
                msg.what = 0;
                LinkDetectedHandler.sendMessage(msg);
                sendMessage("LinkBlueToothSuccess-bluetooth");
                //启动接受数据
                mreadThread = new readThread();
                mreadThread.start();
            }
            catch (IOException e)
            {
                Log.e("connect", "", e);
                Message msg = new Message();
                msg.obj = "连接服务端异常！断开连接重新试一试" + "!!";
                msg.what = 0;
                LinkDetectedHandler.sendMessage(msg);
            }
        }
    };

    //开启服务器
    private class ServerThread extends Thread {
        @Override
        public void run() {

            try {
                    /* 创建一个蓝牙服务器
                     * 参数分别：服务器名称、UUID   */
                mserverSocket = blueadapter.listenUsingRfcommWithServiceRecord(PROTOCOL_SCHEME_RFCOMM,
                        UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"));

                Log.d("server", "wait cilent connect...");

                Message msg = new Message();
                msg.obj = "请稍候，正在等待客户端的连接..." + "!!";
                msg.what = 0;
                LinkDetectedHandler.sendMessage(msg);

                    /* 接受客户端的连接请求 */
                socket = mserverSocket.accept();
                Log.d("server", "accept success !");

                Message msg2 = new Message();
                String info = "客户端已经连接上！可以发送信息" + "!!";
                msg2.obj = info;
                msg.what = 0;
                LinkDetectedHandler.sendMessage(msg2);
                //启动接受数据
                mreadThread = new readThread();
                mreadThread.start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    };
    /* 停止服务器 */
    private void shutdownServer() {
        new Thread() {
            @Override
            public void run() {
                if(startServerThread != null)
                {
                    startServerThread.interrupt();
                    startServerThread = null;
                }
                if(mreadThread != null)
                {
                    mreadThread.interrupt();
                    mreadThread = null;
                }
                try {
                    if(socket != null)
                    {
                        socket.close();
                        socket = null;
                    }
                    if (mserverSocket != null)
                    {
                        mserverSocket.close();/* 关闭服务器 */
                        mserverSocket = null;
                    }
                } catch (IOException e) {
                    Log.e("server", "mserverSocket.close()", e);
                }
            };
        }.start();
    }
    /* 停止客户端连接 */
    private void shutdownClient() {
        new Thread() {
            @Override
            public void run() {
                if(clientConnectThread!=null)
                {
                    clientConnectThread.interrupt();
                    clientConnectThread= null;
                }
                if(mreadThread != null)
                {
                    mreadThread.interrupt();
                    mreadThread = null;
                }
                if (socket != null) {
                    try {
                        socket.close();
                    } catch (IOException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                    socket = null;
                }
            };
        }.start();
    }

    //发送数据
    private void sendMessageHandle(String msg)
    {
        if (socket == null)
        {
            showLog("没有连接");
            return;
        }
        try {
            OutputStream os = socket.getOutputStream();
            os.write(msg.getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    //读取数据
    private class readThread extends Thread {
        @Override
        public void run() {

            byte[] buffer = new byte[1024];
            int bytes;
            InputStream mmInStream = null;

            try {
                mmInStream = socket.getInputStream();
            } catch (IOException e1) {
                // TODO Auto-generated catch block
                e1.printStackTrace();
            }
            while (true) {
                try {
                    // Read from the InputStream
                    if( (bytes = mmInStream.read(buffer)) > 0 )
                    {
                        byte[] buf_data = new byte[bytes];
                        for(int i=0; i<bytes; i++)
                        {
                            buf_data[i] = buffer[i];
                        }
                        String s = new String(buf_data);
                        Message msg = new Message();
                        msg.obj = s;
                        msg.what = 1;
                        LinkDetectedHandler.sendMessage(msg);
                    }
                } catch (IOException e) {
                    try {
                        mmInStream.close();
                    } catch (IOException e1) {
                        // TODO Auto-generated catch block
                        e1.printStackTrace();
                    }
                    break;
                }
            }
        }
    }
	// This ensures the layout will be correct.
}
