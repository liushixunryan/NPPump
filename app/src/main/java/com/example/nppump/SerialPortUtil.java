package com.example.nppump;

import android.util.Log;

import com.blankj.utilcode.util.ConvertUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.TimeUnit;

import android_serialport_api.SerialPort;

/**
 * 通过串口用于接收或发送数据
 */
public class SerialPortUtil {

    private static final String TAG = "SERIALPORTUTIL";

    private static final String pathname = "/dev/ttyS0";
    private static final int baudRate = 9600;
    private static final int flags = 0;
    private SerialPort serialPort = null;
    private InputStream inputStream = null;
    private OutputStream outputStream = null;

    private boolean isStart = false;
    private Thread writeThread = null;
    private Thread receiveThread = null;

    private int index = 0;//当前数据记录到哪一个位置
    private int dataLength = -1;//接收到数据的总长度
    private int readNum = -1;//读取到的一个字节
    private byte[] bytes = null;//当前读取到数据
    private String[] checkHeads = {"AA", "BB", "CC", "00"};//数据头 前3位是固定的校验 00是返回数据长度会在的位置，这里为了下面循环不写重复代码，就随意写了一个00
    private static final int TIME_OUT = 150;//读取超时时间
    private static final int SLEEP_TIME = 30;//线程休眠时间
    private int timeOutCount = 0;//连续读取超时次数
    private static final int MAX_TIME_OUT_COUNT = 10;//连续读取超时超过10次(也就是最少1500秒没收到数据)判定下位机断开连接

    /**
     * 打开串口，接收数据
     * 通过串口，接收单片机发送来的数据
     */
    public void openSerialPort() throws IOException {
        try {
            serialPort = new SerialPort(new File(pathname), baudRate, flags);
            //调用对象SerialPort方法，获取串口中"读和写"的数据流
            inputStream = serialPort.getInputStream();
            outputStream = serialPort.getOutputStream();
            isStart = true;
        } catch (IOException e) {
            throw new IOException();
        } catch (SecurityException e) {
            throw new SecurityException();
        } catch (Exception e) {
            e.printStackTrace();
        }

        //这里读线程一定要在写线程之前wait，因为线程代码运行时间的不确定性
        startReadThread();
        startWriteThread();
    }

    /**
     * 关闭串口
     * 关闭串口中的输入输出流
     * isStart = false 接收线程会自动关闭
     */
    public void closeSerialPort() {
        try {
            isStart = false;
            if (inputStream != null) {
                inputStream.close();
            }
            if (outputStream != null) {
                outputStream.close();
            }
            if (serialPort != null) {
                serialPort.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    /**
     * 开启发送数据线程
     */
    public void startWriteThread() {
        if (writeThread == null) {
            writeThread = new Thread(new WriteThread());
            writeThread.start();
        }
    }

    /**
     * 数据写入线程
     */
    public class WriteThread implements Runnable {
        @Override
        public void run() {
            while (isStart) {
                if (inputStream == null) return;
                //对比此时状态和应该的状态
                sendMsgToSerialPort("这是发送数据线程, 是个同步方法");
            }
        }
    }


    /**
     * 发送数据  String转byte数组
     * 通过串口，发送数据到单片机
     *
     * @param data 要发送的数据
     */
    private synchronized void sendMsgToSerialPort(String data) {
        try {
            outputStream.write(ConvertUtils.hexString2Bytes(data));
            outputStream.flush();
            notify();
            wait();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //------------------------------读取

    /**
     * 开启读取串口数据线程
     */
    private void startReadThread() {
        if (receiveThread == null) {
            receiveThread = new Thread(new ReceiveThread());
            receiveThread.start();
        }
    }

    /**
     * 接收串口数据的线程
     */
    private class ReceiveThread implements Runnable {
        @Override
        public void run() {
            readInputStreamData();
        }
    }

    /**
     * 检查读取的数据头
     * 我们业务里面数据头前3位是固定的校验 这里根据各自的业务对checkHeads进行更改，而且一般会在数据
     * 前面第几位置返回数据的总长度，所以可以根据位置，读取到数据的总长度，我们业务里面是数据头后一个，
     * 也就是第四个，所以在对checkHeads检查完之后直接读取数据长度，这样在读到数据长度就知道一次输出流
     * 都读完了
     * （如果你们数据长度是固定的，可以不用判断数据长度这一位，
     * bytes = new byte[dataLength]也可以放在另外的位置初始化）
     *
     * @param checkHeads
     */
    public boolean checkReadData(String[] checkHeads) throws IOException, InterruptedException {
        for (int i = 0; i < checkHeads.length; i++) {
            index = i;
            if (checkInputStreaData()) {
                //读取缓存中的第一个字节 看是否与数据头对应
                readNum = inputStream.read();
                bytes[index] = (byte) readNum;
                //判断数据头
                if (i < checkHeads.length - 1) {
                    return false;

                } else {
                    return true;
                }
            } else {
                return false;
            }
        }
        return false;
    }

    /**
     * 读取数据并校验数据，如果读取失败，则停止读线程，开启写线程
     */
    private synchronized void readInputStreamData() {
        try {
            wait();//第一次初始化读进程wait

            //条件判断，只要条件为true，则一直执行这个线程
            while (isStart) {
                if (inputStream == null) return;
                if (checkReadData(checkHeads)) {
                    while (true) {
                        if (checkInputStreaData()) {
                            if ((readNum = inputStream.read()) != -1) {
                                index++;
                                bytes[index] = (byte) readNum;
                                if (dataLength == index + 1) {
                                    Log.e(TAG, "数据完整");
                                    break;
                                }
                            }
                        } else {
                            break;
                        }
                    }
                }

                //数据读取完成 或者读取超时失败
                if (bytes != null && bytes.length > 0 && dataLength == index + 1) {
                    //状态
                    timeOutCount = 0;
                } else {
                    timeOutCount++;
                    //如果连续读取TIME_OUT MAX_TIME_OUT_COUNT次，判断下位机断开连接
                    if (timeOutCount > MAX_TIME_OUT_COUNT) {
//                        EventBus.getDefault().post("下位机断开连接");
                        return;
                    }
                    Log.e(TAG, "读取数据数据失败");
                }
                notify();
                wait();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, "读取数据发生异常");
        }
    }


    /**
     * 检查是否存在数据
     * 如果不存在数据就要做延时读取操作
     * 这里的延时时间为 {SLEEP_TIME}
     *
     * @return 存在数据true  不存在数据超时false
     * @throws IOException
     * @throws InterruptedException
     */
    private boolean checkInputStreaData() throws IOException, InterruptedException {
        int waitTime = 0;
        while (true) {
            //查询可以读取到的字节数量
            //读取超时 TIME_OUT秒内无响应 则退出
            if (inputStream.available() <= 0) {
                //当前未检查到数据
                waitTime += SLEEP_TIME;
                TimeUnit.MILLISECONDS.sleep(SLEEP_TIME);
                if (waitTime >= TIME_OUT) {
                    return false;
                }
            } else {
                return true;
            }
        }
    }

}