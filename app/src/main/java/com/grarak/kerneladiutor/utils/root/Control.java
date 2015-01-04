package com.grarak.kerneladiutor.utils.root;

import android.content.Context;
import android.os.Handler;

import com.grarak.kerneladiutor.utils.Constants;
import com.grarak.kerneladiutor.utils.Utils;
import com.grarak.kerneladiutor.utils.database.SysDB;
import com.grarak.kerneladiutor.utils.kernel.CPU;

import java.util.List;

/**
 * Created by willi on 14.12.14.
 */
public class Control implements Constants {

    public enum CommandType {
        GENERIC, CPU, TCP_CONGESTION
    }

    private static void commandSaver(Context context, String sys, String value) {
        SysDB db = new SysDB(context);
        db.open();

        List<SysDB.Sys> sysList = db.getAllSys();
        for (int i = 0; i < sysList.size(); i++)
            if (sysList.get(i).getSys().equals(sys))
                db.deleteSys(sysList.get(i).getId());

        db.insertSys(sys, value);
        db.close();
    }

    private static void runGeneric(String file, String value, Context context) {
        RootUtils.runCommand("echo " + value + " > " + file);

        commandSaver(context, file, "echo " + value + " > " + file);
    }

    private static void runTcpCongestion(String tcpCongestion, Context context) {
        RootUtils.runCommand("sysctl -w net.ipv4.tcp_congestion_control=" + tcpCongestion);

        commandSaver(context, TCP_AVAILABLE_CONGESTIONS, "sysctl -w net.ipv4.tcp_congestion_control=" + tcpCongestion);
    }

    public static void startModule(String module, boolean save, Context context) {
        RootUtils.runCommand("start " + module);

        if (save) commandSaver(context, module, "start " + module);
    }

    public static void stopModule(String module, boolean save, Context context) {
        RootUtils.runCommand("stop " + module);
        if (module.equals(CPU_MPDEC)) bringCoresOnline();

        if (save) commandSaver(context, module, "stop " + module);
    }

    public static void bringCoresOnline() {
        new Thread() {
            public void run() {
                try {
                    for (int i = 0; i < CPU.getCoreCount(); i++)
                        RootUtils.runCommand("echo 1 > " + String.format(CPU_CORE_ONLINE, i));

                    // Give CPU some time to bring core online
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }.start();
    }

    public static void runCommand(final String value, final String file, final CommandType command,
                                  final Context context) {

        Runnable run = new Runnable() {
            @Override
            public void run() {
                if (command == CommandType.CPU) {
                    if (CPU.getCoreCount() > 1) {
                        boolean stoppedMpdec = false;
                        if (RootUtils.moduleActive(CPU_MPDEC)) {
                            stopModule(CPU_MPDEC, false, context);
                            stoppedMpdec = true;
                        }

                        for (int i = 0; i < CPU.getCoreCount(); i++)
                            while (true)
                                if (Utils.existFile(String.format(file, i))) {
                                    runGeneric(String.format(file, i), value, context);
                                    break;
                                }

                        if (stoppedMpdec) startModule(CPU_MPDEC, false, context);
                    }
                } else if (command == CommandType.GENERIC) {
                    runGeneric(file, value, context);
                } else if (command == CommandType.TCP_CONGESTION) {
                    runTcpCongestion(value, context);
                }
            }
        };

        new Handler().post(run);

    }

}