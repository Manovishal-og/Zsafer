package com.zsafer.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import picocli.CommandLine;
import picocli.CommandLine.*;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.Callable;

@Command(name = "zsafer", mixinStandardHelpOptions = true,
        subcommands = {ZsaferCLI.Get.class})
public class ZsaferCLI implements Runnable {

    public static void main(String[] args) {
        new CommandLine(new ZsaferCLI()).execute(args);
    }

    public void run() {
        System.out.println("Use -h for help");
    }

    // ================= GET =================
    @Command(name = "get", description = "Fetch and view file")
    static class Get implements Callable<Integer> {

        @Parameters(index = "0")
        String key;

        @Option(names = "-p")
        String password;

        public Integer call() throws Exception {

            String apiToken = Config.get("apiToken");
            String usernameHash = Config.get("usernameHash");

            URL url = new URL("http://localhost:8080/get?key=" + key);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", apiToken);
            conn.setRequestProperty("Username", usernameHash);

            if (password != null) {
                conn.setRequestProperty("Password", password);
            }

            BufferedReader br = new BufferedReader(
                    new InputStreamReader(conn.getInputStream())
            );

            String json = br.readLine();

            ObjectMapper mapper = new ObjectMapper();
            Map<String, String> res = mapper.readValue(json, Map.class);

            String streamUrl = res.get("streamUrl");
            String type = res.get("fileType");
            String token = res.get("token");

            // Launch GUI
            ProcessBuilder pb = new ProcessBuilder(
                    "java", "-jar", "../viewer/target/viewer.jar",
                    "--url=" + streamUrl,
                    "--type=" + type,
                    "--token=" + token
            );

            pb.inheritIO();
            pb.start();

            return 0;
        }
    }
}
