// src/main/java/org/example/Client.java
package org.example;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

public class Client extends JFrame {

    private static final int SERVER_PORT = 12345;
    private DatagramSocket clientSocket;
    private InetAddress serverAddress;
    private String accountName;
    private File selectedFile;

    public Client() {
        try {
            clientSocket = new DatagramSocket();
            String serverIp = JOptionPane.showInputDialog(this, "Enter the server IP address:", "Server IP", JOptionPane.QUESTION_MESSAGE);
            serverAddress = InetAddress.getByName(serverIp);
            sendRequest("CONNECT");
        } catch (SocketException | UnknownHostException e) {
            JOptionPane.showMessageDialog(this, "Failed to connect to server: " + e.getMessage(), "Connection Error", JOptionPane.ERROR_MESSAGE);
        }

        setTitle("Mail Client");
        setSize(400, 400);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JPanel panel = new JPanel();
        panel.setLayout(new GridLayout(3, 1));
        panel.setBackground(Color.LIGHT_GRAY);

        JButton registerButton = new JButton("Register");
        registerButton.setBackground(Color.CYAN);
        registerButton.addActionListener(e -> new RegisterFrame(clientSocket, serverAddress));

        JButton loginButton = new JButton("Login");
        loginButton.setBackground(Color.GREEN);
        loginButton.addActionListener(e -> new LoginFrame(clientSocket, serverAddress));

        panel.add(registerButton);
        panel.add(loginButton);

        add(panel);
        setLocationRelativeTo(null);
        setVisible(true);
        setState(JFrame.ICONIFIED);

        // Close socket when main window is closed
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                if (clientSocket != null && !clientSocket.isClosed()) {
                    clientSocket.close();
                }
            }
        });
    }

    public static void main(String[] args) {
        new Client();
    }

    private void sendRequest(String request) {
        try {
            byte[] sendData = request.getBytes();
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, serverAddress, SERVER_PORT);
            clientSocket.send(sendPacket);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Failed to send request: " + e.getMessage(), "Request Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void sendRequest(String request, byte[] fileData) {
        try {
            byte[] requestData = request.getBytes();
            byte[] sendData = new byte[requestData.length + fileData.length];
            System.arraycopy(requestData, 0, sendData, 0, requestData.length);
            System.arraycopy(fileData, 0, sendData, requestData.length, fileData.length);
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, serverAddress, SERVER_PORT);
            clientSocket.send(sendPacket);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    class RegisterFrame extends JFrame {

        private DatagramSocket clientSocket;
        private InetAddress serverAddress;
        private JTextField accountField;

        public RegisterFrame(DatagramSocket clientSocket, InetAddress serverAddress) {
            this.clientSocket = clientSocket;
            this.serverAddress = serverAddress;

            setTitle("Register");
            setSize(300, 200);
            setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

            JPanel panel = new JPanel();
            panel.setLayout(new GridLayout(2, 1));

            accountField = new JTextField(20);
            JButton createAccountButton = new JButton("Create Account");
            createAccountButton.addActionListener(e -> sendRequest("CREATE_ACCOUNT:" + accountField.getText()));

            panel.add(new JLabel("Enter account name:"));
            panel.add(accountField);
            panel.add(createAccountButton);

            setLocationRelativeTo(null);
            add(panel);
            setVisible(true);
        }

        private void sendRequest(String request) {
            try {
                byte[] sendData = request.getBytes();
                DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, serverAddress, SERVER_PORT);
                clientSocket.send(sendPacket);
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, "Failed to send request: " + e.getMessage(), "Request Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    class LoginFrame extends JFrame {

        private DatagramSocket clientSocket;
        private InetAddress serverAddress;
        private JTextField accountField;

        public LoginFrame(DatagramSocket clientSocket, InetAddress serverAddress) {
            this.clientSocket = clientSocket;
            this.serverAddress = serverAddress;

            setTitle("Login");
            setSize(300, 200);
            setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

            JPanel panel = new JPanel();
            panel.setLayout(new GridLayout(2, 1));

            accountField = new JTextField(20);
            JButton loginButton = new JButton("Login");
            loginButton.addActionListener(e -> login());

            panel.add(new JLabel("Enter account name:"));
            panel.add(accountField);
            panel.add(loginButton);

            setLocationRelativeTo(null);
            add(panel);
            setVisible(true);
        }

        private void login() {
            accountName = accountField.getText();
            sendRequest("LOGIN:" + accountName);

            // Receive file list
            try {
                byte[] receiveData = new byte[1024];
                DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                clientSocket.receive(receivePacket);
                String fileList = new String(receivePacket.getData(), 0, receivePacket.getLength());

                JOptionPane.showMessageDialog(this, "Files in account: " + fileList);
                new MainFrame(clientSocket, serverAddress, accountName, fileList);
                dispose();
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, "Failed to receive file list: " + e.getMessage(), "Receive Error", JOptionPane.ERROR_MESSAGE);
            }
        }

        private void sendRequest(String request) {
            try {
                byte[] sendData = request.getBytes();
                DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, serverAddress, SERVER_PORT);
                clientSocket.send(sendPacket);
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, "Failed to send request: " + e.getMessage(), "Request Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    class MainFrame extends JFrame {

        private DatagramSocket clientSocket;
        private InetAddress serverAddress;
        private String accountName;
        private JTextArea emailContent;
        private JTextField toField;

        public MainFrame(DatagramSocket clientSocket, InetAddress serverAddress, String accountName, String fileList) {
            this.clientSocket = clientSocket;
            this.serverAddress = serverAddress;
            this.accountName = accountName;

            setTitle("Mail Client - " + accountName);
            setSize(500, 500);
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            setLayout(new BorderLayout());

            // Main panel for email input
            JPanel panel = new JPanel();
            panel.setLayout(new GridLayout(7, 1, 10, 10));
            panel.setBackground(Color.WHITE);
            panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

            // Create and style labels and fields
            JLabel toLabel = createLabel("To (Account):");
            toField = createTextField();

            JLabel contentLabel = createLabel("Email Content:");
            emailContent = createTextArea();

            JButton sendEmailButton = createButton("Send Email", Color.BLUE, Color.WHITE, e -> sendEmail());
            JButton attachFileButton = createButton("Attach File", Color.ORANGE, Color.BLACK, e -> selectFile());

            // Adding components to panel
            panel.add(toLabel);
            panel.add(toField);
            panel.add(contentLabel);
            panel.add(new JScrollPane(emailContent));
            panel.add(attachFileButton);
            panel.add(sendEmailButton);

            add(panel, BorderLayout.CENTER);
            updateFileList(fileList);

            setLocationRelativeTo(null);
            setVisible(true);
        }

        private JLabel createLabel(String text) {
            JLabel label = new JLabel(text);
            label.setFont(new Font("Arial", Font.BOLD, 14));
            label.setForeground(Color.DARK_GRAY);
            return label;
        }

        private JTextField createTextField() {
            JTextField textField = new JTextField(20);
            textField.setFont(new Font("Arial", Font.PLAIN, 14));
            textField.setBorder(BorderFactory.createLineBorder(Color.GRAY, 1));
            return textField;
        }

        private JTextArea createTextArea() {
            JTextArea textArea = new JTextArea(5, 20);
            textArea.setFont(new Font("Arial", Font.PLAIN, 14));
            textArea.setBorder(BorderFactory.createLineBorder(Color.GRAY, 1));
            textArea.setLineWrap(true);
            textArea.setWrapStyleWord(true);
            return textArea;
        }

        private JButton createButton(String text, Color bgColor, Color fgColor, ActionListener action) {
            JButton button = new JButton(text);
            button.setBackground(bgColor);
            button.setForeground(fgColor);
            button.setFont(new Font("Arial", Font.BOLD, 14));
            button.setFocusPainted(false);
            button.addActionListener(action);
            button.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20)); // Padding
            button.setCursor(new Cursor(Cursor.HAND_CURSOR)); // Hand cursor
            return button;
        }

        private void selectFile() {
            JFileChooser fileChooser = new JFileChooser();
            int returnValue = fileChooser.showOpenDialog(this);
            if (returnValue == JFileChooser.APPROVE_OPTION) {
                selectedFile = fileChooser.getSelectedFile();
                JOptionPane.showMessageDialog(this, "Selected file: " + selectedFile.getName());
            }
        }

        private void sendEmail() {
            String toAccount = toField.getText();
            String content = emailContent.getText();
            if (content.isEmpty()) {
                content = "No content"; // Default value if content is empty
            }

            if (selectedFile != null) {
                try {
                    byte[] fileData = Files.readAllBytes(selectedFile.toPath());
                    String fileName = selectedFile.getName();
                    int chunkSize = 1024; // Size of each chunk (max 1024 bytes)
                    int totalChunks = (int) Math.ceil((double) fileData.length / chunkSize);

                    for (int i = 0; i < totalChunks; i++) {
                        int start = i * chunkSize;
                        int length = Math.min(chunkSize, fileData.length - start);
                        byte[] fileChunk = new byte[length];
                        System.arraycopy(fileData, start, fileChunk, 0, length);

                        // Create metadata for each chunk (includes fileName, chunk index, and total chunks)
                        String request = String.format("SEND_EMAIL_WITH_ATTACHMENT:%s:%s:%s:%s:%d:%d",
                                accountName, toAccount, content, fileName, i, totalChunks);

                        System.out.println("Sending request: " + request);
                        sendRequest(request, fileChunk);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            // Send only the message if no file is selected
            String request = String.format("SEND_EMAIL:%s:%s:%s", accountName, toAccount, content);
            sendRequest(request);
        }

        private void updateFileList(String fileList) {
            String[] files = fileList.split(",");
            JPanel filePanel = new JPanel();
            filePanel.setLayout(new GridLayout(files.length, 1));
            filePanel.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20)); // Padding

            for (String file : files) {
                JButton fileButton = createButton(file, Color.LIGHT_GRAY, Color.BLACK, e -> downloadFile(file));
                filePanel.add(fileButton);
            }

            add(filePanel, BorderLayout.SOUTH);
            revalidate();
            repaint();
        }

        private void downloadFile(String fileName) {
            sendRequest("DOWNLOAD_FILE:" + accountName + ":" + fileName);
            try {
                byte[] receiveData = new byte[1024];
                DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                clientSocket.receive(receivePacket);

                try (FileOutputStream fos = new FileOutputStream(fileName)) {
                    fos.write(receivePacket.getData(), 0, receivePacket.getLength());
                    JOptionPane.showMessageDialog(this, "File downloaded successfully!");
                }
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, "Failed to download file: " + e.getMessage(), "Download Error", JOptionPane.ERROR_MESSAGE);
            }
        }

        private void sendRequest(String request) {
            try {
                byte[] sendData = request.getBytes();
                DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, serverAddress, SERVER_PORT);
                clientSocket.send(sendPacket);
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, "Failed to send request: " + e.getMessage(), "Request Error", JOptionPane.ERROR_MESSAGE);
            }
        }

        private void sendRequest(String request, byte[] fileData) {
            try {
                byte[] requestData = request.getBytes(StandardCharsets.UTF_8);
                byte[] sendData = new byte[requestData.length + fileData.length];
                System.arraycopy(requestData, 0, sendData, 0, requestData.length);
                System.arraycopy(fileData, 0, sendData, requestData.length, fileData.length);

                DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, serverAddress, SERVER_PORT);
                clientSocket.send(sendPacket);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}