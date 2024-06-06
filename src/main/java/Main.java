import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

public class Main {
  public static void main(String[] args) {
    String directory = null;
    if ((args.length == 2) && (args[0].equalsIgnoreCase("--directory"))) {
      directory = args[1];
    }
    
    ServerSocket serverSocket = null;
    Socket clientSocket = null;
    
    try {
      serverSocket = new ServerSocket(4221);
      serverSocket.setReuseAddress(true);
      while (true) {
        clientSocket = serverSocket.accept();
        InputStream input = clientSocket.getInputStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(input));
        String requestLine = reader.readLine();
        // parse first line of http req
        String[] httpRequest = requestLine.split(" ", 0);
        // parse http headers
        Map<String, String> requestHeaders = new HashMap<String, String>();
        String header = null;
        while ((header = reader.readLine()) != null && !header.isEmpty()) {
          String[] hmPair = header.split(":", 2);
          if (hmPair.length == 2) {
            requestHeaders.put(hmPair[0], hmPair[1].trim());
          }
        }
        // parse body
        StringBuffer bodyBuffer = new StringBuffer();
        while (reader.ready()) {
          bodyBuffer.append((char)reader.read());
        }
        String body = bodyBuffer.toString();


        OutputStream output = clientSocket.getOutputStream();
        if (httpRequest[0].equals("GET")) {
          if (httpRequest[1].equals("/")) {

            output.write("HTTP/1.1 200 OK\r\n\r\n".getBytes());
  
          } else if (httpRequest[1].startsWith("/echo/")) {
            String msg = httpRequest[1].substring(6);
            int contentLength = msg.length();
            String contentEncoding = requestHeaders.get("Accept-Encoding");
            if (contentEncoding != null && contentEncoding.contains("gzip")) {
              ByteArrayOutputStream bytearrayoutputstream = new ByteArrayOutputStream();
              try (GZIPOutputStream gzipoutputstream = new GZIPOutputStream(bytearrayoutputstream)) {
                gzipoutputstream.write(msg.getBytes());
              }
              byte[] gzipData = bytearrayoutputstream.toByteArray();
              String response = "HTTP/1.1 200 OK\r\nContent-Encoding: gzip\r\nContent-Type: text/plain\r\nContent-Length: " + gzipData.length + "\r\n\r\n";
              output.write(response.getBytes());
              output.write(gzipData);
            } else {
              String response = "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: " + contentLength + "\r\n\r\n" + msg;
              output.write(response.getBytes());
            }
  
          } else if (httpRequest[1].equals("/user-agent")) {
            String userAgent = requestHeaders.get("User-Agent");
            String response = "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: " + userAgent.length() + "\r\n\r\n" + userAgent;
            output.write(response.getBytes());
  
          } else if (httpRequest[1].startsWith("/files/")) {
            String filename = httpRequest[1].substring(7);
            File file = new File(directory, filename);
            if (file.exists()) {
              String fileContent = Files.readString(file.toPath());
              String response = "HTTP/1.1 200 OK\r\nContent-Type: application/octet-stream\r\nContent-Length: " + fileContent.length() + "\r\n\r\n" + fileContent;
              output.write(response.getBytes());
            } else {
              output.write("HTTP/1.1 404 Not Found\r\n\r\n".getBytes());
            }
          } else {

            output.write("HTTP/1.1 404 Not Found\r\n\r\n".getBytes());
  
          }
        } else if (httpRequest[0].equals("POST")) {
          if (httpRequest[1].startsWith("/files/")) {
            String filename = httpRequest[1].substring(7);
            File file = new File(directory, filename);
            if (file.createNewFile()) {
              FileWriter filewriter = new FileWriter(file);
              filewriter.write(body);
              filewriter.close();
            }
            output.write("HTTP/1.1 201 Created\r\n\r\n".getBytes());
          } else {
            output.write("HTTP/1.1 404 Not Found\r\n\r\n".getBytes());
          }
        }

        System.out.println("accepted new connection");
      }
    } catch (IOException e) {
      System.out.println("IOException: " + e.getMessage());
    }
  }
}