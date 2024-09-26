// src/main/java/org/example/Server.java
package org.example;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

public class Server extends JFrame {

    private static final int SERVER_PORT = 12345;
    private static final String SERVER_FOLDER = "MailServer/";
    private JTextArea logArea;
    private Set<String> connectedClients;
    private Map<String, List<byte[]>> fileDataMap = new HashMap<>();

    public Server() {
        setTitle("Mail Server");
        setSize(600, 500);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        logArea = new JTextArea();
        logArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(logArea);
        add(scrollPane, BorderLayout.CENTER);

        connectedClients = new HashSet<>();

        setVisible(true);
    }

    public void log(String message) {
        SimpleDateFormat formatter = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
        Date date = new Date();
        logArea.append(formatter.format(date) + " - " + message + "\n");
    }

    public static void main(String[] args) throws IOException {
        Server server = new Server();
        DatagramSocket serverSocket = new DatagramSocket(SERVER_PORT, InetAddress.getByName("0.0.0.0"));
        server.log("Mail Server is running...");

        byte[] receiveData = new byte[1024];

        while (true) {
            DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
            serverSocket.receive(receivePacket);
            String request = new String(receivePacket.getData(), 0, receivePacket.getLength());
            InetAddress clientAddress = receivePacket.getAddress();
            int clientPort = receivePacket.getPort();
            String clientInfo = clientAddress.getHostAddress() + ":" + clientPort;

            if (request.equals("CONNECT")) {
                server.connectedClients.add(clientInfo);
                server.log(clientInfo + " - Number of connected clients: " + server.connectedClients.size());
                continue;
            }

            if (request.equals("DISCONNECT")) {
                server.connectedClients.remove(clientInfo);
                server.log(clientInfo + " - Client disconnected. Number of connected clients: " + server.connectedClients.size());
                continue;
            }

            // Handle different requests
            String[] parts = request.split(":", 2);
            String command = parts[0];
            String content = parts.length > 1 ? parts[1] : "";

            switch (command) {
                case "CREATE_ACCOUNT":
                    server.createAccount(content, serverSocket, clientAddress, clientPort);
                    break;
                case "SEND_EMAIL":
                    server.sendEmail(content, clientInfo);
                    break;
                case "SEND_EMAIL_WITH_ATTACHMENT":
                    server.receiveFile(receivePacket, serverSocket, clientAddress, clientPort);
                    break;
                case "DOWNLOAD_FILE":
                    server.downloadFile(content, serverSocket, clientAddress, clientPort);
                    break;
                case "LOGIN":
                    server.sendFileList(content, serverSocket, clientAddress, clientPort, clientInfo);
                    break;
                default:
                    server.sendResponse("Unknown command", serverSocket, clientAddress, clientPort);
                    break;
            }
        }
    }

    private void createAccount(String accountName, DatagramSocket socket, InetAddress address, int port) throws IOException {
        File accountFolder = new File(SERVER_FOLDER + accountName);
        if (!accountFolder.exists()) {
            accountFolder.mkdirs();
            File newEmail = new File(accountFolder, "new_email.txt");
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(newEmail))) {
                writer.write("Welcome to your new account!");
            }
            sendResponse("Account created successfully!", socket, address, port);
            log(address.getHostAddress() + ":" + port + " - Created account: " + accountName);
        } else {
            sendResponse("Account already exists!", socket, address, port);
            log(address.getHostAddress() + ":" + port + " - Account already exists: " + accountName);
        }
    }

    private void sendEmail(String emailData, String clientInfo) {
        String[] data = emailData.split(":", 3);
        if (data.length < 3) {
            log(clientInfo + " - Invalid email data format.");
            return;
        }

        String fromAccount = data[0];
        String toAccount = data[1];
        String emailContent = data[2];

        File accountFolder = new File(SERVER_FOLDER + toAccount);
        if (!accountFolder.exists()) {
            log(clientInfo + " - Account " + toAccount + " does not exist!");
            return;
        }

        File emailFile = new File(accountFolder, "email_from_" + fromAccount + ".txt");
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(emailFile, true))) {
            SimpleDateFormat formatter = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
            Date date = new Date();
            writer.write(formatter.format(date) + " - " + emailContent + "\n");
            log(clientInfo + " - Email sent from " + fromAccount + " to " + toAccount);
        } catch (IOException e) {
            log(clientInfo + " - Failed to send email from " + fromAccount + " to " + toAccount + ": " + e.getMessage());
        }
    }

    private void sendFile(String fileData, DatagramSocket socket, InetAddress address, int port, String clientInfo) throws IOException {
        String[] data = fileData.split(":", 2);
        String accountName = data[0];
        String fileName = data[1];

        File file = new File(SERVER_FOLDER + accountName + "/" + fileName);
        if (file.exists()) {
            byte[] fileBytes = Files.readAllBytes(file.toPath());
            DatagramPacket sendPacket = new DatagramPacket(fileBytes, fileBytes.length, address, port);
            socket.send(sendPacket);
            log(clientInfo + " - Sent file: " + fileName + " to " + accountName);
        } else {
            sendResponse("File not found!", socket, address, port);
            log(clientInfo + " - File not found: " + fileName);
        }
    }

    private void sendFileList(String accountName, DatagramSocket socket, InetAddress address, int port, String clientInfo) throws IOException {
        File accountFolder = new File(SERVER_FOLDER + accountName);
        if (accountFolder.exists()) {
            String[] fileList = accountFolder.list();
            String response = String.join(",", Arrays.asList(fileList != null ? fileList : new String[]{}));
            sendResponse(response, socket, address, port);
            log(clientInfo + " - Sent file list for account: " + accountName);
        } else {
            sendResponse("Account not found!", socket, address, port);
            log(clientInfo + " - Account not found: " + accountName);
        }
    }

    private void sendResponse(String response, DatagramSocket socket, InetAddress address, int port) throws IOException {
        byte[] sendData = response.getBytes();
        DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, address, port);
        socket.send(sendPacket);
    }

    private void handleEmailWithAttachment(String emailData, byte[] fileData, String clientInfo) throws IOException {
        String[] data = emailData.split(":", 4);
        String fromAccount = data[0];
        String toAccount = data[1];
        String emailContent = data[2];
        String fileNameWithContent = data[3];

        // Check if the underscore character exists in the fileNameWithContent
        int firstUnderscoreIndex = fileNameWithContent.indexOf('_');
        if (firstUnderscoreIndex == -1) {
            throw new IllegalArgumentException("Invalid fileNameWithContent format: " + fileNameWithContent);
        }

        String fileName = fileNameWithContent.substring(0, firstUnderscoreIndex);
        String fileContent = fileNameWithContent.substring(firstUnderscoreIndex + 1);

        // Sanitize the file name
        fileName = fileName.replaceAll("[^a-zA-Z0-9\\.\\-_]", "_");

        // Truncate the file name if it exceeds 255 characters
        if (fileName.length() > 255) {
            fileName = fileName.substring(0, 255);
        }

        File accountFolder = new File(SERVER_FOLDER + toAccount);
        if (!accountFolder.exists()) {
            accountFolder.mkdirs(); // Ensure the folder exists
        }

        // Save email content
        File emailFile = new File(accountFolder, "email_from_" + fromAccount + ".txt");
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(emailFile, true))) {
            SimpleDateFormat formatter = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
            Date date = new Date();
            writer.write(formatter.format(date) + " - " + emailContent + "\n");
        }

        // Save attachment file
        File attachmentFile = new File(accountFolder, fileName);
        try (FileOutputStream fos = new FileOutputStream(attachmentFile)) {
            fos.write(fileData);
        }

        // Save the content of the file
        if (!fileContent.isEmpty()) {
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(attachmentFile, true))) {
                writer.write(fileContent);
            }
        }

        log(clientInfo + " - Email with attachment sent from " + fromAccount + " to " + toAccount);
    }

    private void downloadFile(String fileData, DatagramSocket socket, InetAddress address, int port) throws IOException {
        String[] data = fileData.split(":", 2);
        String accountName = data[0];
        String fileName = data[1];

        File file = new File(SERVER_FOLDER + accountName + "/" + fileName);
        if (file.exists()) {
            byte[] fileBytes = Files.readAllBytes(file.toPath());
            DatagramPacket sendPacket = new DatagramPacket(fileBytes, fileBytes.length, address, port);
            socket.send(sendPacket);
            log(address.getHostAddress() + ":" + port + " - Sent file: " + fileName + " to " + accountName);

            // Ensure the Downloads directory exists
            String defaultPath = System.getProperty("user.home") + "/Downloads";
            File downloadsDir = new File(defaultPath);
            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs();
            }

            // Save the file to the specified path
            File downloadedFile = new File(downloadsDir, fileName);
            try (FileOutputStream fos = new FileOutputStream(downloadedFile)) {
                fos.write(fileBytes);
            }
        } else {
            sendResponse("File not found!", socket, address, port);
            log(address.getHostAddress() + ":" + port + " - File not found: " + fileName);
        }
    }

    // Receive file in parts and reassemble
    private void receiveFile(DatagramPacket receivePacket, DatagramSocket socket, InetAddress address, int port) throws IOException {
        String[] metadata = extractMetadata(receivePacket);
        String toAccount = metadata[2]; // Assuming the recipient's account name is the second element
        String fileName = metadata[4];

        try {
            int chunkIndex = Integer.parseInt(metadata[5]);
            int totalChunks = Integer.parseInt(metadata[6]);

            String fileKey = toAccount + "/" + fileName; // Use a unique key for each file

            if (!fileDataMap.containsKey(fileKey)) {
                fileDataMap.put(fileKey, new ArrayList<>(Collections.nCopies(totalChunks, null)));
            }

            List<byte[]> fileChunks = fileDataMap.get(fileKey);

            // Calculate the length of metadata
            int metadataLength = Arrays.stream(metadata).mapToInt(String::length).sum() + (metadata.length - 1); // Add the length of colons

            // Extract the file chunk data
            byte[] chunkData = Arrays.copyOfRange(receivePacket.getData(), metadataLength, receivePacket.getLength());

            fileChunks.set(chunkIndex, chunkData);

            if (fileChunks.stream().allMatch(Objects::nonNull)) {
                assembleFile(toAccount, fileName, fileChunks);
                fileDataMap.remove(fileKey);
            }
        } catch (NumberFormatException e) {
            System.err.println("Failed to parse chunk index or total chunks. Received invalid metadata: " + Arrays.toString(metadata));
        }
    }

    private void assembleFile(String toAccount, String fileName, List<byte[]> fileChunks) throws IOException {
        File accountFolder = new File(SERVER_FOLDER + toAccount);
        if (!accountFolder.exists()) {
            accountFolder.mkdirs(); // Ensure the folder exists
        }

        File outputFile = new File(accountFolder, fileName);
        System.out.println("Saving file to: " + outputFile.getAbsolutePath()); // Debug log

        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
            for (byte[] chunk : fileChunks) {
                fos.write(chunk);
            }
        }
        log("File " + fileName + " has been successfully assembled and saved in " + toAccount + "'s folder.");
    }

    private String[] extractMetadata(DatagramPacket receivePacket) {
        String packetData = new String(receivePacket.getData(), 0, receivePacket.getLength()).trim();
        System.out.println("Extracting metadata from packet data: " + packetData); // Dòng debug

        // Tách dữ liệu gói tin bằng dấu hai chấm, giới hạn số phần tách là 7
        String[] parts = packetData.split(":", 7);

        if (parts.length < 7) {
            System.err.println("Received invalid metadata format: " + Arrays.toString(parts));
            throw new IllegalArgumentException("Invalid metadata format: " + Arrays.toString(parts));
        }

        // Chỉ lấy 7 phần đầu tiên làm metadata
        parts[6] = parts[6].replaceAll("[^0-9]", ""); // Loại bỏ các ký tự không phải số trong phần chunk index và total chunks

        return parts;
    }
}
