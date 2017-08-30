package com.example.liuqi.robotcontrol;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

public class blueToothControl {

    private static Activity myActivity;
    //蓝牙相关
    public static final String PROTOCOL_SCHEME_RFCOMM = "btspp";
    private static BluetoothAdapter blueadapter=null;
    private static DeviceReceiver mydevice=new DeviceReceiver();
    private static boolean hasregister=false;
    private static String lockName = "WINGLORD";
    private static Boolean findTargetBT = false;
    private static BluetoothServerSocket mserverSocket = null;
    private static ServerThread startServerThread = null;
    private static clientThread clientConnectThread = null;
    private static BluetoothSocket socket = null;
    private static BluetoothDevice device = null;
    private static readThread mreadThread = null;

    public static void setBluetooth(Activity mainActivity){
        myActivity = mainActivity;
        blueadapter=BluetoothAdapter.getDefaultAdapter();

        if(blueadapter!=null){  //Device support Bluetooth
            //确认开启蓝牙
            if(!blueadapter.isEnabled()){
                //请求用户开启
                Intent intent=new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                myActivity.startActivityForResult(intent, myActivity.RESULT_FIRST_USER);
                //使蓝牙设备可见，方便配对
                Intent in=new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
                in.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 200);
                myActivity.startActivity(in);
                //直接开启，不经过提示
                blueadapter.enable();
            }
        }
        else{   //Device does not support Bluetooth

            AlertDialog.Builder dialog = new AlertDialog.Builder(myActivity);
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

    public static void registerBlueTooth()
    {
        if(!hasregister){
            hasregister=true;
            SearchForBlueTooth();
            MyGoogleActivity.showLog("start search bt");
            IntentFilter filterStart=new IntentFilter(BluetoothDevice.ACTION_FOUND);
            IntentFilter filterEnd=new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
            myActivity.registerReceiver(mydevice, filterStart);
            myActivity.registerReceiver(mydevice, filterEnd);
        }
    }

    public static void SearchForBlueTooth() {
        if (blueadapter != null && !blueadapter.isDiscovering()) {
            blueadapter.startDiscovery();
        }
    }

    private static void bluetoothResume()
    {
        BluetoothMsg.serviceOrCilent=BluetoothMsg.ServerOrCilent.CILENT;

        if(BluetoothMsg.isOpen)
        {
            MyGoogleActivity.showLog( "连接已经打开，可以通信。如果要再建立连接，请先断开！");
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
                MyGoogleActivity.showLog("address is null !");
            }
        }
        else if(BluetoothMsg.serviceOrCilent==BluetoothMsg.ServerOrCilent.SERVICE)
        {
            startServerThread = new ServerThread();
            startServerThread.start();
            BluetoothMsg.isOpen = true;
        }
    }

    public static void bluetoothDestroy()
    {
        if(blueadapter!=null&&blueadapter.isDiscovering()){
            blueadapter.cancelDiscovery();
        }
        if(hasregister){
            hasregister=false;
            myActivity.unregisterReceiver(mydevice);
        }
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

    public static void sendMessageByBlueTooth(String msgText)
    {
        if (msgText.length()>0)
        {
            sendMessageHandle(msgText);
        }
        else
        {
            MyGoogleActivity.showLog("发送内容不能为空!");
        }
    }

    private static class DeviceReceiver extends BroadcastReceiver {
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
                    MyGoogleActivity.sendMessage("SearchBlueToothFailed-bluetooth");
                }
            }
        }
    }

    private static Handler LinkDetectedHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            MyGoogleActivity.showLogBuff((String)msg.obj);
        }
    };

    //开启客户端
    private static class clientThread extends Thread {
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
                MyGoogleActivity.sendMessage("LinkBlueToothSuccess-bluetooth");
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
    private static class ServerThread extends Thread {
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
    private static void shutdownServer() {
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
    private static void shutdownClient() {
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
    private static void sendMessageHandle(String msg)
    {
        if (socket == null)
        {
            MyGoogleActivity.showLog("没有连接");
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
    private static class readThread extends Thread {
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