package cn.apisium.pcraft;

import java.io.BufferedReader;
import java.io.InputStreamReader;

final class Installer {
    private Installer () {}
    static boolean install () {
        try {
            Process p;
            try {
                ProcessBuilder pb = new ProcessBuilder("npm", "install", "--production");
                pb.redirectErrorStream();
                p = pb.start();
            } catch (Exception ignored) {
                ProcessBuilder pb = new ProcessBuilder("npm.cmd", "install", "--production");
                pb.redirectErrorStream();
                p = pb.start();
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            p.waitFor();
            boolean result = p.exitValue() == 0;
            String line;
            while ((line = reader.readLine()) != null) System.out.print(line);
            return result;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}
