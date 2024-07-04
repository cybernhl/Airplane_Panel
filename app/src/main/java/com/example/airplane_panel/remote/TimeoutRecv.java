package com.example.airplane_panel.remote;

import java.util.Timer;
import java.util.TimerTask;

public class TimeoutRecv {
    public interface TimeoutRecvCallback {
        void onReceived(String data);
    }
    private Timer timer;
    private final long TIMEOUT = 100; // 100ms超时
    private String recvCommandStr = "";
    private TimeoutRecvCallback timeoutRecv_CB = null;

    public TimeoutRecv(TimeoutRecvCallback cb) {
        timer = new Timer();
        this.timeoutRecv_CB = cb;
    }

    private void resetTimer() {
        if (timer != null) {
            timer.cancel();
            timer = new Timer();
        }
    }

    private void startTimeoutTask() {
        resetTimer(); // 重置计时器，取消之前的任务

        // 创建一个新的计时任务
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                // 超时逻辑处理
                handleReceivedData("TIMEOUT_FLAG".getBytes()); // 使用特殊标记表示超时
            }
        }, TIMEOUT);
    }

    public void handleReceivedData(byte[] data) {
        String dataString = new String(data);
        boolean endFlag = false;

        if ("TIMEOUT_FLAG".equals(dataString)) {
            // 超时处理
            endFlag = true;
        } else {
            // 正常数据处理逻辑
            int endIndex = dataString.indexOf("\r\n");
            if (endIndex != -1) {
                dataString = dataString.substring(0, endIndex);
                endFlag = true;
            }

            endIndex = dataString.indexOf("\n");
            if (endIndex != -1) {
                dataString = dataString.substring(0, endIndex);
                endFlag = true;
            }

            recvCommandStr += dataString;
        }

        if(endFlag) {
            resetTimer(); // 重置计时器，取消之前的任务

            recvCommandStr = recvCommandStr.replace(" ", "");
            // 这里添加你的处理逻辑，例如显示Toast
            recvCommandStr = ""; // 清空接收缓冲区

            if (timeoutRecv_CB != null) {
                timeoutRecv_CB.onReceived(recvCommandStr);
            }
        }
        else
        {
            startTimeoutTask(); // 重新开始超时计时
        }
    }
}
