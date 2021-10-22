import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class TCPServer {
    public static final int SERVER_PORT = 8080;
    public static final int STATUS404 = 1; // wrong url
    public static final int STATUS400 = 2; // wrong expression
    public static final long DAY = 86400000;
    public static final long HOUR = 3600000;
    public static final long MINUTE = 60000;
    private static final long start = System.currentTimeMillis();

    private static List<String> exps = new ArrayList<>(); // will 400 count?
    private static Set<Long> evalSet = new HashSet<>();
    private static Set<Long> timeSet = new HashSet<>();

    private static final String HTML_START =
            "<html>" +
                    "<title>HTTP Server in java</title>" +
                    "<body>";
    private static final String HTML_END =
            "</body>" +
                    "</html>";

    public static void main(String... args) throws IOException {
        ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
        while(true){
            Socket clientSocket = serverSocket.accept();
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        InputStream in = clientSocket.getInputStream();
                        OutputStream out = clientSocket.getOutputStream();

                        byte[] bytes = new byte[16];
                        int len = in.read(bytes, 0, 16);
                        StringBuffer sb = new StringBuffer();
                        while (len >= 16) {
                            sb.append(new String(bytes, 0, len));
                            len = in.read(bytes, 0, 16);
                        }
                        sb.append(new String(bytes, 0, len));
                        String request = sb.toString();
                        String[] str = request.split("\\R");
                        String htmlPath = str[0].split(" ")[1].substring(1);
                        String method = str[0].split(" ")[0];
                        String httpVersion = str[0].split(" ")[2];
                        int flag = 0;
                        String res = "";
                        String data = "";
                        if (htmlPath.startsWith("api")) {// evalexpression
                            if(htmlPath.length() < 4 )
                                flag = STATUS404;
                            else if (htmlPath.substring(4).equals("evalexpression")) {
                                String contentLen = str[1].split(" ")[1];
                                String s = str[str.length - 1];
                                Deque<Integer> ops = new ArrayDeque<>();
                                ops.push(1);
                                int sign = 1;
                                int ret = 0;
                                int i = 0;
                                while (i < s.length()) {
                                    if (s.charAt(i) != '+' && s.charAt(i) != '-'
                                            && s.charAt(i) != '(' && s.charAt(i) != ')'
                                            && !Character.isDigit(s.charAt(i))) {
                                        flag = STATUS400;
                                        break;
                                    }
                                    if (s.charAt(i) == '+') {
                                        sign = ops.peek();
                                        i++;
                                    } else if (s.charAt(i) == '-') {
                                        sign = -ops.peek();
                                        i++;
                                    } else if (s.charAt(i) == '(') {
                                        ops.push(sign);
                                        i++;
                                    } else if (s.charAt(i) == ')') {
                                        ops.pop();
                                        i++;
                                    } else {
                                        long num = 0;
                                        while (i < s.length() && Character.isDigit(s.charAt(i))) {
                                            num = num * 10 + s.charAt(i) - '0';
                                            i++;
                                        }
                                        ret += sign * num;
                                    }
                                }
                                if (flag != STATUS400) {
                                    data = "" + ret;
                                    evalSet.add(System.currentTimeMillis());
                                    exps.add(s);
                                }
                            }
                            // get time
                            else if (htmlPath.substring(4).equals("gettime")) {
                                DateTimeFormatter dtf = DateTimeFormatter.ofPattern("uuuu/MM/dd HH:mm:ss");
                                LocalDateTime now = LocalDateTime.now();
                                data = dtf.format(now);
                                timeSet.add(System.currentTimeMillis());
                            }
                            else {
                                flag = STATUS404;
                            }

                        } else if (htmlPath.startsWith("status")) { // status.html
                            flag = 0;
                            data += HTML_START;
                            long evalCount = 0;
                            long dayCount = 0;
                            long hourCount = 0;
                            long minCount = 0;
                            for (Long time : evalSet) {
                                long diff = time - start;
                                if (diff < MINUTE) {
                                    minCount++;
                                }
                                if (diff < HOUR) {
                                    hourCount++;
                                }
                                if (diff < DAY) {
                                    dayCount++;
                                }
                                evalCount++;
                            }
                            data += "<h3>/api/evalexpression</h3>\n" +
                                    "\t<ul>\n" +
                                    "\t  <li>last minute: " + minCount + "</li>\n" +
                                    "\t  <li>last hour: " + hourCount + "</li>\n" +
                                    "\t  <li>last 24 hours: " + dayCount + "</li>\n" +
                                    "\t  <li>lifetime: " + evalCount + "</li>\n" +
                                    "\t</ul>\n";
                            long timeCount = 0;
                            long dayCount1 = 0;
                            long hourCount1 = 0;
                            long minCount1 = 0;
                            for (Long time : timeSet) {
                                long diff = time - start;
                                if (diff < MINUTE) {
                                    minCount1++;
                                }
                                if (diff < HOUR) {
                                    hourCount1++;
                                }
                                if (diff < DAY) {
                                    dayCount1++;
                                }
                                timeCount++;
                            }
                            data += "<h3>/api/gettime</h3>\n" +
                                    "\t<ul>\n" +
                                    "\t  <li>last minute: " + minCount1 + "</li>\n" +
                                    "\t  <li>last hour: " + hourCount1 + "</li>\n" +
                                    "\t  <li>last 24 hours: " + dayCount1 + "</li>\n" +
                                    "\t  <li>lifetime: " + timeCount + "</li>\n" +
                                    "\t</ul>\n";
                            data += "\t<h1>Last 10 expressions</h1>\n" +
                                    "\t<ul>\n";
                            for (String exp : exps) {
                                data += "<li>" + exp + "</li>\n";
                            }
                            data += "</ul>";
                            data += HTML_END;
                        } else {
                            flag = STATUS404;
                        }

                        if (flag == 0) {
                            res += httpVersion + " 200 OK\\R";
                            res += "Content-Type:text/html\r\n";
                            res += "Content-Length: " + data.getBytes().length + "\r\n";
                            res += "\r\n";
                            res += data;
                        } else if (flag == STATUS404) {
                            data = "<html>\n" +
                                    "<head><title>404 Not Found</title" +
                                    "></head>\n" +
                                    "<body bgcolor=\"white\">\n" +
                                    "<center><h1>404 Not Found</h1></center>\n" +
                                    "</body>\n" +
                                    "</html>";
                            res += httpVersion + " 404 Not Found\r\n";
                            res += "Content-Type:text/html\r\n";
                            res += "Content-Length: " + data.getBytes().length + "\r\n";
                            res += "\r\n";
                            res += data;
                        } else if (flag == STATUS400) {
                            data = "<html>\n" +
                                    "<head><title>400 Bad Request</title></head>\n" +
                                    "<body bgcolor=\"white\">\n" +
                                    "<center><h1>400 Bad Request</h1></center>\n" +
                                    "</body>\n" +
                                    "</html>";
                            res += httpVersion + " 400 Bad Request\r\n";
                            res += "Content-Type:text/html\r\n";
                            res += "Content-Length: " + data.getBytes().length + "\r\n";
                            res += "\r\n";
                            res += data;
                        }
                        byte[] resBytes = res.getBytes();
                        int ansLen = resBytes.length;
                        int c = 0;
                        for (; ansLen > 0; ) {
                            if (ansLen <= 16) {
                                for (int j = 0; j < ansLen; j++) {
                                    bytes[j] = resBytes[j+c];
                                }
                                out.write(bytes, 0, ansLen);
                                break;
                            } else {
                                for (int j = 0; j < 16; j++) {
                                    bytes[j] = resBytes[j + c];
                                }
                                ansLen -= 16;
                                c += 16;
                                out.write(bytes, 0, 16);
                            }
                        }
                    clientSocket.close();
                }
                catch (IOException e) {
                    e.printStackTrace();
                }
            }
            }).start();
        }
    }
}

    //finally {
    //            serverSocket.close();
    //        }
